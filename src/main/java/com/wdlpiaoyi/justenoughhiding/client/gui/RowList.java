package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** A scrollable, selectable list of rows rendered through {@link Column}s. */
public final class RowList<T> implements Renderable
{
    private static final int ROW_HEIGHT = 18;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_FLEX_WIDTH = 20;

    public interface RowRightClickListener<T>
    {
        void onRightClick(T row, double mouseX, double mouseY);
    }

    private final Font font;

    private int x;
    private int y;
    private int width;
    private int height;

    private List<Column<T>> columns = List.of();
    private List<T> rows = List.of();
    private T selected;
    private final Set<T> selection = Collections.newSetFromMap(new IdentityHashMap<>());
    private T anchor;
    private boolean multiSelect;
    private T pressedRow;
    private RowRightClickListener<T> rightClickListener;
    private int scroll;
    private boolean dragging;
    private boolean hoverEnabled = true;

    public RowList(Font font)
    {
        this.font = font;
    }

    public void setBounds(int x, int y, int width, int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scroll = clampScroll(this.scroll);
    }

    public void setColumns(List<Column<T>> columns)
    {
        this.columns = columns;
    }

    public void setRows(List<T> rows)
    {
        this.rows = rows;
        this.scroll = clampScroll(this.scroll);
        selection.removeIf(row -> !containsIdentity(row));
        if (anchor != null && !containsIdentity(anchor))
        {
            anchor = null;
        }
        if (selected != null && !containsIdentity(selected))
        {
            selected = null;
        }
        if (selected == null && !selection.isEmpty())
        {
            selected = selection.iterator().next();
        }
    }

    private boolean containsIdentity(T row)
    {
        for (T candidate : rows)
        {
            if (candidate == row)
            {
                return true;
            }
        }
        return false;
    }

    public void setMultiSelectEnabled(boolean multiSelect)
    {
        this.multiSelect = multiSelect;
    }

    public List<T> getSelection()
    {
        return new ArrayList<>(selection);
    }

    public boolean isSelected(T row)
    {
        return selection.contains(row);
    }

    public void clearSelection()
    {
        selection.clear();
        selected = null;
        anchor = null;
    }

    public void setRightClickListener(RowRightClickListener<T> rightClickListener)
    {
        this.rightClickListener = rightClickListener;
    }

    public T getSelected()
    {
        return selected;
    }

    public void setHoverEnabled(boolean hoverEnabled)
    {
        this.hoverEnabled = hoverEnabled;
    }

    public int indexOf(T row)
    {
        for (int i = 0; i < rows.size(); i++)
        {
            if (rows.get(i) == row)
            {
                return i;
            }
        }
        return rows.indexOf(row);
    }

    /** Screen-space bounds of a cell, or null when the row/column is not currently visible. */
    public Rect2i cellBounds(int rowIndex, int columnIndex)
    {
        if (rowIndex < scroll || rowIndex >= scroll + visibleRows())
        {
            return null;
        }
        if (columnIndex < 0 || columnIndex >= columns.size())
        {
            return null;
        }
        int[] widths = columnWidths(x + width - SCROLLBAR_WIDTH - x);
        int left = x + 2;
        for (int i = 0; i < columnIndex; i++)
        {
            left += widths[i];
        }
        int rowY = y + (rowIndex - scroll) * ROW_HEIGHT;
        return new Rect2i(left, rowY, widths[columnIndex], ROW_HEIGHT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        guiGraphics.fill(x, y, x + width, y + height, 0xC0101010);

        guiGraphics.enableScissor(x, y, x + width, y + height);
        int visible = visibleRows();
        for (int i = 0; i < visible; i++)
        {
            int index = scroll + i;
            if (index >= rows.size())
            {
                break;
            }
            int rowY = y + i * ROW_HEIGHT;
            int rowBottom = rowY + ROW_HEIGHT;
            int rowRight = x + width - SCROLLBAR_WIDTH;
            boolean hovered = hoverEnabled && mouseX >= x && mouseX < rowRight && mouseY >= rowY && mouseY < rowBottom;

            T row = rows.get(index);
            if (isSelected(row))
            {
                guiGraphics.fill(x, rowY, rowRight, rowBottom, 0xFF3050A0);
            }
            else if (hovered)
            {
                guiGraphics.fill(x, rowY, rowRight, rowBottom, 0x40FFFFFF);
            }
            renderColumns(guiGraphics, row, rowY, rowRight - x);
        }
        guiGraphics.disableScissor();

        drawScrollbar(guiGraphics);
    }

    private void renderColumns(GuiGraphics guiGraphics, T row, int rowTop, int rowWidth)
    {
        if (columns.isEmpty())
        {
            return;
        }

        int[] widths = columnWidths(rowWidth);
        int left = x + 2;
        for (int i = 0; i < columns.size(); i++)
        {
            columns.get(i).render(guiGraphics, font, row, left, rowTop, widths[i], ROW_HEIGHT);
            left += widths[i];
        }
    }

    private int[] columnWidths(int rowWidth)
    {
        int usableWidth = Math.max(MIN_FLEX_WIDTH, rowWidth - 4);
        int fixedWidth = 0;
        int flexCount = 0;
        for (Column<T> column : columns)
        {
            if (column.flexible())
            {
                flexCount++;
            }
            else
            {
                fixedWidth += column.width();
            }
        }
        int flexWidth = flexCount > 0 ? Math.max(MIN_FLEX_WIDTH, (usableWidth - fixedWidth) / flexCount) : 0;

        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++)
        {
            Column<T> column = columns.get(i);
            widths[i] = column.flexible() ? flexWidth : column.width();
        }
        return widths;
    }

    private void drawScrollbar(GuiGraphics guiGraphics)
    {
        int maxScroll = maxScroll();
        if (maxScroll <= 0)
        {
            return;
        }
        int barX = x + width - SCROLLBAR_WIDTH;
        int thumbHeight = thumbHeight();
        int thumbY = y + (height - thumbHeight) * scroll / maxScroll;
        guiGraphics.fill(barX, y, barX + SCROLLBAR_WIDTH, y + height, 0x40000000);
        guiGraphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFA0A0A0);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height)
        {
            return false;
        }

        if (button == 1)
        {
            T row = rowAt((int) mouseX, (int) mouseY);
            if (row != null)
            {
                pressedRow = row;
                if (!selection.contains(row))
                {
                    selection.clear();
                    selection.add(row);
                }
                selected = row;
                anchor = row;
                return true;
            }
            return false;
        }
        if (button != 0)
        {
            return false;
        }

        int barX = x + width - SCROLLBAR_WIDTH;
        if (mouseX >= barX && maxScroll() > 0)
        {
            dragging = true;
            scrollToMouse(mouseY);
            return true;
        }

        int index = scroll + (int) ((mouseY - y) / ROW_HEIGHT);
        T row = (index >= 0 && index < rows.size()) ? rows.get(index) : null;

        if (!multiSelect || row == null)
        {
            selection.clear();
            if (row != null)
            {
                selection.add(row);
            }
            selected = row;
            anchor = row;
            return true;
        }

        boolean shift = Screen.hasShiftDown();
        boolean control = Screen.hasControlDown();
        if (shift && anchor != null)
        {
            int anchorIndex = indexOf(anchor);
            if (anchorIndex >= 0)
            {
                int from = Math.min(anchorIndex, index);
                int to = Math.max(anchorIndex, index);
                selection.clear();
                for (int i = from; i <= to; i++)
                {
                    selection.add(rows.get(i));
                }
            }
            else
            {
                selection.clear();
                selection.add(row);
                anchor = row;
            }
        }
        else if (control)
        {
            if (!selection.remove(row))
            {
                selection.add(row);
            }
            anchor = row;
        }
        else
        {
            selection.clear();
            selection.add(row);
            anchor = row;
        }
        selected = row;
        return true;
    }

    public void mouseDragged(double mouseX, double mouseY, int button)
    {
        if (dragging)
        {
            scrollToMouse(mouseY);
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        dragging = false;

        if (button == 1 && pressedRow != null)
        {
            T pressed = pressedRow;
            pressedRow = null;
            T released = rowAt((int) mouseX, (int) mouseY);
            if (released == pressed && rightClickListener != null)
            {
                rightClickListener.onRightClick(pressed, mouseX, mouseY);
            }
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height)
        {
            return false;
        }
        scroll = clampScroll(scroll - (int) Math.round(delta * 3));
        return true;
    }

    /** Column index under the given screen X, or -1 when outside the row area. */
    public int columnAt(int mouseX)
    {
        if (columns.isEmpty() || mouseX < x || mouseX >= x + width - SCROLLBAR_WIDTH)
        {
            return -1;
        }
        int[] widths = columnWidths(x + width - SCROLLBAR_WIDTH - x);
        int left = x + 2;
        for (int i = 0; i < widths.length; i++)
        {
            if (mouseX >= left && mouseX < left + widths[i])
            {
                return i;
            }
            left += widths[i];
        }
        return -1;
    }

    public T rowAt(int mouseX, int mouseY)
    {
        int barX = x + width - SCROLLBAR_WIDTH;
        if (mouseX < x || mouseX >= barX || mouseY < y || mouseY >= y + height)
        {
            return null;
        }
        int index = scroll + (int) ((mouseY - y) / ROW_HEIGHT);
        return (index >= 0 && index < rows.size()) ? rows.get(index) : null;
    }

    public T rowAtIcon(int mouseX, int mouseY)
    {
        if (columns.isEmpty() || !columns.get(0).isIcon())
        {
            return null;
        }
        int iconRight = x + columns.get(0).width();
        if (mouseX < x || mouseX >= iconRight || mouseY < y || mouseY >= y + height)
        {
            return null;
        }
        int index = scroll + (int) ((mouseY - y) / ROW_HEIGHT);
        return (index >= 0 && index < rows.size()) ? rows.get(index) : null;
    }

    private int visibleRows()
    {
        return Math.max(1, height / ROW_HEIGHT);
    }

    private int maxScroll()
    {
        return Math.max(0, rows.size() - visibleRows());
    }

    private int thumbHeight()
    {
        return Math.max(10, height * visibleRows() / Math.max(1, rows.size()));
    }

    private int clampScroll(int value)
    {
        return Math.max(0, Math.min(maxScroll(), value));
    }

    private void scrollToMouse(double mouseY)
    {
        int thumbHeight = thumbHeight();
        double ratio = (mouseY - y - thumbHeight / 2.0) / Math.max(1, height - thumbHeight);
        scroll = clampScroll((int) Math.round(ratio * maxScroll()));
    }
}

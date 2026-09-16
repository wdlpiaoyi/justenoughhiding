package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

import java.util.List;

public final class IntentList implements Renderable
{
    private static final int ROW_HEIGHT = 18;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_FLEX_WIDTH = 20;

    private final Font font;

    private int x;
    private int y;
    private int width;
    private int height;

    private List<Column> columns = List.of();
    private List<Intent> rows = List.of();
    private Intent selected;
    private int scroll;
    private boolean dragging;
    private boolean hoverEnabled = true;

    public IntentList(Font font)
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

    public void setColumns(List<Column> columns)
    {
        this.columns = columns;
    }

    public void setRows(List<Intent> rows)
    {
        this.rows = rows;
        this.scroll = clampScroll(this.scroll);
        if (this.selected != null && !rows.contains(this.selected))
        {
            this.selected = null;
        }
    }

    public Intent getSelected()
    {
        return selected;
    }

    public void setHoverEnabled(boolean hoverEnabled)
    {
        this.hoverEnabled = hoverEnabled;
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

            Intent intent = rows.get(index);
            if (intent == selected)
            {
                guiGraphics.fill(x, rowY, rowRight, rowBottom, 0xFF3050A0);
            }
            else if (hovered)
            {
                guiGraphics.fill(x, rowY, rowRight, rowBottom, 0x40FFFFFF);
            }
            renderColumns(guiGraphics, intent, rowY, rowRight - x);
        }
        guiGraphics.disableScissor();

        drawScrollbar(guiGraphics);
    }

    private void renderColumns(GuiGraphics guiGraphics, Intent intent, int rowTop, int rowWidth)
    {
        if (columns.isEmpty())
        {
            return;
        }

        int usableWidth = Math.max(MIN_FLEX_WIDTH, rowWidth - 4);
        int fixedWidth = 0;
        int flexCount = 0;
        for (Column column : columns)
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

        int left = x + 2;
        for (Column column : columns)
        {
            int columnWidth = column.flexible() ? flexWidth : column.width();
            column.render(guiGraphics, font, intent, left, rowTop, columnWidth, ROW_HEIGHT);
            left += columnWidth;
        }
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
        if (button != 0 || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height)
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
        selected = (index >= 0 && index < rows.size()) ? rows.get(index) : null;
        return true;
    }

    public void mouseDragged(double mouseX, double mouseY, int button)
    {
        if (dragging)
        {
            scrollToMouse(mouseY);
        }
    }

    public void mouseReleased()
    {
        dragging = false;
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

    public Intent intentAt(int mouseX, int mouseY)
    {
        int barX = x + width - SCROLLBAR_WIDTH;
        if (mouseX < x || mouseX >= barX || mouseY < y || mouseY >= y + height)
        {
            return null;
        }
        int index = scroll + (int) ((mouseY - y) / ROW_HEIGHT);
        return (index >= 0 && index < rows.size()) ? rows.get(index) : null;
    }

    public Intent intentAtIcon(int mouseX, int mouseY)
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

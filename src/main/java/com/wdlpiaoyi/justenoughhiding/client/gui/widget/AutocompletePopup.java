package com.wdlpiaoyi.justenoughhiding.client.gui.widget;

import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

import java.util.List;

/**
 * Target editor helper: a kind tab row ({@code Auto/Item/Recipe/Category}) on top of a
 * text-only suggestion list. Always active while a target is being edited, even when the
 * suggestion list is empty, so the kind tabs stay reachable.
 */
public final class AutocompletePopup implements Renderable
{
    private static final int ROW_HEIGHT = 12;
    private static final int TAB_HEIGHT = 12;
    private static final String[] TAB_LABELS = {"Auto", "Item", "Recipe", "Category"};
    private static final String[] TAB_KEYS = {"", "ingredient", "recipe", "recipe_category"};

    private final Font font;
    private final int maxRows;

    private int x;
    private int y;
    private int width;
    private boolean active;
    private int kindIndex;
    private List<TargetSuggestion> suggestions = List.of();
    private int highlighted = -1;
    private int scroll;

    public AutocompletePopup(Font font, int x, int y, int width, int maxRows)
    {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.maxRows = maxRows;
    }

    public void setPosition(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }

    public void setActive(boolean active)
    {
        this.active = active;
        if (!active)
        {
            this.suggestions = List.of();
            this.highlighted = -1;
            this.scroll = 0;
        }
    }

    public boolean isVisible()
    {
        return active;
    }

    public boolean hasSuggestions()
    {
        return !suggestions.isEmpty();
    }

    public List<TargetSuggestion> getSuggestions()
    {
        return suggestions;
    }

    public void setSuggestions(List<TargetSuggestion> suggestions)
    {
        this.suggestions = suggestions == null ? List.of() : suggestions;
        this.highlighted = this.suggestions.isEmpty() ? -1 : 0;
        this.scroll = 0;
    }

    public int getKindIndex()
    {
        return kindIndex;
    }

    public void setKindIndex(int kindIndex)
    {
        this.kindIndex = Math.max(0, Math.min(TAB_LABELS.length - 1, kindIndex));
    }

    public String getKindKey()
    {
        return TAB_KEYS[kindIndex];
    }

    public TargetSuggestion getHighlighted()
    {
        return highlighted >= 0 && highlighted < suggestions.size() ? suggestions.get(highlighted) : null;
    }

    public void moveHighlight(int delta)
    {
        if (suggestions.isEmpty())
        {
            return;
        }
        int size = suggestions.size();
        highlighted = ((highlighted + delta) % size + size) % size;
        if (highlighted < scroll)
        {
            scroll = highlighted;
        }
        if (highlighted >= scroll + visibleRows())
        {
            scroll = highlighted - visibleRows() + 1;
        }
    }

    public int getHeight()
    {
        return TAB_HEIGHT + visibleRows() * ROW_HEIGHT;
    }

    public boolean isOver(double mouseX, double mouseY)
    {
        return active
            && mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + getHeight();
    }

    /** Kind tab under the cursor, or -1. */
    public int tabAt(double mouseX, double mouseY)
    {
        if (!active || mouseY < y || mouseY >= y + TAB_HEIGHT || mouseX < x || mouseX >= x + width)
        {
            return -1;
        }
        int tabWidth = Math.max(1, width / TAB_LABELS.length);
        int index = (int) ((mouseX - x) / tabWidth);
        return Math.max(0, Math.min(TAB_LABELS.length - 1, index));
    }

    private int visibleRows()
    {
        return Math.min(suggestions.size(), maxRows);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        if (!active)
        {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);

        int height = getHeight();
        guiGraphics.fill(x, y, x + width, y + height, 0xF0101030);

        renderTabs(guiGraphics, mouseX, mouseY);

        int rows = visibleRows();
        for (int i = 0; i < rows; i++)
        {
            int index = scroll + i;
            if (index >= suggestions.size())
            {
                break;
            }
            int rowY = y + TAB_HEIGHT + i * ROW_HEIGHT;
            boolean selected = index == highlighted;
            boolean hovered = isOver(mouseX, mouseY, x, rowY, width, ROW_HEIGHT);
            if (selected || hovered)
            {
                guiGraphics.fill(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, 0xFF3050A0);
            }
            guiGraphics.drawString(font, fit(suggestions.get(index).label()), x + 4, rowY + 2, 0xFFFFFFFF, false);
        }

        guiGraphics.pose().popPose();
    }

    private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        int tabWidth = Math.max(1, width / TAB_LABELS.length);
        for (int i = 0; i < TAB_LABELS.length; i++)
        {
            int tabX = x + i * tabWidth;
            int tabRight = i == TAB_LABELS.length - 1 ? x + width : tabX + tabWidth;
            boolean hovered = active && mouseX >= tabX && mouseX < tabRight
                && mouseY >= y && mouseY < y + TAB_HEIGHT;
            if (i == kindIndex)
            {
                guiGraphics.fill(tabX, y, tabRight, y + TAB_HEIGHT, 0xFF3050A0);
            }
            else if (hovered)
            {
                guiGraphics.fill(tabX, y, tabRight, y + TAB_HEIGHT, 0x40FFFFFF);
            }
            guiGraphics.drawString(font, fitTo(TAB_LABELS[i], tabWidth - 6), tabX + 3, y + 2, 0xFFFFFFFF, false);
        }
        guiGraphics.fill(x, y + TAB_HEIGHT - 1, x + width, y + TAB_HEIGHT, 0xFF606060);
    }

    public TargetSuggestion mouseClicked(double mouseX, double mouseY)
    {
        if (!active || mouseY < y + TAB_HEIGHT || !isOver(mouseX, mouseY))
        {
            return null;
        }
        int index = scroll + (int) ((mouseY - (y + TAB_HEIGHT)) / ROW_HEIGHT);
        return index >= 0 && index < suggestions.size() ? suggestions.get(index) : null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (!active || !isOver(mouseX, mouseY))
        {
            return false;
        }
        int maxScroll = Math.max(0, suggestions.size() - visibleRows());
        if (maxScroll <= 0)
        {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(delta)));
        return true;
    }

    private String fit(String text)
    {
        return fitTo(text, width - 8);
    }

    private String fitTo(String text, int maxWidth)
    {
        if (text == null)
        {
            return "";
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth));
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}

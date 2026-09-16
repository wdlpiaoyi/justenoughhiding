package com.wdlpiaoyi.justenoughhiding.client.gui.widget;

import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

import java.util.List;

/** A text-only suggestion list shown under an inline editor while editing a target. */
public final class AutocompletePopup implements Renderable
{
    private static final int ROW_HEIGHT = 12;

    private final Font font;
    private final int maxRows;

    private int x;
    private int y;
    private int width;
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

    public void setSuggestions(List<TargetSuggestion> suggestions)
    {
        this.suggestions = suggestions == null ? List.of() : suggestions;
        this.highlighted = this.suggestions.isEmpty() ? -1 : 0;
        this.scroll = 0;
    }

    public void hide()
    {
        setSuggestions(List.of());
    }

    public boolean isVisible()
    {
        return !suggestions.isEmpty();
    }

    public List<TargetSuggestion> getSuggestions()
    {
        return suggestions;
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
        return visibleRows() * ROW_HEIGHT;
    }

    public boolean isOver(double mouseX, double mouseY)
    {
        return isVisible() && isOver(mouseX, mouseY, x, y, width, getHeight());
    }

    private int visibleRows()
    {
        return Math.min(suggestions.size(), maxRows);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        if (!isVisible())
        {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);

        int height = getHeight();
        guiGraphics.fill(x, y, x + width, y + height, 0xF0101030);

        int rows = visibleRows();
        for (int i = 0; i < rows; i++)
        {
            int index = scroll + i;
            if (index >= suggestions.size())
            {
                break;
            }
            int rowY = y + i * ROW_HEIGHT;
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

    public TargetSuggestion mouseClicked(double mouseX, double mouseY)
    {
        if (!isVisible() || !isOver(mouseX, mouseY))
        {
            return null;
        }
        int index = scroll + (int) ((mouseY - y) / ROW_HEIGHT);
        return index >= 0 && index < suggestions.size() ? suggestions.get(index) : null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (!isVisible() || !isOver(mouseX, mouseY))
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
        if (text == null)
        {
            return "";
        }
        return font.plainSubstrByWidth(text, width - 8);
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height)
    {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}

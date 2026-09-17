package com.wdlpiaoyi.justenoughhiding.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Dropdown implements Renderable
{
    private static final int OPTION_HEIGHT = 12;
    private static final int MIN_VISIBLE = 1;
    private static final int SCREEN_MARGIN = 4;

    private final Font font;
    private final Consumer<String> onSelect;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private List<String> options;
    private String value;
    private boolean open;
    private int scroll;
    private Component tooltip;
    private Function<String, String> labeler = Function.identity();

    public Dropdown(Font font, int x, int y, int width, int height, List<String> options, String initial, Consumer<String> onSelect)
    {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.options = List.copyOf(options);
        this.value = initial;
        this.onSelect = onSelect;
    }

    public Dropdown tooltip(Component tooltip)
    {
        this.tooltip = tooltip;
        return this;
    }

    /** Maps an option value to its display text; the value itself is unchanged for logic. */
    public Dropdown labels(Function<String, String> labeler)
    {
        this.labeler = labeler == null ? Function.identity() : labeler;
        return this;
    }

    public Component getTooltip()
    {
        return tooltip;
    }

    public String getValue()
    {
        return value;
    }

    public int getX()
    {
        return x;
    }

    public boolean isOverButton(double mouseX, double mouseY)
    {
        return isOver(mouseX, mouseY, x, y, width, height);
    }

    public Rect2i bounds()
    {
        return new Rect2i(x, y, width, height);
    }

    public Rect2i popupBounds()
    {
        if (!open)
        {
            return null;
        }
        return new Rect2i(x, y + height, width, visibleOptions() * OPTION_HEIGHT);
    }

    public boolean isOpen()
    {
        return open;
    }

    public void close()
    {
        open = false;
        scroll = 0;
    }

    public void setOptions(List<String> newOptions)
    {
        this.options = List.copyOf(newOptions);
        if (!this.options.contains(this.value))
        {
            this.value = this.options.isEmpty() ? "" : this.options.get(0);
        }
        this.scroll = clampScroll(this.scroll);
    }

    public void setValue(String newValue)
    {
        this.value = newValue;
        if (onSelect != null)
        {
            onSelect.accept(newValue);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        boolean hovered = isOver(mouseX, mouseY, x, y, width, height);
        guiGraphics.fill(x, y, x + width, y + height, hovered ? 0xFF4A4A8A : 0xC0202040);
        guiGraphics.drawString(font, fit(labeler.apply(value)), x + 4, y + (height - 8) / 2, 0xFFFFFFFF, false);

        if (!open)
        {
            return;
        }

        // Draw the popup above item icons: item models render at an elevated z, so a plain
        // GUI fill would otherwise fail the depth test and let icons show through un-dimmed.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);

        int visible = visibleOptions();
        int popupTop = y + height;
        int popupBottom = popupTop + visible * OPTION_HEIGHT;
        guiGraphics.fill(x, popupTop, x + width, popupBottom, 0xE0101030);

        for (int i = 0; i < visible; i++)
        {
            int index = scroll + i;
            if (index >= options.size())
            {
                break;
            }
            int optionY = popupTop + i * OPTION_HEIGHT;
            if (isOver(mouseX, mouseY, x, optionY, width, OPTION_HEIGHT))
            {
                guiGraphics.fill(x + 1, optionY, x + width - 1, optionY + OPTION_HEIGHT, 0xFF3050A0);
            }
            guiGraphics.drawString(font, fit(labeler.apply(options.get(index))), x + 4, optionY + 2, 0xFFFFFFFF, false);
        }

        drawPopupScrollbar(guiGraphics, popupTop, visible);

        guiGraphics.pose().popPose();
    }

    private void drawPopupScrollbar(GuiGraphics guiGraphics, int popupTop, int visible)
    {
        int maxScroll = maxScroll();
        if (maxScroll <= 0)
        {
            return;
        }
        int barX = x + width - 3;
        int trackHeight = visible * OPTION_HEIGHT;
        int thumbHeight = Math.max(8, trackHeight * visible / options.size());
        int thumbY = popupTop + (trackHeight - thumbHeight) * scroll / maxScroll;
        guiGraphics.fill(barX, popupTop, barX + 2, popupTop + trackHeight, 0x80000000);
        guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0xFFA0A0A0);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button != 0)
        {
            return false;
        }

        if (open)
        {
            int popupTop = y + height;
            int popupBottom = popupTop + visibleOptions() * OPTION_HEIGHT;
            if (mouseX >= x && mouseX < x + width && mouseY >= popupTop && mouseY < popupBottom)
            {
                int index = scroll + (int) ((mouseY - popupTop) / OPTION_HEIGHT);
                if (index >= 0 && index < options.size())
                {
                    setValue(options.get(index));
                    open = false;
                    return true;
                }
            }
            open = false;
            return false;
        }

        if (isOver(mouseX, mouseY, x, y, width, height))
        {
            open = true;
            scroll = 0;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (!open || visibleOptions() >= options.size())
        {
            return false;
        }
        int popupTop = y + height;
        int popupBottom = popupTop + visibleOptions() * OPTION_HEIGHT;
        if (mouseX < x || mouseX >= x + width || mouseY < popupTop || mouseY >= popupBottom)
        {
            return false;
        }
        scroll = clampScroll(scroll - (int) Math.round(delta));
        return true;
    }

    private int maxVisible()
    {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int available = screenHeight - (y + height) - SCREEN_MARGIN;
        return Math.max(MIN_VISIBLE, available / OPTION_HEIGHT);
    }

    private int visibleOptions()
    {
        return Math.min(options.size(), maxVisible());
    }

    private int maxScroll()
    {
        return Math.max(0, options.size() - visibleOptions());
    }

    private int clampScroll(int value)
    {
        return Math.max(0, Math.min(maxScroll(), value));
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

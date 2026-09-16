package com.wdlpiaoyi.justenoughhiding.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

import java.util.List;
import java.util.function.Consumer;

public final class Dropdown implements Renderable
{
    private static final int OPTION_HEIGHT = 12;

    private final Font font;
    private final Consumer<String> onSelect;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private List<String> options;
    private String value;
    private boolean open;

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

    public String getValue()
    {
        return value;
    }

    public int getX()
    {
        return x;
    }

    public boolean isOpen()
    {
        return open;
    }

    public void close()
    {
        open = false;
    }

    public void setOptions(List<String> newOptions)
    {
        this.options = List.copyOf(newOptions);
        if (!this.options.contains(this.value))
        {
            this.value = this.options.isEmpty() ? "" : this.options.get(0);
        }
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
        guiGraphics.drawString(font, fit(value), x + 4, y + (height - 8) / 2, 0xFFFFFFFF, false);

        if (open)
        {
            for (int i = 0; i < options.size(); i++)
            {
                int optionY = y + height + i * OPTION_HEIGHT;
                boolean optionHovered = isOver(mouseX, mouseY, x, optionY, width, OPTION_HEIGHT);
                guiGraphics.fill(x, optionY, x + width, optionY + OPTION_HEIGHT, optionHovered ? 0xFF3050A0 : 0xE0101030);
                guiGraphics.drawString(font, fit(options.get(i)), x + 4, optionY + 2, 0xFFFFFFFF, false);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button != 0)
        {
            return false;
        }

        if (open)
        {
            if (mouseX >= x && mouseX < x + width && mouseY > y + height)
            {
                int index = (int) ((mouseY - (y + height)) / OPTION_HEIGHT);
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
            return true;
        }
        return false;
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

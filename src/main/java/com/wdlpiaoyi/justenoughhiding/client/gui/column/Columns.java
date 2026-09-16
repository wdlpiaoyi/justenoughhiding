package com.wdlpiaoyi.justenoughhiding.client.gui.column;

import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Function;

public final class Columns
{
    private Columns()
    {
    }

    public static <T> Column<T> icon(int width, Function<T, IconRenderer> icons)
    {
        return new Column<>()
        {
            @Override
            public int width()
            {
                return width;
            }

            @Override
            public boolean isIcon()
            {
                return true;
            }

            @Override
            public void render(GuiGraphics guiGraphics, Font font, T row, int left, int top, int columnWidth, int height)
            {
                IconRenderer icon = icons.apply(row);
                if (icon != null && !icon.isEmpty())
                {
                    icon.render(guiGraphics, font, left + 1, top + (height - 16) / 2);
                }
            }
        };
    }

    public static <T> Column<T> fixed(int width, Function<T, String> text, Function<T, Integer> color)
    {
        return new Column<>()
        {
            @Override
            public int width()
            {
                return width;
            }

            @Override
            public void render(GuiGraphics guiGraphics, Font font, T row, int left, int top, int columnWidth, int height)
            {
                guiGraphics.drawString(font, fit(font, text.apply(row), columnWidth), left, top + (height - 8) / 2, color.apply(row), false);
            }
        };
    }

    public static <T> Column<T> flexible(Function<T, String> text, Function<T, Integer> color)
    {
        return new Column<>()
        {
            @Override
            public int width()
            {
                return Column.FLEXIBLE;
            }

            @Override
            public void render(GuiGraphics guiGraphics, Font font, T row, int left, int top, int columnWidth, int height)
            {
                guiGraphics.drawString(font, fit(font, text.apply(row), columnWidth), left, top + (height - 8) / 2, color.apply(row), false);
            }
        };
    }

    private static String fit(Font font, String text, int maxWidth)
    {
        if (text == null)
        {
            return "";
        }
        return font.plainSubstrByWidth(text, Math.max(4, maxWidth));
    }
}

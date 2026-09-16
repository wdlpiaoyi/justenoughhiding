package com.wdlpiaoyi.justenoughhiding.client.gui.column;

import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Function;

public final class Columns
{
    private Columns()
    {
    }

    public static Column icon(int width, Function<Intent, IconRenderer> icons)
    {
        return new Column()
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
            public void render(GuiGraphics guiGraphics, Font font, Intent intent, int left, int top, int columnWidth, int height)
            {
                IconRenderer icon = icons.apply(intent);
                if (icon != null && !icon.isEmpty())
                {
                    icon.render(guiGraphics, font, left + 1, top + (height - 16) / 2);
                }
            }
        };
    }

    public static Column fixed(int width, Function<Intent, String> text, Function<Intent, Integer> color)
    {
        return new Column()
        {
            @Override
            public int width()
            {
                return width;
            }

            @Override
            public void render(GuiGraphics guiGraphics, Font font, Intent intent, int left, int top, int columnWidth, int height)
            {
                guiGraphics.drawString(font, fit(font, text.apply(intent), columnWidth), left, top + (height - 8) / 2, color.apply(intent), false);
            }
        };
    }

    public static Column flexible(Function<Intent, String> text, Function<Intent, Integer> color)
    {
        return new Column()
        {
            @Override
            public int width()
            {
                return Column.FLEXIBLE;
            }

            @Override
            public void render(GuiGraphics guiGraphics, Font font, Intent intent, int left, int top, int columnWidth, int height)
            {
                guiGraphics.drawString(font, fit(font, text.apply(intent), columnWidth), left, top + (height - 8) / 2, color.apply(intent), false);
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

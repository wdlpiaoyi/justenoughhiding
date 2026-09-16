package com.wdlpiaoyi.justenoughhiding.client.viewer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Draws a small (16x16) icon for a target, plus its hover tooltip. */
public interface IconRenderer
{
    IconRenderer EMPTY = new IconRenderer()
    {
        @Override
        public boolean isEmpty()
        {
            return true;
        }

        @Override
        public void render(GuiGraphics guiGraphics, Font font, int x, int y)
        {
        }

        @Override
        public void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY)
        {
        }
    };

    boolean isEmpty();

    void render(GuiGraphics guiGraphics, Font font, int x, int y);

    default void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY)
    {
    }
}

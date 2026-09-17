package com.wdlpiaoyi.justenoughhiding.client.viewer.emi;

import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Draws an EMI stack (item, fluid, ...) and its tooltip. */
public final class EmiIconRenderer implements IconRenderer
{
    private final EmiStack stack;

    public EmiIconRenderer(EmiStack stack)
    {
        this.stack = stack;
    }

    @Override
    public boolean isEmpty()
    {
        return stack == null || stack.isEmpty();
    }

    @Override
    public void render(GuiGraphics guiGraphics, Font font, int x, int y)
    {
        stack.render(guiGraphics, x, y, 0.0F);
    }

    @Override
    public void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY)
    {
        guiGraphics.renderComponentTooltip(font, stack.getTooltipText(), mouseX, mouseY);
    }
}

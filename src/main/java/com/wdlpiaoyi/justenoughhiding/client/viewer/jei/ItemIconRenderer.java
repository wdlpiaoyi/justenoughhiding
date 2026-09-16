package com.wdlpiaoyi.justenoughhiding.client.viewer.jei;

import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class ItemIconRenderer implements IconRenderer
{
    private final ItemStack stack;

    public ItemIconRenderer(ItemStack stack)
    {
        this.stack = stack;
    }

    @Override
    public boolean isEmpty()
    {
        return stack.isEmpty();
    }

    @Override
    public void render(GuiGraphics guiGraphics, Font font, int x, int y)
    {
        guiGraphics.renderItem(stack, x, y);
        guiGraphics.renderItemDecorations(font, stack, x, y);
    }

    @Override
    public void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY)
    {
        guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
    }
}

package com.wdlpiaoyi.justenoughhiding.client.viewer.jei;

import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Draws any JEI ingredient (fluid, chemical, ...) through its own {@link IIngredientRenderer}. */
public final class TypedIconRenderer implements IconRenderer
{
    private final Object ingredient;
    private final IIngredientRenderer<Object> renderer;

    @SuppressWarnings("unchecked")
    public TypedIconRenderer(Object ingredient, IIngredientRenderer<?> renderer)
    {
        this.ingredient = ingredient;
        this.renderer = (IIngredientRenderer<Object>) renderer;
    }

    @Override
    public boolean isEmpty()
    {
        return ingredient == null || renderer == null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, Font font, int x, int y)
    {
        renderer.render(guiGraphics, ingredient, x, y);
    }

    @Override
    public void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY)
    {
        List<Component> tooltip = renderer.getTooltip(ingredient, TooltipFlag.NORMAL);
        if (tooltip != null && !tooltip.isEmpty())
        {
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }
}

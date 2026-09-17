package com.wdlpiaoyi.justenoughhiding.client.viewer.emi;

import com.wdlpiaoyi.justenoughhiding.client.jehide.EmiHide;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Bridges EMI types to {@link EmiHide} (which is intentionally free of EMI types). Only loaded
 * when EMI is present.
 * <p>
 * Lives outside the mixin package on purpose: the package declared in the mixin config is owned
 * by Mixin, so helper classes there cannot be referenced directly.
 */
public final class EmiRevealSupport
{
    private EmiRevealSupport()
    {
    }

    public static boolean isHidden(Object ingredient)
    {
        if (!(ingredient instanceof EmiIngredient emiIngredient))
        {
            return false;
        }
        try
        {
            List<EmiStack> stacks = emiIngredient.getEmiStacks();
            if (stacks != null)
            {
                for (EmiStack stack : stacks)
                {
                    if (stack != null && isHiddenStack(stack))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }
        return false;
    }

    private static boolean isHiddenStack(EmiStack stack)
    {
        try
        {
            ResourceLocation id = stack.getId();
            return id != null && EmiHide.isHidden(id.toString());
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    public static boolean isHiddenRecipe(EmiRecipe recipe)
    {
        if (recipe == null)
        {
            return false;
        }
        String recipeId = null;
        String categoryId = null;
        try
        {
            ResourceLocation id = recipe.getId();
            recipeId = id == null ? null : id.toString();
        }
        catch (Throwable ignored)
        {
        }
        try
        {
            EmiRecipeCategory category = recipe.getCategory();
            ResourceLocation id = category == null ? null : category.getId();
            categoryId = id == null ? null : id.toString();
        }
        catch (Throwable ignored)
        {
        }
        return EmiHide.isHiddenRecipe(recipeId, categoryId);
    }
}

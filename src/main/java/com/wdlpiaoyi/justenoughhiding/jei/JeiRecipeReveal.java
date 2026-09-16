package com.wdlpiaoyi.justenoughhiding.jei;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.List;

/**
 * Unhides every recipe and recipe category when JEI starts, mirroring the ingredient reveal.
 * Uses the public {@link IRecipeManager} API so it does not depend on internal fields.
 */
public final class JeiRecipeReveal
{
    private JeiRecipeReveal()
    {
    }

    public static void reveal(IJeiRuntime runtime)
    {
        if (!JehConfig.revealEnabled() || runtime == null)
        {
            return;
        }

        IRecipeManager manager;
        List<IRecipeCategory<?>> categories;
        try
        {
            manager = runtime.getRecipeManager();
            categories = manager.createRecipeCategoryLookup().includeHidden().get().toList();
        }
        catch (Throwable t)
        {
            return;
        }

        int categoryCount = 0;
        int recipeCount = 0;
        for (IRecipeCategory<?> category : categories)
        {
            try
            {
                int revealed = revealCategory(manager, category);
                if (revealed >= 0)
                {
                    categoryCount++;
                    recipeCount += revealed;
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        JustEnoughHiding.LOGGER.info("[JEH] reveal: unhid {} recipe categories, {} recipes", categoryCount, recipeCount);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int revealCategory(IRecipeManager manager, IRecipeCategory<?> category)
    {
        RecipeType type = category.getRecipeType();
        if (type == null)
        {
            return -1;
        }
        manager.unhideRecipeCategory(type);
        List recipes = manager.createRecipeLookup(type).includeHidden().get().toList();
        if (!recipes.isEmpty())
        {
            manager.unhideRecipes(type, recipes);
        }
        return recipes.size();
    }
}

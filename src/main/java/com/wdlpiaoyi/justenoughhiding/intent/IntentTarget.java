package com.wdlpiaoyi.justenoughhiding.intent;

import net.minecraft.resources.ResourceLocation;

public sealed interface IntentTarget
{
    record Ingredient(IngredientKey key) implements IntentTarget
    {
    }

    record Recipe(ResourceLocation recipeType, String recipeId) implements IntentTarget
    {
    }

    record RecipeCategory(ResourceLocation recipeType) implements IntentTarget
    {
    }

    static IntentTarget of(IngredientKey key)
    {
        return new Ingredient(key);
    }

    static IntentTarget of(ResourceLocation recipeType, String recipeId)
    {
        return new Recipe(recipeType, recipeId);
    }

    static IntentTarget category(ResourceLocation recipeType)
    {
        return new RecipeCategory(recipeType);
    }
}

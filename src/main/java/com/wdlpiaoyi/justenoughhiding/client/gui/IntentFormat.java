package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

public final class IntentFormat
{
    private IntentFormat()
    {
    }

    public static String targetText(IntentTarget target)
    {
        if (target instanceof IntentTarget.Ingredient ingredient)
        {
            return ingredient.key().uid();
        }
        if (target instanceof IntentTarget.Recipe recipe)
        {
            return recipe.recipeType() + " # " + recipe.recipeId();
        }
        if (target instanceof IntentTarget.RecipeCategory category)
        {
            return "category " + category.recipeType();
        }
        return String.valueOf(target);
    }

    public static String uid(Intent intent)
    {
        if (intent.target() instanceof IntentTarget.Ingredient ingredient)
        {
            return ingredient.key().uid();
        }
        return targetText(intent.target());
    }

    public static String line(Intent intent)
    {
        return intent.kind() + "\t" + intent.source().id() + "\tx" + intent.count() + "\t" + targetText(intent.target());
    }
}

package com.wdlpiaoyi.justenoughhiding.intent;

import net.minecraft.resources.ResourceLocation;

/**
 * A thing that an {@link Intent} can apply to: an ingredient, a recipe, a recipe category, ...
 * <p>
 * New kinds (tags, fluids, ...) only need to implement this interface; the GUI renders them
 * through a {@code ViewerAdapter} and falls back to {@link #describe()} when no viewer can
 * display the kind natively.
 */
public sealed interface IntentTarget
{
    /** Stable identifier of this kind, e.g. {@code "ingredient"}. */
    String kind();

    /** Human-readable text used by the list, search and export. */
    String describe();

    /** Text copied by the "Copy" button. Defaults to {@link #describe()}. */
    default String copyText()
    {
        return describe();
    }

    record Ingredient(IngredientKey key) implements IntentTarget
    {
        @Override
        public String kind()
        {
            return "ingredient";
        }

        @Override
        public String describe()
        {
            return key.uid();
        }

        @Override
        public String copyText()
        {
            return key.uid();
        }
    }

    record Recipe(ResourceLocation recipeType, String recipeId) implements IntentTarget
    {
        @Override
        public String kind()
        {
            return "recipe";
        }

        @Override
        public String describe()
        {
            return recipeType + " # " + recipeId;
        }
    }

    record RecipeCategory(ResourceLocation recipeType) implements IntentTarget
    {
        @Override
        public String kind()
        {
            return "recipe_category";
        }

        @Override
        public String describe()
        {
            return "category " + recipeType;
        }
    }

    /** Placeholder used by freshly created list entries until a real target is set. */
    record Unset() implements IntentTarget
    {
        @Override
        public String kind()
        {
            return "unset";
        }

        @Override
        public String describe()
        {
            return "(empty)";
        }
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

    static IntentTarget unset()
    {
        return new Unset();
    }
}

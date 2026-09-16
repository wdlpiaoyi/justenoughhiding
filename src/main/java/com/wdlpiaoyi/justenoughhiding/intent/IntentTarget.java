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

    /** Ingredient type uid when this is an {@link Ingredient}; empty for every other kind. */
    default String typeUid()
    {
        return "";
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

        @Override
        public String typeUid()
        {
            return key.typeUid();
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

    /** A registry tag, e.g. {@code minecraft:logs}. Identified by its id only. */
    record Tag(String tagId) implements IntentTarget
    {
        @Override
        public String kind()
        {
            return "tag";
        }

        @Override
        public String describe()
        {
            return "#" + tagId;
        }

        @Override
        public String copyText()
        {
            return tagId;
        }
    }

    /** How a {@link Pattern} is interpreted. */
    enum MatchMode
    {
        GLOB,
        REGEX
    }

    /**
     * A wildcard or regex rule matching many concrete targets. {@code scope} is a target kind
     * key (blank means "any kind"); {@code pattern} is the glob/regex body.
     */
    record Pattern(String scope, String pattern, MatchMode mode) implements IntentTarget
    {
        @Override
        public String kind()
        {
            return "pattern";
        }

        @Override
        public String describe()
        {
            return mode == MatchMode.REGEX ? "~" + pattern : pattern;
        }

        @Override
        public String copyText()
        {
            return describe();
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

    static IntentTarget tag(String tagId)
    {
        return new Tag(tagId);
    }

    static IntentTarget pattern(String scope, String pattern, MatchMode mode)
    {
        return new Pattern(scope == null ? "" : scope, pattern, mode);
    }

    static IntentTarget unset()
    {
        return new Unset();
    }
}

package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

/** Shared helpers for target ids, kind keys and wildcard/regex detection. */
public final class TargetKeys
{
    private TargetKeys()
    {
    }

    /** Editable/display id of a target (pattern text includes its {@code ~} regex sigil). */
    public static String id(IntentTarget target)
    {
        if (target instanceof IntentTarget.Recipe recipe)
        {
            return recipe.recipeId();
        }
        if (target instanceof IntentTarget.RecipeCategory category)
        {
            return category.recipeType().toString();
        }
        if (target instanceof IntentTarget.Tag tag)
        {
            return tag.tagId();
        }
        if (target instanceof IntentTarget.Pattern pattern)
        {
            return pattern.describe();
        }
        if (target instanceof IntentTarget.Ingredient ingredient)
        {
            return ingredient.key().uid();
        }
        return "";
    }

    /** Kind key used by the editor tabs: a pattern carries its own scope. */
    public static String kindKey(IntentTarget target)
    {
        if (target instanceof IntentTarget.Ingredient ingredient)
        {
            return "ingredient|" + ingredient.key().typeUid();
        }
        if (target instanceof IntentTarget.Pattern pattern)
        {
            return pattern.scope();
        }
        return switch (target.kind())
        {
            case "recipe" -> "recipe";
            case "recipe_category" -> "recipe_category";
            case "tag" -> "tag";
            default -> "";
        };
    }

    public static boolean isPattern(String text)
    {
        if (text == null)
        {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("~") && trimmed.length() > 1)
        {
            return true;
        }
        return trimmed.indexOf('*') >= 0 || trimmed.indexOf('?') >= 0;
    }

    public static IntentTarget.MatchMode modeOf(String text)
    {
        return text != null && text.trim().startsWith("~")
            ? IntentTarget.MatchMode.REGEX
            : IntentTarget.MatchMode.GLOB;
    }

    public static String patternBody(String text)
    {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.startsWith("~") ? trimmed.substring(1) : trimmed;
    }
}

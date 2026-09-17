package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import net.minecraft.network.chat.Component;

import java.util.Locale;

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

    /**
     * Whether an ingredient type uid denotes item stacks. JEI uses {@code item_stack} while EMI
     * uses {@code minecraft:item_stack}; targets recorded by either viewer must resolve in both.
     */
    public static boolean isItemType(String typeUid)
    {
        return typeUid != null && typeUid.toLowerCase(Locale.ROOT).endsWith("item_stack");
    }

    /** Whether an ingredient type uid denotes fluid stacks. */
    public static boolean isFluidType(String typeUid)
    {
        return typeUid != null && typeUid.toLowerCase(Locale.ROOT).endsWith("fluid_stack");
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

    /** Friendly name for a JEI ingredient type uid (Item / Fluid / Chemical / ...). */
    public static String typeLabel(String typeUid)
    {
        if (typeUid == null || typeUid.isBlank())
        {
            return Component.translatable("jeh.kind.ingredient").getString();
        }
        String lower = typeUid.toLowerCase(Locale.ROOT);
        if (lower.contains("item"))
        {
            return Component.translatable("jeh.kind.item").getString();
        }
        if (lower.contains("fluid"))
        {
            return Component.translatable("jeh.kind.fluid").getString();
        }
        if (lower.contains("chemical"))
        {
            return Component.translatable("jeh.kind.chemical").getString();
        }
        if (lower.contains("energy"))
        {
            return Component.translatable("jeh.kind.energy").getString();
        }
        String path = lower;
        int colon = path.indexOf(':');
        if (colon >= 0)
        {
            path = path.substring(colon + 1);
        }
        StringBuilder result = new StringBuilder();
        for (String word : path.replace('_', ' ').trim().split(" "))
        {
            if (word.isEmpty())
            {
                continue;
            }
            if (result.length() > 0)
            {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.length() == 0 ? typeUid : result.toString();
    }

    /** Friendly name for a target kind scope key used by pattern targets. */
    public static String scopeLabel(String scope)
    {
        if (scope == null || scope.isBlank())
        {
            return Component.translatable("jeh.kind.any").getString();
        }
        if (scope.startsWith("ingredient|"))
        {
            return typeLabel(scope.substring("ingredient|".length()));
        }
        return switch (scope)
        {
            case "recipe" -> Component.translatable("jeh.kind.recipe").getString();
            case "recipe_category" -> Component.translatable("jeh.kind.category").getString();
            case "tag" -> Component.translatable("jeh.kind.tag").getString();
            default -> scope;
        };
    }

    /** Display label for a target, showing a pattern's scope to avoid hiding it in the editor. */
    public static String label(IntentTarget target)
    {
        if (target instanceof IntentTarget.Pattern pattern && !pattern.scope().isBlank())
        {
            return scopeLabel(pattern.scope()) + " " + pattern.describe();
        }
        return target.describe();
    }
}

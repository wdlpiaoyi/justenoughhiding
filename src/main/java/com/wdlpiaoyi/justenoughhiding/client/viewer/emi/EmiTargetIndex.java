package com.wdlpiaoyi.justenoughhiding.client.viewer.emi;

import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetKeys;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetMatcher;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A cached snapshot of EMI's stacks, recipes and categories for the autocomplete and the
 * auto-detector. Built lazily (EMI must be loaded); invalidated on every EMI reload.
 */
final class EmiTargetIndex
{
    static final String ITEM_TYPE = "minecraft:item_stack";
    static final String FLUID_TYPE = "fluid_stack";

    private record Entry(IntentTarget target, String kindKey, String id, String label, String search)
    {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, EmiStack> icons = new HashMap<>();
    private final Map<String, IntentTarget> ingredients = new HashMap<>();
    private final Map<String, IntentTarget> recipes = new HashMap<>();
    private final Map<String, IntentTarget> categories = new HashMap<>();

    static EmiTargetIndex build()
    {
        EmiTargetIndex index = new EmiTargetIndex();
        index.addStacks();
        index.addCategories();
        index.addRecipes();
        return index;
    }

    EmiStack icon(String typeUid, String uid)
    {
        return icons.get(typeUid + "|" + uid);
    }

    List<TargetSuggestion> suggest(String query, String kind, int limit)
    {
        if (query == null || query.isBlank() || limit <= 0)
        {
            return List.of();
        }
        String trimmed = query.trim();
        if (TargetKeys.isPattern(trimmed))
        {
            return patternSuggestions(trimmed, kind, limit);
        }
        String q = trimmed.toLowerCase(Locale.ROOT);
        boolean filterKind = kind != null && !kind.isBlank();

        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries)
        {
            if (filterKind && !entry.kindKey().equals(kind))
            {
                continue;
            }
            if (entry.search().contains(q))
            {
                matches.add(entry);
            }
        }
        matches.sort(Comparator.comparingInt((Entry entry) -> rank(entry, q)).thenComparing(Entry::label));

        int count = Math.min(limit, matches.size());
        List<TargetSuggestion> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            Entry entry = matches.get(i);
            result.add(new TargetSuggestion(entry.target(), entry.label(), entry.id()));
        }
        return result;
    }

    private List<TargetSuggestion> patternSuggestions(String query, String kind, int limit)
    {
        IntentTarget target = IntentTarget.pattern(kind == null ? "" : kind,
            TargetKeys.patternBody(query), TargetKeys.modeOf(query));
        IntentTarget.Pattern pattern = (IntentTarget.Pattern) target;
        if (TargetMatcher.compile(pattern) == null)
        {
            return List.of();
        }
        int count = matches(pattern, 1000).size();
        String label = "pattern: " + pattern.describe() + " (" + count + (count >= 1000 ? "+" : "") + " matches)";
        return List.of(new TargetSuggestion(target, label, query));
    }

    IntentTarget detect(String text)
    {
        if (text == null)
        {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty())
        {
            return null;
        }
        if (trimmed.startsWith("#"))
        {
            String tagId = trimmed.substring(1).trim();
            return ResourceLocation.tryParse(tagId) == null ? null : IntentTarget.tag(tagId);
        }
        IntentTarget target = lookup(ingredients, trimmed);
        if (target != null)
        {
            return target;
        }
        target = lookup(recipes, trimmed);
        if (target != null)
        {
            return target;
        }
        target = lookup(categories, trimmed);
        if (target != null)
        {
            return target;
        }
        return ResourceLocation.tryParse(trimmed) == null
            ? null
            : IntentTarget.of(IngredientKey.of(ITEM_TYPE, trimmed));
    }

    IntentTarget recipeTarget(String id)
    {
        return id == null ? null : recipes.get(id.trim());
    }

    List<TargetSuggestion> matches(IntentTarget target, int limit)
    {
        if (!(target instanceof IntentTarget.Pattern pattern) || limit <= 0)
        {
            return List.of();
        }
        java.util.regex.Pattern compiled = TargetMatcher.compile(pattern);
        if (compiled == null)
        {
            return List.of();
        }
        List<TargetSuggestion> result = new ArrayList<>();
        for (Entry entry : entries)
        {
            if (!pattern.scope().isEmpty() && !pattern.scope().equals(entry.kindKey()))
            {
                continue;
            }
            if (compiled.matcher(entry.id()).matches())
            {
                result.add(new TargetSuggestion(entry.target(), entry.label(), entry.id()));
                if (result.size() >= limit)
                {
                    break;
                }
            }
        }
        return result;
    }

    private static IntentTarget lookup(Map<String, IntentTarget> map, String key)
    {
        IntentTarget target = map.get(key);
        return target != null ? target : map.get(key.toLowerCase(Locale.ROOT));
    }

    private static int rank(Entry entry, String query)
    {
        String id = entry.id().toLowerCase(Locale.ROOT);
        if (id.equals(query))
        {
            return 0;
        }
        if (id.startsWith(query))
        {
            return 1;
        }
        if (id.contains(query))
        {
            return 2;
        }
        return 3;
    }

    private void addStacks()
    {
        List<EmiStack> stacks;
        try
        {
            stacks = EmiApi.getIndexStacks();
        }
        catch (Throwable t)
        {
            return;
        }
        if (stacks == null)
        {
            return;
        }
        for (EmiStack stack : stacks)
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String typeUid;
            ResourceLocation id;
            try
            {
                Object key = stack.getKey();
                if (key instanceof Fluid)
                {
                    typeUid = FLUID_TYPE;
                }
                else if (!stack.getItemStack().isEmpty())
                {
                    typeUid = ITEM_TYPE;
                }
                else
                {
                    continue;
                }
                id = stack.getId();
            }
            catch (Throwable t)
            {
                continue;
            }
            if (id == null || ingredients.containsKey(id.toString()))
            {
                continue;
            }
            String uid = id.toString();
            String name;
            try
            {
                name = stack.getName().getString();
            }
            catch (Throwable t)
            {
                name = uid;
            }
            IntentTarget target = IntentTarget.of(IngredientKey.of(typeUid, uid));
            ingredients.put(uid, target);
            icons.put(typeUid + "|" + uid, stack);
            entries.add(new Entry(target, "ingredient|" + typeUid, uid,
                name + " (" + uid + ")", search(uid, name)));
        }
    }

    private void addCategories()
    {
        EmiRecipeManager manager = recipeManager();
        if (manager == null)
        {
            return;
        }
        List<EmiRecipeCategory> list;
        try
        {
            list = manager.getCategories();
        }
        catch (Throwable t)
        {
            return;
        }
        if (list == null)
        {
            return;
        }
        for (EmiRecipeCategory category : list)
        {
            if (category == null)
            {
                continue;
            }
            ResourceLocation id = category.getId();
            if (id == null)
            {
                continue;
            }
            String key = id.toString();
            if (categories.containsKey(key))
            {
                continue;
            }
            String name;
            try
            {
                name = category.getName().getString();
            }
            catch (Throwable t)
            {
                name = key;
            }
            IntentTarget target = IntentTarget.category(id);
            categories.put(key, target);
            entries.add(new Entry(target, "recipe_category", key,
                "category: " + name + " (" + key + ")", search(key, name)));
        }
    }

    private void addRecipes()
    {
        EmiRecipeManager manager = recipeManager();
        if (manager == null)
        {
            return;
        }
        List<EmiRecipe> list;
        try
        {
            list = manager.getRecipes();
        }
        catch (Throwable t)
        {
            return;
        }
        if (list == null)
        {
            return;
        }
        for (EmiRecipe recipe : list)
        {
            if (recipe == null)
            {
                continue;
            }
            ResourceLocation id;
            EmiRecipeCategory category;
            try
            {
                id = recipe.getId();
                category = recipe.getCategory();
            }
            catch (Throwable t)
            {
                continue;
            }
            if (id == null || category == null || category.getId() == null || recipes.containsKey(id.toString()))
            {
                continue;
            }
            ResourceLocation type = category.getId();
            String key = id.toString();
            IntentTarget target = IntentTarget.of(type, key);
            recipes.put(key, target);
            entries.add(new Entry(target, "recipe", key,
                "recipe: " + type + " # " + key, search(key, type.toString())));
        }
    }

    private static EmiRecipeManager recipeManager()
    {
        try
        {
            return EmiApi.getRecipeManager();
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String search(String id, String name)
    {
        return (id + " " + name).toLowerCase(Locale.ROOT);
    }
}

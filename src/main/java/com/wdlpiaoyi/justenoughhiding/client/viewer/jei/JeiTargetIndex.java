package com.wdlpiaoyi.justenoughhiding.client.viewer.jei;

import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A cached, searchable snapshot of everything a target can point at: item ingredients, recipes
 * and recipe categories. Built once (per runtime / level) and queried by the autocomplete.
 */
final class JeiTargetIndex
{
    private record Entry(IntentTarget target, String id, String label, String completion, String search)
    {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, IntentTarget> ingredients = new HashMap<>();
    private final Map<String, IntentTarget> recipes = new HashMap<>();
    private final Map<String, IntentTarget> categories = new HashMap<>();

    static JeiTargetIndex build(IJeiRuntime runtime, Level level)
    {
        JeiTargetIndex index = new JeiTargetIndex();
        if (runtime != null)
        {
            index.addIngredients(runtime);
            index.addCategories(runtime);
            index.addRecipes(level);
        }
        return index;
    }

    List<TargetSuggestion> suggest(String query, int limit)
    {
        if (query == null || query.isBlank() || limit <= 0)
        {
            return List.of();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);

        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries)
        {
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
            result.add(new TargetSuggestion(entry.target(), entry.label(), entry.completion()));
        }
        return result;
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

        int brace = trimmed.indexOf('{');
        ResourceLocation id = ResourceLocation.tryParse(brace >= 0 ? trimmed.substring(0, brace) : trimmed);
        if (id == null)
        {
            return null;
        }
        return IntentTarget.of(IngredientKey.of(VanillaTypes.ITEM_STACK.getUid(), trimmed));
    }

    private static IntentTarget lookup(Map<String, IntentTarget> map, String key)
    {
        IntentTarget target = map.get(key);
        if (target != null)
        {
            return target;
        }
        return map.get(key.toLowerCase(Locale.ROOT));
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

    private void addIngredients(IJeiRuntime runtime)
    {
        String typeUid = VanillaTypes.ITEM_STACK.getUid();
        IIngredientHelper<ItemStack> helper;
        try
        {
            helper = runtime.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
        }
        catch (Throwable t)
        {
            return;
        }
        if (helper == null)
        {
            return;
        }

        for (ItemStack stack : safeItemStacks(runtime.getIngredientManager()))
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            String uid;
            try
            {
                uid = helper.getUniqueId(stack, UidContext.Ingredient);
            }
            catch (Throwable t)
            {
                continue;
            }
            if (uid == null || ingredients.containsKey(uid))
            {
                continue;
            }

            String name;
            try
            {
                name = helper.getDisplayName(stack);
            }
            catch (Throwable t)
            {
                name = uid;
            }

            IntentTarget target = IntentTarget.of(IngredientKey.of(typeUid, uid));
            ingredients.put(uid, target);
            entries.add(new Entry(target, uid, name + " (" + uid + ")", uid, search(uid, name)));
        }
    }

    private static Iterable<ItemStack> safeItemStacks(IIngredientManager manager)
    {
        try
        {
            var stacks = manager.getAllItemStacks();
            return stacks == null ? List.of() : stacks;
        }
        catch (Throwable t)
        {
            return List.of();
        }
    }

    private void addCategories(IJeiRuntime runtime)
    {
        List<IRecipeCategory<?>> list;
        try
        {
            list = runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().toList();
        }
        catch (Throwable t)
        {
            return;
        }

        for (IRecipeCategory<?> category : list)
        {
            RecipeType<?> type;
            try
            {
                type = category.getRecipeType();
            }
            catch (Throwable t)
            {
                continue;
            }
            ResourceLocation uid = type == null ? null : type.getUid();
            if (uid == null)
            {
                continue;
            }
            String key = uid.toString();
            if (categories.containsKey(key))
            {
                continue;
            }

            String title;
            try
            {
                title = category.getTitle().getString();
            }
            catch (Throwable t)
            {
                title = key;
            }

            IntentTarget target = IntentTarget.category(uid);
            categories.put(key, target);
            entries.add(new Entry(target, key, "category: " + title + " (" + key + ")",
                "category " + key, search(key, title)));
        }
    }

    private void addRecipes(Level level)
    {
        if (level == null)
        {
            return;
        }
        RecipeManager recipeManager;
        try
        {
            recipeManager = level.getRecipeManager();
        }
        catch (Throwable t)
        {
            return;
        }
        if (recipeManager == null)
        {
            return;
        }

        try
        {
            recipeManager.getRecipeIds().forEach(id ->
            {
                try
                {
                    Recipe<?> recipe = recipeManager.byKey(id).orElse(null);
                    if (recipe == null)
                    {
                        return;
                    }
                    ResourceLocation typeUid = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                    if (typeUid == null)
                    {
                        return;
                    }
                    String key = id.toString();
                    if (recipes.containsKey(key))
                    {
                        return;
                    }
                    IntentTarget target = IntentTarget.of(typeUid, key);
                    recipes.put(key, target);
                    entries.add(new Entry(target, key,
                        "recipe: " + typeUid + " # " + key, "recipe " + typeUid + " " + key,
                        search(key, typeUid.toString())));
                }
                catch (Throwable ignored)
                {
                }
            });
        }
        catch (Throwable ignored)
        {
        }
    }

    private static String search(String id, String name)
    {
        return (id + " " + name).toLowerCase(Locale.ROOT);
    }
}

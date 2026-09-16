package com.wdlpiaoyi.justenoughhiding.client.viewer.jei;

import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetKeys;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetMatcher;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * A cached, searchable snapshot of everything a target can point at: every registered JEI
 * ingredient type, recipes, recipe categories and (lazily) tags. Built once per runtime/level
 * and queried by the autocomplete and the auto-detector.
 */
final class JeiTargetIndex
{
    private static final int MATCH_CAP = 1000;

    private record Entry(IntentTarget target, String kindKey, String id, String label, String search)
    {
    }

    private final IJeiRuntime runtime;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Map<String, IntentTarget>> ingredients = new HashMap<>();
    private final Map<String, IntentTarget> recipes = new HashMap<>();
    private final Map<String, IntentTarget> categories = new HashMap<>();
    private final TreeSet<String> tagIds = new TreeSet<>();
    private boolean tagsBuilt;

    private JeiTargetIndex(IJeiRuntime runtime)
    {
        this.runtime = runtime;
    }

    static JeiTargetIndex build(IJeiRuntime runtime, Level level)
    {
        JeiTargetIndex index = new JeiTargetIndex(runtime);
        if (runtime != null)
        {
            index.addIngredientTypes();
            index.addCategories();
            index.addRecipes(level);
        }
        return index;
    }

    static String typeLabel(String typeUid)
    {
        return TargetKeys.typeLabel(typeUid);
    }

    List<TargetSuggestion> suggest(String query, String kind, int limit)
    {
        if (query == null || query.isBlank() || limit <= 0)
        {
            return List.of();
        }
        String trimmedQuery = query.trim();
        if (TargetKeys.isPattern(trimmedQuery))
        {
            return patternSuggestions(trimmedQuery, kind, limit);
        }
        if ("tag".equals(kind))
        {
            ensureTags();
        }
        String q = trimmedQuery.toLowerCase(Locale.ROOT);
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
        int count = matches(pattern, MATCH_CAP).size();
        String suffix = count >= MATCH_CAP ? "+" : "";
        String label = "pattern: " + pattern.describe() + " (" + count + suffix + " matches)";
        return List.of(new TargetSuggestion(target, label, query));
    }

    List<TargetSuggestion> matches(IntentTarget target, int limit)
    {
        if (!(target instanceof IntentTarget.Pattern pattern) || limit <= 0)
        {
            return List.of();
        }
        if (pattern.scope().isEmpty() || "tag".equals(pattern.scope()))
        {
            ensureTags();
        }
        java.util.regex.Pattern compiled = TargetMatcher.compile(pattern);
        if (compiled == null)
        {
            return List.of();
        }

        List<TargetSuggestion> result = new ArrayList<>();
        for (Entry entry : entries)
        {
            if (entry.target() instanceof IntentTarget.Pattern)
            {
                continue;
            }
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

        for (Map<String, IntentTarget> byUid : ingredients.values())
        {
            IntentTarget target = lookup(byUid, trimmed);
            if (target != null)
            {
                return target;
            }
        }
        IntentTarget target = lookup(recipes, trimmed);
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

    IntentTarget recipeTarget(String id)
    {
        if (id == null)
        {
            return null;
        }
        String key = id.trim();
        IntentTarget target = recipes.get(key);
        return target != null ? target : recipes.get(key.toLowerCase(Locale.ROOT));
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

    // ---- building ----

    private void addIngredientTypes()
    {
        IIngredientManager manager;
        try
        {
            manager = runtime.getIngredientManager();
        }
        catch (Throwable t)
        {
            return;
        }
        if (manager == null)
        {
            return;
        }
        for (IIngredientType<?> type : safeTypes(manager))
        {
            addIngredientType(manager, type);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addIngredientType(IIngredientManager manager, IIngredientType<?> type)
    {
        String typeUid;
        try
        {
            typeUid = type.getUid();
        }
        catch (Throwable t)
        {
            return;
        }
        if (typeUid == null)
        {
            return;
        }

        IIngredientHelper helper;
        Collection<?> all;
        try
        {
            helper = manager.getIngredientHelper((IIngredientType) type);
            all = manager.getAllIngredients((IIngredientType) type);
        }
        catch (Throwable t)
        {
            return;
        }
        if (helper == null || all == null)
        {
            return;
        }

        String kindKey = "ingredient|" + typeUid;
        String typeLabel = typeLabel(typeUid);
        boolean isItemStack = VanillaTypes.ITEM_STACK.getUid().equals(typeUid);
        Map<String, IntentTarget> byUid = ingredients.computeIfAbsent(typeUid, ignored -> new HashMap<>());

        for (Object ingredient : all)
        {
            if (ingredient == null)
            {
                continue;
            }
            String uid;
            try
            {
                uid = helper.getUniqueId(ingredient, UidContext.Ingredient);
            }
            catch (Throwable t)
            {
                continue;
            }
            if (uid == null || byUid.containsKey(uid))
            {
                continue;
            }
            String name;
            try
            {
                name = helper.getDisplayName(ingredient);
            }
            catch (Throwable t)
            {
                name = uid;
            }

            IntentTarget target = IntentTarget.of(IngredientKey.of(typeUid, uid));
            byUid.put(uid, target);
            String label = isItemStack ? name + " (" + uid + ")" : "[" + typeLabel + "] " + name + " (" + uid + ")";
            entries.add(new Entry(target, kindKey, uid, label, search(uid, name)));
        }
    }

    private static Collection<IIngredientType<?>> safeTypes(IIngredientManager manager)
    {
        try
        {
            Collection<IIngredientType<?>> types = manager.getRegisteredIngredientTypes();
            return types == null ? List.of() : types;
        }
        catch (Throwable t)
        {
            return List.of();
        }
    }

    private void addCategories()
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
            entries.add(new Entry(target, "recipe_category", key,
                "category: " + title + " (" + key + ")", search(key, title)));
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
                    entries.add(new Entry(target, "recipe", key,
                        "recipe: " + typeUid + " # " + key, search(key, typeUid.toString())));
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

    private void ensureTags()
    {
        if (tagsBuilt)
        {
            return;
        }
        tagsBuilt = true;
        if (runtime == null)
        {
            return;
        }
        IIngredientManager manager;
        try
        {
            manager = runtime.getIngredientManager();
        }
        catch (Throwable t)
        {
            return;
        }
        if (manager == null)
        {
            return;
        }

        for (IIngredientType<?> type : safeTypes(manager))
        {
            collectTags(manager, type);
        }
        for (String tagId : tagIds)
        {
            if (ResourceLocation.tryParse(tagId) == null)
            {
                continue;
            }
            IntentTarget target = IntentTarget.tag(tagId);
            entries.add(new Entry(target, "tag", tagId, "tag: " + tagId, search(tagId, tagId)));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void collectTags(IIngredientManager manager, IIngredientType<?> type)
    {
        try
        {
            IIngredientHelper helper = manager.getIngredientHelper((IIngredientType) type);
            Collection<?> all = manager.getAllIngredients((IIngredientType) type);
            if (helper == null || all == null)
            {
                return;
            }
            for (Object ingredient : all)
            {
                if (ingredient == null)
                {
                    continue;
                }
                try
                {
                    helper.getTagStream(ingredient).forEach(tag ->
                    {
                        if (tag != null)
                        {
                            tagIds.add(tag.toString());
                        }
                    });
                }
                catch (Throwable ignored)
                {
                }
            }
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

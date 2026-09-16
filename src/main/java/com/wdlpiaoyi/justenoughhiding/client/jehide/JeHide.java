package com.wdlpiaoyi.justenoughhiding.client.jehide;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentRecorder;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHiding;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHidingEntry;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the enabled {@link ListEHiding} entries and hides the matching JEI content at runtime
 * (visibility only, reversible, never written to JEI's config files).
 */
public final class JeHide
{
    private static final int EXPAND_CAP = 100_000;
    private static final List<UidContext> CONTEXTS = List.of(UidContext.Ingredient, UidContext.Recipe);

    private static final Set<String> HIDDEN_INGREDIENTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> HIDDEN_RECIPES = ConcurrentHashMap.newKeySet();
    private static final Set<String> HIDDEN_CATEGORIES = ConcurrentHashMap.newKeySet();

    private static final Map<IIngredientType<?>, List<Object>> HIDDEN_INGREDIENT_OBJECTS = new LinkedHashMap<>();
    private static final Map<RecipeType<?>, List<Object>> HIDDEN_RECIPE_OBJECTS = new LinkedHashMap<>();
    private static final Set<RecipeType<?>> HIDDEN_CATEGORY_OBJECTS = new LinkedHashSet<>();

    private static IJeiRuntime currentRuntime;
    private static IJeiRuntime previousRuntime;

    private JeHide()
    {
    }

    public static synchronized void apply(IJeiRuntime runtime)
    {
        if (runtime == null)
        {
            return;
        }
        currentRuntime = runtime;
        JeiIntentRecorder.runSuppressed(() -> applyInternal(runtime));
        refreshIngredientFilter(runtime);
    }

    private static void applyInternal(IJeiRuntime runtime)
    {
        clearPrevious(runtime);

        if (!JehConfig.jehideEnabled())
        {
            JustEnoughHiding.LOGGER.info("[JEH] jehide: disabled, cleared previous hides");
            return;
        }

        List<IntentTarget> targets = new ArrayList<>();
        for (ListEHidingEntry entry : ListEHiding.get().entries())
        {
            if (entry.enabled())
            {
                targets.add(entry.target());
            }
        }
        List<IntentTarget> expanded = expand(targets);

        int ingredients = hideIngredients(runtime, expanded);
        int recipes = hideRecipes(runtime, expanded);
        int categories = hideCategories(runtime, expanded);
        JustEnoughHiding.LOGGER.info("[JEH] jehide: hid {} ingredients, {} recipes, {} categories",
            ingredients, recipes, categories);
    }

    /**
     * JEI caches its ingredient list. {@code hideIngredients}/{@code unhideIngredients} do not
     * always re-evaluate it, so a deleted/disabled entry would stay hidden until a manual reload.
     * Reach the internal filter and ask it to recompute hidden state (it calls isIngredientVisible,
     * which our visibility mixin controls) and drop its cache.
     */
    private static void refreshIngredientFilter(IJeiRuntime runtime)
    {
        try
        {
            IIngredientFilter api = runtime.getIngredientFilter();
            Object internal = api;
            try
            {
                Field field = api.getClass().getDeclaredField("ingredientFilter");
                field.setAccessible(true);
                internal = field.get(api);
            }
            catch (Throwable ignored)
            {
            }

            boolean refreshed = invokeNoArg(internal, "updateHidden");
            refreshed |= invokeNoArg(internal, "invalidateCache");
            if (!refreshed)
            {
                reloadClientResources();
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private static void reloadClientResources()
    {
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null)
            {
                minecraft.reloadResourcePacks();
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private static boolean invokeNoArg(Object target, String method)
    {
        if (target == null)
        {
            return false;
        }
        try
        {
            target.getClass().getMethod(method).invoke(target);
            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    public static void reapply()
    {
        IJeiRuntime runtime = currentRuntime;
        if (runtime != null)
        {
            apply(runtime);
        }
    }

    public static synchronized void reset()
    {
        currentRuntime = null;
        previousRuntime = null;
        clearState();
    }

    /** Fast check used by the visibility mixin; uid is always computed with the Ingredient context. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean isHidden(ITypedIngredient<?> typed, IIngredientHelper<?> helper)
    {
        if (HIDDEN_INGREDIENTS.isEmpty() || typed == null || helper == null)
        {
            return false;
        }
        try
        {
            String typeUid = typed.getType().getUid();
            String uid = ((IIngredientHelper) helper).getUniqueId(typed.getIngredient(), UidContext.Ingredient);
            return uid != null && HIDDEN_INGREDIENTS.contains(typeUid + "|" + uid);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    private static void clearPrevious(IJeiRuntime runtime)
    {
        if (previousRuntime == runtime && !HIDDEN_INGREDIENT_OBJECTS.isEmpty())
        {
            try
            {
                IIngredientVisibility visibility = runtime.getIngredientVisibility();
                for (Map.Entry<IIngredientType<?>, List<Object>> entry : HIDDEN_INGREDIENT_OBJECTS.entrySet())
                {
                    try
                    {
                        unhideIngredients(visibility, entry.getKey(), entry.getValue());
                    }
                    catch (Throwable ignored)
                    {
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
            try
            {
                IRecipeManager manager = runtime.getRecipeManager();
                for (Map.Entry<RecipeType<?>, List<Object>> entry : HIDDEN_RECIPE_OBJECTS.entrySet())
                {
                    try
                    {
                        unhideRecipes(manager, entry.getKey(), entry.getValue());
                    }
                    catch (Throwable ignored)
                    {
                    }
                }
                for (RecipeType<?> type : HIDDEN_CATEGORY_OBJECTS)
                {
                    try
                    {
                        manager.unhideRecipeCategory(type);
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
        previousRuntime = runtime;
        clearState();
    }

    private static void clearState()
    {
        HIDDEN_INGREDIENTS.clear();
        HIDDEN_RECIPES.clear();
        HIDDEN_CATEGORIES.clear();
        HIDDEN_INGREDIENT_OBJECTS.clear();
        HIDDEN_RECIPE_OBJECTS.clear();
        HIDDEN_CATEGORY_OBJECTS.clear();
    }

    // ---- expansion ----

    private static List<IntentTarget> expand(List<IntentTarget> targets)
    {
        List<IntentTarget> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (IntentTarget target : targets)
        {
            expandInto(target, result, seen);
        }
        return result;
    }

    private static void expandInto(IntentTarget target, List<IntentTarget> out, Set<String> seen)
    {
        if (target == null || target instanceof IntentTarget.Unset)
        {
            return;
        }
        if (target instanceof IntentTarget.Pattern pattern)
        {
            for (TargetSuggestion suggestion : Adapters.active().matches(pattern, EXPAND_CAP))
            {
                expandInto(suggestion.target(), out, seen);
            }
            return;
        }
        if (seen.add(target.kind() + "|" + target.describe()))
        {
            out.add(target);
        }
    }

    // ---- ingredients ----

    private static int hideIngredients(IJeiRuntime runtime, List<IntentTarget> targets)
    {
        IIngredientManager manager = runtime.getIngredientManager();
        IIngredientVisibility visibility = runtime.getIngredientVisibility();

        Map<IIngredientType<?>, List<Object>> toHide = new LinkedHashMap<>();
        Set<String> requestedTags = new HashSet<>();

        for (IntentTarget target : targets)
        {
            if (target instanceof IntentTarget.Ingredient ingredient)
            {
                addIngredientByUid(manager, toHide, ingredient.key().typeUid(), ingredient.key().uid());
            }
            else if (target instanceof IntentTarget.Tag tag)
            {
                requestedTags.add(tag.tagId());
            }
        }
        if (!requestedTags.isEmpty())
        {
            collectTaggedIngredients(manager, requestedTags, toHide);
        }

        int count = 0;
        for (Map.Entry<IIngredientType<?>, List<Object>> entry : toHide.entrySet())
        {
            if (entry.getValue().isEmpty())
            {
                continue;
            }
            try
            {
                hideIngredients(visibility, entry.getKey(), entry.getValue());
                HIDDEN_INGREDIENT_OBJECTS.put(entry.getKey(), entry.getValue());
                count += entry.getValue().size();
            }
            catch (Throwable t)
            {
                JustEnoughHiding.LOGGER.warn("[JEH] jehide: failed to hide ingredients of type {}", entry.getKey(), t);
            }
        }
        return count;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addIngredientByUid(IIngredientManager manager, Map<IIngredientType<?>, List<Object>> toHide,
        String typeUid, String uid)
    {
        if (typeUid == null || uid == null)
        {
            return;
        }
        HIDDEN_INGREDIENTS.add(typeUid + "|" + uid);
        try
        {
            Optional<IIngredientType<?>> typeOptional = manager.getIngredientTypeForUid(typeUid);
            if (typeOptional.isEmpty())
            {
                return;
            }
            IIngredientType<?> type = typeOptional.get();
            Optional<?> ingredient = manager.getIngredientByUid((IIngredientType) type, uid);
            if (ingredient.isPresent() && ingredient.get() != null)
            {
                toHide.computeIfAbsent(type, ignored -> new ArrayList<>()).add(ingredient.get());
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void collectTaggedIngredients(IIngredientManager manager, Set<String> tags,
        Map<IIngredientType<?>, List<Object>> toHide)
    {
        for (IIngredientType<?> type : safeTypes(manager))
        {
            try
            {
                IIngredientHelper helper = manager.getIngredientHelper((IIngredientType) type);
                Collection<?> all = manager.getAllIngredients((IIngredientType) type);
                if (helper == null || all == null)
                {
                    continue;
                }
                for (Object ingredient : all)
                {
                    if (ingredient == null)
                    {
                        continue;
                    }
                    boolean tagged;
                    try
                    {
                        tagged = helper.getTagStream(ingredient)
                            .anyMatch(tag -> tag != null && tags.contains(tag.toString()));
                    }
                    catch (Throwable t)
                    {
                        continue;
                    }
                    if (!tagged)
                    {
                        continue;
                    }
                    toHide.computeIfAbsent(type, ignored -> new ArrayList<>()).add(ingredient);
                    try
                    {
                        String uid = helper.getUniqueId(ingredient, UidContext.Ingredient);
                        if (uid != null)
                        {
                            HIDDEN_INGREDIENTS.add(type.getUid() + "|" + uid);
                        }
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

    // ---- recipes / categories ----

    private static int hideRecipes(IJeiRuntime runtime, List<IntentTarget> targets)
    {
        IRecipeManager manager = runtime.getRecipeManager();
        Map<ResourceLocation, Set<String>> byType = new LinkedHashMap<>();
        for (IntentTarget target : targets)
        {
            if (target instanceof IntentTarget.Recipe recipe)
            {
                byType.computeIfAbsent(recipe.recipeType(), ignored -> new HashSet<>()).add(recipe.recipeId());
            }
        }

        int count = 0;
        for (Map.Entry<ResourceLocation, Set<String>> entry : byType.entrySet())
        {
            try
            {
                Optional<RecipeType<?>> typeOptional = manager.getRecipeType(entry.getKey());
                if (typeOptional.isEmpty())
                {
                    continue;
                }
                RecipeType<?> type = typeOptional.get();
                List<Object> matched = matchRecipes(manager, type, entry.getValue());
                if (matched.isEmpty())
                {
                    continue;
                }
                hideRecipes(manager, type, matched);
                HIDDEN_RECIPE_OBJECTS.put(type, matched);
                for (Object recipe : matched)
                {
                    String id = recipeId(manager, type, recipe);
                    if (id != null)
                    {
                        HIDDEN_RECIPES.add(type.getUid() + "|" + id);
                    }
                }
                count += matched.size();
            }
            catch (Throwable t)
            {
                JustEnoughHiding.LOGGER.warn("[JEH] jehide: failed to hide recipes of type {}", entry.getKey(), t);
            }
        }
        return count;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> matchRecipes(IRecipeManager manager, RecipeType<?> type, Set<String> ids)
    {
        List<Object> matched = new ArrayList<>();
        List<?> recipes = manager.createRecipeLookup((RecipeType) type).includeHidden().get().toList();
        for (Object recipe : recipes)
        {
            String id = recipeId(manager, type, recipe);
            if (id != null && ids.contains(id))
            {
                matched.add(recipe);
            }
        }
        return matched;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String recipeId(IRecipeManager manager, RecipeType<?> type, Object recipe)
    {
        if (recipe instanceof Recipe<?> vanilla)
        {
            try
            {
                return vanilla.getId().toString();
            }
            catch (Throwable ignored)
            {
            }
        }
        try
        {
            IRecipeCategory category = manager.getRecipeCategory((RecipeType) type);
            if (category != null)
            {
                ResourceLocation id = category.getRegistryName(recipe);
                if (id != null)
                {
                    return id.toString();
                }
            }
        }
        catch (Throwable ignored)
        {
        }
        return null;
    }

    private static int hideCategories(IJeiRuntime runtime, List<IntentTarget> targets)
    {
        IRecipeManager manager = runtime.getRecipeManager();
        int count = 0;
        for (IntentTarget target : targets)
        {
            if (!(target instanceof IntentTarget.RecipeCategory category))
            {
                continue;
            }
            try
            {
                Optional<RecipeType<?>> typeOptional = manager.getRecipeType(category.recipeType());
                if (typeOptional.isEmpty())
                {
                    continue;
                }
                RecipeType<?> type = typeOptional.get();
                if (HIDDEN_CATEGORY_OBJECTS.add(type))
                {
                    manager.hideRecipeCategory(type);
                    HIDDEN_CATEGORIES.add(type.getUid().toString());
                    count++;
                }
            }
            catch (Throwable t)
            {
                JustEnoughHiding.LOGGER.warn("[JEH] jehide: failed to hide category {}", category.recipeType(), t);
            }
        }
        return count;
    }

    // ---- raw helpers ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hideIngredients(IIngredientVisibility visibility, IIngredientType<?> type, List<Object> ingredients)
    {
        visibility.hideIngredients((IIngredientType) type, (Collection) ingredients, CONTEXTS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void unhideIngredients(IIngredientVisibility visibility, IIngredientType<?> type, List<Object> ingredients)
    {
        visibility.unhideIngredients((IIngredientType) type, (Collection) ingredients, CONTEXTS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hideRecipes(IRecipeManager manager, RecipeType<?> type, List<Object> recipes)
    {
        manager.hideRecipes((RecipeType) type, (Collection) recipes);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void unhideRecipes(IRecipeManager manager, RecipeType<?> type, List<Object> recipes)
    {
        manager.unhideRecipes((RecipeType) type, (Collection) recipes);
    }
}

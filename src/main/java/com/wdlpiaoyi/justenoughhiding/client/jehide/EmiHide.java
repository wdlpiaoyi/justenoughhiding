package com.wdlpiaoyi.justenoughhiding.client.jehide;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetMatcher;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHiding;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHidingEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the enabled {@link ListEHiding} entries (plus recorded hide-intents when
 * {@code [jehide] applyIntents}) and hides the matching content from EMI.
 * <p>
 * EMI has no runtime hide API: hiding is expressed as a predicate that EMI evaluates while it
 * bakes its index. The EMI mixins add a predicate consulting this class, after clearing the
 * invalidators registered by other mods (the reveal module). Takes precedence over reveal for
 * its targets.
 * <p>
 * This class deliberately stays free of EMI types (no hard dependency): the mixins translate
 * {@code EmiStack}/{@code EmiRecipe} into plain ids before asking here.
 */
public final class EmiHide
{
    private static final long DEBOUNCE_MS = 800L;

    private record Snapshot(Set<String> ingredientIds, Set<String> recipeIds, Set<String> categoryIds,
        List<Pattern> ingredientPatterns, List<Pattern> recipePatterns, List<Pattern> categoryPatterns)
    {
        static final Snapshot EMPTY = new Snapshot(Set.of(), Set.of(), Set.of(), List.of(), List.of(), List.of());
    }

    private static volatile Snapshot snapshot;

    private static volatile boolean dirty;
    private static volatile long lastChangeMs;
    private static volatile boolean lastEnabled = true;
    private static volatile boolean lastApplyIntents = true;
    private static boolean listenerRegistered;

    private EmiHide()
    {
    }

    /**
     * Called by the EMI plugin on every reload; the snapshot is rebuilt lazily so it always
     * reflects the current list and intents.
     */
    public static void apply(Object registry)
    {
        invalidate();
    }

    public static void invalidate()
    {
        snapshot = null;
    }

    /** Called every client tick: re-apply (debounced) after config changes. */
    public static void tick()
    {
        boolean enabled = JehConfig.jehideEnabled();
        boolean applyIntents = JehConfig.jehideApplyIntents();
        if (enabled != lastEnabled || applyIntents != lastApplyIntents)
        {
            lastEnabled = enabled;
            lastApplyIntents = applyIntents;
            dirty = true;
            lastChangeMs = System.currentTimeMillis();
        }
        if (!dirty || System.currentTimeMillis() - lastChangeMs < DEBOUNCE_MS)
        {
            return;
        }
        dirty = false;
        reapply();
    }

    public static void reapply()
    {
        invalidate();
        if (!reloadEmi())
        {
            reloadResources();
        }
    }

    public static boolean isHidden(String id)
    {
        if (id == null)
        {
            return false;
        }
        Snapshot current = snapshot();
        if (current.ingredientIds().contains(id))
        {
            return true;
        }
        return matches(current.ingredientPatterns(), id);
    }

    public static boolean isHiddenRecipeId(String recipeId)
    {
        if (recipeId == null)
        {
            return false;
        }
        Snapshot current = snapshot();
        return current.recipeIds().contains(recipeId) || matches(current.recipePatterns(), recipeId);
    }

    public static boolean isHiddenCategoryId(String categoryId)
    {
        if (categoryId == null)
        {
            return false;
        }
        Snapshot current = snapshot();
        return current.categoryIds().contains(categoryId) || matches(current.categoryPatterns(), categoryId);
    }

    public static boolean isHiddenRecipe(String recipeId, String categoryId)
    {
        return isHiddenRecipeId(recipeId) || isHiddenCategoryId(categoryId);
    }

    private static boolean matches(List<Pattern> patterns, String id)
    {
        for (Pattern pattern : patterns)
        {
            if (pattern.matcher(id).matches())
            {
                return true;
            }
        }
        return false;
    }

    private static Snapshot snapshot()
    {
        Snapshot current = snapshot;
        if (current == null)
        {
            current = build();
            snapshot = current;
        }
        return current;
    }

    private static synchronized Snapshot build()
    {
        Snapshot current = snapshot;
        if (current != null)
        {
            return current;
        }
        if (!JehConfig.jehideEnabled())
        {
            return Snapshot.EMPTY;
        }
        ensureListener();

        List<IntentTarget> targets = new ArrayList<>();
        for (ListEHidingEntry entry : ListEHiding.get().entries())
        {
            if (entry.enabled())
            {
                targets.add(entry.target());
            }
        }
        if (JehConfig.jehideApplyIntents())
        {
            for (Intent intent : IntentRegistry.query().all())
            {
                if (intent.kind().isHide() && IntentOverrides.isEnabled(intent.target(), intent.source().id()))
                {
                    targets.add(intent.target());
                }
            }
        }

        Set<String> ingredientIds = new HashSet<>();
        Set<String> recipeIds = new HashSet<>();
        Set<String> categoryIds = new HashSet<>();
        List<Pattern> ingredientPatterns = new ArrayList<>();
        List<Pattern> recipePatterns = new ArrayList<>();
        List<Pattern> categoryPatterns = new ArrayList<>();

        for (IntentTarget target : targets)
        {
            if (target == null || target instanceof IntentTarget.Unset)
            {
                continue;
            }
            if (target instanceof IntentTarget.Ingredient ingredient)
            {
                ingredientIds.add(ingredient.key().uid());
            }
            else if (target instanceof IntentTarget.Recipe recipe)
            {
                recipeIds.add(recipe.recipeId());
            }
            else if (target instanceof IntentTarget.RecipeCategory category)
            {
                categoryIds.add(category.recipeType().toString());
            }
            else if (target instanceof IntentTarget.Tag tag)
            {
                expandTag(tag.tagId(), ingredientIds);
            }
            else if (target instanceof IntentTarget.Pattern pattern)
            {
                Pattern compiled = TargetMatcher.compile(pattern);
                if (compiled == null)
                {
                    continue;
                }
                String scope = pattern.scope() == null ? "" : pattern.scope();
                boolean any = scope.isEmpty();
                if (any || scope.startsWith("ingredient|"))
                {
                    ingredientPatterns.add(compiled);
                }
                if (any || scope.equals("recipe"))
                {
                    recipePatterns.add(compiled);
                }
                if (any || scope.equals("recipe_category"))
                {
                    categoryPatterns.add(compiled);
                }
            }
        }

        return new Snapshot(Set.copyOf(ingredientIds), Set.copyOf(recipeIds), Set.copyOf(categoryIds),
            List.copyOf(ingredientPatterns), List.copyOf(recipePatterns), List.copyOf(categoryPatterns));
    }

    private static void expandTag(String tagId, Set<String> out)
    {
        ResourceLocation id = tagId == null ? null : ResourceLocation.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId);
        if (id == null)
        {
            return;
        }
        TagKey<Item> itemTag = TagKey.create(Registries.ITEM, id);
        for (Item item : ForgeRegistries.ITEMS)
        {
            if (item == null)
            {
                continue;
            }
            try
            {
                if (new ItemStack(item).is(itemTag))
                {
                    ResourceLocation name = ForgeRegistries.ITEMS.getKey(item);
                    if (name != null)
                    {
                        out.add(name.toString());
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        TagKey<Fluid> fluidTag = TagKey.create(Registries.FLUID, id);
        for (Fluid fluid : ForgeRegistries.FLUIDS)
        {
            if (fluid == null)
            {
                continue;
            }
            try
            {
                if (fluid.is(fluidTag))
                {
                    ResourceLocation name = ForgeRegistries.FLUIDS.getKey(fluid);
                    if (name != null)
                    {
                        out.add(name.toString());
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static synchronized void ensureListener()
    {
        if (listenerRegistered)
        {
            return;
        }
        listenerRegistered = true;
        IntentRegistry.query().addListener(intent ->
        {
            if (JehConfig.jehideEnabled() && JehConfig.jehideApplyIntents())
            {
                lastChangeMs = System.currentTimeMillis();
                dirty = true;
            }
        });
    }

    private static boolean reloadEmi()
    {
        try
        {
            Class<?> manager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
            Method reload = manager.getMethod("reload");
            reload.invoke(null);
            return true;
        }
        catch (Throwable t)
        {
            JustEnoughHiding.LOGGER.warn("[JEH] jehide: failed to reload EMI, falling back to resource reload", t);
            return false;
        }
    }

    private static void reloadResources()
    {
        try
        {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft != null)
            {
                minecraft.reloadResourcePacks();
            }
        }
        catch (Throwable ignored)
        {
        }
    }
}

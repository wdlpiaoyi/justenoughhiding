package com.wdlpiaoyi.justenoughhiding.integration.emi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.jehide.JehReloadHooks;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSuppressor;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.intent.source.ModSourceResolver;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiHidden;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Records EMI-native hide actions as intents, mirroring {@code JeiIntentRecorder}:
 * plugin stack/recipe removals, EMI edit-mode visibility changes and the hidden tags /
 * plugin-disabled stacks EMI itself applies while baking. Only loaded when EMI is present.
 * <p>
 * The expensive parts (reading the data-pack files and scanning the item/fluid registries for tag
 * and data-pack filter matches) are cached per {@code (ResourceManager, Level)} and only recomputed
 * when the resource manager or the world changes. Plugin-disabled stacks/filters change on every
 * EMI reload, so those are rechecked each bake (with a fast path when empty).
 */
public final class EmiIntentRecorder
{
    private static final String ITEM_TYPE = "minecraft:item_stack";
    private static final String FLUID_TYPE = "fluid_stack";

    private record PackFilter(Predicate<String> predicate, IntentSource source)
    {
    }

    private record RecipePredicate(Predicate<EmiRecipe> predicate, IntentSource source)
    {
    }

    private record HiddenEntry(EmiStack stack, IntentKind kind, IntentSource source)
    {
    }

    /** {@code removeRecipes} predicates captured at register time, evaluated at the next bake. */
    private static final List<RecipePredicate> CAPTURED_RECIPES = new CopyOnWriteArrayList<>();

    private static volatile ResourceManager cachedResourceManager;
    private static volatile Level cachedLevel;
    private static volatile List<HiddenEntry> cachedItemHides;
    private static volatile List<RecipePredicate> cachedPackRecipeFilters;

    static
    {
        JehReloadHooks.add(EmiIntentRecorder::invalidateCache);
    }

    private EmiIntentRecorder()
    {
    }

    private static boolean disabled()
    {
        return !JehConfig.intentRecordingEnabled() || IntentSuppressor.suppressed();
    }

    /** Drops the cached data-pack/item scan so it is rebuilt on the next bake. */
    public static void invalidateCache()
    {
        cachedResourceManager = null;
        cachedLevel = null;
        cachedItemHides = null;
        cachedPackRecipeFilters = null;
    }

    /** Evaluates a {@code removeEmiStacks} predicate against the current raw stack list. */
    public static void recordRemovedStacks(Predicate<EmiStack> predicate)
    {
        if (disabled() || predicate == null)
        {
            return;
        }
        IntentSource source = currentSource();
        if (isSelf(source))
        {
            return;
        }

        List<EmiStack> stacks;
        try
        {
            stacks = EmiStackList.stacks;
        }
        catch (Throwable t)
        {
            return;
        }
        if (stacks == null)
        {
            return;
        }

        for (EmiStack stack : List.copyOf(stacks))
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }
            boolean removed;
            try
            {
                removed = predicate.test(stack);
            }
            catch (Throwable t)
            {
                continue;
            }
            if (removed)
            {
                recordStack(stack, IntentKind.REMOVED, source);
            }
        }
    }

    public static void recordIngredient(EmiIngredient ingredient, IntentKind kind, IntentSource source)
    {
        if (disabled() || ingredient == null)
        {
            return;
        }
        IntentSource resolved = source != null ? source : currentSource();
        if (isSelf(resolved))
        {
            return;
        }
        for (EmiStack stack : stacksOf(ingredient))
        {
            recordStack(stack, kind, resolved);
        }
    }

    public static void removeIngredient(EmiIngredient ingredient, IntentSource source)
    {
        if (disabled() || ingredient == null)
        {
            return;
        }
        IntentSource resolved = source != null ? source : currentSource();
        if (isSelf(resolved))
        {
            return;
        }
        for (EmiStack stack : stacksOf(ingredient))
        {
            IntentTarget target = targetOf(stack);
            if (target != null)
            {
                IntentRegistry.remove(target, resolved.id());
            }
        }
    }

    /**
     * Captures a plugin {@code removeRecipes} predicate at register time. It is evaluated later,
     * at the next recipe bake over the full recipe list (where the recipes actually exist).
     */
    public static void captureRemovedRecipes(Predicate<EmiRecipe> predicate)
    {
        if (disabled() || predicate == null)
        {
            return;
        }
        IntentSource source = currentSource();
        if (isSelf(source))
        {
            return;
        }
        CAPTURED_RECIPES.add(new RecipePredicate(predicate, source));
    }

    /** Cleared at the start of every EMI reload, before plugins re-register. */
    public static void clearCapturedRecipes()
    {
        CAPTURED_RECIPES.clear();
    }

    /**
     * Records what EMI itself hides while baking: the {@code c:hidden_from_recipe_viewers} tags,
     * plugin-disabled stacks/filters, and the data-pack ({@code emi:index_stacks}) removals and
     * filters. Data-pack hides are attributed to the resource/data pack that provided the file;
     * tag/plugin hides use the hidden stack's namespace (best effort). Duplicates are not counted.
     */
    public static void scanHiddenStacks()
    {
        if (disabled())
        {
            return;
        }
        try
        {
            ensureCache();
            List<HiddenEntry> hides = cachedItemHides;
            if (hides != null)
            {
                Map<String, Set<String>> produced = new HashMap<>();
                for (HiddenEntry entry : hides)
                {
                    recordWith(entry.stack(), entry.kind(), entry.source());
                    addProduced(produced, entry.source(), entry.stack());
                }
                IntentRegistry.retainResourcePackTargets(Set.of(IntentKind.HIDDEN), produced);
            }
            recordPluginDisabled();
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Records recipe hiding applied by the {@code emi:recipe_filters} data-pack files (attributed
     * to the providing pack) and by plugin {@code removeRecipes} predicates (attributed to the
     * plugin's mod). Called after EMI baked its recipes, over the full recipe list.
     */
    public static void scanHiddenRecipes(List<EmiRecipe> allRecipes)
    {
        if (disabled() || allRecipes == null || allRecipes.isEmpty())
        {
            return;
        }
        try
        {
            ensureCache();
            List<RecipePredicate> predicates = new ArrayList<>(CAPTURED_RECIPES);
            if (cachedPackRecipeFilters != null)
            {
                predicates.addAll(cachedPackRecipeFilters);
            }
            if (predicates.isEmpty())
            {
                return;
            }
            Map<String, Set<String>> produced = new HashMap<>();
            for (EmiRecipe recipe : allRecipes)
            {
                if (recipe == null)
                {
                    continue;
                }
                IntentTarget target = recipeTarget(recipe);
                if (target == null)
                {
                    continue;
                }
                for (RecipePredicate entry : predicates)
                {
                    boolean matched;
                    try
                    {
                        matched = entry.predicate().test(recipe);
                    }
                    catch (Throwable t)
                    {
                        continue;
                    }
                    if (!matched)
                    {
                        continue;
                    }
                    if (entry.source().type() == IntentSource.Type.RESOURCE_PACK)
                    {
                        produced.computeIfAbsent(entry.source().id(), ignored -> new HashSet<>()).add(targetKey(target));
                    }
                    if (!IntentRegistry.contains(target, IntentKind.RECIPE_HIDDEN, entry.source().id()))
                    {
                        IntentRegistry.record(target, IntentKind.RECIPE_HIDDEN, entry.source());
                    }
                }
            }
            if (cachedPackRecipeFilters != null)
            {
                IntentRegistry.retainResourcePackTargets(Set.of(IntentKind.RECIPE_HIDDEN), produced);
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private static synchronized void ensureCache()
    {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager manager = minecraft == null ? null : minecraft.getResourceManager();
        Level level = minecraft == null ? null : minecraft.level;
        if (manager == null)
        {
            invalidateCache();
            return;
        }
        if (manager == cachedResourceManager && level == cachedLevel && cachedItemHides != null)
        {
            return;
        }

        List<HiddenEntry> hides = new ArrayList<>();
        List<PackFilter> filters = new ArrayList<>();
        List<RecipePredicate> recipeFilters = new ArrayList<>();
        readPackFiles(hides, filters, recipeFilters);
        scanItems(hides, filters);

        cachedPackRecipeFilters = recipeFilters;
        cachedItemHides = hides;
        cachedResourceManager = manager;
        cachedLevel = level;
    }

    /** Recheck plugin-disabled stacks/filters each bake; they are rebuilt on every EMI reload. */
    private static void recordPluginDisabled()
    {
        List<EmiIngredient> disabledStacks = List.copyOf(EmiHidden.pluginDisabledStacks);
        List<Predicate<EmiStack>> disabledFilters = List.copyOf(EmiHidden.pluginDisabledFilters);

        for (EmiIngredient ingredient : disabledStacks)
        {
            if (ingredient == null)
            {
                continue;
            }
            for (EmiStack stack : stacksOf(ingredient))
            {
                recordAbsent(stack, IntentKind.HIDDEN, namespaceOf(stack));
            }
        }

        if (disabledFilters.isEmpty())
        {
            return;
        }
        for (Item item : ForgeRegistries.ITEMS)
        {
            if (item == null)
            {
                continue;
            }
            EmiStack stack;
            String namespace;
            try
            {
                stack = EmiStack.of(item);
                if (stack == null || stack.isEmpty())
                {
                    continue;
                }
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                namespace = id == null ? "unknown" : id.getNamespace();
            }
            catch (Throwable t)
            {
                continue;
            }
            for (Predicate<EmiStack> filter : disabledFilters)
            {
                boolean isDisabled;
                try
                {
                    isDisabled = filter.test(stack);
                }
                catch (Throwable t)
                {
                    continue;
                }
                if (isDisabled)
                {
                    recordAbsent(stack, IntentKind.HIDDEN, namespace);
                    break;
                }
            }
        }
    }

    private static void readPackFiles(List<HiddenEntry> hides, List<PackFilter> filters,
        List<RecipePredicate> recipeFilters)
    {
        forEachPackResource("index/stacks", (resource, source) ->
        {
            try (BufferedReader reader = resource.openAsReader())
            {
                JsonObject json = parseObject(reader);
                if (json == null)
                {
                    return;
                }
                readRemoved(json, source, hides);
                readFilters(json, source, filters);
            }
            catch (Throwable ignored)
            {
            }
        });

        forEachPackResource("recipe/filters", (resource, source) ->
        {
            try (BufferedReader reader = resource.openAsReader())
            {
                JsonObject json = parseObject(reader);
                if (json == null)
                {
                    return;
                }
                JsonElement element = json.get("filters");
                if (element == null || !element.isJsonArray())
                {
                    return;
                }
                for (JsonElement entry : element.getAsJsonArray())
                {
                    if (entry == null || !entry.isJsonObject())
                    {
                        continue;
                    }
                    Predicate<EmiRecipe> predicate = recipeFilter(entry.getAsJsonObject());
                    if (predicate != null)
                    {
                        recipeFilters.add(new RecipePredicate(predicate, source));
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        });
    }

    private static void readRemoved(JsonObject json, IntentSource source, List<HiddenEntry> out)
    {
        JsonElement element = json.get("removed");
        if (element == null || !element.isJsonArray())
        {
            return;
        }
        for (JsonElement entry : element.getAsJsonArray())
        {
            try
            {
                EmiIngredient ingredient = EmiIngredientSerializer.getDeserialized(entry);
                for (EmiStack stack : stacksOf(ingredient))
                {
                    out.add(new HiddenEntry(stack, IntentKind.HIDDEN, source));
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static void readFilters(JsonObject json, IntentSource source, List<PackFilter> out)
    {
        JsonElement element = json.get("filters");
        if (element == null || !element.isJsonArray())
        {
            return;
        }
        for (JsonElement entry : element.getAsJsonArray())
        {
            if (entry == null || !entry.isJsonPrimitive())
            {
                continue;
            }
            Predicate<String> predicate = filterPredicate(entry.getAsString());
            if (predicate != null)
            {
                out.add(new PackFilter(predicate, source));
            }
        }
    }

    private static JsonObject parseObject(BufferedReader reader)
    {
        JsonElement root = JsonParser.parseReader(reader);
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    /** Scans the item/fluid registries once for tag-hidden entries and data-pack filter matches. */
    private static void scanItems(List<HiddenEntry> hides, List<PackFilter> filters)
    {
        TagKey<Item> itemTag = TagKey.create(Registries.ITEM, EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
        TagKey<Fluid> fluidTag = TagKey.create(Registries.FLUID, EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);

        for (Item item : ForgeRegistries.ITEMS)
        {
            if (item == null)
            {
                continue;
            }
            EmiStack stack;
            ItemStack vanilla;
            String namespace;
            String idString;
            try
            {
                vanilla = new ItemStack(item);
                if (vanilla.isEmpty())
                {
                    continue;
                }
                stack = EmiStack.of(item);
                if (stack == null || stack.isEmpty())
                {
                    continue;
                }
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                namespace = id == null ? "unknown" : id.getNamespace();
                idString = id == null ? null : id.toString();
            }
            catch (Throwable t)
            {
                continue;
            }

            boolean hidden;
            try
            {
                hidden = vanilla.is(itemTag)
                    || (item instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().is(blockTag));
            }
            catch (Throwable t)
            {
                hidden = false;
            }
            if (hidden)
            {
                hides.add(new HiddenEntry(stack, IntentKind.TAG_HIDDEN, IntentSource.mod(namespace)));
                continue;
            }
            IntentSource filterSource = matchPackFilter(filters, idString);
            if (filterSource != null)
            {
                hides.add(new HiddenEntry(stack, IntentKind.HIDDEN, filterSource));
            }
        }

        for (Fluid fluid : ForgeRegistries.FLUIDS)
        {
            if (fluid == null)
            {
                continue;
            }
            try
            {
                if (!fluid.is(fluidTag))
                {
                    continue;
                }
                EmiStack stack = EmiStack.of(fluid);
                if (stack != null && !stack.isEmpty())
                {
                    ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
                    String namespace = id == null ? "unknown" : id.getNamespace();
                    hides.add(new HiddenEntry(stack, IntentKind.TAG_HIDDEN, IntentSource.mod(namespace)));
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static IntentSource matchPackFilter(List<PackFilter> filters, String id)
    {
        if (id == null || filters.isEmpty())
        {
            return null;
        }
        for (PackFilter filter : filters)
        {
            try
            {
                if (filter.predicate().test(id))
                {
                    return filter.source();
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        return null;
    }

    private interface PackResourceConsumer
    {
        void accept(Resource resource, IntentSource source);
    }

    private static void forEachPackResource(String path, PackResourceConsumer consumer)
    {
        ResourceManager manager;
        Map<ResourceLocation, List<Resource>> resources;
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || (manager = minecraft.getResourceManager()) == null)
            {
                return;
            }
            resources = manager.listResourceStacks(path, location -> location.getPath().endsWith(".json"));
        }
        catch (Throwable t)
        {
            return;
        }
        if (resources == null)
        {
            return;
        }
        for (Map.Entry<ResourceLocation, List<Resource>> entry : resources.entrySet())
        {
            ResourceLocation location = entry.getKey();
            if (location == null || !"emi".equals(location.getNamespace()) || entry.getValue() == null)
            {
                continue;
            }
            for (Resource resource : entry.getValue())
            {
                if (resource != null)
                {
                    consumer.accept(resource, packSource(resource));
                }
            }
        }
    }

    /** Builds a predicate for one {@code recipe_filters} entry ({@code id} and/or {@code category}). */
    private static Predicate<EmiRecipe> recipeFilter(JsonObject object)
    {
        List<Predicate<EmiRecipe>> parts = new ArrayList<>();
        if (object.has("id") && object.get("id").isJsonPrimitive())
        {
            Predicate<String> idPredicate = filterPredicate(object.get("id").getAsString());
            if (idPredicate != null)
            {
                parts.add(recipe ->
                {
                    String id = recipeId(recipe);
                    return id != null && idPredicate.test(id);
                });
            }
        }
        if (object.has("category") && object.get("category").isJsonPrimitive())
        {
            Predicate<String> categoryPredicate = filterPredicate(object.get("category").getAsString());
            if (categoryPredicate != null)
            {
                parts.add(recipe ->
                {
                    String category = recipeCategoryId(recipe);
                    return category != null && categoryPredicate.test(category);
                });
            }
        }
        if (parts.isEmpty())
        {
            return null;
        }
        if (parts.size() == 1)
        {
            return parts.get(0);
        }
        return recipe ->
        {
            for (Predicate<EmiRecipe> part : parts)
            {
                if (!part.test(recipe))
                {
                    return false;
                }
            }
            return true;
        };
    }

    private static Predicate<String> filterPredicate(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        if (value.startsWith("/") && value.endsWith("/") && value.length() > 2)
        {
            try
            {
                Pattern pattern = Pattern.compile(value.substring(1, value.length() - 1));
                return id -> pattern.matcher(id).find();
            }
            catch (Throwable t)
            {
                return null;
            }
        }
        return value::equals;
    }

    private static IntentSource packSource(Resource resource)
    {
        String id;
        try
        {
            id = resource.sourcePackId();
        }
        catch (Throwable t)
        {
            id = null;
        }
        return IntentSource.pack(cleanPackId(id));
    }

    private static String cleanPackId(String id)
    {
        if (id == null || id.isBlank())
        {
            return "datapack";
        }
        String value = id.trim();
        if (value.startsWith("file/"))
        {
            value = value.substring("file/".length());
        }
        if (value.toLowerCase(Locale.ROOT).endsWith(".zip"))
        {
            value = value.substring(0, value.length() - ".zip".length());
        }
        while (value.endsWith("/"))
        {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "datapack" : value;
    }

    private static IntentTarget recipeTarget(EmiRecipe recipe)
    {
        try
        {
            String id = recipeId(recipe);
            String category = recipeCategoryId(recipe);
            if (id == null || category == null)
            {
                return null;
            }
            ResourceLocation type = ResourceLocation.tryParse(category);
            return type == null ? null : IntentTarget.of(type, id);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String recipeId(EmiRecipe recipe)
    {
        try
        {
            ResourceLocation id = recipe.getId();
            return id == null ? null : id.toString();
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String recipeCategoryId(EmiRecipe recipe)
    {
        try
        {
            return recipe.getCategory() == null || recipe.getCategory().getId() == null
                ? null
                : recipe.getCategory().getId().toString();
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static void recordWith(EmiStack stack, IntentKind kind, IntentSource source)
    {
        if (source == null)
        {
            return;
        }
        IntentTarget target = targetOf(stack);
        if (target == null)
        {
            return;
        }
        if (IntentRegistry.contains(target, kind, source.id()))
        {
            return;
        }
        IntentRegistry.record(target, kind, source);
    }

    /** Adds a resource pack's currently produced target key, used to prune stale pack intents. */
    private static void addProduced(Map<String, Set<String>> produced, IntentSource source, EmiStack stack)
    {
        if (source == null || source.type() != IntentSource.Type.RESOURCE_PACK)
        {
            return;
        }
        IntentTarget target = targetOf(stack);
        if (target != null)
        {
            produced.computeIfAbsent(source.id(), ignored -> new HashSet<>()).add(targetKey(target));
        }
    }

    private static String targetKey(IntentTarget target)
    {
        return target.kind() + "|" + target.describe();
    }

    private static void recordAbsent(EmiStack stack, IntentKind kind, String namespace)
    {
        recordWith(stack, kind, IntentSource.mod(namespace));
    }

    private static void recordStack(EmiStack stack, IntentKind kind, IntentSource source)
    {
        IntentTarget target = targetOf(stack);
        if (target != null)
        {
            IntentRegistry.record(target, kind, source);
        }
    }

    private static IntentTarget targetOf(EmiStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return null;
        }
        try
        {
            String typeUid;
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
                return null;
            }
            ResourceLocation id = stack.getId();
            return id == null ? null : IntentTarget.of(IngredientKey.of(typeUid, id.toString()));
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static List<EmiStack> stacksOf(EmiIngredient ingredient)
    {
        try
        {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            return stacks == null ? List.of() : stacks;
        }
        catch (Throwable t)
        {
            return List.of();
        }
    }

    private static String namespaceOf(EmiStack stack)
    {
        try
        {
            ResourceLocation id = stack.getId();
            return id == null ? "unknown" : id.getNamespace();
        }
        catch (Throwable t)
        {
            return "unknown";
        }
    }

    private static IntentSource currentSource()
    {
        return ModSourceResolver.sourceOfCaller();
    }

    private static boolean isSelf(IntentSource source)
    {
        return source != null && JustEnoughHiding.MODID.equals(source.id());
    }
}

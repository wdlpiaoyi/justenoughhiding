package com.wdlpiaoyi.justenoughhiding.integration.emi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSuppressor;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.intent.source.ModSourceResolver;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Records EMI-native hide actions as intents, mirroring {@code JeiIntentRecorder}:
 * plugin stack removals ({@code EmiRegistry.removeEmiStacks}), EMI edit-mode visibility changes
 * ({@code EmiHidden.setVisibility}) and the hidden tags / plugin-disabled stacks EMI itself applies
 * while baking. Only loaded when EMI is present.
 * <p>
 * Plugin removals are predicates, not concrete stacks. EMI's reload calls
 * {@code EmiStackList.reload()} (the raw list) <em>before</em> plugins register, so the predicate
 * can be evaluated right away.
 */
public final class EmiIntentRecorder
{
    private static final String ITEM_TYPE = "minecraft:item_stack";
    private static final String FLUID_TYPE = "fluid_stack";

    private record PackRemoved(EmiStack stack, IntentSource source)
    {
    }

    private record PackFilter(Predicate<String> predicate, IntentSource source)
    {
    }

    private EmiIntentRecorder()
    {
    }

    private static boolean disabled()
    {
        return !JehConfig.intentRecordingEnabled() || IntentSuppressor.suppressed();
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
     * Records what EMI itself hides while baking: the {@code c:hidden_from_recipe_viewers} tags,
     * plugin-disabled stacks/predicates, and the data-pack ({@code emi:index_stacks}) removals and
     * filters. Data-pack hides are attributed to the resource/data pack that provided the file;
     * tag/plugin hides use the hidden stack's namespace (best effort). Duplicates are not counted
     * again.
     */
    public static void scanHiddenStacks()
    {
        if (disabled())
        {
            return;
        }
        try
        {
            TagKey<Item> itemTag = TagKey.create(Registries.ITEM, EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
            TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
            TagKey<Fluid> fluidTag = TagKey.create(Registries.FLUID, EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
            List<Predicate<EmiStack>> disabledFilters = List.copyOf(EmiHidden.pluginDisabledFilters);

            List<PackRemoved> packRemoved = new ArrayList<>();
            List<PackFilter> packFilters = new ArrayList<>();
            collectPackData(packRemoved, packFilters);

            for (Item item : ForgeRegistries.ITEMS)
            {
                if (item == null)
                {
                    continue;
                }
                EmiStack stack;
                ItemStack vanilla = ItemStack.EMPTY;
                String namespace = "unknown";
                String idString = null;
                try
                {
                    vanilla = new ItemStack(item);
                    if (vanilla.isEmpty())
                    {
                        continue;
                    }
                    stack = EmiStack.of(item);
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id != null)
                    {
                        namespace = id.getNamespace();
                        idString = id.toString();
                    }
                }
                catch (Throwable t)
                {
                    continue;
                }
                if (stack == null || stack.isEmpty())
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
                    recordAbsent(stack, IntentKind.TAG_HIDDEN, namespace);
                    continue;
                }
                if (recordPackFilterMatch(stack, idString, packFilters))
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

            for (Fluid fluid : ForgeRegistries.FLUIDS)
            {
                if (fluid == null)
                {
                    continue;
                }
                boolean hidden;
                EmiStack stack;
                String namespace = "unknown";
                try
                {
                    hidden = fluid.is(fluidTag);
                    if (!hidden)
                    {
                        continue;
                    }
                    stack = EmiStack.of(fluid);
                    ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
                    if (id != null)
                    {
                        namespace = id.getNamespace();
                    }
                }
                catch (Throwable t)
                {
                    continue;
                }
                if (stack != null && !stack.isEmpty())
                {
                    recordAbsent(stack, IntentKind.TAG_HIDDEN, namespace);
                }
            }

            for (PackRemoved removed : packRemoved)
            {
                if (removed.stack() != null)
                {
                    recordWith(removed.stack(), IntentKind.HIDDEN, removed.source());
                }
            }

            for (EmiIngredient ingredient : List.copyOf(EmiHidden.pluginDisabledStacks))
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
        }
        catch (Throwable ignored)
        {
        }
    }

    /** Reads {@code assets/emi/index/stacks/*.json} per pack, so hides are attributed to the pack. */
    private static void collectPackData(List<PackRemoved> removedOut, List<PackFilter> filtersOut)
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
            resources = manager.listResourceStacks("index/stacks", path -> path.getPath().endsWith(".json"));
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
                if (resource == null)
                {
                    continue;
                }
                IntentSource source = packSource(resource);
                try (BufferedReader reader = resource.openAsReader())
                {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (root == null || !root.isJsonObject())
                    {
                        continue;
                    }
                    JsonObject json = root.getAsJsonObject();
                    readRemoved(json, source, removedOut);
                    readFilters(json, source, filtersOut);
                }
                catch (Throwable ignored)
                {
                }
            }
        }
    }

    private static void readRemoved(JsonObject json, IntentSource source, List<PackRemoved> out)
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
                    out.add(new PackRemoved(stack, source));
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

    private static boolean recordPackFilterMatch(EmiStack stack, String id, List<PackFilter> filters)
    {
        if (id == null || filters.isEmpty())
        {
            return false;
        }
        for (PackFilter filter : filters)
        {
            boolean matched;
            try
            {
                matched = filter.predicate().test(id);
            }
            catch (Throwable t)
            {
                continue;
            }
            if (matched)
            {
                recordWith(stack, IntentKind.HIDDEN, filter.source());
                return true;
            }
        }
        return false;
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

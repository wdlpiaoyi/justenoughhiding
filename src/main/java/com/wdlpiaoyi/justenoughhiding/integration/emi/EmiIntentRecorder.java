package com.wdlpiaoyi.justenoughhiding.integration.emi;

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
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiHidden;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.function.Predicate;

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
     * Records what EMI itself hides while baking: the {@code c:hidden_from_recipe_viewers} tags and
     * plugin-disabled stacks/predicates. The source is the hidden stack's namespace (best effort,
     * mirroring JEH's JEI "absent" attribution); duplicates are not counted again.
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

            for (Item item : ForgeRegistries.ITEMS)
            {
                if (item == null)
                {
                    continue;
                }
                EmiStack stack;
                ItemStack vanilla = ItemStack.EMPTY;
                String namespace = "unknown";
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

            for (EmiIngredient ingredient : List.copyOf(EmiHidden.pluginDisabledStacks))
            {
                if (ingredient == null)
                {
                    continue;
                }
                for (EmiStack stack : stacksOf(ingredient))
                {
                    String namespace = "unknown";
                    try
                    {
                        ResourceLocation id = stack.getId();
                        if (id != null)
                        {
                            namespace = id.getNamespace();
                        }
                    }
                    catch (Throwable ignored)
                    {
                    }
                    recordAbsent(stack, IntentKind.HIDDEN, namespace);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private static void recordAbsent(EmiStack stack, IntentKind kind, String namespace)
    {
        IntentTarget target = targetOf(stack);
        if (target == null)
        {
            return;
        }
        IntentSource source = IntentSource.mod(namespace);
        if (IntentRegistry.contains(target, kind, source.id()))
        {
            return;
        }
        IntentRegistry.record(target, kind, source);
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

    private static IntentSource currentSource()
    {
        return ModSourceResolver.sourceOfCaller();
    }

    private static boolean isSelf(IntentSource source)
    {
        return source != null && JustEnoughHiding.MODID.equals(source.id());
    }
}

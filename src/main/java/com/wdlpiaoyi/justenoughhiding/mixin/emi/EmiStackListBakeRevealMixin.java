package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.viewer.emi.EmiRevealSupport;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.integration.emi.EmiIntentRecorder;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSuppressor;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.data.IndexStackData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * EMI stack index integration:
 * <ul>
 *   <li>Reveal: predicates registered through {@code EmiRegistry.removeEmiStacks(...)} are stored
 *       in {@code EmiStackList.invalidators} and applied destructively in {@code bake()}. Dropping
 *       them before the bake makes those stacks reappear.</li>
 *   <li>Re-adds item stacks that are missing from the index (e.g. because the {@code
 *       emi_accelerator} cache or a plugin dropped them).</li>
 *   <li>JEHide: re-adds JEH's own hide predicate after the clear, so JEH's list takes precedence
 *       over reveal.</li>
 * </ul>
 * Both behaviours are controlled by {@code [reveal] enabled} and {@code [jehide] enabled}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiStackList", remap = false)
public class EmiStackListBakeRevealMixin
{
    @Shadow
    public static List<Predicate<EmiStack>> invalidators;

    @Shadow
    public static List<EmiStack> stacks;

    private static final Predicate<EmiStack> JEH_HIDE = EmiRevealSupport::isHidden;

    private static boolean jeh$loggedActive;

    @Inject(method = "bake", at = @At("HEAD"), remap = false, require = 0)
    private static void jeh$revealInvalidators(CallbackInfo ci)
    {
        IntentSuppressor.release();
        if (!jeh$loggedActive)
        {
            jeh$loggedActive = true;
            JustEnoughHiding.LOGGER.info("[JEH] EMI reveal mixin active");
        }
        if (JehConfig.revealEnabled())
        {
            int count = invalidators.size();
            if (count > 0)
            {
                invalidators.clear();
                JustEnoughHiding.LOGGER.info("[JEH] reveal: cleared {} EMI stack invalidators", count);
            }
            jeh$addMissingItemStacks();
        }
        invalidators.remove(JEH_HIDE);
        if (JehConfig.jehideEnabled())
        {
            invalidators.add(JEH_HIDE);
        }
    }

    /** After EMI baked its index, record the hiding it applied (tags / plugin-disabled stacks). */
    @Inject(method = "bake", at = @At("TAIL"), remap = false, require = 0)
    private static void jeh$scanHiddenStacks(CallbackInfo ci)
    {
        EmiIntentRecorder.scanHiddenStacks();
    }

    /**
     * Reveal support: while reveal is enabled, ignore the {@code emi:index_stacks} data-pack
     * removals/filters, so those stacks are not dropped from EMI's index.
     */
    @Redirect(
        method = "bake",
        at = @At(value = "INVOKE", target = "Ldev/emi/emi/data/IndexStackData;removed()Ljava/util/List;", ordinal = 1),
        remap = false,
        require = 0
    )
    private static List<?> jeh$revealDataRemoved(IndexStackData data)
    {
        return JehConfig.revealEnabled() ? List.of() : data.removed();
    }

    @Redirect(
        method = "bake",
        at = @At(value = "INVOKE", target = "Ldev/emi/emi/data/IndexStackData;filters()Ljava/util/List;", ordinal = 1),
        remap = false,
        require = 0
    )
    private static List<?> jeh$revealDataFilters(IndexStackData data)
    {
        return JehConfig.revealEnabled() ? List.of() : data.filters();
    }

    /** Re-add default item stacks that are absent from the index, so reveal works with cached lists. */
    private static void jeh$addMissingItemStacks()
    {
        List<EmiStack> list = stacks;
        if (list == null)
        {
            return;
        }
        if (!(list instanceof ArrayList))
        {
            list = new ArrayList<>(list);
            stacks = list;
        }

        Set<Object> keys = new HashSet<>();
        for (EmiStack stack : list)
        {
            if (stack == null)
            {
                continue;
            }
            try
            {
                Object key = stack.getKey();
                if (key != null)
                {
                    keys.add(key);
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        int added = 0;
        for (Item item : ForgeRegistries.ITEMS)
        {
            if (item == null || keys.contains(item))
            {
                continue;
            }
            try
            {
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty())
                {
                    continue;
                }
                EmiStack emiStack = EmiStack.of(item);
                if (emiStack.isEmpty())
                {
                    continue;
                }
                list.add(emiStack);
                keys.add(item);
                added++;
            }
            catch (Throwable ignored)
            {
            }
        }
        if (added > 0)
        {
            JustEnoughHiding.LOGGER.info("[JEH] reveal: added {} item stacks that EMI was missing", added);
        }
    }
}

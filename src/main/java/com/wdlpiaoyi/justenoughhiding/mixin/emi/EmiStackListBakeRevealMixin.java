package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reveal support for EMI: predicates registered through {@code EmiRegistry.removeEmiStacks(...)}
 * are stored in {@code EmiStackList.invalidators} and applied destructively in {@code bake()}.
 * Dropping them before the bake makes those stacks reappear (mirrors JEH's JEI reveal of removed
 * ingredients). Also re-adds item stacks that are missing from the index (e.g. because the
 * {@code emi_accelerator} cache or a plugin dropped them). Controlled by {@code [reveal] enabled}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiStackList", remap = false)
public class EmiStackListBakeRevealMixin
{
    @Shadow
    public static List<Predicate<EmiStack>> invalidators;

    @Shadow
    public static List<EmiStack> stacks;

    private static boolean jeh$loggedActive;

    @Inject(method = "bake", at = @At("HEAD"), remap = false, require = 0)
    private static void jeh$revealInvalidators(CallbackInfo ci)
    {
        if (!jeh$loggedActive)
        {
            jeh$loggedActive = true;
            JustEnoughHiding.LOGGER.info("[JEH] EMI reveal mixin active");
        }
        if (!JehConfig.revealEnabled())
        {
            return;
        }
        int count = invalidators.size();
        if (count > 0)
        {
            invalidators.clear();
            JustEnoughHiding.LOGGER.info("[JEH] reveal: cleared {} EMI stack invalidators", count);
        }
        jeh$addMissingItemStacks();
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

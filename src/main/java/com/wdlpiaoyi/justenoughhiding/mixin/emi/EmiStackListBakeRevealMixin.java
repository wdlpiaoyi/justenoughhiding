package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import dev.emi.emi.api.stack.EmiStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reveal support for EMI: predicates registered through {@code EmiRegistry.removeEmiStacks(...)}
 * are stored in {@code EmiStackList.invalidators} and applied destructively in {@code bake()}.
 * Dropping them before the bake makes those stacks reappear (mirrors JEH's JEI reveal of removed
 * ingredients). Controlled by {@code [reveal] enabled}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiStackList", remap = false)
public class EmiStackListBakeRevealMixin
{
    @Shadow
    public static List<Predicate<EmiStack>> invalidators;

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
    }
}

package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import dev.emi.emi.api.recipe.EmiRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reveal support for EMI: predicates registered through {@code EmiRegistry.removeRecipes(...)}
 * live in {@code EmiRecipes.invalidators} and remove recipes during {@code bake()}. Dropping them
 * before the bake makes those recipes reappear. Controlled by {@code [reveal] enabled}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiRecipes", remap = false)
public class EmiRecipesBakeRevealMixin
{
    @Shadow
    public static List<Predicate<EmiRecipe>> invalidators;

    @Inject(method = "bake", at = @At("HEAD"), remap = false, require = 0)
    private static void jeh$revealInvalidators(CallbackInfo ci)
    {
        if (!JehConfig.revealEnabled())
        {
            return;
        }
        int count = invalidators.size();
        if (count > 0)
        {
            invalidators.clear();
            JustEnoughHiding.LOGGER.info("[JEH] reveal: cleared {} EMI recipe invalidators", count);
        }
    }
}

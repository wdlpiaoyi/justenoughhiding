package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.viewer.emi.EmiRevealSupport;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSuppressor;
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
 * EMI recipe index integration:
 * <ul>
 *   <li>Reveal: predicates registered through {@code EmiRegistry.removeRecipes(...)} live in
 *       {@code EmiRecipes.invalidators} and remove recipes during {@code bake()}. Dropping them
 *       before the bake makes those recipes reappear.</li>
 *   <li>JEHide: re-adds JEH's own hide predicate after the clear, so JEH's list takes precedence
 *       over reveal.</li>
 * </ul>
 * Controlled by {@code [reveal] enabled} / {@code [jehide] enabled}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiRecipes", remap = false)
public class EmiRecipesBakeRevealMixin
{
    @Shadow
    public static List<Predicate<EmiRecipe>> invalidators;

    private static final Predicate<EmiRecipe> JEH_HIDE = EmiRevealSupport::isHiddenRecipe;

    @Inject(method = "bake", at = @At("HEAD"), remap = false, require = 0)
    private static void jeh$revealInvalidators(CallbackInfo ci)
    {
        IntentSuppressor.release();
        if (JehConfig.revealEnabled())
        {
            int count = invalidators.size();
            if (count > 0)
            {
                invalidators.clear();
                JustEnoughHiding.LOGGER.info("[JEH] reveal: cleared {} EMI recipe invalidators", count);
            }
        }
        invalidators.remove(JEH_HIDE);
        if (JehConfig.jehideEnabled())
        {
            invalidators.add(JEH_HIDE);
        }
    }
}

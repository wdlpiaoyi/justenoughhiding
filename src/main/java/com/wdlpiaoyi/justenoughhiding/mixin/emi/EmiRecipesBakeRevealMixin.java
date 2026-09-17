package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.viewer.emi.EmiRevealSupport;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.integration.emi.EmiIntentRecorder;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSuppressor;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.data.EmiData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * EMI recipe index integration:
 * <ul>
 *   <li>Reveal: predicates registered through {@code EmiRegistry.removeRecipes(...)} live in
 *       {@code EmiRecipes.invalidators} and remove recipes during {@code bake()}. Dropping them
 *       before the bake makes those recipes reappear. Data-pack {@code emi:recipe_filters} are
 *       added inside {@code bake()}, so their field read is redirected to empty while revealing.</li>
 *   <li>JEHide: re-adds JEH's own hide predicate after the clear, so JEH's list takes precedence
 *       over reveal.</li>
 *   <li>Intent recording: after the bake, records recipe hiding (data-pack filters and captured
 *       plugin {@code removeRecipes} predicates).</li>
 * </ul>
 * Controlled by {@code [reveal] enabled} / {@code [jehide] enabled}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiRecipes", remap = false)
public class EmiRecipesBakeRevealMixin
{
    @Shadow
    public static List<Predicate<EmiRecipe>> invalidators;

    @Shadow
    private static List<EmiRecipe> recipes;

    private static final Predicate<EmiRecipe> JEH_HIDE = EmiRevealSupport::isHiddenRecipe;

    @Inject(method = "clear", at = @At("HEAD"), remap = false, require = 0)
    private static void jeh$clearCapturedRecipes(CallbackInfo ci)
    {
        EmiIntentRecorder.clearCapturedRecipes();
    }

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

    /** Reveal support for data-pack {@code emi:recipe_filters}. */
    @Redirect(
        method = "bake",
        at = @At(
            value = "FIELD",
            target = "Ldev/emi/emi/data/EmiData;recipeFilters:Ljava/util/List;",
            opcode = Opcodes.GETSTATIC
        ),
        remap = false,
        require = 0
    )
    private static List<?> jeh$revealRecipeFilters()
    {
        return JehConfig.revealEnabled() ? List.of() : EmiData.recipeFilters;
    }

    @Inject(method = "bake", at = @At("TAIL"), remap = false, require = 0)
    private static void jeh$scanHiddenRecipes(CallbackInfo ci)
    {
        EmiIntentRecorder.scanHiddenRecipes(recipes);
    }
}

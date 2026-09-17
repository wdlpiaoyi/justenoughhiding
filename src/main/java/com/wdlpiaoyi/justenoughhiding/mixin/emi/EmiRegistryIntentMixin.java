package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.integration.emi.EmiIntentRecorder;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Records plugin removals as intents. Stack removals are evaluated right away (EMI's reload fills
 * the raw stack list before plugins register); recipe removals are captured and evaluated at the
 * next recipe bake, when the recipes exist (see {@link EmiIntentRecorder}).
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiRegistryImpl", remap = false)
public class EmiRegistryIntentMixin
{
    @Inject(method = "removeEmiStacks", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onRemoveEmiStacks(Predicate<EmiStack> predicate, CallbackInfo ci)
    {
        EmiIntentRecorder.recordRemovedStacks(predicate);
    }

    @Inject(method = "removeRecipes", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onRemoveRecipes(Predicate<EmiRecipe> predicate, CallbackInfo ci)
    {
        EmiIntentRecorder.captureRemovedRecipes(predicate);
    }
}

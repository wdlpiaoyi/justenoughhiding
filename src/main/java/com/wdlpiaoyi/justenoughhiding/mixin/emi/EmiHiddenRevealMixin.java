package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.client.jehide.EmiHide;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import dev.emi.emi.api.stack.EmiIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reveal support for EMI: force {@code EmiHidden.isHidden}/{@code isDisabled} to {@code false}
 * (unless JEH hides the ingredient itself), so stacks hidden by EMI's edit mode, data or plugins
 * are shown again. Mirrors the JEI {@code RevealMixin}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.runtime.EmiHidden", remap = false)
public class EmiHiddenRevealMixin
{
    @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void jeh$revealHidden(EmiIngredient ingredient, CallbackInfoReturnable<Boolean> cir)
    {
        if (!JehConfig.revealEnabled() || EmiHide.isHidden(ingredient))
        {
            return;
        }
        cir.setReturnValue(Boolean.FALSE);
    }

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void jeh$revealDisabled(EmiIngredient ingredient, CallbackInfoReturnable<Boolean> cir)
    {
        if (!JehConfig.revealEnabled() || EmiHide.isHidden(ingredient))
        {
            return;
        }
        cir.setReturnValue(Boolean.FALSE);
    }
}

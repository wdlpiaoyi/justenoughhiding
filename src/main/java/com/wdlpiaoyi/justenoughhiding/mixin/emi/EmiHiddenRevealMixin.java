package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.client.viewer.emi.EmiRevealSupport;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.EmiConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reveal support for EMI: force {@code EmiHidden.isHidden}/{@code isDisabled} to {@code false}
 * (unless JEH hides the ingredient itself), so stacks hidden by EMI's data or plugins are shown
 * again. Mirrors the JEI {@code RevealMixin}.
 * <p>
 * While EMI's edit mode is on, EMI's own toggle reads {@code isHidden} to decide whether to hide
 * or show; forcing it here would invert that toggle, so reveal is skipped in edit mode.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.runtime.EmiHidden", remap = false)
public class EmiHiddenRevealMixin
{
    @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void jeh$revealHidden(EmiIngredient ingredient, CallbackInfoReturnable<Boolean> cir)
    {
        if (!JehConfig.revealEnabled() || EmiConfig.editMode || EmiRevealSupport.isHidden(ingredient))
        {
            return;
        }
        cir.setReturnValue(Boolean.FALSE);
    }

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void jeh$revealDisabled(EmiIngredient ingredient, CallbackInfoReturnable<Boolean> cir)
    {
        if (!JehConfig.revealEnabled() || EmiConfig.editMode || EmiRevealSupport.isHidden(ingredient))
        {
            return;
        }
        cir.setReturnValue(Boolean.FALSE);
    }
}

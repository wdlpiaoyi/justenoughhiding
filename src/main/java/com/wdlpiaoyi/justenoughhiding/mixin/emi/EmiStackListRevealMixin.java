package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.client.jehide.EmiHide;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reveal support for EMI: ingredients excluded from EMI by the
 * {@code c:hidden_from_recipe_viewers} tag (or any other tag check) are kept, unless JEH itself
 * hides them. Applied on every EMI (re)build, mirroring the JEI {@code RevealMixin}.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiStackList", remap = false)
public class EmiStackListRevealMixin
{
    @Inject(method = "isHiddenFromRecipeViewers", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void jeh$reveal(Object stack, CallbackInfoReturnable<Boolean> cir)
    {
        if (!JehConfig.revealEnabled() || EmiHide.isHidden(stack))
        {
            return;
        }
        cir.setReturnValue(Boolean.FALSE);
    }
}

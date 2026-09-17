package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reveal support for EMI together with the third-party {@code emi_accelerator}: that mod cancels
 * {@code EmiStackList.reload()} and restores a cached, already-baked stack list (which omits
 * anything that was hidden). While {@code [reveal] enabled} we force its cache load to fail, so
 * EMI always rebuilds its index from the registries and reveal actually takes effect.
 */
@Pseudo
@Mixin(targets = "org.chatterjay.emi_accelerator.util.EmiStackCache", remap = false)
public class EmiStackCacheMixin
{
    @Inject(method = "tryLoad", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void jeh$skipCache(CallbackInfoReturnable<Boolean> cir)
    {
        if (JehConfig.revealEnabled())
        {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}

package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.integration.emi.EmiIntentRecorder;
import dev.emi.emi.api.stack.EmiStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Records plugin stack removals as intents. EMI's reload fills the raw stack list before plugins
 * register, so the predicate can be evaluated immediately (see {@link EmiIntentRecorder}).
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
}

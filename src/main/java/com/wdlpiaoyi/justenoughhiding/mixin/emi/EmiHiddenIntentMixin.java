package com.wdlpiaoyi.justenoughhiding.mixin.emi;

import com.wdlpiaoyi.justenoughhiding.integration.emi.EmiIntentRecorder;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import dev.emi.emi.api.stack.EmiIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records EMI edit-mode/config visibility changes as intents: hiding records an
 * {@code EDIT_MODE_HIDDEN} intent, showing forgets it again (visible is the default).
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.runtime.EmiHidden", remap = false)
public class EmiHiddenIntentMixin
{
    @Inject(method = "setVisibility", at = @At("HEAD"), remap = false, require = 0)
    private static void jeh$onSetVisibility(EmiIngredient stack, boolean visible, boolean disabled, CallbackInfo ci)
    {
        if (visible)
        {
            EmiIntentRecorder.removeIngredient(stack, IntentSource.EMI_EDIT_MODE);
        }
        else
        {
            EmiIntentRecorder.recordIngredient(stack, IntentKind.EDIT_MODE_HIDDEN, IntentSource.EMI_EDIT_MODE);
        }
    }
}

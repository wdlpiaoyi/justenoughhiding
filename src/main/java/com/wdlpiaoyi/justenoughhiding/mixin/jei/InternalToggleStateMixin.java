package com.wdlpiaoyi.justenoughhiding.mixin.jei;

import com.wdlpiaoyi.justenoughhiding.client.jehide.JehEditMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures JEI's {@code ClientToggleState} from its factory method instead of its constructor.
 * <p>
 * JEI 15.74.0.230+ compiles {@code ClientToggleState.<init>} with slot 0 reused as a scratch slot
 * ({@code aload_0; dup; ...; new ArrayList; astore_0; putfield}), so a {@code @At("RETURN")}
 * injection there reloads {@code this} from a slot that now holds an {@code ArrayList} and the
 * class fails verification ({@code VerifyError: Bad type on operand stack}). The static factory
 * {@code mezz.jei.common.Internal#getClientToggleState()} has the same signature in every version
 * JEH supports (15.59 and 15.74), and a static handler never reloads {@code this}.
 */
@Pseudo
@Mixin(targets = "mezz.jei.common.Internal", remap = false)
public class InternalToggleStateMixin
{
    @Inject(method = "getClientToggleState", at = @At("RETURN"), remap = false, require = 0)
    private static void jeh$captureToggleState(CallbackInfoReturnable<Object> cir)
    {
        Object state = cir.getReturnValue();
        if (state != null)
        {
            JehEditMode.setToggleState(state);
        }
    }
}

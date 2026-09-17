package com.wdlpiaoyi.justenoughhiding.mixin.jei;

import com.wdlpiaoyi.justenoughhiding.client.jehide.JeiHide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures JEI's internal {@code IngredientFilter} when it is constructed, so JEHide can ask it to
 * recompute hidden state after hiding/showing. Capturing {@code this} (instead of matching the
 * constructor's arguments) avoids descriptor mismatches; reaching it this way also avoids deep
 * reflection on a private field, which the module system blocks on modern Java.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false)
public class IngredientFilterMixin
{
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void jeh$captureFilter(CallbackInfo ci)
    {
        JeiHide.captureIngredientFilter((Object) this);
    }
}

package com.wdlpiaoyi.justenoughhiding.mixin.jei;

import com.wdlpiaoyi.justenoughhiding.client.jehide.JeiHide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures JEI's internal {@code IngredientFilter} while its API wrapper is constructed, so JEHide
 * can ask it to recompute hidden state after hiding/showing. Reaching it this way avoids deep
 * reflection on a private field, which the module system blocks on modern Java (and which used to
 * leave JEH forcing an unrelated full resource reload).
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilterApi", remap = false)
public class IngredientFilterApiMixin
{
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void jeh$captureFilter(Object ingredientFilter, Object filterTextSource, CallbackInfo ci)
    {
        JeiHide.captureIngredientFilter(ingredientFilter);
    }
}

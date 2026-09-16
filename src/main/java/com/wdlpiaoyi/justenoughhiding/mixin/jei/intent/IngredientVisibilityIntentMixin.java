package com.wdlpiaoyi.justenoughhiding.mixin.jei.intent;

import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentRecorder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Pseudo
@Mixin(targets = "mezz.jei.library.ingredients.IngredientVisibility", remap = false)
public class IngredientVisibilityIntentMixin
{
    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Inject(method = "hideIngredients", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onHide(IIngredientType<?> type, Collection<?> ingredients, Collection<UidContext> contexts, CallbackInfo ci)
    {
        JeiIntentRecorder.recordIngredients(this.ingredientManager, type, ingredients, IntentKind.HIDDEN);
    }

    @Inject(method = "unhideIngredients", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onUnhide(IIngredientType<?> type, Collection<?> ingredients, Collection<UidContext> contexts, CallbackInfo ci)
    {
        JeiIntentRecorder.recordIngredients(this.ingredientManager, type, ingredients, IntentKind.SHOWN);
    }
}

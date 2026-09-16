package com.wdlpiaoyi.justenoughhiding.mixin.jei.intent;

import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentRecorder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Pseudo
@Mixin(targets = "mezz.jei.library.ingredients.IngredientManager", remap = false)
public class IngredientManagerIntentMixin
{
    @Inject(method = "removeIngredientsAtRuntime", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onRemove(IIngredientType<?> type, Collection<?> ingredients, CallbackInfo ci)
    {
        JeiIntentRecorder.recordIngredients((IIngredientManager) (Object) this, type, ingredients, IntentKind.REMOVED);
    }

    @Inject(method = "addIngredientsAtRuntime", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onAdd(IIngredientType<?> type, Collection<?> ingredients, CallbackInfo ci)
    {
        JeiIntentRecorder.recordIngredients((IIngredientManager) (Object) this, type, ingredients, IntentKind.ADDED);
    }
}

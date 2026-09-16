package com.wdlpiaoyi.justenoughhiding.mixin.jei.intent;

import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentRecorder;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.config.EditModeConfig", remap = false)
public class EditModeConfigIntentMixin
{
    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Inject(method = "hideIngredientUsingConfigFile", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onEditModeHide(ITypedIngredient<?> ingredient, IEditModeConfig.HideMode mode, CallbackInfo ci)
    {
        JeiIntentRecorder.recordTyped(this.ingredientManager, ingredient, IntentKind.EDIT_MODE_HIDDEN, IntentSource.JEI_EDIT_MODE);
    }

    @Inject(method = "showIngredientUsingConfigFile", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onEditModeShow(ITypedIngredient<?> ingredient, IEditModeConfig.HideMode mode, CallbackInfo ci)
    {
        // Returning to the default (visible) state is not an intent, so forget the hidden entry instead of recording a show.
        JeiIntentRecorder.removeTyped(this.ingredientManager, ingredient, IntentSource.JEI_EDIT_MODE);
    }
}

package com.wdlpiaoyi.justenoughhiding.mixin.jei;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.library.ingredients.IngredientVisibility", remap = false)
public class IngredientVisibilityMixin
{
    @Inject(
        method = "isIngredientVisible(Lmezz/jei/api/ingredients/ITypedIngredient;Lmezz/jei/api/ingredients/IIngredientHelper;Lmezz/jei/api/ingredients/subtypes/UidContext;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void jeh$forceVisible(ITypedIngredient<?> typedIngredient, IIngredientHelper<?> ingredientHelper, UidContext context, CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(Boolean.TRUE);
    }
}

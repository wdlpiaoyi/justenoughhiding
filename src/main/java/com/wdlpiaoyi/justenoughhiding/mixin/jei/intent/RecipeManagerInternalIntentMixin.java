package com.wdlpiaoyi.justenoughhiding.mixin.jei.intent;

import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentRecorder;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public class RecipeManagerInternalIntentMixin
{
    @Inject(method = "hideRecipes", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onHideRecipes(RecipeType<?> recipeType, Collection<?> recipes, CallbackInfo ci)
    {
        recordRecipes(recipeType, recipes, IntentKind.RECIPE_HIDDEN);
    }

    @Inject(method = "unhideRecipes", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onUnhideRecipes(RecipeType<?> recipeType, Collection<?> recipes, CallbackInfo ci)
    {
        recordRecipes(recipeType, recipes, IntentKind.RECIPE_SHOWN);
    }

    @Inject(method = "hideRecipeCategory", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onHideCategory(RecipeType<?> recipeType, CallbackInfo ci)
    {
        JeiIntentRecorder.recordRecipeCategory(uidOf(recipeType), IntentKind.RECIPE_CATEGORY_HIDDEN);
    }

    @Inject(method = "unhideRecipeCategory", at = @At("HEAD"), remap = false, require = 0)
    private void jeh$onUnhideCategory(RecipeType<?> recipeType, CallbackInfo ci)
    {
        JeiIntentRecorder.recordRecipeCategory(uidOf(recipeType), IntentKind.RECIPE_CATEGORY_SHOWN);
    }

    private static void recordRecipes(RecipeType<?> recipeType, Collection<?> recipes, IntentKind kind)
    {
        if (recipeType == null || recipes == null)
        {
            return;
        }
        ResourceLocation uid = uidOf(recipeType);
        for (Object recipe : recipes)
        {
            JeiIntentRecorder.recordRecipe(uid, recipe, kind);
        }
    }

    private static ResourceLocation uidOf(RecipeType<?> recipeType)
    {
        try
        {
            return recipeType == null ? null : recipeType.getUid();
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}

package com.wdlpiaoyi.justenoughhiding.mixin.jei.intent;

import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentRecorder;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Collection;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public class RecipeManagerInternalIntentMixin
{
    private static Method recipeCategoryMethod;
    private static boolean recipeCategoryResolved;

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

    private void recordRecipes(RecipeType<?> recipeType, Collection<?> recipes, IntentKind kind)
    {
        if (recipeType == null || recipes == null)
        {
            return;
        }
        ResourceLocation uid = uidOf(recipeType);
        if (uid == null)
        {
            return;
        }
        for (Object recipe : recipes)
        {
            if (recipe != null)
            {
                JeiIntentRecorder.recordRecipe(uid, resolveRecipeId(recipeType, recipe), kind);
            }
        }
    }

    /**
     * Resolves a stable id for a recipe object: the vanilla {@link Recipe#getId()} when possible,
     * otherwise the owning category's {@code getRegistryName(recipe)} (JEI internals are reached
     * reflectively because RecipeManagerInternal is not on the compile classpath), and finally
     * the object's toString as a last resort.
     */
    private String resolveRecipeId(RecipeType<?> recipeType, Object recipe)
    {
        if (recipe instanceof Recipe<?> vanilla)
        {
            try
            {
                ResourceLocation id = vanilla.getId();
                if (id != null)
                {
                    return id.toString();
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        try
        {
            Object category = recipeCategory(recipeType);
            if (category instanceof IRecipeCategory<?> typedCategory)
            {
                ResourceLocation id = ((IRecipeCategory) typedCategory).getRegistryName(recipe);
                if (id != null)
                {
                    return id.toString();
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return String.valueOf(recipe);
    }

    private Object recipeCategory(RecipeType<?> recipeType)
    {
        Method method = resolveRecipeCategoryMethod();
        if (method == null)
        {
            return null;
        }
        try
        {
            return method.invoke(this, recipeType);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private Method resolveRecipeCategoryMethod()
    {
        Method cached = recipeCategoryMethod;
        if (cached != null)
        {
            return cached;
        }
        if (recipeCategoryResolved)
        {
            return null;
        }
        recipeCategoryResolved = true;
        try
        {
            Method method = getClass().getMethod("getRecipeCategory", RecipeType.class);
            recipeCategoryMethod = method;
            return method;
        }
        catch (Throwable t)
        {
            return null;
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

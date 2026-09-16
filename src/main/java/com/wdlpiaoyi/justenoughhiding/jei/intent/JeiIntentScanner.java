package com.wdlpiaoyi.justenoughhiding.jei.intent;

import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class JeiIntentScanner
{
    private JeiIntentScanner()
    {
    }

    public static void scan(IJeiRuntime runtime)
    {
        if (!JehConfig.intentRecordingEnabled() || runtime == null)
        {
            return;
        }

        IIngredientManager manager = runtime.getIngredientManager();
        IEditModeConfig editModeConfig = runtime.getEditModeConfig();
        IIngredientHelper<ItemStack> helper = manager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        String typeUid = VanillaTypes.ITEM_STACK.getUid();

        for (Item item : ForgeRegistries.ITEMS)
        {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty())
            {
                continue;
            }

            IngredientKey key;
            try
            {
                String uid = helper.getUniqueId(stack, UidContext.Ingredient);
                if (uid == null)
                {
                    continue;
                }
                key = IngredientKey.of(typeUid, uid);
            }
            catch (Throwable t)
            {
                continue;
            }

            ITypedIngredient<ItemStack> typed = null;
            try
            {
                typed = manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack).orElse(null);
            }
            catch (Throwable ignored)
            {
            }

            if (typed != null)
            {
                try
                {
                    if (helper.isHiddenFromRecipeViewersByTags(typed))
                    {
                        IntentRegistry.record(IntentTarget.of(key), IntentKind.TAG_HIDDEN, IntentSource.TAG);
                    }
                }
                catch (Throwable ignored)
                {
                }

                if (editModeConfig != null)
                {
                    try
                    {
                        if (editModeConfig.isIngredientHiddenUsingConfigFile(typed))
                        {
                            IntentRegistry.record(IntentTarget.of(key), IntentKind.EDIT_MODE_HIDDEN, IntentSource.JEI_EDIT_MODE);
                        }
                    }
                    catch (Throwable ignored)
                    {
                    }
                }
            }

            try
            {
                if (!helper.isIngredientOnServer(stack))
                {
                    IntentRegistry.record(IntentTarget.of(key), IntentKind.SERVER_MISSING, IntentSource.SERVER);
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }
}

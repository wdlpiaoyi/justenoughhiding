package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.jei.JehJeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemIcons
{
    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    private ItemIcons()
    {
    }

    public static ItemStack resolve(IntentTarget target)
    {
        if (!(target instanceof IntentTarget.Ingredient ingredient))
        {
            return ItemStack.EMPTY;
        }
        if (!VanillaTypes.ITEM_STACK.getUid().equals(ingredient.key().typeUid()))
        {
            return ItemStack.EMPTY;
        }

        String uid = ingredient.key().uid();
        ItemStack cached = CACHE.get(uid);
        if (cached != null)
        {
            return cached;
        }

        ItemStack resolved = lookup(uid);
        if (!resolved.isEmpty())
        {
            CACHE.put(uid, resolved);
        }
        return resolved;
    }

    private static ItemStack lookup(String uid)
    {
        IJeiRuntime runtime = JehJeiPlugin.getRuntime();
        if (runtime != null)
        {
            try
            {
                Optional<ITypedIngredient<ItemStack>> typed = runtime.getIngredientManager()
                    .getTypedIngredientByUid(VanillaTypes.ITEM_STACK, uid);
                if (typed.isPresent() && !typed.get().getIngredient().isEmpty())
                {
                    return typed.get().getIngredient();
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        int brace = uid.indexOf('{');
        String registryName = brace >= 0 ? uid.substring(0, brace) : uid;
        ResourceLocation id = ResourceLocation.tryParse(registryName);
        if (id != null)
        {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null)
            {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }
}

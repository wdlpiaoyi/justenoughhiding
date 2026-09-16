package com.wdlpiaoyi.justenoughhiding.client.viewer.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import com.wdlpiaoyi.justenoughhiding.client.viewer.DefaultColumns;
import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.jei.JeiReveal;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentScanner;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JeiAdapter implements ViewerAdapter
{
    public static final String ID = "jei";

    private final JeiReveal reveal = new JeiReveal();
    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();
    private volatile IJeiRuntime runtime;

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public boolean available()
    {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(ID);
    }

    @Override
    public List<Column<Intent>> columns()
    {
        return DefaultColumns.withIcon(intent -> icon(intent.target()));
    }

    @Override
    public IconRenderer icon(IntentTarget target)
    {
        ItemStack stack = resolveItem(target);
        return stack.isEmpty() ? IconRenderer.EMPTY : new ItemIconRenderer(stack);
    }

    @Override
    public boolean isBookmarkKey(int keyCode, int scanCode)
    {
        IJeiRuntime current = this.runtime;
        if (current == null)
        {
            return false;
        }
        try
        {
            IJeiKeyMapping bookmark = current.getKeyMappings().getBookmark();
            return bookmark != null && !bookmark.isUnbound()
                && bookmark.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Override
    public boolean bookmark(IntentTarget target)
    {
        IJeiRuntime current = this.runtime;
        if (current == null)
        {
            return false;
        }
        ItemStack stack = resolveItem(target);
        if (stack.isEmpty())
        {
            return false;
        }
        try
        {
            current.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
                .ifPresent(typed -> current.getBookmarkManager().add(typed));
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Override
    public void onRuntimeAvailable(Object runtime)
    {
        if (!(runtime instanceof IJeiRuntime jeiRuntime))
        {
            return;
        }
        this.runtime = jeiRuntime;
        reveal.activate(jeiRuntime);
        JeiIntentScanner.scan(jeiRuntime);
        JustEnoughHiding.LOGGER.info("[JEH] intents recorded for this runtime: {} entries", IntentRegistry.size());
    }

    @Override
    public void onRuntimeUnavailable()
    {
        this.runtime = null;
        reveal.deactivate();
        IntentRegistry.clear();
    }

    private ItemStack resolveItem(IntentTarget target)
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
        ItemStack cached = iconCache.get(uid);
        if (cached != null)
        {
            return cached;
        }

        ItemStack resolved = lookup(uid);
        if (!resolved.isEmpty())
        {
            iconCache.put(uid, resolved);
        }
        return resolved;
    }

    private ItemStack lookup(String uid)
    {
        IJeiRuntime current = this.runtime;
        if (current != null)
        {
            try
            {
                Optional<ITypedIngredient<ItemStack>> typed = current.getIngredientManager()
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

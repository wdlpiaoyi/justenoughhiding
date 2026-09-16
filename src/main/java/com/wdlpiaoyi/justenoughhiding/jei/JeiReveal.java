package com.wdlpiaoyi.justenoughhiding.jei;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class JeiReveal
{
    private final Queue<ItemStack> removedStacks = new ConcurrentLinkedQueue<>();
    private IJeiRuntime runtime;
    private boolean ticking;

    public void activate(IJeiRuntime jeiRuntime)
    {
        this.runtime = jeiRuntime;
        jeiRuntime.getIngredientManager().registerIngredientListener(new RemovalCollector());
        beginTicking();
        revealMissing(jeiRuntime);
    }

    public void deactivate()
    {
        this.runtime = null;
        this.removedStacks.clear();
        stopTicking();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || this.removedStacks.isEmpty())
        {
            return;
        }

        IJeiRuntime current = this.runtime;
        if (current == null)
        {
            this.removedStacks.clear();
            return;
        }

        List<ItemStack> batch = new ArrayList<>();
        for (ItemStack stack = this.removedStacks.poll(); stack != null; stack = this.removedStacks.poll())
        {
            batch.add(stack);
        }

        current.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, batch);
        JustEnoughHiding.LOGGER.info("[JEH] reveal: restored {} item stacks that were removed from JEI", batch.size());
    }

    private void beginTicking()
    {
        if (!this.ticking)
        {
            this.ticking = true;
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    private void stopTicking()
    {
        if (this.ticking)
        {
            this.ticking = false;
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    private final class RemovalCollector implements IIngredientManager.IIngredientListener
    {
        @Override
        public <V> void onIngredientsAdded(IIngredientHelper<V> ingredientHelper, Collection<ITypedIngredient<V>> ingredients)
        {
        }

        @Override
        public <V> void onIngredientsRemoved(IIngredientHelper<V> ingredientHelper, Collection<ITypedIngredient<V>> ingredients)
        {
            for (ITypedIngredient<V> ingredient : ingredients)
            {
                ItemStack stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).orElse(null);
                if (stack != null)
                {
                    JeiReveal.this.removedStacks.add(stack);
                }
            }
        }
    }

    private static void revealMissing(IJeiRuntime jeiRuntime)
    {
        IIngredientManager manager = jeiRuntime.getIngredientManager();
        IIngredientHelper<ItemStack> helper = manager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        String typeUid = VanillaTypes.ITEM_STACK.getUid();

        Set<Item> alreadyPresent = manager.getAllItemStacks().stream()
            .map(ItemStack::getItem)
            .collect(Collectors.toSet());

        List<ItemStack> absent = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS)
        {
            if (alreadyPresent.contains(item))
            {
                continue;
            }

            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty())
            {
                continue;
            }
            absent.add(stack);

            boolean onServer;
            try
            {
                onServer = helper.isIngredientOnServer(stack);
            }
            catch (Throwable t)
            {
                onServer = true;
            }
            if (!onServer)
            {
                // The scanner records these as SERVER_MISSING.
                continue;
            }

            try
            {
                String uid = helper.getUniqueId(stack, UidContext.Ingredient);
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                IntentSource source = IntentSource.mod(itemId == null ? "unknown" : itemId.getNamespace());
                IntentRegistry.record(IntentTarget.of(IngredientKey.of(typeUid, uid)), IntentKind.ABSENT_FROM_JEI, source);
            }
            catch (Throwable ignored)
            {
            }
        }

        if (!absent.isEmpty())
        {
            manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, absent);
            JustEnoughHiding.LOGGER.info("[JEH] reveal: added {} item stacks that JEI was missing", absent.size());
        }
    }
}

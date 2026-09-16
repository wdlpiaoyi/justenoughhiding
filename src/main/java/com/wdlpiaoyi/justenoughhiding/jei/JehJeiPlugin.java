package com.wdlpiaoyi.justenoughhiding.jei;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IExtraIngredientRegistration;
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

@JeiPlugin
public final class JehJeiPlugin implements IModPlugin
{
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(JustEnoughHiding.MODID, "unhide");

    private final Queue<ItemStack> removedStacks = new ConcurrentLinkedQueue<>();
    private IJeiRuntime runtime;
    private boolean ticking;

    @Override
    public ResourceLocation getPluginUid()
    {
        return PLUGIN_UID;
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration)
    {
        List<ItemStack> items = everyRegisteredItem();
        registration.addExtraItemStacks(items);
        JustEnoughHiding.LOGGER.info("[JEH] offered {} item stacks to JEI", items.size());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime)
    {
        this.runtime = jeiRuntime;
        jeiRuntime.getIngredientManager().registerIngredientListener(new RemovalCollector());
        beginTicking();
        fillInMissing(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable()
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
        JustEnoughHiding.LOGGER.info("[JEH] restored {} item stacks that were removed from JEI", batch.size());
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
                ItemStack stack = ingredient.getCastIngredient(VanillaTypes.ITEM_STACK);
                if (stack != null)
                {
                    JehJeiPlugin.this.removedStacks.add(stack);
                }
            }
        }
    }

    private static void fillInMissing(IJeiRuntime jeiRuntime)
    {
        Set<Item> alreadyPresent = jeiRuntime.getIngredientManager().getAllItemStacks().stream()
            .map(ItemStack::getItem)
            .collect(Collectors.toSet());

        List<ItemStack> absent = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS)
        {
            if (!alreadyPresent.contains(item))
            {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty())
                {
                    absent.add(stack);
                }
            }
        }

        if (!absent.isEmpty())
        {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, absent);
            JustEnoughHiding.LOGGER.info("[JEH] added {} item stacks that JEI was missing", absent.size());
        }
    }

    private static List<ItemStack> everyRegisteredItem()
    {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS)
        {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty())
            {
                stacks.add(stack);
            }
        }
        return stacks;
    }
}

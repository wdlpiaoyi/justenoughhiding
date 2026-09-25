package com.wdlpiaoyi.justenoughhiding.jei;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.viewer.jei.JeiNativeOptions;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentKind;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentRecorder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class JeiReveal
{
    private record Removal(IIngredientType<?> type, Object ingredient)
    {
    }

    private final Queue<Removal> removedIngredients = new ConcurrentLinkedQueue<>();
    private IJeiRuntime runtime;
    private boolean ticking;

    public void activate(IJeiRuntime jeiRuntime)
    {
        this.runtime = jeiRuntime;
        jeiRuntime.getIngredientManager().registerIngredientListener(new RemovalCollector());
        beginTicking();
        if (!JehConfig.revealEnabled() && !JehConfig.intentRecordingEnabled())
        {
            return;
        }

        // Scan and record independently of whether we re-add: the recorded ABSENT_FROM_JEI intents
        // must not depend on the reveal / native-option decision.
        List<ItemStack> absent = scanMissing(jeiRuntime.getIngredientManager());
        if (absent.isEmpty())
        {
            return;
        }

        // JEI's own [cheating] showHiddenIngredients already adds the registry items that are absent
        // from the creative inventory, so re-adding them here would be duplicated work.
        if (JehConfig.revealEnabled() && !JeiNativeOptions.showHiddenIngredients())
        {
            JeiIntentRecorder.runSuppressed(() ->
                jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, absent));
            JustEnoughHiding.LOGGER.info("[JEH] reveal: added {} item stacks that JEI was missing", absent.size());
        }
    }

    public void deactivate()
    {
        this.runtime = null;
        this.removedIngredients.clear();
        stopTicking();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || this.removedIngredients.isEmpty())
        {
            return;
        }

        IJeiRuntime current = this.runtime;
        if (current == null || !JehConfig.revealEnabled())
        {
            this.removedIngredients.clear();
            return;
        }

        Map<IIngredientType<?>, List<Object>> byType = new LinkedHashMap<>();
        for (Removal removal = this.removedIngredients.poll(); removal != null; removal = this.removedIngredients.poll())
        {
            byType.computeIfAbsent(removal.type(), ignored -> new ArrayList<>()).add(removal.ingredient());
        }

        IIngredientManager manager = current.getIngredientManager();
        int restored = 0;
        for (Map.Entry<IIngredientType<?>, List<Object>> entry : byType.entrySet())
        {
            try
            {
                JeiIntentRecorder.runSuppressed(() -> addRaw(manager, entry.getKey(), entry.getValue()));
                restored += entry.getValue().size();
            }
            catch (Throwable t)
            {
                JustEnoughHiding.LOGGER.warn("[JEH] reveal: failed to restore ingredients of type {}",
                    entry.getKey(), t);
            }
        }
        JustEnoughHiding.LOGGER.info("[JEH] reveal: restored {} ingredients that were removed from JEI", restored);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addRaw(IIngredientManager manager, IIngredientType<?> type, List<Object> ingredients)
    {
        manager.addIngredientsAtRuntime((IIngredientType) type, (Collection) ingredients);
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
                try
                {
                    IIngredientType<V> type = ingredient.getType();
                    V value = ingredient.getIngredient();
                    if (type != null && value != null)
                    {
                        JeiReveal.this.removedIngredients.add(new Removal(type, value));
                    }
                }
                catch (Throwable ignored)
                {
                }
            }
        }
    }

    /**
     * Collects the item stacks that are missing from JEI's list, recording {@code ABSENT_FROM_JEI}
     * for those that exist on the server. Always runs (subject to {@code intentRecording}) so the
     * recorded intents are independent of whether reveal re-adds them; the caller decides the add.
     */
    private static List<ItemStack> scanMissing(IIngredientManager manager)
    {
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

            if (!JehConfig.intentRecordingEnabled())
            {
                continue;
            }

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
        return absent;
    }
}

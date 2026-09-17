package com.wdlpiaoyi.justenoughhiding.intent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class IntentRegistry implements IntentQuery
{
    private static final IntentRegistry INSTANCE = new IntentRegistry();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private record Key(IntentTarget target, String sourceId)
    {
    }

    private static final class Entry
    {
        private final IntentSource source;
        private volatile IntentKind kind;
        private volatile long sequence;
        private volatile int count;

        private Entry(IntentSource source, IntentKind kind, long sequence, int count)
        {
            this.source = source;
            this.kind = kind;
            this.sequence = sequence;
            this.count = count;
        }

        private Intent snapshot(IntentTarget target)
        {
            return new Intent(target, this.kind, this.source, this.sequence, this.count);
        }
    }

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final List<Consumer<Intent>> listeners = new CopyOnWriteArrayList<>();

    private IntentRegistry()
    {
    }

    public static IntentQuery query()
    {
        return INSTANCE;
    }

    public static void record(IntentTarget target, IntentKind kind, IntentSource source)
    {
        if (target == null || kind == null || source == null)
        {
            return;
        }
        INSTANCE.put(target, kind, source);
    }

    public static void clear()
    {
        INSTANCE.entries.clear();
    }

    /** True when the exact target/kind/source combination is already recorded. */
    public static boolean contains(IntentTarget target, IntentKind kind, String sourceId)
    {
        if (target == null || kind == null)
        {
            return false;
        }
        Key key = new Key(target, sourceId == null ? "" : sourceId);
        Entry entry = INSTANCE.entries.get(key);
        return entry != null && entry.kind == kind;
    }

    public static void dropBySource(String sourceId)
    {
        if (sourceId == null)
        {
            return;
        }
        INSTANCE.entries.keySet().removeIf(key -> sourceId.equals(key.sourceId()));
    }

    /**
     * Keeps only intents of the given source type whose source id is in {@code activeSourceIds},
     * dropping the rest (e.g. resource packs that are no longer selected).
     */
    public static void retainSources(IntentSource.Type type, Collection<String> activeSourceIds)
    {
        if (type == null)
        {
            return;
        }
        Set<String> active = activeSourceIds == null ? Set.of() : new HashSet<>(activeSourceIds);
        INSTANCE.entries.entrySet().removeIf(entry ->
            entry.getValue().source.type() == type && !active.contains(entry.getValue().source.id()));
    }

    /**
     * Drops resource-pack intents of the given kinds that are no longer produced by their pack,
     * which removes stale entries when a pack's content changes (its id stays the same).
     * {@code producedBySource} maps a pack id to the target keys ("kind|describe") it now hides.
     */
    public static void retainResourcePackTargets(Set<IntentKind> kinds, Map<String, Set<String>> producedBySource)
    {
        if (kinds == null || kinds.isEmpty())
        {
            return;
        }
        INSTANCE.entries.entrySet().removeIf(entry ->
        {
            Entry value = entry.getValue();
            if (value.source.type() != IntentSource.Type.RESOURCE_PACK || !kinds.contains(value.kind))
            {
                return false;
            }
            Set<String> produced = producedBySource == null ? null : producedBySource.get(value.source.id());
            IntentTarget target = entry.getKey().target();
            String key = target.kind() + "|" + target.describe();
            return produced == null || !produced.contains(key);
        });
    }

    public static void remove(IntentTarget target, String sourceId)
    {
        if (target == null || sourceId == null)
        {
            return;
        }
        INSTANCE.entries.remove(new Key(target, sourceId));
    }

    public static int size()
    {
        return INSTANCE.entries.size();
    }

    private void put(IntentTarget target, IntentKind kind, IntentSource source)
    {
        Key key = new Key(target, source.id());
        long sequence = SEQUENCE.incrementAndGet();
        Entry entry = entries.compute(key, (ignored, existing) ->
        {
            if (existing == null)
            {
                return new Entry(source, kind, sequence, 1);
            }
            if (existing.kind == kind)
            {
                existing.count++;
                existing.sequence = sequence;
            }
            else
            {
                existing.kind = kind;
                existing.sequence = sequence;
                existing.count = 1;
            }
            return existing;
        });

        Intent snapshot = entry.snapshot(target);
        for (Consumer<Intent> listener : listeners)
        {
            try
            {
                listener.accept(snapshot);
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    @Override
    public Collection<Intent> all()
    {
        List<Intent> result = new ArrayList<>(entries.size());
        entries.forEach((key, entry) -> result.add(entry.snapshot(key.target())));
        return Collections.unmodifiableList(result);
    }

    @Override
    public Collection<Intent> forIngredient(IngredientKey ingredientKey)
    {
        List<Intent> result = new ArrayList<>();
        entries.forEach((key, entry) ->
        {
            if (key.target() instanceof IntentTarget.Ingredient ingredient && ingredient.key().equals(ingredientKey))
            {
                result.add(entry.snapshot(key.target()));
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public Collection<Intent> forItem(Item item)
    {
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(item);
        if (registryName == null)
        {
            return List.of();
        }
        String base = registryName.toString();
        List<Intent> result = new ArrayList<>();
        entries.forEach((key, entry) ->
        {
            if (key.target() instanceof IntentTarget.Ingredient ingredient)
            {
                String uid = ingredient.key().uid();
                if (uid.equals(base) || uid.startsWith(base + "{"))
                {
                    result.add(entry.snapshot(key.target()));
                }
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public Collection<Intent> forRecipe(ResourceLocation recipeType, String recipeId)
    {
        List<Intent> result = new ArrayList<>();
        entries.forEach((key, entry) ->
        {
            if (key.target() instanceof IntentTarget.Recipe recipe
                && recipe.recipeType().equals(recipeType)
                && recipe.recipeId().equals(recipeId))
            {
                result.add(entry.snapshot(key.target()));
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public Collection<Intent> bySource(String sourceId)
    {
        List<Intent> result = new ArrayList<>();
        entries.forEach((key, entry) ->
        {
            if (key.sourceId().equals(sourceId))
            {
                result.add(entry.snapshot(key.target()));
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public Collection<Intent> byKind(IntentKind kind)
    {
        List<Intent> result = new ArrayList<>();
        entries.forEach((key, entry) ->
        {
            if (entry.kind == kind)
            {
                result.add(entry.snapshot(key.target()));
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public Set<IngredientKey> currentlyHidden()
    {
        Set<IngredientKey> result = new LinkedHashSet<>();
        entries.forEach((key, entry) ->
        {
            if (entry.kind.isHide() && key.target() instanceof IntentTarget.Ingredient ingredient)
            {
                result.add(ingredient.key());
            }
        });
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Map<IngredientKey, Set<String>> hiddenSources()
    {
        Map<IngredientKey, Set<String>> result = new LinkedHashMap<>();
        entries.forEach((key, entry) ->
        {
            if (entry.kind.isHide() && key.target() instanceof IntentTarget.Ingredient ingredient)
            {
                result.computeIfAbsent(ingredient.key(), ignored -> new LinkedHashSet<>()).add(key.sourceId());
            }
        });
        return Collections.unmodifiableMap(result);
    }

    @Override
    public void addListener(Consumer<Intent> listener)
    {
        if (listener != null)
        {
            listeners.add(listener);
        }
    }
}

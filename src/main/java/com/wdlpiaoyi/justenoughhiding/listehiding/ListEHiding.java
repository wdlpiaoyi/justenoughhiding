package com.wdlpiaoyi.justenoughhiding.listehiding;

import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The locally persisted list backing the {@code /jeh list} GUI. */
public final class ListEHiding
{
    private static final ListEHiding INSTANCE = new ListEHiding();

    private final List<ListEHidingEntry> entries = new ArrayList<>();
    private boolean loaded;
    private boolean dirty;

    private ListEHiding()
    {
    }

    public static ListEHiding get()
    {
        return INSTANCE;
    }

    public static ListEHidingEntry blankEntry()
    {
        return new ListEHidingEntry(new IntentTarget.Unset(), true, "", 0);
    }

    public List<ListEHidingEntry> entries()
    {
        ensureLoaded();
        return Collections.unmodifiableList(entries);
    }

    public int indexOf(ListEHidingEntry entry)
    {
        ensureLoaded();
        for (int i = 0; i < entries.size(); i++)
        {
            if (entries.get(i) == entry)
            {
                return i;
            }
        }
        return entries.indexOf(entry);
    }

    public void add(ListEHidingEntry entry)
    {
        ensureLoaded();
        entries.add(entry);
        dirty = true;
    }

    public void set(int index, ListEHidingEntry entry)
    {
        ensureLoaded();
        if (index >= 0 && index < entries.size())
        {
            entries.set(index, entry);
            dirty = true;
        }
    }

    public void remove(int index)
    {
        ensureLoaded();
        if (index >= 0 && index < entries.size())
        {
            entries.remove(index);
            dirty = true;
        }
    }

    public void reload()
    {
        this.loaded = false;
        this.dirty = false;
        this.entries.clear();
        ensureLoaded();
    }

    public void saveIfDirty()
    {
        if (dirty)
        {
            ListEHidingStore.save(entries);
            dirty = false;
        }
    }

    private void ensureLoaded()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;
        entries.addAll(ListEHidingStore.load());
    }
}

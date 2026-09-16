package com.wdlpiaoyi.justenoughhiding.listehiding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The locally persisted list backing the {@code /jeh list} GUI. */
public final class ListEHiding
{
    private static final ListEHiding INSTANCE = new ListEHiding();

    private final List<ListEHidingEntry> entries = new ArrayList<>();
    private boolean loaded;

    private ListEHiding()
    {
    }

    public static ListEHiding get()
    {
        return INSTANCE;
    }

    public List<ListEHidingEntry> entries()
    {
        ensureLoaded();
        return Collections.unmodifiableList(entries);
    }

    public void reload()
    {
        this.loaded = false;
        this.entries.clear();
        ensureLoaded();
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

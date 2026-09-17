package com.wdlpiaoyi.justenoughhiding.client.jehide;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Viewer-agnostic hook fired after every client resource reload. Lets viewer-specific caches
 * (e.g. EMI's data-pack hide scan) invalidate themselves without referencing that viewer here.
 */
public final class JehReloadHooks
{
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private JehReloadHooks()
    {
    }

    public static void add(Runnable listener)
    {
        if (listener != null && !LISTENERS.contains(listener))
        {
            LISTENERS.add(listener);
        }
    }

    public static void onReload()
    {
        for (Runnable listener : LISTENERS)
        {
            try
            {
                listener.run();
            }
            catch (Throwable ignored)
            {
            }
        }
    }
}

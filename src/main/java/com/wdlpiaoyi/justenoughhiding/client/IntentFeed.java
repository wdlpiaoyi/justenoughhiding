package com.wdlpiaoyi.justenoughhiding.client;

import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts intent changes since the last GUI merge, so {@link com.wdlpiaoyi.justenoughhiding.client.gui.IntentScreen}
 * can show a "+N new" badge without auto-refreshing. Listener callbacks may run on any thread, so this
 * only does an atomic increment; the screen reads the value on the render thread.
 */
public final class IntentFeed
{
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static boolean registered;

    private IntentFeed()
    {
    }

    public static synchronized void ensureRegistered()
    {
        if (registered)
        {
            return;
        }
        registered = true;
        IntentRegistry.query().addListener(intent -> PENDING.incrementAndGet());
    }

    public static int pending()
    {
        return PENDING.get();
    }

    public static void clear()
    {
        PENDING.set(0);
    }
}

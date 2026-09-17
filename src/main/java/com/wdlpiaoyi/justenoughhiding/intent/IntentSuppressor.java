package com.wdlpiaoyi.justenoughhiding.intent;

/**
 * Shared recording suppression for the viewer intent recorders (JEI/EMI).
 * <p>
 * {@link #runSuppressed} is a thread-local guard used while JEH itself mutates a viewer, so its
 * own actions are not recorded. {@link #holdOnce}/{@link #release} is a process-wide guard used
 * when JEH triggers a viewer reload: parts of that reload (e.g. EMI's register step) may run on a
 * worker thread, where a thread-local would not apply.
 */
public final class IntentSuppressor
{
    private static final ThreadLocal<Integer> SUPPRESS = ThreadLocal.withInitial(() -> 0);
    private static final long HOLD_MS = 15_000L;
    private static volatile long holdUntil;

    private IntentSuppressor()
    {
    }

    public static void runSuppressed(Runnable action)
    {
        SUPPRESS.set(SUPPRESS.get() + 1);
        try
        {
            action.run();
        }
        finally
        {
            int depth = SUPPRESS.get() - 1;
            if (depth <= 0)
            {
                SUPPRESS.remove();
            }
            else
            {
                SUPPRESS.set(depth);
            }
        }
    }

    /**
     * Suppress recording until {@link #release()} (or a safety timeout), used around a
     * JEH-triggered viewer reload whose register step may run on a worker thread.
     */
    public static void holdOnce()
    {
        holdUntil = System.currentTimeMillis() + HOLD_MS;
    }

    public static void release()
    {
        holdUntil = 0L;
    }

    public static boolean suppressed()
    {
        return System.currentTimeMillis() < holdUntil || SUPPRESS.get() > 0;
    }
}

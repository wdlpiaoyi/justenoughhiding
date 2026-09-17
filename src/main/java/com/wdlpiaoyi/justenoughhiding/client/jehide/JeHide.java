package com.wdlpiaoyi.justenoughhiding.client.jehide;

import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;

/**
 * Viewer-agnostic entry point for applying the hide list. Delegates to the active
 * {@link com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter}, so no JEI/EMI class is
 * touched when that recipe viewer is not installed.
 */
public final class JeHide
{
    private JeHide()
    {
    }

    /** Apply (or re-apply) the current hide rules to the active viewer. */
    public static void reapply()
    {
        Adapters.active().reapplyHides();
    }

    /** Called every client tick; lets the active viewer debounce re-application. */
    public static void tick()
    {
        Adapters.active().tickHides();
    }
}

package com.wdlpiaoyi.justenoughhiding.client.jehide;

import java.lang.reflect.Method;

/**
 * Reads JEI's client edit-mode toggle (mezz.jei.common.config.IClientToggleState) without a
 * compile-time dependency: the mixin hands us the {@code ClientToggleState} instance and we
 * query {@code isEditModeEnabled()} reflectively.
 */
public final class JehEditMode
{
    private static volatile Object toggleState;
    private static volatile Method isEditModeMethod;

    private JehEditMode()
    {
    }

    public static void setToggleState(Object state)
    {
        toggleState = state;
        Method method = null;
        if (state != null)
        {
            try
            {
                method = state.getClass().getMethod("isEditModeEnabled");
            }
            catch (Throwable ignored)
            {
            }
        }
        isEditModeMethod = method;
    }

    public static boolean isEditModeEnabled()
    {
        Object state = toggleState;
        Method method = isEditModeMethod;
        if (state == null || method == null)
        {
            return false;
        }
        try
        {
            Object result = method.invoke(state);
            return result instanceof Boolean value && value;
        }
        catch (Throwable t)
        {
            return false;
        }
    }
}

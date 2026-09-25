package com.wdlpiaoyi.justenoughhiding.client.viewer.jei;

/**
 * Reads JEI's effective client configuration, so JEH can avoid duplicating what JEI already does.
 * <p>
 * JEI's forge jar is runtime-only (its internal classes are not on the compile classpath), and the
 * values we need are exposed through public API of JEI's internal code
 * ({@code Internal.getClientConfigs()} / {@code IClientConfig.getShowHiddenIngredients()}). Only
 * public methods are reached, so the module system does not block this (unlike private-field
 * reflection).
 */
public final class JeiNativeOptions
{
    private JeiNativeOptions()
    {
    }

    /**
     * True when JEI's {@code [cheating] showHiddenIngredients} is enabled, i.e. JEI already adds the
     * items that are absent from the creative inventory (its item + block registry scan) to its own
     * ingredient list. In that case JEH must not re-add them.
     */
    public static boolean showHiddenIngredients()
    {
        try
        {
            Class<?> internal = Class.forName("mezz.jei.common.Internal");
            Object configs = internal.getMethod("getClientConfigs").invoke(null);
            if (configs == null)
            {
                return false;
            }
            Object config = configs.getClass().getMethod("getClientConfig").invoke(configs);
            if (config == null)
            {
                return false;
            }
            Object value = config.getClass().getMethod("getShowHiddenIngredients").invoke(config);
            return value instanceof Boolean bool && bool;
        }
        catch (Throwable t)
        {
            return false;
        }
    }
}

package com.wdlpiaoyi.justenoughhiding.viewer;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;

import java.util.List;

public final class RecipeViewerBackends
{
    public static final RecipeViewerBackend JEI = new JeiBackend();
    public static final RecipeViewerBackend EMI = new EmiBackend();

    private static final List<RecipeViewerBackend> ALL = List.of(JEI, EMI);

    private RecipeViewerBackends()
    {
    }

    public static List<RecipeViewerBackend> all()
    {
        return ALL;
    }

    public static void logStatus()
    {
        for (RecipeViewerBackend backend : ALL)
        {
            JustEnoughHiding.LOGGER.info(
                "[JEH] viewer backend '{}': available={}, implemented={}",
                backend.id(),
                backend.isAvailable(),
                backend.isImplemented()
            );
        }
    }
}

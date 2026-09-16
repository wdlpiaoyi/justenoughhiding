package com.wdlpiaoyi.justenoughhiding.viewer;

import net.minecraftforge.fml.ModList;

public final class JeiBackend implements RecipeViewerBackend
{
    public static final String ID = "jei";

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public boolean isAvailable()
    {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(ID);
    }

    @Override
    public boolean isImplemented()
    {
        return true;
    }
}

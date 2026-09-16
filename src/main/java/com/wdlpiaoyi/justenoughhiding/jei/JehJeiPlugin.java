package com.wdlpiaoyi.justenoughhiding.jei;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.jei.JeiAdapter;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JehJeiPlugin implements IModPlugin
{
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(JustEnoughHiding.MODID, "main");

    private final JeiAdapter adapter = new JeiAdapter();

    public JehJeiPlugin()
    {
        Adapters.register(adapter);
    }

    @Override
    public ResourceLocation getPluginUid()
    {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime)
    {
        adapter.onRuntimeAvailable(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable()
    {
        adapter.onRuntimeUnavailable();
    }
}

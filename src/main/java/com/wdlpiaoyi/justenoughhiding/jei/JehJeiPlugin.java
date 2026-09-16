package com.wdlpiaoyi.justenoughhiding.jei;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentScanner;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JehJeiPlugin implements IModPlugin
{
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(JustEnoughHiding.MODID, "main");

    private static volatile IJeiRuntime runtime;

    private final JeiReveal reveal = new JeiReveal();

    public static IJeiRuntime getRuntime()
    {
        return runtime;
    }

    @Override
    public ResourceLocation getPluginUid()
    {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime)
    {
        runtime = jeiRuntime;
        reveal.activate(jeiRuntime);
        JeiIntentScanner.scan(jeiRuntime);
        JustEnoughHiding.LOGGER.info("[JEH] intents recorded for this runtime: {} entries", IntentRegistry.size());
    }

    @Override
    public void onRuntimeUnavailable()
    {
        runtime = null;
        reveal.deactivate();
        IntentRegistry.clear();
    }
}

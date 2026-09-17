package com.wdlpiaoyi.justenoughhiding.client;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.jehide.JehReloadHooks;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: notifies {@link JehReloadHooks} after each resource reload. */
@Mod.EventBusSubscriber(modid = JustEnoughHiding.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JehClientReloadEvents
{
    private JehClientReloadEvents()
    {
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event)
    {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>()
        {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler)
            {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler)
            {
                JehReloadHooks.onReload();
            }
        });
    }
}

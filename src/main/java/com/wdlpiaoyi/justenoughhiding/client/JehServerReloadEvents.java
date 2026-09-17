package com.wdlpiaoyi.justenoughhiding.client;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.jehide.JehReloadHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only: server data reloads (world join, {@code /reload}, datapack changes) also invalidate
 * JEH's viewer caches, so tag/recipe driven data is re-scanned. Asset changes still need a client
 * resource reload (F3+T or toggling a resource pack), which is handled by
 * {@link JehClientReloadEvents}; both funnel into {@link JehReloadHooks}.
 */
@Mod.EventBusSubscriber(modid = JustEnoughHiding.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JehServerReloadEvents
{
    private JehServerReloadEvents()
    {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new SimplePreparableReloadListener<Void>()
        {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler)
            {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler)
            {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null)
                {
                    minecraft.execute(JehReloadHooks::onReload);
                }
            }
        });
    }
}

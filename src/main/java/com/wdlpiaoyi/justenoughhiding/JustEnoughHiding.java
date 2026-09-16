package com.wdlpiaoyi.justenoughhiding;

import com.mojang.logging.LogUtils;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.viewer.RecipeViewerBackends;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(JustEnoughHiding.MODID)
public final class JustEnoughHiding
{
    public static final String MODID = "justenoughhiding";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JustEnoughHiding(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.CLIENT, JehConfig.SPEC, "jeh/client.toml");
        modEventBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event));
        modEventBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event));

        RecipeViewerBackends.logStatus();
    }

    private static void onConfigChanged(ModConfigEvent event)
    {
        if (event.getConfig().getSpec() == JehConfig.SPEC)
        {
            JehConfig.refresh();
        }
    }
}

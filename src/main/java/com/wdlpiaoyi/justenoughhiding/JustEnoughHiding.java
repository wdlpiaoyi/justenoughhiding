package com.wdlpiaoyi.justenoughhiding;

import com.mojang.logging.LogUtils;
import com.wdlpiaoyi.justenoughhiding.viewer.RecipeViewerBackends;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(JustEnoughHiding.MODID)
public final class JustEnoughHiding
{
    public static final String MODID = "justenoughhiding";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JustEnoughHiding(FMLJavaModLoadingContext context)
    {
        RecipeViewerBackends.logStatus();
    }
}

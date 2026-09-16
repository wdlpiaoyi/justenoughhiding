package com.wdlpiaoyi.justenoughhiding.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = JustEnoughHiding.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JehKeyMappings
{
    public static final String CATEGORY = "key.categories.justenoughhiding";

    public static final KeyMapping OPEN_INTENTS = new KeyMapping(
        "key.justenoughhiding.open_intents",
        KeyConflictContext.UNIVERSAL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY
    );

    public static final KeyMapping OPEN_LIST = new KeyMapping(
        "key.justenoughhiding.open_list",
        KeyConflictContext.UNIVERSAL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY
    );

    private JehKeyMappings()
    {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event)
    {
        event.register(OPEN_INTENTS);
        event.register(OPEN_LIST);
    }
}

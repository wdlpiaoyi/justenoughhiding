package com.wdlpiaoyi.justenoughhiding.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.gui.IntentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JustEnoughHiding.MODID, value = Dist.CLIENT)
public final class JehClientEvents
{
    private JehClientEvents()
    {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jeh")
            .then(Commands.literal("intents").executes(context -> openScreen()));
        event.getDispatcher().register(root);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        while (JehKeyMappings.OPEN_INTENTS.consumeClick())
        {
            openScreen();
        }
    }

    private static int openScreen()
    {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new IntentScreen()));
        return 1;
    }
}

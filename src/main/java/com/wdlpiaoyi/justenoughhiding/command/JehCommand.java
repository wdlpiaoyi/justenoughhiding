package com.wdlpiaoyi.justenoughhiding.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentQuery;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class JehCommand
{
    private JehCommand()
    {
    }

    public static void register(RegisterClientCommandsEvent event)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jeh")
            .then(Commands.literal("intents").executes(context -> dump(context.getSource())));
        event.getDispatcher().register(root);
    }

    private static int dump(CommandSourceStack source)
    {
        IntentQuery query = IntentRegistry.query();
        Collection<Intent> all = query.all();

        Map<String, Integer> bySource = new TreeMap<>();
        for (Intent intent : all)
        {
            bySource.merge(intent.source().id(), 1, Integer::sum);
        }

        source.sendSuccess(
            () -> Component.literal("[JEH] intents: " + all.size() + " entries, " + query.currentlyHidden().size() + " hidden; sources: " + bySource),
            false
        );

        Path out = writeDump(all);
        if (out != null)
        {
            source.sendSuccess(() -> Component.literal("[JEH] full dump written to " + out.toAbsolutePath()), false);
        }
        return all.size();
    }

    private static Path writeDump(Collection<Intent> all)
    {
        try
        {
            Path dir = Path.of("logs", "justenoughhiding");
            Files.createDirectories(dir);
            Path file = dir.resolve("intents.txt");

            List<String> lines = new ArrayList<>();
            all.stream()
                .sorted(Comparator
                    .comparing((Intent intent) -> intent.source().id())
                    .thenComparing(intent -> intent.kind().name())
                    .thenComparing(intent -> String.valueOf(intent.target())))
                .forEach(intent -> lines.add(
                    intent.kind() + "\t" + intent.source().id() + "\tx" + intent.count() + "\t" + intent.target()
                ));

            Files.write(file, lines);
            return file;
        }
        catch (Throwable t)
        {
            JustEnoughHiding.LOGGER.warn("[JEH] failed to dump intents", t);
            return null;
        }
    }
}

package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class IntentExporter
{
    private IntentExporter()
    {
    }

    public static Path export(Collection<Intent> intents)
    {
        try
        {
            Path dir = Path.of("logs", "justenoughhiding");
            Files.createDirectories(dir);
            Path file = dir.resolve("intents.txt");

            List<String> lines = new ArrayList<>(intents.size());
            for (Intent intent : intents)
            {
                lines.add(IntentFormat.line(intent));
            }
            Files.write(file, lines);
            return file;
        }
        catch (Throwable t)
        {
            JustEnoughHiding.LOGGER.warn("[JEH] failed to export intents", t);
            return null;
        }
    }
}

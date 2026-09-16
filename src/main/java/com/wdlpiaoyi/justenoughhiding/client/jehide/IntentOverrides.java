package com.wdlpiaoyi.justenoughhiding.client.jehide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-intent "apply or not" overrides, persisted at {@code config/jeh/intentoverrides.json}.
 * Only the disabled keys are stored; anything absent is enabled.
 */
public final class IntentOverrides
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> DISABLED = new LinkedHashSet<>();
    private static boolean loaded;

    private IntentOverrides()
    {
    }

    public static Path file()
    {
        return FMLPaths.CONFIGDIR.get().resolve("jeh").resolve("intentoverrides.json");
    }

    public static synchronized boolean isEnabled(IntentTarget target, String sourceId)
    {
        ensureLoaded();
        return !DISABLED.contains(key(target, sourceId));
    }

    public static synchronized void setEnabled(IntentTarget target, String sourceId, boolean enabled)
    {
        ensureLoaded();
        String key = key(target, sourceId);
        boolean changed = enabled ? DISABLED.remove(key) : DISABLED.add(key);
        if (changed)
        {
            save();
        }
    }

    private static String key(IntentTarget target, String sourceId)
    {
        return target.kind() + "|" + target.describe() + "|" + (sourceId == null ? "" : sourceId);
    }

    private static void ensureLoaded()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.exists(file))
        {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            Root root = GSON.fromJson(reader, Root.class);
            if (root != null && root.disabled != null)
            {
                for (String value : root.disabled)
                {
                    if (value != null && !value.isBlank())
                    {
                        DISABLED.add(value);
                    }
                }
            }
        }
        catch (Throwable t)
        {
            JustEnoughHiding.LOGGER.warn("[JEH] failed to load {}", file, t);
        }
    }

    private static void save()
    {
        Path file = file();
        try
        {
            Path parent = file.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            Root root = new Root();
            root.disabled.addAll(DISABLED);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
            {
                GSON.toJson(root, writer);
            }
        }
        catch (Throwable t)
        {
            JustEnoughHiding.LOGGER.warn("[JEH] failed to save {}", file, t);
        }
    }

    private static final class Root
    {
        private final Set<String> disabled = new LinkedHashSet<>();
    }
}

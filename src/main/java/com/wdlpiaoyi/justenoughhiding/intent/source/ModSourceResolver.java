package com.wdlpiaoyi.justenoughhiding.intent.source;

import com.wdlpiaoyi.justenoughhiding.intent.IntentSource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ModSourceResolver
{
    private static final String[] SKIP_PREFIXES = {
        "java.",
        "javax.",
        "jdk.",
        "sun.",
        "com.sun.",
        "net.minecraft.",
        "net.minecraftforge.",
        "cpw.mods.",
        "org.spongepowered.",
        "mezz.jei.",
        "com.wdlpiaoyi.justenoughhiding."
    };

    private static volatile Map<String, String> pathToMod;

    private ModSourceResolver()
    {
    }

    public static IntentSource sourceOfCaller()
    {
        Optional<Class<?>> caller = StackWalker
            .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(clazz -> !isSkipped(clazz))
                .findFirst());
        return caller.map(clazz -> IntentSource.mod(modIdOf(clazz))).orElse(IntentSource.UNKNOWN);
    }

    public static String modIdOf(Class<?> clazz)
    {
        try
        {
            Path path = codeSourcePath(clazz);
            if (path == null)
            {
                return "unknown";
            }
            String modId = paths().get(path.toString());
            return modId != null ? modId : "unknown";
        }
        catch (Throwable t)
        {
            return "unknown";
        }
    }

    private static Path codeSourcePath(Class<?> clazz)
    {
        var protectionDomain = clazz.getProtectionDomain();
        if (protectionDomain == null)
        {
            return null;
        }
        var codeSource = protectionDomain.getCodeSource();
        if (codeSource == null)
        {
            return null;
        }
        URL location = codeSource.getLocation();
        if (location == null || !"file".equals(location.getProtocol()))
        {
            return null;
        }
        return normalize(Path.of(location.getPath()));
    }

    private static boolean isSkipped(Class<?> clazz)
    {
        String name = clazz.getName();
        for (String prefix : SKIP_PREFIXES)
        {
            if (name.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }

    private static Path normalize(Path path)
    {
        try
        {
            return path.toAbsolutePath().normalize();
        }
        catch (Throwable t)
        {
            return path;
        }
    }

    private static Map<String, String> paths()
    {
        Map<String, String> map = pathToMod;
        if (map == null)
        {
            map = build();
            pathToMod = map;
        }
        return map;
    }

    private static Map<String, String> build()
    {
        Map<String, String> map = new HashMap<>();
        try
        {
            ModList modList = ModList.get();
            if (modList == null)
            {
                return map;
            }
            for (IModInfo info : modList.getMods())
            {
                IModFile file = info.getOwningFile().getFile();
                Path path = normalize(file.getFilePath());
                map.putIfAbsent(path.toString(), info.getModId());
            }
        }
        catch (Throwable ignored)
        {
        }
        return map;
    }
}

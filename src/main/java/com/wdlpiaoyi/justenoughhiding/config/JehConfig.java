package com.wdlpiaoyi.justenoughhiding.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class JehConfig
{
    public static final ForgeConfigSpec SPEC;

    public static final List<String> DEFAULT_SORT_MODES = List.of(
        "source,kind,target",
        "kind,source,target",
        "target",
        "count:desc",
        "sequence"
    );

    private static final ForgeConfigSpec.BooleanValue INTENT_RECORDING_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SORT_MODES;

    private static volatile boolean intentRecordingEnabled = true;
    private static volatile List<String> sortModes = DEFAULT_SORT_MODES;

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Just Enough Hiding").push("intentRecording");
        INTENT_RECORDING_ENABLED = builder
            .comment(
                "Record what other mods (and JEI itself) do to hide or show ingredients:",
                "runtime removal/addition, visibility changes, JEI edit mode, hidden tags,",
                "server-side absence, and recipe/category hiding.",
                "Dev builds default to true. Turn this off to disable all recording."
            )
            .define("enabled", true);
        builder.pop();

        builder.comment("Intent viewer GUI").push("intentView");
        SORT_MODES = builder
            .comment(
                "Sort modes offered in the intent viewer.",
                "Each entry is a comma-separated list of keys with an optional ':asc' or ':desc' suffix.",
                "Available keys: source, kind, target, count, sequence.",
                "Default direction is ascending, except 'count' which defaults to descending when written as 'count:desc'."
            )
            .defineListAllowEmpty("sortModes", DEFAULT_SORT_MODES, JehConfig::isString);
        builder.pop();

        SPEC = builder.build();
    }

    private JehConfig()
    {
    }

    public static boolean intentRecordingEnabled()
    {
        return intentRecordingEnabled;
    }

    public static List<String> sortModes()
    {
        return sortModes;
    }

    public static void refresh()
    {
        if (!SPEC.isLoaded())
        {
            return;
        }
        intentRecordingEnabled = INTENT_RECORDING_ENABLED.get();

        List<String> cleaned = new ArrayList<>();
        for (String entry : SORT_MODES.get())
        {
            if (entry != null && !entry.isBlank())
            {
                cleaned.add(entry.trim());
            }
        }
        sortModes = cleaned.isEmpty() ? DEFAULT_SORT_MODES : List.copyOf(cleaned);
    }

    private static boolean isString(Object value)
    {
        return value instanceof String;
    }
}

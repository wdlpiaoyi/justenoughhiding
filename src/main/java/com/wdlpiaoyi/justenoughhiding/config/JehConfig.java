package com.wdlpiaoyi.justenoughhiding.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class JehConfig
{
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue INTENT_RECORDING_ENABLED;

    private static volatile boolean intentRecordingEnabled = true;

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

        SPEC = builder.build();
    }

    private JehConfig()
    {
    }

    public static boolean intentRecordingEnabled()
    {
        return intentRecordingEnabled;
    }

    public static void refresh()
    {
        if (!SPEC.isLoaded())
        {
            return;
        }
        intentRecordingEnabled = INTENT_RECORDING_ENABLED.get();
    }
}

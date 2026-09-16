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

    public enum BookmarkTarget
    {
        ICON,
        ROW
    }

    private static final ForgeConfigSpec.BooleanValue INTENT_RECORDING_ENABLED;
    private static final ForgeConfigSpec.BooleanValue REVEAL_ENABLED;
    private static final ForgeConfigSpec.BooleanValue JEHIDE_ENABLED;
    private static final ForgeConfigSpec.BooleanValue JEHIDE_APPLY_INTENTS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SORT_MODES;
    private static final ForgeConfigSpec.EnumValue<BookmarkTarget> BOOKMARK_TARGET;

    private static volatile boolean intentRecordingEnabled = true;
    private static volatile boolean revealEnabled = true;
    private static volatile boolean jehideEnabled = true;
    private static volatile boolean jehideApplyIntents = true;
    private static volatile List<String> sortModes = DEFAULT_SORT_MODES;
    private static volatile BookmarkTarget bookmarkTarget = BookmarkTarget.ICON;

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

        builder.comment("Reveal module").push("reveal");
        REVEAL_ENABLED = builder
            .comment(
                "Bypass hiding when JEI starts:",
                "ingredients hidden by tags, JEI edit mode or the blacklist are shown again;",
                "ingredients removed at runtime are restored (any ingredient type);",
                "hidden recipes and recipe categories are unhidden.",
                "Toggling this at runtime does not undo reveals that were already applied."
            )
            .define("enabled", true);
        builder.pop();

        builder.comment("JEHide: hide ListEHiding entries from JEI").push("jehide");
        JEHIDE_ENABLED = builder
            .comment(
                "Read the enabled entries of config/jeh/listehiding.json and hide the matching",
                "ingredients, recipes and recipe categories from JEI (runtime visibility only,",
                "never written to JEI's config files). Re-applied when JEI starts, when the list",
                "changes, or via /jeh apply. Takes precedence over the reveal module for its targets."
            )
            .define("enabled", true);
        JEHIDE_APPLY_INTENTS = builder
            .comment(
                "Also treat recorded intents (hide kinds) as hide rules, so everything the reveal",
                "module brings back is re-hidden from one place. Re-applied after intent changes",
                "(debounced). Turn off to keep JEHide driven by the list only."
            )
            .define("applyIntents", true);
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
        BOOKMARK_TARGET = builder
            .comment(
                "Where the JEI bookmark key applies in the intent viewer:",
                "ICON - only while hovering the item icon;",
                "ROW  - anywhere on the row."
            )
            .defineEnum("bookmarkTarget", BookmarkTarget.ICON);
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

    public static boolean revealEnabled()
    {
        return revealEnabled;
    }

    public static boolean jehideEnabled()
    {
        return jehideEnabled;
    }

    public static boolean jehideApplyIntents()
    {
        return jehideApplyIntents;
    }

    public static List<String> sortModes()
    {
        return sortModes;
    }

    public static BookmarkTarget bookmarkTarget()
    {
        return bookmarkTarget;
    }

    public static void refresh()
    {
        if (!SPEC.isLoaded())
        {
            return;
        }
        intentRecordingEnabled = INTENT_RECORDING_ENABLED.get();
        revealEnabled = REVEAL_ENABLED.get();
        jehideEnabled = JEHIDE_ENABLED.get();
        jehideApplyIntents = JEHIDE_APPLY_INTENTS.get();

        List<String> cleaned = new ArrayList<>();
        for (String entry : SORT_MODES.get())
        {
            if (entry != null && !entry.isBlank())
            {
                cleaned.add(entry.trim());
            }
        }
        sortModes = cleaned.isEmpty() ? DEFAULT_SORT_MODES : List.copyOf(cleaned);

        bookmarkTarget = BOOKMARK_TARGET.get();
    }

    private static boolean isString(Object value)
    {
        return value instanceof String;
    }
}

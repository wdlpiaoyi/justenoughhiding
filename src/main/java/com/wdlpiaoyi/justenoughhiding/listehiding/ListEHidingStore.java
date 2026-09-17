package com.wdlpiaoyi.justenoughhiding.listehiding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and saves {@link ListEHidingEntry} entries as JSON at
 * {@code config/jeh/listehiding.json}.
 * <p>
 * On first run the mod creates an empty {@code listehiding.json} plus a
 * {@code listehiding.example.json} with commented examples; the active list is normally edited
 * through the GUI or externally.
 */
public final class ListEHidingStore
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String EMPTY_JSON = "{\n  \"entries\": []\n}\n";
    private static final String EXAMPLE_JSON = """
        {
          "_comment": "JEH ListEHiding examples. Entries are ignored while \\"enabled\\" is false; the mod uses config/jeh/listehiding.json (auto-created empty on first run).",
          "entries": [
            { "kind": "ingredient", "typeUid": "minecraft:item_stack", "uid": "minecraft:stone", "enabled": false, "note": "example: hide an item", "priority": 0 },
            { "kind": "tag", "tag": "minecraft:logs", "enabled": false, "note": "example: hide a tag" },
            { "kind": "recipe", "recipeType": "minecraft:crafting", "recipeId": "minecraft:stick", "enabled": false, "note": "example: hide a recipe" },
            { "kind": "recipe_category", "recipeType": "minecraft:smithing", "enabled": false, "note": "example: hide a recipe category" },
            { "kind": "pattern", "scope": "ingredient|minecraft:item_stack", "pattern": "minecraft:*_ore", "mode": "glob", "enabled": false, "note": "example: hide by wildcard" }
          ]
        }
        """;

    private ListEHidingStore()
    {
    }

    public static Path file()
    {
        return FMLPaths.CONFIGDIR.get().resolve("jeh").resolve("listehiding.json");
    }

    /** Writes an empty {@code listehiding.json} and a {@code listehiding.example.json} if absent. */
    public static synchronized void ensureDefaults()
    {
        try
        {
            Path file = file();
            Path dir = file.getParent();
            if (dir != null)
            {
                Files.createDirectories(dir);
            }
            if (!Files.exists(file))
            {
                Files.writeString(file, EMPTY_JSON, StandardCharsets.UTF_8);
            }
            Path example = dir == null ? null : dir.resolve("listehiding.example.json");
            if (example != null && !Files.exists(example))
            {
                Files.writeString(example, EXAMPLE_JSON, StandardCharsets.UTF_8);
            }
        }
        catch (Throwable t)
        {
            JustEnoughHiding.LOGGER.warn("[JEH] failed to create default listehiding files", t);
        }
    }

    public static List<ListEHidingEntry> load()
    {
        Path file = file();
        if (!Files.exists(file))
        {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            Root root = GSON.fromJson(reader, Root.class);
            if (root == null || root.entries == null)
            {
                return List.of();
            }
            List<ListEHidingEntry> result = new ArrayList<>();
            for (EntryDto dto : root.entries)
            {
                ListEHidingEntry entry = fromDto(dto);
                if (entry != null)
                {
                    result.add(entry);
                }
            }
            return result;
        }
        catch (Throwable t)
        {
            JustEnoughHiding.LOGGER.warn("[JEH] failed to load {}", file, t);
            return List.of();
        }
    }

    public static void save(List<ListEHidingEntry> entries)
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
            for (ListEHidingEntry entry : entries)
            {
                root.entries.add(toDto(entry));
            }
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

    private static ListEHidingEntry fromDto(EntryDto dto)
    {
        if (dto == null || dto.kind == null)
        {
            return null;
        }
        IntentTarget target = switch (dto.kind)
        {
            case "ingredient" -> dto.typeUid != null && dto.uid != null
                ? IntentTarget.of(IngredientKey.of(dto.typeUid, dto.uid))
                : null;
            case "recipe" -> {
                ResourceLocation type = dto.recipeType == null ? null : ResourceLocation.tryParse(dto.recipeType);
                yield type != null && dto.recipeId != null ? IntentTarget.of(type, dto.recipeId) : null;
            }
            case "recipe_category" -> {
                ResourceLocation type = dto.recipeType == null ? null : ResourceLocation.tryParse(dto.recipeType);
                yield type != null ? IntentTarget.category(type) : null;
            }
            case "tag" -> dto.tag == null || dto.tag.isBlank() ? null : IntentTarget.tag(dto.tag);
            case "pattern" -> {
                if (dto.pattern == null || dto.pattern.isBlank())
                {
                    yield null;
                }
                IntentTarget.MatchMode mode = "regex".equalsIgnoreCase(dto.mode)
                    ? IntentTarget.MatchMode.REGEX
                    : IntentTarget.MatchMode.GLOB;
                yield IntentTarget.pattern(dto.scope == null ? "" : dto.scope, dto.pattern, mode);
            }
            case "unset" -> IntentTarget.unset();
            default -> null;
        };
        if (target == null)
        {
            return null;
        }
        return new ListEHidingEntry(target, dto.enabled == null || dto.enabled,
            dto.note == null ? "" : dto.note, dto.priority == null ? 0 : dto.priority);
    }

    private static EntryDto toDto(ListEHidingEntry entry)
    {
        EntryDto dto = new EntryDto();
        dto.kind = entry.target().kind();
        if (entry.target() instanceof IntentTarget.Ingredient ingredient)
        {
            dto.typeUid = ingredient.key().typeUid();
            dto.uid = ingredient.key().uid();
        }
        else if (entry.target() instanceof IntentTarget.Recipe recipe)
        {
            dto.recipeType = recipe.recipeType().toString();
            dto.recipeId = recipe.recipeId();
        }
        else if (entry.target() instanceof IntentTarget.RecipeCategory category)
        {
            dto.recipeType = category.recipeType().toString();
        }
        else if (entry.target() instanceof IntentTarget.Tag tag)
        {
            dto.tag = tag.tagId();
        }
        else if (entry.target() instanceof IntentTarget.Pattern pattern)
        {
            dto.pattern = pattern.pattern();
            dto.mode = pattern.mode() == IntentTarget.MatchMode.REGEX ? "regex" : "glob";
            dto.scope = pattern.scope();
        }
        dto.enabled = entry.enabled();
        dto.note = entry.note();
        dto.priority = entry.priority();
        return dto;
    }

    private static final class Root
    {
        private final List<EntryDto> entries = new ArrayList<>();
    }

    private static final class EntryDto
    {
        private String kind;
        private String typeUid;
        private String uid;
        private String recipeType;
        private String recipeId;
        private String tag;
        private String pattern;
        private String mode;
        private String scope;
        private Boolean enabled;
        private String note;
        private Integer priority;
    }
}

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
 * The mod never generates data by itself; the file is edited externally.
 */
public final class ListEHidingStore
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ListEHidingStore()
    {
    }

    public static Path file()
    {
        return FMLPaths.CONFIGDIR.get().resolve("jeh").resolve("listehiding.json");
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
        return new ListEHidingEntry(target, dto.enabled == null || dto.enabled, dto.note == null ? "" : dto.note);
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
    }
}

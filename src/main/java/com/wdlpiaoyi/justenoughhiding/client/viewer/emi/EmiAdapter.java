package com.wdlpiaoyi.justenoughhiding.client.viewer.emi;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import com.wdlpiaoyi.justenoughhiding.client.jehide.EmiHide;
import com.wdlpiaoyi.justenoughhiding.client.viewer.DefaultColumns;
import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetKeys;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetKind;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetMatcher;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI implementation of {@link ViewerAdapter}. Registered by {@code JehEmiPlugin}
 * ({@code @EmiEntrypoint}); takes priority over JEI because EMI overrides the recipe-viewer
 * overlay when installed.
 */
public final class EmiAdapter implements ViewerAdapter
{
    public static final String ID = "emi";

    private volatile EmiTargetIndex index;

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public boolean available()
    {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(ID);
    }

    @Override
    public int priority()
    {
        return 100;
    }

    /** Called by the EMI plugin on every reload so the index is rebuilt from the new data. */
    public void invalidateIndex()
    {
        this.index = null;
    }

    @Override
    public List<TargetKind> targetKinds()
    {
        List<TargetKind> kinds = new ArrayList<>();
        kinds.add(new TargetKind("", Component.translatable("jeh.kind.auto").getString()));
        kinds.add(new TargetKind("ingredient|" + EmiTargetIndex.ITEM_TYPE,
            Component.translatable("jeh.kind.item").getString()));
        kinds.add(new TargetKind("ingredient|" + EmiTargetIndex.FLUID_TYPE,
            Component.translatable("jeh.kind.fluid").getString()));
        kinds.add(new TargetKind("recipe", Component.translatable("jeh.kind.recipe").getString()));
        kinds.add(new TargetKind("recipe_category", Component.translatable("jeh.kind.category").getString()));
        kinds.add(new TargetKind("tag", Component.translatable("jeh.kind.tag").getString()));
        return kinds;
    }

    @Override
    public List<Column<Intent>> columns()
    {
        return DefaultColumns.withIcon(intent -> icon(intent.target()));
    }

    @Override
    public IconRenderer icon(IntentTarget target)
    {
        if (!(target instanceof IntentTarget.Ingredient ingredient))
        {
            return IconRenderer.EMPTY;
        }
        String typeUid = ingredient.key().typeUid();
        String uid = ingredient.key().uid();
        String canonical = TargetKeys.isFluidType(typeUid) ? EmiTargetIndex.FLUID_TYPE
            : TargetKeys.isItemType(typeUid) ? EmiTargetIndex.ITEM_TYPE
            : typeUid;
        EmiStack stack = index().icon(canonical, uid);
        if (stack == null || stack.isEmpty())
        {
            stack = resolveStack(typeUid, uid);
        }
        return stack == null || stack.isEmpty() ? IconRenderer.EMPTY : new EmiIconRenderer(stack);
    }

    /**
     * Resolves an ingredient id straight from the registries, so targets recorded by JEI (which
     * spells the item type uid differently) or missing from EMI's index still get an icon.
     */
    private static EmiStack resolveStack(String typeUid, String uid)
    {
        if (uid == null || uid.isBlank())
        {
            return null;
        }
        int brace = uid.indexOf('{');
        ResourceLocation id = ResourceLocation.tryParse(brace >= 0 ? uid.substring(0, brace) : uid);
        if (id == null)
        {
            return null;
        }
        try
        {
            if (TargetKeys.isFluidType(typeUid))
            {
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
                return fluid == null ? null : EmiStack.of(fluid);
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return item == null ? null : EmiStack.of(item);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    @Override
    public boolean isBookmarkKey(int keyCode, int scanCode)
    {
        return false;
    }

    @Override
    public boolean bookmark(IntentTarget target)
    {
        return false;
    }

    @Override
    public List<TargetSuggestion> suggest(String query, String kind, int limit)
    {
        return index().suggest(query, kind, limit);
    }

    @Override
    public IntentTarget detect(String text)
    {
        return index().detect(text);
    }

    @Override
    public List<TargetSuggestion> matches(IntentTarget pattern, int limit)
    {
        return index().matches(pattern, limit);
    }

    @Override
    public IntentTarget ofKind(String kind, String id)
    {
        if (id == null || id.isBlank())
        {
            return null;
        }
        String value = id.trim();
        if (TargetKeys.isPattern(value))
        {
            IntentTarget target = IntentTarget.pattern(kind == null ? "" : kind,
                TargetKeys.patternBody(value), TargetKeys.modeOf(value));
            return TargetMatcher.compile((IntentTarget.Pattern) target) == null ? null : target;
        }
        if (kind == null || kind.isBlank())
        {
            return index().detect(value);
        }
        if (kind.startsWith("ingredient|"))
        {
            String typeUid = kind.substring("ingredient|".length());
            if (EmiTargetIndex.ITEM_TYPE.equals(typeUid) || EmiTargetIndex.FLUID_TYPE.equals(typeUid))
            {
                return ResourceLocation.tryParse(value) == null
                    ? null
                    : IntentTarget.of(IngredientKey.of(typeUid, value));
            }
            return null;
        }
        return switch (kind)
        {
            case "recipe" -> index().recipeTarget(value);
            case "recipe_category" ->
            {
                ResourceLocation type = ResourceLocation.tryParse(value);
                yield type == null ? null : IntentTarget.category(type);
            }
            case "tag" ->
            {
                String tagId = value.startsWith("#") ? value.substring(1) : value;
                yield ResourceLocation.tryParse(tagId) == null ? null : IntentTarget.tag(tagId);
            }
            default -> index().detect(value);
        };
    }

    @Override
    public IntentTarget ingredientTarget(String uid)
    {
        if (uid == null || uid.isBlank())
        {
            return null;
        }
        String value = uid.trim();
        return ResourceLocation.tryParse(value) == null
            ? null
            : IntentTarget.of(IngredientKey.of(EmiTargetIndex.ITEM_TYPE, value));
    }

    @Override
    public void reapplyHides()
    {
        EmiHide.reapply();
    }

    @Override
    public void tickHides()
    {
        EmiHide.tick();
    }

    private EmiTargetIndex index()
    {
        EmiTargetIndex cached = this.index;
        if (cached == null)
        {
            cached = EmiTargetIndex.build();
            this.index = cached;
        }
        return cached;
    }
}

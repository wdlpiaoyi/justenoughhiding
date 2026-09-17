package com.wdlpiaoyi.justenoughhiding.client.viewer.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.wdlpiaoyi.justenoughhiding.JustEnoughHiding;
import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import com.wdlpiaoyi.justenoughhiding.client.jehide.JeHide;
import com.wdlpiaoyi.justenoughhiding.client.viewer.DefaultColumns;
import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetKind;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetKeys;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetMatcher;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter;
import com.wdlpiaoyi.justenoughhiding.intent.IngredientKey;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.jei.JeiRecipeReveal;
import com.wdlpiaoyi.justenoughhiding.jei.JeiReveal;
import com.wdlpiaoyi.justenoughhiding.jei.intent.JeiIntentScanner;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JeiAdapter implements ViewerAdapter
{
    public static final String ID = "jei";

    private final JeiReveal reveal = new JeiReveal();
    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();
    private final Map<String, IconRenderer> typedIconCache = new ConcurrentHashMap<>();
    private volatile IJeiRuntime runtime;
    private volatile JeiTargetIndex targetIndex;
    private volatile IJeiRuntime indexRuntime;
    private volatile Level indexLevel;

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
        return 10;
    }

    @Override
    public List<TargetKind> targetKinds()
    {
        List<TargetKind> kinds = new ArrayList<>();
        kinds.add(new TargetKind("", Component.translatable("jeh.kind.auto").getString()));
        IJeiRuntime current = this.runtime;
        if (current != null)
        {
            try
            {
                for (IIngredientType<?> type : current.getIngredientManager().getRegisteredIngredientTypes())
                {
                    String uid = type.getUid();
                    if (uid != null)
                    {
                        kinds.add(new TargetKind("ingredient|" + uid, JeiTargetIndex.typeLabel(uid)));
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
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
        if (VanillaTypes.ITEM_STACK.getUid().equals(ingredient.key().typeUid()))
        {
            ItemStack stack = resolveItem(ingredient);
            return stack.isEmpty() ? IconRenderer.EMPTY : new ItemIconRenderer(stack);
        }
        return typedIcon(ingredient);
    }

    private IconRenderer typedIcon(IntentTarget.Ingredient ingredient)
    {
        String typeUid = ingredient.key().typeUid();
        String uid = ingredient.key().uid();
        String cacheKey = typeUid + "|" + uid;
        IconRenderer cached = typedIconCache.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        IJeiRuntime current = this.runtime;
        if (current == null)
        {
            return IconRenderer.EMPTY;
        }
        try
        {
            IIngredientManager manager = current.getIngredientManager();
            Optional<IIngredientType<?>> typeOptional = manager.getIngredientTypeForUid(typeUid);
            if (typeOptional.isEmpty())
            {
                return IconRenderer.EMPTY;
            }
            IIngredientType<?> type = typeOptional.get();
            Optional<? extends ITypedIngredient<?>> typed = typedIngredient(manager, type, uid);
            if (typed.isEmpty())
            {
                return IconRenderer.EMPTY;
            }
            IIngredientRenderer<?> renderer = manager.getIngredientRenderer(type);
            if (renderer == null)
            {
                return IconRenderer.EMPTY;
            }
            IconRenderer result = new TypedIconRenderer(typed.get().getIngredient(), renderer);
            typedIconCache.put(cacheKey, result);
            return result;
        }
        catch (Throwable t)
        {
            return IconRenderer.EMPTY;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<? extends ITypedIngredient<?>> typedIngredient(
        IIngredientManager manager, IIngredientType<?> type, String uid)
    {
        return (Optional) manager.getTypedIngredientByUid((IIngredientType) type, uid);
    }

    @Override
    public boolean isBookmarkKey(int keyCode, int scanCode)
    {
        IJeiRuntime current = this.runtime;
        if (current == null)
        {
            return false;
        }
        try
        {
            IJeiKeyMapping bookmark = current.getKeyMappings().getBookmark();
            return bookmark != null && !bookmark.isUnbound()
                && bookmark.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Override
    public boolean bookmark(IntentTarget target)
    {
        IJeiRuntime current = this.runtime;
        if (current == null)
        {
            return false;
        }
        ItemStack stack = resolveItem(target);
        if (stack.isEmpty())
        {
            return false;
        }
        try
        {
            current.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
                .ifPresent(typed -> current.getBookmarkManager().add(typed));
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Override
    public IntentTarget ingredientTarget(String uid)
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
        return IntentTarget.of(IngredientKey.of(VanillaTypes.ITEM_STACK.getUid(), uid));
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
            default -> kind.startsWith("ingredient|")
                ? ingredientOfType(kind.substring("ingredient|".length()), value)
                : index().detect(value);
        };
    }

    private IntentTarget ingredientOfType(String typeUid, String uid)
    {
        if (VanillaTypes.ITEM_STACK.getUid().equals(typeUid))
        {
            return ingredientTarget(uid);
        }
        int brace = uid.indexOf('{');
        return ResourceLocation.tryParse(brace >= 0 ? uid.substring(0, brace) : uid) == null
            ? null
            : IntentTarget.of(IngredientKey.of(typeUid, uid));
    }

    private JeiTargetIndex index()
    {
        IJeiRuntime currentRuntime = this.runtime;
        Level currentLevel = Minecraft.getInstance().level;
        JeiTargetIndex cached = this.targetIndex;
        if (cached != null && this.indexRuntime == currentRuntime && this.indexLevel == currentLevel)
        {
            return cached;
        }
        JeiTargetIndex built = JeiTargetIndex.build(currentRuntime, currentLevel);
        this.targetIndex = built;
        this.indexRuntime = currentRuntime;
        this.indexLevel = currentLevel;
        return built;
    }

    @Override
    public void onRuntimeAvailable(Object runtime)
    {
        if (!(runtime instanceof IJeiRuntime jeiRuntime))
        {
            return;
        }
        this.runtime = jeiRuntime;
        this.targetIndex = null;
        reveal.activate(jeiRuntime);
        JeiRecipeReveal.reveal(jeiRuntime);
        JeHide.apply(jeiRuntime);
        JeiIntentScanner.scan(jeiRuntime);
        JustEnoughHiding.LOGGER.info("[JEH] intents recorded for this runtime: {} entries", IntentRegistry.size());
    }

    @Override
    public void onRuntimeUnavailable()
    {
        this.runtime = null;
        this.targetIndex = null;
        reveal.deactivate();
        JeHide.reset();
        IntentRegistry.clear();
    }

    private ItemStack resolveItem(IntentTarget target)
    {
        if (!(target instanceof IntentTarget.Ingredient ingredient))
        {
            return ItemStack.EMPTY;
        }
        if (!VanillaTypes.ITEM_STACK.getUid().equals(ingredient.key().typeUid()))
        {
            return ItemStack.EMPTY;
        }

        String uid = ingredient.key().uid();
        ItemStack cached = iconCache.get(uid);
        if (cached != null)
        {
            return cached;
        }

        ItemStack resolved = lookup(uid);
        if (!resolved.isEmpty())
        {
            iconCache.put(uid, resolved);
        }
        return resolved;
    }

    private ItemStack lookup(String uid)
    {
        IJeiRuntime current = this.runtime;
        if (current != null)
        {
            try
            {
                Optional<ITypedIngredient<ItemStack>> typed = current.getIngredientManager()
                    .getTypedIngredientByUid(VanillaTypes.ITEM_STACK, uid);
                if (typed.isPresent() && !typed.get().getIngredient().isEmpty())
                {
                    return typed.get().getIngredient();
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        int brace = uid.indexOf('{');
        String registryName = brace >= 0 ? uid.substring(0, brace) : uid;
        ResourceLocation id = ResourceLocation.tryParse(registryName);
        if (id != null)
        {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null)
            {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }
}

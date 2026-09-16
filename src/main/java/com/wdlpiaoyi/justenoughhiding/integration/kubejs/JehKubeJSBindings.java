package com.wdlpiaoyi.justenoughhiding.integration.kubejs;

import com.wdlpiaoyi.justenoughhiding.client.jehide.JeHide;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetKeys;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetMatcher;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHiding;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHidingEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The {@code JEH} object available to KubeJS client scripts.
 * <p>
 * Changes are made in memory only (re-applied to JEI immediately); call {@link #save()} to write
 * them to {@code config/jeh/listehiding.json}. Scripts that want to own the list can {@code clear()}
 * first so entries do not pile up between launches.
 */
public final class JehKubeJSBindings
{
    public int size()
    {
        return ListEHiding.get().entries().size();
    }

    public String[] list()
    {
        List<ListEHidingEntry> entries = ListEHiding.get().entries();
        String[] result = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++)
        {
            ListEHidingEntry entry = entries.get(i);
            result[i] = TargetKeys.label(entry.target())
                + (entry.enabled() ? "" : " [disabled]");
        }
        return result;
    }

    // ---- add (auto-detected) ----

    public boolean add(String spec)
    {
        return addTarget(parse(spec), "", 0);
    }

    public boolean add(String spec, String note)
    {
        return addTarget(parse(spec), note, 0);
    }

    public boolean add(String spec, String note, int priority)
    {
        return addTarget(parse(spec), note, priority);
    }

    // ---- add (explicit kinds) ----

    public boolean addItem(String id)
    {
        return addTarget(Adapters.active().ingredientTarget(id), "", 0);
    }

    public boolean addTag(String id)
    {
        String tag = id != null && id.startsWith("#") ? id.substring(1) : id;
        if (tag == null || tag.isBlank() || ResourceLocation.tryParse(tag.trim()) == null)
        {
            return false;
        }
        return addTarget(IntentTarget.tag(tag.trim()), "", 0);
    }

    public boolean addPattern(String spec)
    {
        if (!TargetKeys.isPattern(spec))
        {
            return false;
        }
        IntentTarget target = IntentTarget.pattern("", TargetKeys.patternBody(spec), TargetKeys.modeOf(spec));
        if (TargetMatcher.compile((IntentTarget.Pattern) target) == null)
        {
            return false;
        }
        return addTarget(target, "", 0);
    }

    public boolean addRecipe(String recipeType, String recipeId)
    {
        ResourceLocation type = recipeType == null ? null : ResourceLocation.tryParse(recipeType.trim());
        if (type == null || recipeId == null || recipeId.isBlank())
        {
            return false;
        }
        return addTarget(IntentTarget.of(type, recipeId.trim()), "", 0);
    }

    public boolean addCategory(String recipeType)
    {
        ResourceLocation type = recipeType == null ? null : ResourceLocation.tryParse(recipeType.trim());
        return type != null && addTarget(IntentTarget.category(type), "", 0);
    }

    // ---- edit / remove ----

    public int remove(String spec)
    {
        IntentTarget target = parse(spec);
        if (target == null)
        {
            return 0;
        }
        int removed = 0;
        List<ListEHidingEntry> entries = ListEHiding.get().entries();
        for (int i = entries.size() - 1; i >= 0; i--)
        {
            if (entries.get(i).target().equals(target))
            {
                ListEHiding.get().remove(i);
                removed++;
            }
        }
        if (removed > 0)
        {
            JeHide.reapply();
        }
        return removed;
    }

    public int clear()
    {
        int size = ListEHiding.get().entries().size();
        for (int i = size - 1; i >= 0; i--)
        {
            ListEHiding.get().remove(i);
        }
        if (size > 0)
        {
            JeHide.reapply();
        }
        return size;
    }

    public int setEnabled(String spec, boolean enabled)
    {
        IntentTarget target = parse(spec);
        if (target == null)
        {
            return 0;
        }
        int changed = 0;
        List<ListEHidingEntry> entries = ListEHiding.get().entries();
        for (int i = 0; i < entries.size(); i++)
        {
            ListEHidingEntry entry = entries.get(i);
            if (entry.target().equals(target) && entry.enabled() != enabled)
            {
                ListEHiding.get().set(i, new ListEHidingEntry(entry.target(), enabled, entry.note(), entry.priority()));
                changed++;
            }
        }
        if (changed > 0)
        {
            JeHide.reapply();
        }
        return changed;
    }

    // ---- persist / apply ----

    public void save()
    {
        ListEHiding.get().saveIfDirty();
    }

    public void reload()
    {
        ListEHiding.get().reload();
        JeHide.reapply();
    }

    public void apply()
    {
        JeHide.reapply();
    }

    // ---- internals ----

    private static IntentTarget parse(String spec)
    {
        if (spec == null || spec.isBlank())
        {
            return null;
        }
        return Adapters.active().ofKind("", spec.trim());
    }

    private static boolean addTarget(IntentTarget target, String note, int priority)
    {
        if (target == null || target instanceof IntentTarget.Unset)
        {
            return false;
        }
        for (ListEHidingEntry entry : ListEHiding.get().entries())
        {
            if (entry.target().equals(target))
            {
                return false;
            }
        }
        ListEHiding.get().add(new ListEHidingEntry(target, true, ListEHiding.kubeJsNote(note, false), priority));
        JeHide.reapply();
        return true;
    }
}

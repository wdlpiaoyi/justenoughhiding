package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Columns;
import com.wdlpiaoyi.justenoughhiding.client.gui.widget.AutocompletePopup;
import com.wdlpiaoyi.justenoughhiding.client.gui.widget.Dropdown;
import com.wdlpiaoyi.justenoughhiding.client.gui.widget.PopupMenu;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.client.viewer.TargetSuggestion;
import com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHiding;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHidingEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public final class HidingListScreen extends Screen
{
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int TOP = 32;
    private static final int TARGET_COLUMN = 1;
    private static final int NOTE_COLUMN = 3;
    private static final String[] SORT_MODES = {"target", "enabled", "note", "kind"};
    private static final String TARGET_HINT = "Type an id; pick a type tab (Auto detects the kind)";
    private static final int SUGGEST_LIMIT = 40;

    private final List<Dropdown> dropdowns = new ArrayList<>();

    private RowList<ListEHidingEntry> list;
    private EditBox inlineEditor;
    private AutocompletePopup autocomplete;
    private EditBox search;
    private Dropdown kindDropdown;
    private Dropdown stateDropdown;
    private Dropdown sortDropdown;
    private Button refreshButton;
    private Button descButton;
    private boolean refreshArmed;
    private boolean descending;

    private ListEHidingEntry editingEntry;
    private int editingColumn = -1;
    private IntentTarget originalTarget;
    private String originalTargetText = "";
    private int originalKindIndex;
    private boolean suggestionsFrozen;
    private long lastClickTime;
    private ListEHidingEntry lastClickRow;
    private int lastClickColumn = -1;
    private PopupMenu menu;
    private int menuX;
    private int menuY;

    private String status = "";
    private long statusUntil;

    public HidingListScreen()
    {
        super(Component.literal("Just Enough Hiding - List"));
    }

    @Override
    protected void init()
    {
        ViewerAdapter adapter = Adapters.active();
        dropdowns.clear();
        refreshArmed = false;

        int buttonX = MARGIN;

        Button newButton = Button.builder(Component.literal("New Entry"), b -> newEntry())
            .tooltip(Tooltip.create(Component.literal("Add a blank entry")))
            .bounds(buttonX, TOP, 76, ROW_HEIGHT).build();
        addRenderableWidget(newButton);
        buttonX += 80;

        Button saveButton = Button.builder(Component.literal("Save"), b -> save())
            .tooltip(Tooltip.create(Component.literal("Write changes to config/jeh/listehiding.json")))
            .bounds(buttonX, TOP, 54, ROW_HEIGHT).build();
        addRenderableWidget(saveButton);
        buttonX += 58;

        refreshButton = Button.builder(Component.literal("Refresh"), b -> onRefreshClicked())
            .tooltip(Tooltip.create(Component.literal("Discard in-memory changes and re-read the file (click twice)")))
            .bounds(buttonX, TOP, 70, ROW_HEIGHT).build();
        addRenderableWidget(refreshButton);
        buttonX += 74;

        Button closeButton = Button.builder(Component.literal("Close"), b -> onClose())
            .tooltip(Tooltip.create(Component.literal("Save and close")))
            .bounds(buttonX, TOP, 54, ROW_HEIGHT).build();
        addRenderableWidget(closeButton);

        int controlsY = TOP + ROW_HEIGHT + 4;

        search = new EditBox(this.font, MARGIN, controlsY, Math.min(200, Math.max(120, this.width / 4)), ROW_HEIGHT, Component.literal("Search"));
        search.setHint(Component.literal("Search"));
        search.setResponder(value -> apply());
        addRenderableWidget(search);

        int dropdownX = search.getX() + search.getWidth() + 6;
        kindDropdown = new Dropdown(this.font, dropdownX, controlsY, 150, ROW_HEIGHT, kindOptions(), "All", value -> apply())
            .tooltip(Component.literal("Filter by target kind"));
        dropdownX += 156;
        stateDropdown = new Dropdown(this.font, dropdownX, controlsY, 110, ROW_HEIGHT, List.of("All", "Enabled", "Disabled"), "All", value -> apply())
            .tooltip(Component.literal("Filter by enabled state"));
        dropdownX += 116;
        sortDropdown = new Dropdown(this.font, dropdownX, controlsY, 130, ROW_HEIGHT, List.of(SORT_MODES), "target", value -> apply())
            .tooltip(Component.literal("Sort order"));
        dropdowns.add(kindDropdown);
        dropdowns.add(stateDropdown);
        dropdowns.add(sortDropdown);
        dropdownX += 136;

        descButton = Button.builder(Component.literal("Desc: off"), b -> toggleDescending())
            .tooltip(Tooltip.create(Component.literal("Reverse the sort order")))
            .bounds(dropdownX, controlsY, 74, ROW_HEIGHT).build();
        addRenderableWidget(descButton);

        int listTop = controlsY + ROW_HEIGHT + 6;
        int listHeight = Math.max(20, this.height - MARGIN - listTop);
        list = new RowList<>(this.font);
        list.setBounds(MARGIN, listTop, this.width - MARGIN * 2, listHeight);
        list.setColumns(List.of(
            Columns.icon(18, (ListEHidingEntry entry) -> adapter.icon(entry.target())),
            Columns.<ListEHidingEntry>flexible(entry -> entry.target().describe(), entry -> 0xFFE0E0E0),
            Columns.<ListEHidingEntry>fixed(64, entry -> entry.enabled() ? "enabled" : "disabled",
                entry -> entry.enabled() ? 0xFF70FF70 : 0xFFFF7070),
            Columns.<ListEHidingEntry>fixed(200, ListEHidingEntry::note, entry -> 0xFFB0B0B0)
        ));
        list.setRightClickListener(this::openMenu);
        addRenderableOnly(list);

        inlineEditor = new EditBox(this.font, 0, 0, 10, ROW_HEIGHT, Component.literal("Edit"));
        inlineEditor.setMaxLength(256);
        inlineEditor.visible = false;
        inlineEditor.setResponder(this::onEditorChanged);
        addRenderableWidget(inlineEditor);

        autocomplete = new AutocompletePopup(this.font, 0, 0, 240, 10);

        apply();
    }

    private List<String> kindOptions()
    {
        TreeSet<String> kinds = new TreeSet<>();
        for (ListEHidingEntry entry : ListEHiding.get().entries())
        {
            kinds.add(entry.target().kind());
        }
        List<String> options = new ArrayList<>();
        options.add("All");
        options.addAll(kinds);
        return options;
    }

    private void apply()
    {
        if (search == null || kindDropdown == null || stateDropdown == null || sortDropdown == null)
        {
            return;
        }

        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        String kind = kindDropdown.getValue();
        String state = stateDropdown.getValue();

        List<ListEHidingEntry> view = new ArrayList<>();
        for (ListEHidingEntry entry : ListEHiding.get().entries())
        {
            if (!"All".equals(kind) && !entry.target().kind().equals(kind))
            {
                continue;
            }
            if ("Enabled".equals(state) && !entry.enabled())
            {
                continue;
            }
            if ("Disabled".equals(state) && entry.enabled())
            {
                continue;
            }
            if (!query.isEmpty())
            {
                String haystack = (entry.target().describe() + " " + entry.target().kind() + " "
                    + (entry.enabled() ? "enabled" : "disabled") + " " + entry.note())
                    .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query))
                {
                    continue;
                }
            }
            view.add(entry);
        }
        Comparator<ListEHidingEntry> comparator = comparator(sortDropdown.getValue());
        if (descending)
        {
            comparator = comparator.reversed();
        }
        view.sort(comparator);
        list.setRows(view);
    }

    private void toggleDescending()
    {
        descending = !descending;
        descButton.setMessage(Component.literal("Desc: " + (descending ? "on" : "off")));
        apply();
    }

    private static Comparator<ListEHidingEntry> comparator(String mode)
    {
        return switch (mode)
        {
            case "enabled" -> Comparator.comparing(ListEHidingEntry::enabled);
            case "note" -> Comparator.comparing(ListEHidingEntry::note, String.CASE_INSENSITIVE_ORDER);
            case "kind" -> Comparator.comparing(entry -> entry.target().kind());
            default -> Comparator.comparing(entry -> entry.target().describe(), String.CASE_INSENSITIVE_ORDER);
        };
    }

    private void newEntry()
    {
        commitEdit();
        disarmRefresh();
        ListEHiding.get().add(ListEHiding.blankEntry());
        kindDropdown.setOptions(kindOptions());
        apply();
    }

    private void save()
    {
        commitEdit();
        disarmRefresh();
        ListEHiding.get().saveIfDirty();
        setStatus("Saved");
    }

    private void onRefreshClicked()
    {
        if (!refreshArmed)
        {
            refreshArmed = true;
            refreshButton.setMessage(Component.literal("Confirm"));
            return;
        }
        refreshArmed = false;
        refreshButton.setMessage(Component.literal("Refresh"));
        commitEdit();
        menu = null;
        ListEHiding.get().reload();
        kindDropdown.setOptions(kindOptions());
        apply();
        setStatus("Reloaded");
    }

    private void disarmRefresh()
    {
        if (refreshArmed)
        {
            refreshArmed = false;
            refreshButton.setMessage(Component.literal("Refresh"));
        }
    }

    private void openMenu(ListEHidingEntry entry, double mouseX, double mouseY)
    {
        commitEdit();
        disarmRefresh();
        menuX = (int) mouseX;
        menuY = (int) mouseY;
        showMainMenu(entry);
    }

    private void showMainMenu(ListEHidingEntry entry)
    {
        List<PopupMenu.Item> items = new ArrayList<>();
        items.add(new PopupMenu.Item(entry.enabled() ? "Disable" : "Enable", 0xFFFFFFFF, () -> toggle(entry)));
        items.add(new PopupMenu.Item("Edit target...", 0xFFFFFFFF, () -> startTargetEdit(entry)));
        items.add(new PopupMenu.Item("Edit note...", 0xFFFFFFFF, () -> startNoteEdit(entry)));
        items.add(new PopupMenu.Item("Delete", 0xFFFF7070, () -> showDeleteConfirm(entry)));
        menu = PopupMenu.at(this.font, menuX, menuY, 190, items);
    }

    private void showDeleteConfirm(ListEHidingEntry entry)
    {
        menu = PopupMenu.at(this.font, menuX, menuY, 190, List.of(
            new PopupMenu.Item("Confirm delete", 0xFFFF3030, () -> delete(entry))));
    }

    private void toggle(ListEHidingEntry entry)
    {
        int index = ListEHiding.get().indexOf(entry);
        ListEHiding.get().set(index, new ListEHidingEntry(entry.target(), !entry.enabled(), entry.note()));
        apply();
    }

    private void delete(ListEHidingEntry entry)
    {
        int index = ListEHiding.get().indexOf(entry);
        ListEHiding.get().remove(index);
        kindDropdown.setOptions(kindOptions());
        apply();
    }

    private void startNoteEdit(ListEHidingEntry entry)
    {
        startEdit(entry, NOTE_COLUMN, entry.note());
    }

    private void startTargetEdit(ListEHidingEntry entry)
    {
        IntentTarget target = entry.target();
        String id = targetId(target);
        int kindIndex = kindIndexOf(target);
        startEdit(entry, TARGET_COLUMN, id);
        autocomplete.setKindIndex(kindIndex);
        originalTarget = target;
        originalTargetText = id;
        originalKindIndex = kindIndex;
        refreshSuggestions(id);
        setStatus(TARGET_HINT);
    }

    private void onEditorChanged(String text)
    {
        if (autocomplete == null)
        {
            return;
        }
        if (editingColumn != TARGET_COLUMN)
        {
            autocomplete.setActive(false);
            inlineEditor.setSuggestion("");
            return;
        }
        autocomplete.setActive(true);
        if (suggestionsFrozen)
        {
            updateGhostText(text);
            return;
        }
        refreshSuggestions(text);
    }

    private void refreshSuggestions(String text)
    {
        autocomplete.setSuggestions(Adapters.active().suggest(text, autocomplete.getKindKey(), SUGGEST_LIMIT));
        positionAutocomplete();
        updateGhostText(text);
    }

    private void updateGhostText(String text)
    {
        if (text == null || text.isEmpty())
        {
            inlineEditor.setSuggestion("");
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (TargetSuggestion suggestion : autocomplete.getSuggestions())
        {
            String completion = suggestion.completion();
            if (completion != null && completion.length() > text.length()
                && completion.toLowerCase(Locale.ROOT).startsWith(lower))
            {
                inlineEditor.setSuggestion(completion.substring(text.length()));
                return;
            }
        }
        inlineEditor.setSuggestion("");
    }

    private void completeHighlighted()
    {
        TargetSuggestion suggestion = autocomplete.getHighlighted();
        if (suggestion == null)
        {
            return;
        }
        String completion = suggestion.completion();
        suggestionsFrozen = true;
        inlineEditor.setValue(completion);
        inlineEditor.setCursorPosition(completion.length());
        suggestionsFrozen = false;
        autocomplete.moveHighlight(1);
        updateGhostText(completion);
    }

    private void positionAutocomplete()
    {
        if (autocomplete == null)
        {
            return;
        }
        int width = Math.max(inlineEditor.getWidth(), 240);
        int maxX = Math.max(MARGIN, this.width - MARGIN - width);
        int x = Math.min(Math.max(inlineEditor.getX(), MARGIN), maxX);
        int height = autocomplete.getHeight();
        int y = inlineEditor.getY() + ROW_HEIGHT;
        if (y + height > this.height - 4)
        {
            y = Math.max(4, inlineEditor.getY() - height);
        }
        autocomplete.setWidth(width);
        autocomplete.setPosition(x, y);
    }

    private void acceptSuggestion(TargetSuggestion suggestion)
    {
        if (suggestion == null || editingEntry == null)
        {
            commitEdit();
            return;
        }
        ListEHidingEntry entry = editingEntry;
        editingEntry = null;
        editingColumn = -1;
        inlineEditor.visible = false;
        inlineEditor.setSuggestion("");
        autocomplete.setActive(false);
        setFocused(null);
        replace(entry, new ListEHidingEntry(suggestion.target(), entry.enabled(), entry.note()));
    }

    private boolean handleDoubleClick(double mouseX, double mouseY)
    {
        ListEHidingEntry row = list.rowAt((int) mouseX, (int) mouseY);
        if (row == null)
        {
            lastClickRow = null;
            lastClickColumn = -1;
            return false;
        }
        int column = list.columnAt((int) mouseX);
        long now = System.currentTimeMillis();
        boolean doubleClick = row == lastClickRow && column == lastClickColumn
            && now - lastClickTime <= 300L
            && (column == TARGET_COLUMN || column == NOTE_COLUMN);

        lastClickRow = row;
        lastClickColumn = column;
        lastClickTime = now;

        if (!doubleClick)
        {
            return false;
        }
        lastClickRow = null;
        lastClickColumn = -1;
        if (column == TARGET_COLUMN)
        {
            startTargetEdit(row);
        }
        else
        {
            startNoteEdit(row);
        }
        return true;
    }

    private void startEdit(ListEHidingEntry entry, int column, String initialValue)
    {
        int viewIndex = list.indexOf(entry);
        Rect2i cell = list.cellBounds(viewIndex, column);
        if (cell == null)
        {
            return;
        }
        editingEntry = entry;
        editingColumn = column;
        inlineEditor.setX(cell.getX());
        inlineEditor.setY(cell.getY());
        inlineEditor.setWidth(cell.getWidth());
        inlineEditor.setHeight(ROW_HEIGHT);
        inlineEditor.setValue(initialValue);
        inlineEditor.visible = true;
        setFocused(inlineEditor);
    }

    private void commitEdit()
    {
        if (editingEntry == null)
        {
            return;
        }
        ListEHidingEntry entry = editingEntry;
        int column = editingColumn;
        editingEntry = null;
        editingColumn = -1;
        inlineEditor.visible = false;
        inlineEditor.setSuggestion("");
        autocomplete.setActive(false);
        setFocused(null);

        String text = inlineEditor.getValue();
        if (column == TARGET_COLUMN)
        {
            String id = text.trim();
            int kindIndex = autocomplete.getKindIndex();
            if (originalTarget != null && id.equals(originalTargetText) && kindIndex == originalKindIndex)
            {
                return;
            }
            IntentTarget parsed = parseTarget(id, kindIndex);
            if (parsed == null)
            {
                setStatus("Unknown target: " + id);
                return;
            }
            replace(entry, new ListEHidingEntry(parsed, entry.enabled(), entry.note()));
        }
        else if (!text.equals(entry.note()))
        {
            replace(entry, new ListEHidingEntry(entry.target(), entry.enabled(), text));
        }
    }

    private void cancelEdit()
    {
        editingEntry = null;
        editingColumn = -1;
        originalTarget = null;
        inlineEditor.visible = false;
        inlineEditor.setSuggestion("");
        autocomplete.setActive(false);
        setFocused(null);
    }

    private void replace(ListEHidingEntry entry, ListEHidingEntry replacement)
    {
        int index = ListEHiding.get().indexOf(entry);
        ListEHiding.get().set(index, replacement);
        kindDropdown.setOptions(kindOptions());
        apply();
    }

    private static String targetId(IntentTarget target)
    {
        if (target instanceof IntentTarget.Recipe recipe)
        {
            return recipe.recipeId();
        }
        if (target instanceof IntentTarget.RecipeCategory category)
        {
            return category.recipeType().toString();
        }
        if (target instanceof IntentTarget.Ingredient ingredient)
        {
            return ingredient.key().uid();
        }
        return "";
    }

    private static int kindIndexOf(IntentTarget target)
    {
        return switch (target.kind())
        {
            case "ingredient" -> 1;
            case "recipe" -> 2;
            case "recipe_category" -> 3;
            default -> 0;
        };
    }

    private static IntentTarget parseTarget(String id, int kindIndex)
    {
        if (id.isEmpty())
        {
            return IntentTarget.unset();
        }
        return Adapters.active().ofKind(kindKey(kindIndex), id);
    }

    private static String kindKey(int kindIndex)
    {
        return switch (kindIndex)
        {
            case 1 -> "ingredient";
            case 2 -> "recipe";
            case 3 -> "recipe_category";
            default -> "";
        };
    }

    private void setStatus(String message)
    {
        this.status = message;
        this.statusUntil = System.currentTimeMillis() + 5000L;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        Dropdown openDropdown = openDropdown();
        list.setHoverEnabled(openDropdown == null && (autocomplete == null || !autocomplete.isVisible()));

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        for (Dropdown dropdown : dropdowns)
        {
            if (dropdown != openDropdown)
            {
                dropdown.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
        if (openDropdown != null)
        {
            openDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (autocomplete != null && autocomplete.isVisible())
        {
            autocomplete.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.drawString(this.font, this.title, MARGIN, 8, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, listSize() + " entries", MARGIN, 19, 0xFFA0A0A0, false);
        if (System.currentTimeMillis() < this.statusUntil)
        {
            guiGraphics.drawString(this.font, this.status, this.width - MARGIN - this.font.width(this.status), 8, 0xFFFFE080, false);
        }

        if (menu != null)
        {
            menu.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        else if (openDropdown == null && (autocomplete == null || !autocomplete.isVisible()))
        {
            if (!drawDropdownTooltip(mouseX, mouseY))
            {
                drawTooltip(guiGraphics, mouseX, mouseY);
            }
        }
    }

    private boolean drawDropdownTooltip(int mouseX, int mouseY)
    {
        for (Dropdown dropdown : dropdowns)
        {
            if (dropdown.isOverButton(mouseX, mouseY) && dropdown.getTooltip() != null)
            {
                this.setTooltipForNextRenderPass(List.of(dropdown.getTooltip().getVisualOrderText()));
                return true;
            }
        }
        return false;
    }

    private int listSize()
    {
        return ListEHiding.get().entries().size();
    }

    private Dropdown openDropdown()
    {
        for (Dropdown dropdown : dropdowns)
        {
            if (dropdown.isOpen())
            {
                return dropdown;
            }
        }
        return null;
    }

    private void drawTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        ListEHidingEntry hovered = list.rowAt(mouseX, mouseY);
        if (hovered == null)
        {
            return;
        }

        IconRenderer icon = Adapters.active().icon(hovered.target());
        if (!icon.isEmpty())
        {
            icon.renderTooltip(guiGraphics, this.font, mouseX, mouseY);
            return;
        }

        this.setTooltipForNextRenderPass(List.of(
            Component.literal("target: " + hovered.target().describe()),
            Component.literal("enabled: " + hovered.enabled()),
            Component.literal("note: " + hovered.note())
        ).stream().map(Component::getVisualOrderText).toList());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (menu != null)
        {
            PopupMenu current = menu;
            menu = null;
            PopupMenu.Item item = current.itemAt(mouseX, mouseY);
            if (item != null)
            {
                item.action().run();
            }
            return true;
        }

        if (autocomplete != null && autocomplete.isVisible())
        {
            int tab = autocomplete.tabAt(mouseX, mouseY);
            if (tab >= 0)
            {
                autocomplete.setKindIndex(tab);
                refreshSuggestions(inlineEditor.getValue());
                return true;
            }
            TargetSuggestion picked = autocomplete.mouseClicked(mouseX, mouseY);
            if (picked != null)
            {
                acceptSuggestion(picked);
                return true;
            }
            if (autocomplete.isOver(mouseX, mouseY))
            {
                return true;
            }
        }

        if (editingEntry != null && !isOver(inlineEditor, mouseX, mouseY))
        {
            commitEdit();
        }

        for (Dropdown dropdown : dropdowns)
        {
            if (dropdown.isOpen())
            {
                dropdown.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        for (Dropdown dropdown : dropdowns)
        {
            if (dropdown.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
        }

        if (button == 0 && handleDoubleClick(mouseX, mouseY))
        {
            return true;
        }

        if (list.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && getFocused() instanceof Button)
        {
            setFocused(null);
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (editingEntry != null)
        {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE)
            {
                cancelEdit();
                return true;
            }
            boolean hasSuggestions = autocomplete != null && autocomplete.hasSuggestions();
            if (hasSuggestions && keyCode == GLFW.GLFW_KEY_DOWN)
            {
                autocomplete.moveHighlight(1);
                return true;
            }
            if (hasSuggestions && keyCode == GLFW.GLFW_KEY_UP)
            {
                autocomplete.moveHighlight(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB)
            {
                if (hasSuggestions)
                {
                    completeHighlighted();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            {
                if (hasSuggestions)
                {
                    acceptSuggestion(autocomplete.getHighlighted());
                }
                else
                {
                    commitEdit();
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (editingEntry != null)
        {
            if (autocomplete != null && autocomplete.mouseScrolled(mouseX, mouseY, delta))
            {
                return true;
            }
            return true;
        }
        if (autocomplete != null && autocomplete.isVisible()
            && autocomplete.mouseScrolled(mouseX, mouseY, delta))
        {
            return true;
        }
        for (Dropdown dropdown : dropdowns)
        {
            if (dropdown.isOpen() && dropdown.mouseScrolled(mouseX, mouseY, delta))
            {
                return true;
            }
        }
        for (Dropdown dropdown : dropdowns)
        {
            dropdown.close();
        }
        if (menu != null)
        {
            menu = null;
            return true;
        }
        if (list.mouseScrolled(mouseX, mouseY, delta))
        {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        list.mouseDragged(mouseX, mouseY, button);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (list.mouseReleased(mouseX, mouseY, button))
        {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose()
    {
        commitEdit();
        ListEHiding.get().saveIfDirty();
        super.onClose();
    }

    private static boolean isOver(AbstractWidget widget, double mouseX, double mouseY)
    {
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
            && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }
}

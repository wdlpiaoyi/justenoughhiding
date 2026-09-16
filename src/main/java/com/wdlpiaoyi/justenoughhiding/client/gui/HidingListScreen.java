package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Columns;
import com.wdlpiaoyi.justenoughhiding.client.gui.widget.PopupMenu;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter;
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
import java.util.List;

public final class HidingListScreen extends Screen
{
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int TOP = 32;
    private static final int NOTE_COLUMN = 3;

    private RowList<ListEHidingEntry> list;
    private EditBox noteEditor;
    private ListEHidingEntry editingEntry;
    private PopupMenu menu;
    private int menuX;
    private int menuY;

    public HidingListScreen()
    {
        super(Component.literal("Just Enough Hiding - List"));
    }

    @Override
    protected void init()
    {
        ViewerAdapter adapter = Adapters.active();

        int buttonX = MARGIN;

        Button newButton = Button.builder(Component.literal("New Entry"), b -> newEntry())
            .tooltip(Tooltip.create(Component.literal("Add a blank entry")))
            .bounds(buttonX, TOP, 76, ROW_HEIGHT).build();
        addRenderableWidget(newButton);
        buttonX += 80;

        Button refreshButton = Button.builder(Component.literal("Refresh"), b -> refresh())
            .tooltip(Tooltip.create(Component.literal("Discard in-memory changes and re-read config/jeh/listehiding.json")))
            .bounds(buttonX, TOP, 60, ROW_HEIGHT).build();
        addRenderableWidget(refreshButton);
        buttonX += 64;

        Button closeButton = Button.builder(Component.literal("Close"), b -> onClose())
            .tooltip(Tooltip.create(Component.literal("Save and close")))
            .bounds(buttonX, TOP, 54, ROW_HEIGHT).build();
        addRenderableWidget(closeButton);

        int listTop = TOP + ROW_HEIGHT + 6;
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

        noteEditor = new EditBox(this.font, 0, 0, 10, ROW_HEIGHT, Component.literal("Note"));
        noteEditor.setMaxLength(256);
        noteEditor.visible = false;
        addRenderableWidget(noteEditor);

        refreshList();
    }

    private void newEntry()
    {
        commitNoteEdit();
        ListEHiding.get().add(ListEHiding.blankEntry());
        refreshList();
    }

    private void refreshList()
    {
        list.setRows(ListEHiding.get().entries());
    }

    private void refresh()
    {
        commitNoteEdit();
        menu = null;
        ListEHiding.get().reload();
        refreshList();
    }

    private void openMenu(ListEHidingEntry entry, double mouseX, double mouseY)
    {
        commitNoteEdit();
        menuX = (int) mouseX;
        menuY = (int) mouseY;
        showMainMenu(entry);
    }

    private void showMainMenu(ListEHidingEntry entry)
    {
        List<PopupMenu.Item> items = new ArrayList<>();
        items.add(new PopupMenu.Item(entry.enabled() ? "Disable" : "Enable", 0xFFFFFFFF, () -> toggle(entry)));
        items.add(new PopupMenu.Item("Edit note...", 0xFFFFFFFF, () -> startNoteEdit(entry)));
        items.add(new PopupMenu.Item("Edit target... (not implemented)", 0xFF808080, () -> { }));
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
        refreshList();
    }

    private void delete(ListEHidingEntry entry)
    {
        int index = ListEHiding.get().indexOf(entry);
        ListEHiding.get().remove(index);
        refreshList();
    }

    private void startNoteEdit(ListEHidingEntry entry)
    {
        int index = ListEHiding.get().indexOf(entry);
        Rect2i cell = list.cellBounds(index, NOTE_COLUMN);
        if (cell == null)
        {
            return;
        }
        editingEntry = entry;
        noteEditor.setX(cell.getX());
        noteEditor.setY(cell.getY());
        noteEditor.setWidth(cell.getWidth());
        noteEditor.setHeight(ROW_HEIGHT);
        noteEditor.setValue(entry.note());
        noteEditor.visible = true;
        setFocused(noteEditor);
    }

    private void commitNoteEdit()
    {
        if (editingEntry == null)
        {
            return;
        }
        ListEHidingEntry entry = editingEntry;
        editingEntry = null;
        noteEditor.visible = false;
        setFocused(null);

        String text = noteEditor.getValue();
        if (!text.equals(entry.note()))
        {
            int index = ListEHiding.get().indexOf(entry);
            ListEHiding.get().set(index, new ListEHidingEntry(entry.target(), entry.enabled(), text));
            refreshList();
        }
    }

    private void cancelNoteEdit()
    {
        editingEntry = null;
        noteEditor.visible = false;
        setFocused(null);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, this.title, MARGIN, 8, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, ListEHiding.get().entries().size() + " entries", MARGIN, 19, 0xFFA0A0A0, false);

        if (menu != null)
        {
            menu.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        else
        {
            drawTooltip(guiGraphics, mouseX, mouseY);
        }
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

        if (editingEntry != null && !isOver(noteEditor, mouseX, mouseY))
        {
            commitNoteEdit();
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
                cancelNoteEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            {
                commitNoteEdit();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
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
        commitNoteEdit();
        ListEHiding.get().saveIfDirty();
        super.onClose();
    }

    private static boolean isOver(AbstractWidget widget, double mouseX, double mouseY)
    {
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
            && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }
}

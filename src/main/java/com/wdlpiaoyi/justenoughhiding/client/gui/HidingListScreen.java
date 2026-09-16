package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Columns;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHiding;
import com.wdlpiaoyi.justenoughhiding.listehiding.ListEHidingEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class HidingListScreen extends Screen
{
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int TOP = 32;

    private RowList<ListEHidingEntry> list;
    private int lastMouseX;
    private int lastMouseY;

    public HidingListScreen()
    {
        super(Component.literal("Just Enough Hiding - List"));
    }

    @Override
    protected void init()
    {
        ViewerAdapter adapter = Adapters.active();

        Button refreshButton = Button.builder(Component.literal("Refresh"), b -> refresh())
            .tooltip(Tooltip.create(Component.literal("Re-read config/justenoughhiding/listehiding.json")))
            .bounds(MARGIN, TOP, 60, ROW_HEIGHT).build();
        addRenderableWidget(refreshButton);

        Button closeButton = Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(MARGIN + 64, TOP, 54, ROW_HEIGHT).build();
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
        addRenderableOnly(list);

        refresh();
    }

    private void refresh()
    {
        ListEHiding.get().reload();
        list.setRows(ListEHiding.get().entries());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(this.font, this.title, MARGIN, 8, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, ListEHiding.get().entries().size() + " entries", MARGIN, 19, 0xFFA0A0A0, false);

        drawTooltip(guiGraphics, mouseX, mouseY);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
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
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

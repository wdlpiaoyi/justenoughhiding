package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.client.IntentFeed;
import com.wdlpiaoyi.justenoughhiding.client.gui.widget.Dropdown;
import com.wdlpiaoyi.justenoughhiding.client.viewer.Adapters;
import com.wdlpiaoyi.justenoughhiding.client.viewer.IconRenderer;
import com.wdlpiaoyi.justenoughhiding.client.viewer.ViewerAdapter;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Function;

public final class IntentScreen extends Screen
{
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int TOP = 32;

    private final List<Intent> all = new ArrayList<>();
    private final List<Intent> view = new ArrayList<>();
    private final List<Dropdown> dropdowns = new ArrayList<>();

    private EditBox search;
    private Button refreshButton;
    private Button exportViewButton;
    private Button exportAllButton;
    private Button copyUidButton;
    private Button copyLineButton;
    private Dropdown sourceDropdown;
    private Dropdown kindDropdown;
    private Dropdown sortDropdown;
    private Button descButton;
    private boolean descending;
    private RowList<Intent> list;

    private Component status = Component.empty();
    private long statusUntil;
    private int lastMouseX;
    private int lastMouseY;

    public IntentScreen()
    {
        super(Component.translatable("jeh.screen.intents"));
    }

    @Override
    protected void init()
    {
        dropdowns.clear();
        IntentFeed.ensureRegistered();
        all.clear();
        all.addAll(IntentRegistry.query().all());
        IntentFeed.clear();

        int searchWidth = Math.min(220, Math.max(120, this.width / 3));
        search = new EditBox(this.font, MARGIN, TOP, searchWidth, ROW_HEIGHT, Component.translatable("jeh.search.default"));
        search.setResponder(value -> apply());
        addRenderableWidget(search);

        int buttonX = search.getX() + search.getWidth() + 6;

        refreshButton = Button.builder(Component.translatable("jeh.button.refresh"), b -> refresh())
            .tooltip(Tooltip.create(Component.translatable("jeh.tooltip.refresh_intents")))
            .bounds(buttonX, TOP, 60, ROW_HEIGHT).build();
        addRenderableWidget(refreshButton);
        buttonX += 64;

        exportViewButton = Button.builder(Component.translatable("jeh.button.export_view"), b -> exportView())
            .tooltip(Tooltip.create(Component.translatable("jeh.tooltip.export_view")))
            .bounds(buttonX, TOP, 90, ROW_HEIGHT).build();
        addRenderableWidget(exportViewButton);
        buttonX += 94;

        exportAllButton = Button.builder(Component.translatable("jeh.button.export_all"), b -> exportAll())
            .tooltip(Tooltip.create(Component.translatable("jeh.tooltip.export_all")))
            .bounds(buttonX, TOP, 84, ROW_HEIGHT).build();
        addRenderableWidget(exportAllButton);
        buttonX += 88;

        copyUidButton = Button.builder(Component.translatable("jeh.button.copy_uid"), b -> copy(true))
            .tooltip(Tooltip.create(Component.translatable("jeh.tooltip.copy_uid")))
            .bounds(buttonX, TOP, 74, ROW_HEIGHT).build();
        addRenderableWidget(copyUidButton);
        buttonX += 78;

        copyLineButton = Button.builder(Component.translatable("jeh.button.copy_line"), b -> copy(false))
            .tooltip(Tooltip.create(Component.translatable("jeh.tooltip.copy_line")))
            .bounds(buttonX, TOP, 78, ROW_HEIGHT).build();
        addRenderableWidget(copyLineButton);

        int dropdownY = TOP + ROW_HEIGHT + 4;
        List<String> sortOptions = JehConfig.sortModes();
        Function<String, String> allLabeler = value -> "All".equals(value)
            ? Component.translatable("jeh.filter.all").getString() : value;
        sourceDropdown = new Dropdown(this.font, MARGIN, dropdownY, 150, ROW_HEIGHT,
            optionList(intent -> intent.source().id()), "All", value -> apply())
            .tooltip(Component.translatable("jeh.tooltip.filter_source"))
            .labels(allLabeler);
        kindDropdown = new Dropdown(this.font, MARGIN + 156, dropdownY, 160, ROW_HEIGHT,
            optionList(intent -> intent.kind().name()), "All", value -> apply())
            .tooltip(Component.translatable("jeh.tooltip.filter_kind"))
            .labels(allLabeler);
        sortDropdown = new Dropdown(this.font, MARGIN + 322, dropdownY, 170, ROW_HEIGHT,
            sortOptions, sortOptions.get(0), value -> apply())
            .tooltip(Component.translatable("jeh.tooltip.sort"));
        dropdowns.add(sourceDropdown);
        dropdowns.add(kindDropdown);
        dropdowns.add(sortDropdown);

        Button closeButton = Button.builder(Component.translatable("jeh.button.close"), b -> onClose())
            .bounds(sortDropdown.getX() + 176 + 78, dropdownY, 54, ROW_HEIGHT).build();
        addRenderableWidget(closeButton);

        descButton = Button.builder(descMessage(), b -> toggleDescending())
            .tooltip(Tooltip.create(Component.translatable("jeh.tooltip.desc")))
            .bounds(sortDropdown.getX() + 176, dropdownY, 74, ROW_HEIGHT).build();
        addRenderableWidget(descButton);

        int listTop = dropdownY + ROW_HEIGHT + 6;
        int listHeight = Math.max(20, this.height - MARGIN - listTop);
        list = new RowList<>(this.font);
        list.setBounds(MARGIN, listTop, this.width - MARGIN * 2, listHeight);
        list.setColumns(Adapters.active().columns());
        addRenderableOnly(list);

        apply();
    }

    private List<String> optionList(Function<Intent, String> extractor)
    {
        TreeSet<String> values = new TreeSet<>();
        for (Intent intent : all)
        {
            values.add(extractor.apply(intent));
        }
        List<String> options = new ArrayList<>();
        options.add("All");
        options.addAll(values);
        return options;
    }

    private void apply()
    {
        if (sourceDropdown == null || kindDropdown == null || sortDropdown == null)
        {
            return;
        }

        String source = sourceDropdown.getValue();
        String kind = kindDropdown.getValue();
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);

        List<Intent> filtered = new ArrayList<>();
        for (Intent intent : all)
        {
            if (!"All".equals(source) && !intent.source().id().equals(source))
            {
                continue;
            }
            if (!"All".equals(kind) && !intent.kind().name().equals(kind))
            {
                continue;
            }
            if (!query.isEmpty())
            {
                String haystack = (intent.kind().name() + " " + intent.source().id() + " "
                    + intent.target().describe() + " " + intent.target().typeUid())
                    .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query))
                {
                    continue;
                }
            }
            filtered.add(intent);
        }
        filtered.sort(comparator());

        view.clear();
        view.addAll(filtered);
        list.setRows(view);
        updateButtons();
    }

    private Comparator<Intent> comparator()
    {
        Comparator<Intent> comparator = IntentSort.comparator(sortDropdown.getValue());
        return descending ? comparator.reversed() : comparator;
    }

    private void toggleDescending()
    {
        descending = !descending;
        descButton.setMessage(descMessage());
        apply();
    }

    private Component descMessage()
    {
        return Component.translatable("jeh.button.desc",
            Component.translatable(descending ? "jeh.value.on" : "jeh.value.off"));
    }

    private void refresh()
    {
        all.clear();
        all.addAll(IntentRegistry.query().all());
        IntentFeed.clear();
        sourceDropdown.setOptions(optionList(intent -> intent.source().id()));
        kindDropdown.setOptions(optionList(intent -> intent.kind().name()));
        apply();
        setStatus(Component.translatable("jeh.status.refreshed"));
    }

    private void updateButtons()
    {
        boolean hasSelection = list != null && list.getSelected() != null;
        if (copyUidButton != null)
        {
            copyUidButton.active = hasSelection;
        }
        if (copyLineButton != null)
        {
            copyLineButton.active = hasSelection;
        }
    }

    private void exportView()
    {
        Path path = IntentExporter.export(view);
        setStatus(path == null
            ? Component.translatable("jeh.status.export_failed")
            : Component.translatable("jeh.status.exported", view.size(), path.toString()));
    }

    private void exportAll()
    {
        List<Intent> sorted = new ArrayList<>(all);
        sorted.sort(comparator());
        Path path = IntentExporter.export(sorted);
        setStatus(path == null
            ? Component.translatable("jeh.status.export_failed")
            : Component.translatable("jeh.status.exported", sorted.size(), path.toString()));
    }

    private void copy(boolean uidOnly)
    {
        Intent selected = list.getSelected();
        if (selected == null)
        {
            return;
        }
        String text = uidOnly ? selected.target().copyText() : IntentFormat.line(selected);
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        setStatus(Component.translatable("jeh.status.copied", text));
    }

    private void setStatus(Component message)
    {
        this.status = message;
        this.statusUntil = System.currentTimeMillis() + 5000L;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        Dropdown openDropdown = openDropdown();
        list.setHoverEnabled(openDropdown == null);

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

        drawSearchHint(guiGraphics);
        drawHeader(guiGraphics);
        drawTooltips(guiGraphics, mouseX, mouseY);
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

    private void drawSearchHint(GuiGraphics guiGraphics)
    {
        if (!search.getValue().isEmpty())
        {
            return;
        }
        guiGraphics.drawString(this.font, Component.translatable("jeh.search.intents_hint"), search.getX() + 4, search.getY() + (search.getHeight() - 8) / 2, 0xFF808080, false);
    }

    private void drawHeader(GuiGraphics guiGraphics)
    {
        guiGraphics.drawString(this.font, this.title, MARGIN, 8, 0xFFFFFFFF, true);

        Component summary = Component.translatable("jeh.summary.intents",
            all.size(), view.size(), IntentRegistry.query().currentlyHidden().size());
        guiGraphics.drawString(this.font, summary, MARGIN, 19, 0xFFA0A0A0, false);

        int pending = IntentFeed.pending();
        if (refreshButton != null)
        {
            refreshButton.setMessage(pending > 0
                ? Component.translatable("jeh.button.refresh_pending", pending)
                : Component.translatable("jeh.button.refresh"));
        }
        if (pending > 0)
        {
            Component badge = Component.translatable("jeh.badge.new", pending);
            guiGraphics.drawString(this.font, badge, this.width - MARGIN - this.font.width(badge), 19, 0xFFFFB060, false);
        }

        if (System.currentTimeMillis() < this.statusUntil)
        {
            guiGraphics.drawString(this.font, this.status, this.width - MARGIN - this.font.width(this.status), 8, 0xFFFFE080, false);
        }
    }

    private void drawTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        if (openDropdown() != null)
        {
            return;
        }

        for (Dropdown dropdown : dropdowns)
        {
            if (dropdown.isOverButton(mouseX, mouseY) && dropdown.getTooltip() != null)
            {
                this.setTooltipForNextRenderPass(List.of(dropdown.getTooltip().getVisualOrderText()));
                return;
            }
        }

        Intent hovered = list.rowAt(mouseX, mouseY);
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
            Component.literal(hovered.kind().name()),
            Component.translatable("jeh.tip.source", hovered.source().id()),
            Component.translatable("jeh.tip.target", hovered.target().describe()),
            Component.translatable("jeh.tip.count", hovered.count())
        ).stream().map(Component::getVisualOrderText).toList());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (!isOver(search, mouseX, mouseY))
        {
            setFocused(null);
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
        if (list.mouseClicked(mouseX, mouseY, button))
        {
            updateButtons();
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
        list.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (addBookmarkUnderMouse(keyCode, scanCode))
        {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean addBookmarkUnderMouse(int keyCode, int scanCode)
    {
        ViewerAdapter adapter = Adapters.active();
        if (!adapter.isBookmarkKey(keyCode, scanCode))
        {
            return false;
        }

        Intent hovered = JehConfig.bookmarkTarget() == JehConfig.BookmarkTarget.ROW
            ? list.rowAt(lastMouseX, lastMouseY)
            : list.rowAtIcon(lastMouseX, lastMouseY);
        if (hovered == null || !adapter.bookmark(hovered.target()))
        {
            return false;
        }
        setStatus(Component.translatable("jeh.status.bookmarked", hovered.target().describe()));
        return true;
    }

    private static boolean isOver(AbstractWidget widget, double mouseX, double mouseY)
    {
        return mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
            && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }
}

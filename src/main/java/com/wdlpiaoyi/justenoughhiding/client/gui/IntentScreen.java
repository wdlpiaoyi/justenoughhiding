package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.client.gui.widget.Dropdown;
import com.wdlpiaoyi.justenoughhiding.config.JehConfig;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import com.wdlpiaoyi.justenoughhiding.intent.IntentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
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
    private IntentList list;

    private String status = "";
    private long statusUntil;

    public IntentScreen()
    {
        super(Component.literal("Just Enough Hiding - Intents"));
    }

    @Override
    protected void init()
    {
        dropdowns.clear();
        all.clear();
        all.addAll(IntentRegistry.query().all());

        search = new EditBox(this.font, MARGIN, TOP, Math.min(220, Math.max(120, this.width / 3)), ROW_HEIGHT, Component.literal("Search"));
        search.setResponder(value -> apply());
        addRenderableWidget(search);

        List<String> sortOptions = JehConfig.sortModes();
        sourceDropdown = new Dropdown(this.font, search.getX() + search.getWidth() + 6, TOP, 150, ROW_HEIGHT,
            optionList(intent -> intent.source().id()), "All", value -> apply());
        kindDropdown = new Dropdown(this.font, sourceDropdown.getX() + 156, TOP, 160, ROW_HEIGHT,
            optionList(intent -> intent.kind().name()), "All", value -> apply());
        dropdowns.add(sourceDropdown);
        dropdowns.add(kindDropdown);

        int buttonY = TOP + ROW_HEIGHT + 4;
        int buttonX = MARGIN;

        refreshButton = Button.builder(Component.literal("Refresh"), b -> refresh()).bounds(buttonX, buttonY, 60, ROW_HEIGHT).build();
        addRenderableWidget(refreshButton);
        buttonX += 64;

        sortDropdown = new Dropdown(this.font, buttonX, buttonY, 170, ROW_HEIGHT, sortOptions, sortOptions.get(0), value -> apply());
        dropdowns.add(sortDropdown);
        buttonX += 176;

        exportViewButton = Button.builder(Component.literal("Export View"), b -> exportView()).bounds(buttonX, buttonY, 90, ROW_HEIGHT).build();
        addRenderableWidget(exportViewButton);
        buttonX += 94;

        exportAllButton = Button.builder(Component.literal("Export All"), b -> exportAll()).bounds(buttonX, buttonY, 84, ROW_HEIGHT).build();
        addRenderableWidget(exportAllButton);
        buttonX += 88;

        copyUidButton = Button.builder(Component.literal("Copy UID"), b -> copy(true)).bounds(buttonX, buttonY, 74, ROW_HEIGHT).build();
        addRenderableWidget(copyUidButton);
        buttonX += 78;

        copyLineButton = Button.builder(Component.literal("Copy Line"), b -> copy(false)).bounds(buttonX, buttonY, 78, ROW_HEIGHT).build();
        addRenderableWidget(copyLineButton);
        buttonX += 82;

        Button closeButton = Button.builder(Component.literal("Close"), b -> onClose()).bounds(buttonX, buttonY, 54, ROW_HEIGHT).build();
        addRenderableWidget(closeButton);

        int listTop = buttonY + ROW_HEIGHT + 6;
        int listHeight = Math.max(20, this.height - MARGIN - listTop);
        list = new IntentList(this.font);
        list.setBounds(MARGIN, listTop, this.width - MARGIN * 2, listHeight);
        addRenderableOnly(list);

        for (Dropdown dropdown : dropdowns)
        {
            addRenderableOnly(dropdown);
        }

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
                String haystack = (intent.kind().name() + " " + intent.source().id() + " " + IntentFormat.targetText(intent.target()))
                    .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query))
                {
                    continue;
                }
            }
            filtered.add(intent);
        }
        filtered.sort(IntentSort.comparator(sortDropdown.getValue()));

        view.clear();
        view.addAll(filtered);
        list.setRows(view);
        updateButtons();
    }

    private void refresh()
    {
        all.clear();
        all.addAll(IntentRegistry.query().all());
        sourceDropdown.setOptions(optionList(intent -> intent.source().id()));
        kindDropdown.setOptions(optionList(intent -> intent.kind().name()));
        apply();
        setStatus("Refreshed");
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
        setStatus(path == null ? "Export failed" : "Exported " + view.size() + " -> " + path);
    }

    private void exportAll()
    {
        List<Intent> sorted = new ArrayList<>(all);
        sorted.sort(IntentSort.comparator(sortDropdown.getValue()));
        Path path = IntentExporter.export(sorted);
        setStatus(path == null ? "Export failed" : "Exported " + sorted.size() + " -> " + path);
    }

    private void copy(boolean uidOnly)
    {
        Intent selected = list.getSelected();
        if (selected == null)
        {
            return;
        }
        String text = uidOnly ? IntentFormat.uid(selected) : IntentFormat.line(selected);
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        setStatus("Copied: " + text);
    }

    private void setStatus(String message)
    {
        this.status = message;
        this.statusUntil = System.currentTimeMillis() + 5000L;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawHeader(guiGraphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics guiGraphics, int mouseX, int mouseY)
    {
        guiGraphics.drawString(this.font, this.title, MARGIN, 8, 0xFFFFFFFF, true);

        String summary = all.size() + " entries, " + view.size() + " shown, "
            + IntentRegistry.query().currentlyHidden().size() + " hidden";
        guiGraphics.drawString(this.font, summary, MARGIN, 19, 0xFFA0A0A0, false);

        if (System.currentTimeMillis() < this.statusUntil)
        {
            guiGraphics.drawString(this.font, this.status, this.width - MARGIN - this.font.width(this.status), 8, 0xFFFFE080, false);
        }

        Intent hovered = list.intentAt(mouseX, mouseY);
        if (hovered != null)
        {
            this.setTooltipForNextRenderPass(List.of(
                Component.literal(hovered.kind().name()),
                Component.literal("source: " + hovered.source().id()),
                Component.literal("target: " + IntentFormat.targetText(hovered.target())),
                Component.literal("count: " + hovered.count())
            ).stream().map(Component::getVisualOrderText).toList());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
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
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

package com.wdlpiaoyi.justenoughhiding.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PopupMenu implements Renderable
{
    private static final int ITEM_HEIGHT = 14;

    private final Font font;
    private final List<Item> items;
    private final int x;
    private final int y;
    private final int width;

    public PopupMenu(Font font, int x, int y, int width, List<Item> items)
    {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.items = items;
    }

    public static PopupMenu at(Font font, int mouseX, int mouseY, int width, List<Item> items)
    {
        int height = items.size() * ITEM_HEIGHT;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int x = Math.max(2, Math.min(mouseX, screenWidth - width - 4));
        int y = Math.max(2, Math.min(mouseY, screenHeight - height - 4));
        return new PopupMenu(font, x, y, width, items);
    }

    public Item itemAt(double mouseX, double mouseY)
    {
        int height = items.size() * ITEM_HEIGHT;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height)
        {
            return null;
        }
        int index = (int) ((mouseY - y) / ITEM_HEIGHT);
        return (index >= 0 && index < items.size()) ? items.get(index) : null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);

        int height = items.size() * ITEM_HEIGHT;
        guiGraphics.fill(x, y, x + width, y + height, 0xF0101030);

        for (int i = 0; i < items.size(); i++)
        {
            int itemY = y + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered)
            {
                guiGraphics.fill(x + 1, itemY, x + width - 1, itemY + ITEM_HEIGHT, 0xFF3050A0);
            }
            guiGraphics.drawString(font, fit(items.get(i).label()), x + 4, itemY + 3, items.get(i).color(), false);
        }

        guiGraphics.pose().popPose();
    }

    private String fit(Component text)
    {
        if (text == null)
        {
            return "";
        }
        return font.plainSubstrByWidth(text.getString(), width - 8);
    }

    public record Item(Component label, int color, Runnable action)
    {
    }
}

package com.wdlpiaoyi.justenoughhiding.client.gui.column;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One column of a {@link com.wdlpiaoyi.justenoughhiding.client.gui.RowList}.
 * Generic over the row type so the same list/columns can render intents, the hiding list, etc.
 */
public interface Column<T>
{
    int FLEXIBLE = -1;

    /** Fixed width in pixels, or {@link #FLEXIBLE} to take the remaining space. */
    int width();

    default boolean flexible()
    {
        return width() == FLEXIBLE;
    }

    /** True for the icon column, which is used for icon hover hit-testing. */
    default boolean isIcon()
    {
        return false;
    }

    void render(GuiGraphics guiGraphics, Font font, T row, int left, int top, int width, int height);
}

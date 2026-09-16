package com.wdlpiaoyi.justenoughhiding.client.gui.column;

import com.wdlpiaoyi.justenoughhiding.intent.Intent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One column of the intent list. A {@code ViewerAdapter} supplies the list of columns,
 * so new viewers (or new target kinds) can change the layout without touching the GUI.
 */
public interface Column
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

    void render(GuiGraphics guiGraphics, Font font, Intent intent, int left, int top, int width, int height);
}

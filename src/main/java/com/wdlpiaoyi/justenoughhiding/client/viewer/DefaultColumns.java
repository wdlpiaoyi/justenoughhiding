package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import com.wdlpiaoyi.justenoughhiding.client.gui.column.Columns;
import com.wdlpiaoyi.justenoughhiding.intent.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class DefaultColumns
{
    private DefaultColumns()
    {
    }

    public static List<Column<Intent>> withoutIcon()
    {
        return List.of(
            Columns.<Intent>fixed(96, intent -> intent.kind().name(), intent -> intent.kind().isHide() ? 0xFFFF7070 : 0xFF70FF70),
            Columns.<Intent>fixed(110, intent -> intent.source().id(), intent -> 0xFFB0B0B0),
            Columns.<Intent>flexible(intent -> intent.target().describe(), intent -> 0xFFE0E0E0),
            Columns.<Intent>fixed(34, intent -> "x" + intent.count(), intent -> 0xFF808080)
        );
    }

    public static List<Column<Intent>> withIcon(Function<Intent, IconRenderer> icons)
    {
        List<Column<Intent>> columns = new ArrayList<>();
        columns.add(Columns.icon(18, icons));
        columns.addAll(withoutIcon());
        return List.copyOf(columns);
    }
}

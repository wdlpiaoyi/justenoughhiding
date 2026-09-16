package com.wdlpiaoyi.justenoughhiding.client.viewer;

import com.wdlpiaoyi.justenoughhiding.client.gui.column.Column;
import com.wdlpiaoyi.justenoughhiding.intent.IntentTarget;

import java.util.ArrayList;
import java.util.List;

public final class Adapters
{
    private static final List<ViewerAdapter> REGISTERED = new ArrayList<>();
    private static final ViewerAdapter FALLBACK = new FallbackAdapter();

    private Adapters()
    {
    }

    public static void register(ViewerAdapter adapter)
    {
        REGISTERED.add(adapter);
    }

    public static ViewerAdapter active()
    {
        for (ViewerAdapter adapter : REGISTERED)
        {
            if (adapter.available())
            {
                return adapter;
            }
        }
        return FALLBACK;
    }

    private static final class FallbackAdapter implements ViewerAdapter
    {
        @Override
        public String id()
        {
            return "none";
        }

        @Override
        public boolean available()
        {
            return true;
        }

        @Override
        public List<Column> columns()
        {
            return DefaultColumns.withoutIcon();
        }

        @Override
        public IconRenderer icon(IntentTarget target)
        {
            return IconRenderer.EMPTY;
        }

        @Override
        public boolean isBookmarkKey(int keyCode, int scanCode)
        {
            return false;
        }

        @Override
        public boolean bookmark(IntentTarget target)
        {
            return false;
        }
    }
}

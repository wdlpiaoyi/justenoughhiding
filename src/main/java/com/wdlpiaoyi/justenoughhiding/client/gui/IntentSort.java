package com.wdlpiaoyi.justenoughhiding.client.gui;

import com.wdlpiaoyi.justenoughhiding.intent.Intent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class IntentSort
{
    private IntentSort()
    {
    }

    public static Comparator<Intent> comparator(String mode)
    {
        Comparator<Intent> result = null;
        for (Key key : parse(mode))
        {
            Comparator<Intent> next;
            switch (key.name())
            {
                case "source" -> next = Comparator.comparing((Intent intent) -> intent.source().id());
                case "kind" -> next = Comparator.comparing((Intent intent) -> intent.kind().name());
                case "target" -> next = Comparator.comparing((Intent intent) -> IntentFormat.targetText(intent.target()));
                case "count" -> next = Comparator.comparingInt(Intent::count);
                case "sequence" -> next = Comparator.comparingLong(Intent::sequence);
                default -> next = null;
            }
            if (next == null)
            {
                continue;
            }
            if (key.desc())
            {
                next = next.reversed();
            }
            result = result == null ? next : result.thenComparing(next);
        }
        return result == null ? (a, b) -> 0 : result;
    }

    private static List<Key> parse(String mode)
    {
        List<Key> keys = new ArrayList<>();
        if (mode == null)
        {
            return keys;
        }
        for (String token : mode.split(","))
        {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty())
            {
                continue;
            }
            boolean desc = false;
            int colon = value.indexOf(':');
            if (colon >= 0)
            {
                String direction = value.substring(colon + 1).trim();
                value = value.substring(0, colon).trim();
                if ("desc".equals(direction))
                {
                    desc = true;
                }
                else if (!"asc".equals(direction))
                {
                    continue;
                }
            }
            keys.add(new Key(value, desc));
        }
        return keys;
    }

    private record Key(String name, boolean desc)
    {
    }
}

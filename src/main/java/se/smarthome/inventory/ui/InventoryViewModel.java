package se.smarthome.inventory.ui;

import se.smarthome.inventory.domain.Item;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/** Pure display and filtering functions, independent of JavaFX and the database. */
public final class InventoryViewModel {
    private InventoryViewModel() {}

    public static String path(Item item) {
        var names = new ArrayDeque<String>();
        for (Item current = item; current != null; current = current.getParent()) {
            names.addFirst(current.getName());
        }
        return String.join(" → ", names);
    }

    public static boolean isWithin(Item item, Item container) {
        if (container == null) return true;
        for (Item parent = item.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == container || (container.getId() != null && container.getId().equals(parent.getId()))) {
                return true;
            }
        }
        return false;
    }

    public static List<Item> filter(List<Item> items, Item container, boolean recursive, String query) {
        String text = query.strip().toLowerCase(Locale.ROOT);
        return items.stream().filter(item -> {
            boolean inScope = container == null || (recursive ? isWithin(item, container)
                : item.getParent() != null && item.getParent().getId().equals(container.getId()));
            String searchable = (path(item) + " " + item.getDescription()).toLowerCase(Locale.ROOT);
            return inScope && searchable.contains(text);
        }).toList();
    }
}

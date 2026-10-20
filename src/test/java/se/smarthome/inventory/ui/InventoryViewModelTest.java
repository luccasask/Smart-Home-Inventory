package se.smarthome.inventory.ui;

import org.junit.jupiter.api.Test;
import se.smarthome.inventory.domain.Item;
import se.smarthome.inventory.domain.ItemObject;
import se.smarthome.inventory.domain.Location;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryViewModelTest {
    @Test
    void matchesPartOfNameWithoutRequiringWholeWord() {
        Item charger = new ItemObject(1, "Telefonladdare");
        Item cable = new ItemObject(2, "USB-kabel");
        assertEquals(List.of(charger),
            InventoryViewModel.filter(List.of(charger, cable), null, true, "ladd"));
    }

    @Test
    void requiresMultipleWordsToAppearAsOneContinuousPhrase() {
        Item matching = new ItemObject(1, "Laddare");
        matching.setDescription("Vit USB kabel till datorn");
        Item separated = new ItemObject(2, "Adapter");
        separated.setDescription("USB och en separat kabel");
        var all = List.of(matching, separated);
        assertEquals(List.of(matching), InventoryViewModel.filter(all, null, true, "USB kabel"));
        assertTrue(InventoryViewModel.filter(all, null, true, "kabel USB").isEmpty());
    }

    @Test
    void matchesSwedishLettersIgnoringCaseAndSurroundingWhitespace() {
        Item storage = new Location(1, "Förråd");
        Item item = new ItemObject(2, "Måttband");
        item.setDescription("Äldre modell");
        item.moveTo(storage);
        for (String query : List.of("  FÖRRÅD  ", "MÅTT", "ÄLDRE")) {
            assertEquals(List.of(item), InventoryViewModel.filter(List.of(item), null, true, query), query);
        }
    }

    @Test
    void combinesSearchWithDirectOrRecursiveScopeAndCanSearchAllPlaces() {
        Item bedroom = new Location(1, "Sovrum");
        Item kitchen = new Location(2, "Kök");
        Item box = new ItemObject(3, "Låda");
        Item direct = new ItemObject(4, "Telefonladdare");
        Item nested = new ItemObject(5, "Datorladdare");
        Item elsewhere = new ItemObject(6, "Reservladdare");
        box.moveTo(bedroom);
        direct.moveTo(bedroom);
        nested.moveTo(box);
        elsewhere.moveTo(kitchen);
        var all = List.of(bedroom, kitchen, box, direct, nested, elsewhere);
        assertEquals(List.of(direct), InventoryViewModel.filter(all, bedroom, false, "ladd"));
        assertEquals(List.of(direct, nested), InventoryViewModel.filter(all, bedroom, true, "ladd"));
        assertEquals(List.of(elsewhere), InventoryViewModel.filter(all, kitchen, true, "ladd"));
        assertEquals(List.of(direct, nested, elsewhere), InventoryViewModel.filter(all, null, true, "ladd"));
        assertEquals(List.of(box, direct), InventoryViewModel.filter(all, bedroom, false, ""));
    }

    @Test
    void showsFullPathAndMatchesPlaceNamesCaseInsensitively() {
        Item room = new Location(1, "Sovrum");
        Item box = new ItemObject(2, "Låda");
        Item charger = new ItemObject(3, "Laddare");
        box.moveTo(room);
        charger.moveTo(box);
        assertEquals("Sovrum → Låda → Laddare", InventoryViewModel.path(charger));
        assertEquals(List.of(box, charger), InventoryViewModel.filter(List.of(box, charger), null, true, " SOVRUM "));
    }

    @Test
    void filtersDirectAndRecursiveContentsWithoutIncludingTheContainer() {
        Item room = new Location(1, "Rum");
        Item box = new ItemObject(2, "Låda");
        Item cable = new ItemObject(3, "Kabel");
        box.moveTo(room);
        cable.moveTo(box);
        var all = List.of(room, box, cable);
        assertEquals(List.of(box), InventoryViewModel.filter(all, room, false, ""));
        assertEquals(List.of(box, cable), InventoryViewModel.filter(all, room, true, ""));
        assertFalse(InventoryViewModel.isWithin(room, room));
    }

    @Test
    void findsDescriptionAndReturnsEmptyForNoMatch() {
        Item item = new ItemObject(1, "Kabel");
        item.setDescription("USB-C till skärmen");
        assertEquals(List.of(item), InventoryViewModel.filter(List.of(item), null, true, "usb-c"));
        assertTrue(InventoryViewModel.filter(List.of(item), null, true, "hammare").isEmpty());
    }
}

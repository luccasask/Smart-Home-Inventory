package se.smarthome.inventory.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemTest {
    @Test
    void movingContainerPreservesContents() {
        var bedroom = new Location("Sovrum");
        var storage = new Location("Förråd");
        var drawer = new ItemObject("Låda");
        var charger = new ItemObject("Laddare");
        drawer.moveTo(bedroom);
        charger.moveTo(drawer);

        drawer.moveTo(storage);

        assertSame(drawer, charger.getParent());
        assertSame(storage, charger.getParent().getParent());
    }

    @Test
    void rejectsSelfAsParentWithoutChangingExistingParent() {
        var room = new Location("Rum");
        var item = new ItemObject("Sak");
        item.moveTo(room);
        assertThrows(IllegalArgumentException.class, () -> item.moveTo(item));
        assertSame(room, item.getParent());
    }

    @Test
    void rejectsMovingUnderDeepDescendant() {
        var home = new Location("Hem");
        var room = new Location("Rum");
        var drawer = new ItemObject("Låda");
        var charger = new ItemObject("Laddare");
        room.moveTo(home);
        drawer.moveTo(room);
        charger.moveTo(drawer);
        assertThrows(IllegalArgumentException.class, () -> home.moveTo(charger));
        assertNull(home.getParent());
        assertSame(home, room.getParent());
    }

    @Test
    void canMoveBackToRoot() {
        var item = new ItemObject("Sak");
        item.moveTo(new Location("Rum"));
        item.moveTo(null);
        assertNull(item.getParent());
    }

    @Test
    void rejectsEmptyNamesOnCreationAndRename() {
        assertThrows(IllegalArgumentException.class, () -> new Location(" "));
        assertThrows(IllegalArgumentException.class, () -> new ItemObject(null));
        var item = new ItemObject("Laddare");
        assertThrows(IllegalArgumentException.class, () -> item.rename("\t"));
        assertEquals("Laddare", item.getName());
    }
}

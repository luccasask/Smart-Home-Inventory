package se.smarthome.inventory.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.smarthome.inventory.domain.Item;
import se.smarthome.inventory.domain.ItemObject;
import se.smarthome.inventory.domain.Location;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ItemRepositoryTest {
    @TempDir Path directory;
    InventoryDatabase database;
    ItemRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        database = new InventoryDatabase(directory.resolve("inventory.db"));
        repository = new ItemRepository(database);
    }

    private Item saveUnder(Item item, Item parent) throws SQLException {
        item.moveTo(parent);
        return repository.save(item);
    }

    @Test
    void restoresTypesIdsDescriptionsAndParentChainAfterRestart() throws Exception {
        Item home = repository.save(new Location("Hem"));
        Item room = saveUnder(new Location("Sovrum"), home);
        Item drawer = saveUnder(new ItemObject("Låda"), room);
        var charger = new ItemObject("USB-C-laddare");
        charger.setDescription("Till datorn – 65 W");
        Item saved = saveUnder(charger, drawer);

        var reopened = new ItemRepository(new InventoryDatabase(directory.resolve("inventory.db")));
        Item loaded = reopened.findById(saved.getId()).orElseThrow();
        assertInstanceOf(ItemObject.class, loaded);
        assertEquals(saved.getId(), loaded.getId());
        assertEquals("Till datorn – 65 W", loaded.getDescription());
        assertEquals("Låda", loaded.getParent().getName());
        assertInstanceOf(Location.class, loaded.getParent().getParent());
        assertEquals(home.getId(), loaded.getParent().getParent().getParent().getId());
        assertNull(loaded.getParent().getParent().getParent().getParent());
        assertEquals(4, reopened.findAll().size());
    }

    @Test
    void databaseAssignsDistinctIdsEvenForIdenticalNames() throws Exception {
        var draft = new ItemObject("Kabel");
        assertNull(draft.getId());
        Item first = repository.save(draft);
        Item second = repository.save(new ItemObject("Kabel"));
        assertTrue(first.getId() > 0);
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void movingContainerAndEditingPersistWithoutRecreatingContents() throws Exception {
        Item room = repository.save(new Location("Rum"));
        Item drawer = saveUnder(new ItemObject("Låda"), room);
        Item charger = saveUnder(new ItemObject("Laddare"), drawer);
        // Parent has a higher ID than its child; reconstruction must not depend on ID order.
        Item storage = repository.save(new Location("Förråd"));
        drawer.moveTo(storage);
        drawer.rename("Elektroniklåda");
        repository.save(drawer);

        Item loaded = repository.findById(charger.getId()).orElseThrow();
        assertEquals(drawer.getId(), loaded.getParent().getId());
        assertEquals("Elektroniklåda", loaded.getParent().getName());
        assertEquals(storage.getId(), loaded.getParent().getParent().getId());
        assertEquals(4, repository.findAll().size());
        Item reloadedDrawer = loaded.getParent();
        reloadedDrawer.moveTo(null);
        repository.save(reloadedDrawer);
        assertNull(repository.findById(drawer.getId()).orElseThrow().getParent());
    }

    @Test
    void databaseRejectsCycleHiddenByStaleObjectSnapshotAndRollsBack() throws Exception {
        Item room = repository.save(new Location("Rum"));
        Item drawer = repository.save(new ItemObject("Låda"));
        Item staleDrawer = repository.findById(drawer.getId()).orElseThrow();
        drawer.moveTo(room);
        repository.save(drawer);

        // This old snapshot has no parent, so only the database can see the cycle.
        room.moveTo(staleDrawer);
        room.rename("Får inte sparas");
        assertThrows(SQLException.class, () -> repository.save(room));
        Item loaded = repository.findById(room.getId()).orElseThrow();
        assertNull(loaded.getParent());
        assertEquals("Rum", loaded.getName());
        assertEquals(room.getId(), repository.findById(drawer.getId()).orElseThrow().getParent().getId());
    }

    @Test
    void blocksDeletingContainerUntilContentsAreRemoved() throws Exception {
        Item drawer = repository.save(new ItemObject("Låda"));
        Item charger = saveUnder(new ItemObject("Laddare"), drawer);
        assertThrows(SQLException.class, () -> repository.delete(drawer.getId()));
        assertEquals(2, repository.findAll().size());
        assertTrue(repository.delete(charger.getId()));
        assertTrue(repository.delete(drawer.getId()));
        assertFalse(repository.delete(drawer.getId()));
        assertTrue(repository.findById(drawer.getId()).isEmpty());
    }

    @Test
    void rejectsUnsavedOrMissingParent() throws Exception {
        var child = new ItemObject("Sak");
        child.moveTo(new Location("Osparat rum"));
        assertThrows(IllegalArgumentException.class, () -> repository.save(child));
        child.moveTo(new Location(999, "Saknat rum"));
        assertThrows(SQLException.class, () -> repository.save(child));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void doesNotRecreateDeletedObjectsOnUpdate() throws Exception {
        Item item = repository.save(new ItemObject("Sak"));
        repository.delete(item.getId());
        assertThrows(NoSuchElementException.class, () -> repository.save(item));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void preservesSqlCharactersAsPlainText() throws Exception {
        String name = "Låda '); DROP TABLE items; --";
        Item saved = repository.save(new ItemObject(name));
        assertEquals(name, repository.findById(saved.getId()).orElseThrow().getName());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void rejectsSelfParentEvenThroughRawSql() throws Exception {
        Item item = repository.save(new ItemObject("Sak"));
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("UPDATE items SET parent_id = id WHERE id = ?")) {
            statement.setLong(1, item.getId());
            assertThrows(SQLException.class, statement::executeUpdate);
        }
        assertNull(repository.findById(item.getId()).orElseThrow().getParent());
    }
}

package se.smarthome.inventory.persistence;

import se.smarthome.inventory.domain.Item;
import se.smarthome.inventory.domain.ItemObject;
import se.smarthome.inventory.domain.Location;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/** SQLite persistence. Objects returned by reads form a fresh, connected snapshot. */
public final class ItemRepository {
    private final InventoryDatabase database;

    public ItemRepository(InventoryDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    /**
     * Save one object, not its whole subtree. Parents must already be persisted.
     * Returns a fresh persisted object; use this return value after inserting.
     * For a move, change parent with moveTo() before calling save().
     */
    public Item save(Item item) throws SQLException {
        Objects.requireNonNull(item);
        if (!(item instanceof Location) && !(item instanceof ItemObject)) {
            throw new IllegalArgumentException("Okänd objekttyp.");
        }
        Item parent = item.getParent();
        if (parent != null && parent.getId() == null) {
            throw new IllegalArgumentException("Spara föräldern först.");
        }
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean insert = item.getId() == null;
                String sql = insert
                    ? "INSERT INTO items(name, description, parent_id, type) VALUES (?, ?, ?, ?)"
                    : "UPDATE items SET name = ?, description = ?, parent_id = ? WHERE id = ? AND type = ?";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, item.getName());
                    statement.setString(2, item.getDescription());
                    if (parent == null) {
                        statement.setNull(3, Types.INTEGER);
                    } else {
                        statement.setLong(3, parent.getId());
                    }
                    String type = item instanceof Location ? "LOCATION" : "OBJECT";
                    if (insert) {
                        statement.setString(4, type);
                    } else {
                        statement.setLong(4, item.getId());
                        statement.setString(5, type);
                    }
                    if (statement.executeUpdate() != 1) {
                        throw new NoSuchElementException("Objektet saknas eller har fel typ: " + item.getId());
                    }
                }
                long id;
                if (insert) {
                    try (var statement = connection.createStatement();
                         var result = statement.executeQuery("SELECT last_insert_rowid()")) {
                        result.next();
                        id = result.getLong(1);
                    }
                } else {
                    id = item.getId();
                }
                Item saved = readAll(connection).get(id);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public List<Item> findAll() throws SQLException {
        try (var connection = database.openConnection()) {
            return List.copyOf(readAll(connection).values());
        }
    }

    /** Loads the snapshot so the item's complete parent chain is available. */
    public Optional<Item> findById(long id) throws SQLException {
        try (var connection = database.openConnection()) {
            return Optional.ofNullable(readAll(connection).get(id));
        }
    }

    /** Foreign keys prevent deleting a container while it still has children. */
    public boolean delete(long id) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        }
    }

    private Map<Long, Item> readAll(Connection connection) throws SQLException {
        Map<Long, Item> items = new LinkedHashMap<>();
        Map<Long, Long> parents = new LinkedHashMap<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT id, type, name, description, parent_id FROM items ORDER BY id")) {
            while (result.next()) {
                long id = result.getLong("id");
                String name = result.getString("name");
                Item item = switch (result.getString("type")) {
                    case "LOCATION" -> new Location(id, name);
                    case "OBJECT" -> new ItemObject(id, name);
                    default -> throw new SQLException("Okänd objekttyp i databasen.");
                };
                item.setDescription(result.getString("description"));
                items.put(id, item);
                long parentId = result.getLong("parent_id");
                if (!result.wasNull()) {
                    parents.put(id, parentId);
                }
            }
        }
        try {
            for (var relation : parents.entrySet()) {
                Item parent = items.get(relation.getValue());
                if (parent == null) {
                    throw new SQLException("Databasen innehåller en förälder som saknas.");
                }
                items.get(relation.getKey()).moveTo(parent);
            }
        } catch (IllegalArgumentException ex) {
            throw new SQLException("Databasen innehåller en cirkulär hierarki.", ex);
        }
        return items;
    }
}

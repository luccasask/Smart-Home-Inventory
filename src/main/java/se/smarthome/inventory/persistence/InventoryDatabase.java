package se.smarthome.inventory.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Creates the schema and opens short-lived connections to a local database file. */
public final class InventoryDatabase {
    private final String url;

    public InventoryDatabase(Path file) throws IOException, SQLException {
        Path absolute = file.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        url = "jdbc:sqlite:" + absolute;
        initialize();
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            return connection;
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
    }

    private void initialize() throws SQLException {
        try (var connection = openConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                int version;
                try (var result = statement.executeQuery("PRAGMA user_version")) {
                    result.next();
                    version = result.getInt(1);
                }
                if (version > 1) {
                    throw new SQLException("Databasen kräver en nyare programversion.");
                }
                if (version == 0) {
                    statement.execute("""
                        CREATE TABLE items (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            type TEXT NOT NULL CHECK (type IN ('LOCATION', 'OBJECT')),
                            name TEXT NOT NULL CHECK (length(trim(name)) > 0),
                            description TEXT NOT NULL DEFAULT '',
                            parent_id INTEGER REFERENCES items(id) ON DELETE RESTRICT,
                            CHECK (parent_id IS NULL OR parent_id <> id)
                        )
                        """);
                    statement.execute("CREATE INDEX idx_items_parent ON items(parent_id)");
                    // A CTE nested in a SELECT expression is valid inside SQLite triggers.
                    statement.execute("""
                        CREATE TRIGGER prevent_item_cycle BEFORE UPDATE OF parent_id ON items
                        WHEN NEW.parent_id IS NOT NULL
                        BEGIN
                            SELECT RAISE(ABORT, 'Cirkulär placering är inte tillåten')
                            WHERE NEW.id IN (
                                WITH RECURSIVE ancestors(id) AS (
                                    SELECT NEW.parent_id
                                    UNION
                                    SELECT i.parent_id FROM items i
                                    JOIN ancestors a ON i.id = a.id
                                    WHERE i.parent_id IS NOT NULL
                                )
                                SELECT id FROM ancestors
                            );
                        END
                        """);
                    statement.execute("PRAGMA user_version = 1");
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
}

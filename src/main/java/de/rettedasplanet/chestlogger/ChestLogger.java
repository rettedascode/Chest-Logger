package de.rettedasplanet.chestlogger;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.Objects;

public class ChestLogger extends JavaPlugin {

    private Connection connection;
    private ChestListener chestListener;

    private String prefix = "[ChestLogger] ";

    @Override
    public void onEnable() {
        getLogger().info(getPrefix() + "ChestLogger is now enabled!");
        getLogger().info(getPrefix() + "Connecting to the database...");
        getLogger().info(getPrefix() + "Total Chests Tracked: " + getChestCount());
        getLogger().info(getPrefix() + "Total Chest Logs: " + getLogCount());

        setupDatabase();

        updateDatabaseSchema();

        chestListener = new ChestListener(this);

        getServer().getPluginManager().registerEvents(chestListener, this);

        Objects.requireNonNull(this.getCommand("chestlog")).setExecutor(new ChestLogCommand(this));
    }

    @Override
    public void onDisable() {
        getLogger().info("ChestLogger is now disabled.");
        closeDatabase();
    }

    public ChestListener getChestListener() {
        return chestListener;
    }

    public Connection getConnection() {
        return connection;
    }

    private void setupDatabase() {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdir();
            }

            File dbFile = new File(dataFolder, "chestlogger.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            connection = DriverManager.getConnection(url);

            createTables();

            getLogger().info("Database connected successfully.");

        } catch (SQLException e) {
            getLogger().severe("Could not connect to SQLite database.");
            e.printStackTrace();
        }
    }

    private void updateDatabaseSchema() {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(chests);");
            boolean hasOwnerName = false;
            while (rs.next()) {
                if ("owner_name".equalsIgnoreCase(rs.getString("name"))) {
                    hasOwnerName = true;
                    break;
                }
            }
            if (!hasOwnerName) {
                stmt.execute("ALTER TABLE chests ADD COLUMN owner_name TEXT NOT NULL DEFAULT 'Unknown';");
                getLogger().info("Added 'owner_name' column to 'chests' table.");
            }

            // Update 'log' table
            rs = stmt.executeQuery("PRAGMA table_info(log);");
            boolean hasPlayerName = false;
            while (rs.next()) {
                if ("player_name".equalsIgnoreCase(rs.getString("name"))) {
                    hasPlayerName = true;
                    break;
                }
            }
            if (!hasPlayerName) {
                stmt.execute("ALTER TABLE log ADD COLUMN player_name TEXT NOT NULL DEFAULT 'Unknown';");
                getLogger().info("Added 'player_name' column to 'log' table.");
            }

        } catch (SQLException e) {
            getLogger().severe("Failed to update database schema.");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String createChestsTable = "CREATE TABLE IF NOT EXISTS chests ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "owner_uuid TEXT NOT NULL,"
                + "owner_name TEXT NOT NULL,"
                + "world TEXT NOT NULL,"
                + "x INTEGER NOT NULL,"
                + "y INTEGER NOT NULL,"
                + "z INTEGER NOT NULL"
                + ");";

        String createLogTable = "CREATE TABLE IF NOT EXISTS log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "chest_id INTEGER NOT NULL,"
                + "player_uuid TEXT NOT NULL,"
                + "player_name TEXT NOT NULL,"
                + "action TEXT NOT NULL,"
                + "item TEXT,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(chest_id) REFERENCES chests(id)"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createChestsTable);
            stmt.execute(createLogTable);
            getLogger().info("Database tables created or already exist.");
        } catch (SQLException e) {
            getLogger().severe("Could not create tables in SQLite database.");
            e.printStackTrace();
        }
    }

    private void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            getLogger().severe("Could not close SQLite database connection.");
            e.printStackTrace();
        }
    }

    public int getChestCount() {
        String sql = "SELECT COUNT(*) AS count FROM chests";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("count");
            }

        } catch (SQLException e) {
            getLogger().severe("Failed to retrieve chest count.");
            e.printStackTrace();
        }
        return 0;
    }

    public int getLogCount() {
        String sql = "SELECT COUNT(*) AS count FROM log";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("count");
            }

        } catch (SQLException e) {
            getLogger().severe("Failed to retrieve log count.");
            e.printStackTrace();
        }
        return 0;
    }

    public String getPrefix() {
        return prefix;
    }
}

package com.example.Coi.database;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;

// SQLiteデータベース
public class SQLiteDatabase implements DatabaseManager {

    private final CoiPlugin plugin;
    private Connection connection;

    public SQLiteDatabase(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/data.db");    // SQLiteデータベースに接続
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid TEXT," +
                    "player_name TEXT," +
                    "timestamp LONG," +
                    "inventory BLOB," +
                    "armor_contents BLOB," +
                    "extra_contents BLOB," +
                    "ender_chest_contents BLOB" +
                    ");"
                );
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories_before_rollback (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid TEXT," +
                    "player_name TEXT," +
                    "timestamp LONG," +
                    "inventory BLOB," +
                    "armor_contents BLOB," +
                    "extra_contents BLOB," +
                    "ender_chest_contents BLOB" +
                    ");"
                );
                // Enable foreign key support for SQLite if not enabled by default connection string
                statement.execute("PRAGMA foreign_keys = ON;");

                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS rollback_history (" +
                    "history_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "rollback_timestamp INTEGER NOT NULL" + // SQLite uses INTEGER for timestamps (Unix epoch)
                    ");"
                );
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS rollback_history_players (" +
                    "entry_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "history_id INTEGER NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "FOREIGN KEY (history_id) REFERENCES rollback_history(history_id) ON DELETE CASCADE" +
                    ");"
                );
                plugin.getLogger().info("SQLite tables ('player_inventories', 'player_inventories_before_rollback', 'rollback_history', 'rollback_history_players') created successfully or already exist.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create tables in SQLiteDatabase.initialize. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database in SQLiteDatabase.initialize. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
        }
    }

    @Override
    public void savePlayerInventory(UUID playerUUID, String playerName, PlayerInventoryState inventoryState) {
        String sql = "INSERT INTO player_inventories (uuid, player_name, timestamp, inventory, armor_contents, extra_contents, ender_chest_contents) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, playerName);
            statement.setLong(3, inventoryState.getTimestamp());
            statement.setBytes(4, serializeInventory(inventoryState.getInventoryContents()));
            statement.setBytes(5, serializeInventory(inventoryState.getArmorContents()));
            statement.setBytes(6, serializeInventory(inventoryState.getExtraContents()));
            statement.setBytes(7, serializeInventory(inventoryState.getEnderChestContents()));
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            String errorMessage = "Failed to save player inventory in SQLiteDatabase.savePlayerInventory.";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }

    @Override
    public void savePlayerInventoryBeforeRollback(UUID playerUUID, PlayerInventoryState inventoryState) {
        String sql = "INSERT INTO player_inventories_before_rollback (uuid, player_name, timestamp, inventory, armor_contents, extra_contents, ender_chest_contents) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, inventoryState.getPlayerName());
            statement.setLong(3, inventoryState.getTimestamp());
            statement.setBytes(4, serializeInventory(inventoryState.getInventoryContents()));
            statement.setBytes(5, serializeInventory(inventoryState.getArmorContents()));
            statement.setBytes(6, serializeInventory(inventoryState.getExtraContents()));
            statement.setBytes(7, serializeInventory(inventoryState.getEnderChestContents()));
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            String errorMessage = "Failed to save player inventory before rollback in SQLiteDatabase.savePlayerInventoryBeforeRollback.";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }

    @Override
    public List<PlayerInventoryState> getPlayerInventoryBeforeRollback(UUID playerUUID) {
        List<PlayerInventoryState> inventoryStates = new ArrayList<>();
        String sql = "SELECT * FROM player_inventories_before_rollback WHERE uuid = ? ORDER BY timestamp DESC";    // ロールバック前のインベントリ状態を取得
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long ts = rs.getLong("timestamp");
                    String playerName = rs.getString("player_name");
                    ItemStack[] inventoryContents = deserializeInventory(rs.getBytes("inventory"));
                    ItemStack[] armorContents = deserializeInventory(rs.getBytes("armor_contents"));
                    ItemStack[] extraContents = deserializeInventory(rs.getBytes("extra_contents"));
                    ItemStack[] enderChestContents = deserializeInventory(rs.getBytes("ender_chest_contents"));
                    inventoryStates.add(new PlayerInventoryState(ts, inventoryContents, playerName, armorContents, extraContents, enderChestContents));
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            String errorMessage = "Failed to get player inventory before rollback in SQLiteDatabase.getPlayerInventoryBeforeRollback.";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
        return inventoryStates;
    }

    @Override
    public List<PlayerInventoryState> getPlayerInventory(UUID playerUUID, long timestamp) {
        List<PlayerInventoryState> inventoryStates = new ArrayList<>();
        String sql = "SELECT * FROM player_inventories WHERE uuid = ? AND timestamp <= ? ORDER BY timestamp DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());
            statement.setLong(2, timestamp);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long ts = rs.getLong("timestamp");
                    String playerName = rs.getString("player_name");
                    ItemStack[] inventoryContents = deserializeInventory(rs.getBytes("inventory"));
                    ItemStack[] armorContents = deserializeInventory(rs.getBytes("armor_contents"));
                    ItemStack[] extraContents = deserializeInventory(rs.getBytes("extra_contents"));
                    ItemStack[] enderChestContents = deserializeInventory(rs.getBytes("ender_chest_contents"));
                    inventoryStates.add(new PlayerInventoryState(ts, inventoryContents, playerName, armorContents, extraContents, enderChestContents));
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            String errorMessage = "Failed to get player inventory in SQLiteDatabase.getPlayerInventory.";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
        return inventoryStates;
    }

    @Override
    public void updateInventoryState(UUID playerUUID, PlayerInventoryState inventoryState) {
        String sql = "UPDATE player_inventories SET inventory = ?, armor_contents = ?, extra_contents = ?, ender_chest_contents = ?, timestamp = ? WHERE uuid = ? AND timestamp = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, serializeInventory(inventoryState.getInventoryContents()));
            statement.setBytes(2, serializeInventory(inventoryState.getArmorContents()));
            statement.setBytes(3, serializeInventory(inventoryState.getExtraContents()));
            statement.setBytes(4, serializeInventory(inventoryState.getEnderChestContents()));
            statement.setLong(5, inventoryState.getTimestamp());
            statement.setString(6, playerUUID.toString());
            statement.setLong(7, inventoryState.getTimestamp()); // Assumes timestamp identifies the record to update
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            String errorMessage = "Failed to update inventory state in SQLiteDatabase.updateInventoryState.";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }

    @Override
    public void deleteInventoryState(UUID playerUUID, long timestamp) {
        // This method might need to be adjusted if player_inventories_before_rollback also needs selective deletion.
        // For now, it only targets player_inventories.
        String sql = "DELETE FROM player_inventories WHERE uuid = ? AND timestamp = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());
            statement.setLong(2, timestamp);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete inventory state from player_inventories in SQLiteDatabase.deleteInventoryState. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
        }
    }
    
    // Note: A similar delete method for 'player_inventories_before_rollback' might be needed
    // if individual pre-rollback states need to be deleted.
    // For now, 'UndoCommand' deletes from 'player_inventories_before_rollback' using this generic 'deleteInventoryState'
    // which might be unintentional if that table should be cleared differently.
    // However, the current UndoCommand calls deleteInventoryState which targets 'player_inventories'.
    // This needs careful review of intended logic for UndoCommand's deletion.
    // For the scope of this subtask (extending inventory data), I will assume deleteInventoryState
    // is correctly used by other parts of the plugin or will be adjusted separately.
    // The critical part here is that `PlayerInventoryState` now has more fields, and `updateInventoryState` handles them.

    @Override
    public void deletePlayerInventoryBeforeRollback(UUID playerUUID, long timestamp) {
        String sql = "DELETE FROM player_inventories_before_rollback WHERE uuid = ? AND timestamp = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());
            statement.setLong(2, timestamp);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete inventory state from player_inventories_before_rollback in SQLiteDatabase.deletePlayerInventoryBeforeRollback. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close SQLite database connection in SQLiteDatabase.close. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
        }
    }

    private byte[] serializeInventory(ItemStack[] inventoryContents) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(inventoryContents);    // インベントリ状態をシリアライズ
            return baos.toByteArray();    // インベントリ状態をバイト配列に変換
        }
    }

    private ItemStack[] deserializeInventory(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack[]) bois.readObject();    // インベントリ状態をデシリアライズ
        }
    }

    @Override
    public void saveRollbackEvent(List<UUID> playerUUIDs) {
        String insertHistorySQL = "INSERT INTO rollback_history (rollback_timestamp) VALUES (?)";
        String insertPlayerSQL = "INSERT INTO rollback_history_players (history_id, player_uuid) VALUES (?, ?)";

        try {
            connection.setAutoCommit(false); // Start transaction

            long historyId = -1;
            try (PreparedStatement historyStmt = connection.prepareStatement(insertHistorySQL, Statement.RETURN_GENERATED_KEYS)) {
                historyStmt.setLong(1, System.currentTimeMillis());
                historyStmt.executeUpdate();
                try (ResultSet generatedKeys = historyStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        historyId = generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("Creating rollback_history failed, no ID obtained.");
                    }
                }
            }

            if (historyId != -1) {
                try (PreparedStatement playerStmt = connection.prepareStatement(insertPlayerSQL)) {
                    for (UUID playerUUID : playerUUIDs) {
                        playerStmt.setLong(1, historyId);
                        playerStmt.setString(2, playerUUID.toString());
                        playerStmt.addBatch();
                    }
                    playerStmt.executeBatch();
                }
            }
            connection.commit(); // Commit transaction
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save rollback event in SQLiteDatabase.saveRollbackEvent. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            try {
                connection.rollback(); // Rollback on error
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction in SQLiteDatabase.saveRollbackEvent. SQLState: " + ex.getSQLState() + ", ErrorCode: " + ex.getErrorCode(), ex);
            }
        } finally {
            try {
                connection.setAutoCommit(true); // Restore auto-commit
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to restore auto-commit in SQLiteDatabase.saveRollbackEvent. SQLState: " + ex.getSQLState() + ", ErrorCode: " + ex.getErrorCode(), ex);
            }
        }
    }

    @Override
    public List<UUID> getMostRecentRollbackAffectedPlayers() {
        List<UUID> affectedPlayers = new ArrayList<>();
        String getHistoryIdSQL = "SELECT history_id FROM rollback_history ORDER BY rollback_timestamp DESC LIMIT 1";
        String getPlayersSQL = "SELECT player_uuid FROM rollback_history_players WHERE history_id = ?";
        long historyId = -1;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(getHistoryIdSQL)) {
            if (rs.next()) {
                historyId = rs.getLong("history_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get most recent rollback history_id in SQLiteDatabase.getMostRecentRollbackAffectedPlayers. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            return affectedPlayers; // Return empty list on error
        }

        if (historyId != -1) {
            try (PreparedStatement pstmt = connection.prepareStatement(getPlayersSQL)) {
                pstmt.setLong(1, historyId);
                try (ResultSet rsPlayers = pstmt.executeQuery()) {
                    while (rsPlayers.next()) {
                        affectedPlayers.add(UUID.fromString(rsPlayers.getString("player_uuid")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get affected players for history_id " + historyId + " in SQLiteDatabase.getMostRecentRollbackAffectedPlayers. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            }
        }
        return affectedPlayers;
    }

    @Override
    public void deleteMostRecentRollbackEvent() {
        String getHistoryIdSQL = "SELECT history_id FROM rollback_history ORDER BY rollback_timestamp DESC LIMIT 1";
        // For SQLite, ensure foreign key constraints are enabled if ON DELETE CASCADE is to work.
        // This is often done per-connection: PRAGMA foreign_keys = ON;
        // Assuming it's enabled for the connection.
        String deleteHistorySQL = "DELETE FROM rollback_history WHERE history_id = ?";
        long historyId = -1;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(getHistoryIdSQL)) {
            if (rs.next()) {
                historyId = rs.getLong("history_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get most recent rollback history_id for deletion in SQLiteDatabase.deleteMostRecentRollbackEvent. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            return;
        }

        if (historyId != -1) {
            try (PreparedStatement pstmt = connection.prepareStatement(deleteHistorySQL)) {
                pstmt.setLong(1, historyId);
                int affectedRows = pstmt.executeUpdate();
                 if (affectedRows > 0) {
                    plugin.getLogger().info("Successfully deleted most recent rollback event (history_id: " + historyId + ") from SQLite database.");
                } else {
                    plugin.getLogger().warning("No rollback event found to delete, or history_id " + historyId + " was already deleted from SQLite database.");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete most recent rollback event (history_id: " + historyId + ") in SQLiteDatabase.deleteMostRecentRollbackEvent. SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            }
        } else {
             plugin.getLogger().info("No rollback history found to delete in SQLiteDatabase.deleteMostRecentRollbackEvent.");
        }
    }
}

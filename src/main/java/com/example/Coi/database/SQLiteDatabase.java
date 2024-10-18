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

public class SQLiteDatabase implements DatabaseManager {

    private final CoiPlugin plugin;
    private Connection connection;

    public SQLiteDatabase(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/data.db");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT," +
                "player_name TEXT," +
                "timestamp LONG," +
                    "inventory BLOB" +
                    ");"
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース接続に失敗しました", e);
        }
    }

    @Override
    public void savePlayerInventory(UUID playerUUID, String playerName, PlayerInventoryState inventoryState) {
        try {
            String sql = "INSERT INTO player_inventories (uuid, player_name, timestamp, inventory) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, playerName);
                statement.setLong(3, inventoryState.getTimestamp());
                
                // インベントリをシリアライズして保存
                byte[] serializedInventory = serializeInventory(inventoryState.getInventoryContents());
                statement.setBytes(4, serializedInventory);

                statement.executeUpdate();
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの保存に失敗しました", e);
        }
    }

    @Override
    public List<PlayerInventoryState> getPlayerInventory(UUID playerUUID, long timestamp) {
        List<PlayerInventoryState> inventoryStates = new ArrayList<>();
        try {
            String sql = "SELECT * FROM player_inventories WHERE uuid = ? AND timestamp <= ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setLong(2, timestamp);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        long ts = rs.getLong("timestamp");
                        byte[] inventoryData = rs.getBytes("inventory");
                        ItemStack[] inventoryContents = deserializeInventory(inventoryData);
                        inventoryStates.add(new PlayerInventoryState(ts, inventoryContents));
                    }
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの取得に失敗しました", e);
        }
        return inventoryStates;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース接続を閉じる際にエラーが発生しました", e);
        }
    }

    // インベントリをシリアライズ
    private byte[] serializeInventory(ItemStack[] inventoryContents) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(inventoryContents);
            return baos.toByteArray();
        }
    }

    // インベントリをデシリアライズ
    private ItemStack[] deserializeInventory(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack[]) bois.readObject();
        }
    }
}

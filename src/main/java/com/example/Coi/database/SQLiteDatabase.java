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
                    "inventory BLOB" +
                    ");"
                );    // テーブルを作成
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories_before_rollback (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid TEXT," +
                    "player_name TEXT," +
                    "timestamp LONG," +
                    "inventory BLOB" +
                    ");"
                );    // ロールバック前のインベントリテーブルを作成
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース接続に失敗しました", e);
        }
    }

    @Override
    public void savePlayerInventory(UUID playerUUID, String playerName, PlayerInventoryState inventoryState) {
        try {
            String sql = "INSERT INTO player_inventories (uuid, player_name, timestamp, inventory) VALUES (?, ?, ?, ?)";    // インベントリ状態を保存
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
                statement.setString(2, playerName);
                statement.setLong(3, inventoryState.getTimestamp());    // タイムスタンプを設定
                byte[] serializedInventory = serializeInventory(inventoryState.getInventoryContents());    // インベントリ状態をシリアライズ
                statement.setBytes(4, serializedInventory);    // インベントリ状態を保存
                statement.executeUpdate();    // インベントリ状態を保存
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの保存に失敗しました", e);
        }
    }

    @Override
    public void savePlayerInventoryBeforeRollback(UUID playerUUID, PlayerInventoryState inventoryState) {
        try {
            String sql = "INSERT INTO player_inventories_before_rollback (uuid, player_name, timestamp, inventory) VALUES (?, ?, ?, ?)";    // ロールバック前のインベントリ状態を保存
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
                statement.setString(2, inventoryState.getPlayerName());    // プレイヤー名を設定
                statement.setLong(3, inventoryState.getTimestamp());    // タイムスタンプを設定
                byte[] serializedInventory = serializeInventory(inventoryState.getInventoryContents());    // インベントリ状態をシリアライズ
                statement.setBytes(4, serializedInventory);    // インベントリ状態を保存
                statement.executeUpdate();    // インベントリ状態を保存
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "ロールバック前のインベントリの保存に失敗しました", e);
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
                    long ts = rs.getLong("timestamp");    // タイムスタンプを取得
                    byte[] inventoryData = rs.getBytes("inventory");    // インベントリ状態を取得
                    ItemStack[] inventoryContents = deserializeInventory(inventoryData);    // インベントリ状態をデシリアライズ
                    String playerName = rs.getString("player_name");    // プレイヤー名を取得
                    inventoryStates.add(new PlayerInventoryState(ts, inventoryContents, playerName));    // インベントリ状態を追加
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "ロールバック前のインベントリの取得に失敗しました", e);    // ロールバック前のインベントリ状態を取得に失敗した場合のログを出力
        }
        return inventoryStates;
    }

    @Override
    public List<PlayerInventoryState> getPlayerInventory(UUID playerUUID, long timestamp) {
        List<PlayerInventoryState> inventoryStates = new ArrayList<>();
        try {
            String sql = "SELECT * FROM player_inventories WHERE uuid = ? AND timestamp <= ? ORDER BY timestamp DESC";    // インベントリ状態を取得
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
                statement.setLong(2, timestamp);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        long ts = rs.getLong("timestamp");
                        byte[] inventoryData = rs.getBytes("inventory");    // インベントリ状態を取得
                        ItemStack[] inventoryContents = deserializeInventory(inventoryData);    // インベントリ状態をデシリアライズ
                        String playerName = rs.getString("player_name");    // プレイヤー名を取得
                        inventoryStates.add(new PlayerInventoryState(ts, inventoryContents, playerName));    // インベントリ状態を追加
                    }
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの取得に失敗しました", e);    // インベントリ状態を取得に失敗した場合のログを出力
        }
        return inventoryStates;
    }

    @Override
    public void updateInventoryState(UUID playerUUID, PlayerInventoryState inventoryState) {
        try {
            String sql = "UPDATE player_inventories SET inventory = ?, timestamp = ? WHERE uuid = ?";    // インベントリ状態を更新
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                byte[] serializedInventory = serializeInventory(inventoryState.getInventoryContents());
                statement.setBytes(1, serializedInventory);
                statement.setLong(2, inventoryState.getTimestamp());
                statement.setString(3, playerUUID.toString());

                statement.executeUpdate();
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの更新に失敗しました", e);
        }
    }

    @Override
    public void deleteInventoryState(UUID playerUUID, long timestamp) {
        try {
            String sql = "DELETE FROM player_inventories WHERE uuid = ? AND timestamp = ?";    // インベントリ状態を削除
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
                statement.setLong(2, timestamp);    // タイムスタンプを設定
                statement.executeUpdate();    // インベントリ状態を削除
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの削除に失敗しました", e);    // インベントリ状態を削除に失敗した場合のログを出力
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース接続を閉じる際にエラーが発生しました", e);    // データベース接続を閉じる際にエラーが発生した場合のログを出力
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
}

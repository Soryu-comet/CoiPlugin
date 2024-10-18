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

// MySQLデータベース
public class MySQLDatabase implements DatabaseManager {

    private final CoiPlugin plugin;
    private Connection connection;

    public MySQLDatabase(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    // 初期化
    @Override
    public void initialize() {
        try {
            String url = "jdbc:mysql://" + plugin.getConfig().getString("mysql.host") + ":" +
                         plugin.getConfig().getInt("mysql.port") + "/" +
                         plugin.getConfig().getString("mysql.database");    // データベースURLを取得
            String username = plugin.getConfig().getString("mysql.username");    // ユーザー名を取得
            String password = plugin.getConfig().getString("mysql.password");    // パスワードを取得

            connection = DriverManager.getConnection(url, username, password);    // データベースに接続
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories (" +    // テーブルを作成
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36)," +
                    "player_name VARCHAR(16)," +
                    "timestamp BIGINT," +
                    "inventory BLOB" +
                    ");"    // テーブルを作成
                );
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories_before_rollback (" +    // ロールバック前のインベントリテーブルを作成
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36)," +
                    "player_name VARCHAR(16)," +
                    "timestamp BIGINT," +
                    "inventory BLOB" +
                    ");"    // テーブルを作成
                );
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "テーブル作成に失敗しました", e);    // テーブル作成に失敗した場合のログを出力
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース接続に失敗しました", e);    // データベース接続に失敗した場合のログを出力
            plugin.getServer().getPluginManager().disablePlugin(plugin);    // プラグインを無効化
        }
    }

    @Override
    public void savePlayerInventory(UUID playerUUID, String playerName, PlayerInventoryState inventoryState) {
        String sql = "INSERT INTO player_inventories (uuid, player_name, timestamp, inventory) VALUES (?, ?, ?, ?)";    // インベントリ状態を保存
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
            statement.setString(2, playerName);    // プレイヤー名を設定
            statement.setLong(3, inventoryState.getTimestamp());    // タイムスタンプを設定

            byte[] serializedInventory = serializeInventory(inventoryState.getInventoryContents());    // インベントリ状態をシリアライズ
            statement.setBytes(4, serializedInventory);    // インベントリ状態を設定

            statement.executeUpdate();    // インベントリ状態を保存
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの保存に失敗しました", e);    // インベントリ状態を保存に失敗した場合のログを出力
        }
    }

    @Override
    public void savePlayerInventoryBeforeRollback(UUID playerUUID, PlayerInventoryState inventoryState) {
        String sql = "INSERT INTO player_inventories_before_rollback (uuid, player_name, timestamp, inventory) VALUES (?, ?, ?, ?)";    // ロールバック前のインベントリ状態を保存
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
            statement.setString(2, inventoryState.getPlayerName());    // プレイヤー名を設定
            statement.setLong(3, inventoryState.getTimestamp());    // タイムスタンプを設定

            byte[] serializedInventory = serializeInventory(inventoryState.getInventoryContents());    // インベントリ状態をシリアライズ
            statement.setBytes(4, serializedInventory);    // インベントリ状態を設定

            statement.executeUpdate();    // インベントリ状態を保存
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "ロールバック前のインベントリの保存に失敗しました", e);    // ロールバック前のインベントリ状態を保存に失敗した場合のログを出力
        }
    }

    @Override
    public List<PlayerInventoryState> getPlayerInventory(UUID playerUUID, long timestamp) {
        List<PlayerInventoryState> inventoryStates = new ArrayList<>();
        String sql = "SELECT * FROM player_inventories WHERE uuid = ? AND timestamp <= ? ORDER BY timestamp DESC";    // インベントリ状態を取得
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
            statement.setLong(2, timestamp);    // タイムスタンプを設定
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
            plugin.getLogger().log(Level.SEVERE, "インベントリの取得に失敗しました", e);    // インベントリ状態を取得に失敗した場合のログを出力
        }
        return inventoryStates;
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
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース接続を閉じる際にエラーが発生しました", e);
        }
    }

    // インベントリ状態をシリアライズ
    private byte[] serializeInventory(ItemStack[] inventoryContents) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {    // インベントリ状態をシリアライズ
            boos.writeObject(inventoryContents);
        }
        return baos.toByteArray();
    }

    private ItemStack[] deserializeInventory(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);    // インベントリ状態をデシリアライズ
        BukkitObjectInputStream bois = new BukkitObjectInputStream(bais);
        ItemStack[] inventoryContents = (ItemStack[]) bois.readObject();
        return inventoryContents;
    }

    @Override
    public void deleteInventoryState(UUID playerUUID, long timestamp) {
        String sql = "DELETE FROM player_inventories WHERE uuid = ? AND timestamp = ?";    // インベントリ状態を削除
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());    // プレイヤーUUIDを設定
            statement.setLong(2, timestamp);    // タイムスタンプを設定
            statement.executeUpdate();    // インベントリ状態を削除
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの削除に失敗しました", e);
        }
    }

    @Override
    public void updateInventoryState(UUID playerUUID, PlayerInventoryState inventoryState) {
        String sql = "UPDATE player_inventories SET inventory = ?, timestamp = ? WHERE uuid = ?";    // インベントリ状態を更新
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            byte[] serializedInventory = serializeInventory(inventoryState.getInventoryContents());    // インベントリ状態をシリアライズ
            statement.setBytes(1, serializedInventory);
            statement.setLong(2, inventoryState.getTimestamp());    // タイムスタンプを設定
            statement.setString(3, playerUUID.toString());    // プレイヤーUUIDを設定

            statement.executeUpdate();    // インベントリ状態を更新
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリの更新に失敗しました", e);    // インベントリ状態を更新に失敗した場合のログを出力
        }
    }
}

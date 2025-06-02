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

// MySQLデータベースクラス
public class MySQLDatabase implements DatabaseManager {

    private final CoiPlugin plugin; // プラグインのインスタンス
    private Connection connection; // データベース接続

    public MySQLDatabase(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    // データベースを初期化します
    @Override
    public void initialize() {
        try {
            String url = "jdbc:mysql://" + plugin.getConfig().getString("mysql.host") + ":" +
                         plugin.getConfig().getInt("mysql.port") + "/" +
                         plugin.getConfig().getString("mysql.database");    // 設定からデータベースURLを取得
            String username = plugin.getConfig().getString("mysql.username");    // 設定からユーザー名を取得
            String password = plugin.getConfig().getString("mysql.password");    // 設定からパスワードを取得

            connection = DriverManager.getConnection(url, username, password);    // データベースに接続
            try (Statement statement = connection.createStatement()) {
                // プレイヤーインベントリテーブルの作成
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36)," +        // プレイヤーUUID
                    "player_name VARCHAR(16)," + // プレイヤー名
                    "timestamp BIGINT," +        // タイムスタンプ
                    "inventory BLOB," +          // メインインベントリの内容
                    "armor_contents BLOB," +     // 鎧の内容
                    "extra_contents BLOB," +     // オフハンド等の追加内容
                    "ender_chest_contents BLOB" + // エンダーチェストの内容
                    ");"
                );
                // ロールバック前インベントリテーブルの作成
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_inventories_before_rollback (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "uuid VARCHAR(36)," +
                    "player_name VARCHAR(16)," +
                    "timestamp BIGINT," +
                    "inventory BLOB," +
                    "armor_contents BLOB," +
                    "extra_contents BLOB," +
                    "ender_chest_contents BLOB" +
                    ");"
                );
                // ロールバック履歴テーブルの作成
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS rollback_history (" +
                    "history_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "rollback_timestamp BIGINT NOT NULL" + // ロールバック実行時のタイムスタンプ
                    ");"
                );
                // ロールバック履歴対象プレイヤーテーブルの作成
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS rollback_history_players (" +
                    "entry_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "history_id INT NOT NULL," +             // rollback_historyテーブルの外部キー
                    "player_uuid VARCHAR(36) NOT NULL," +    // 対象プレイヤーUUID
                    "FOREIGN KEY (history_id) REFERENCES rollback_history(history_id) ON DELETE CASCADE" + // 履歴削除時に自動削除
                    ");"
                );
                plugin.getLogger().info("MySQLのテーブル（player_inventories, player_inventories_before_rollback, rollback_history, rollback_history_players）が正常に作成されたか、既に存在します。");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "MySQLデータベースのテーブル作成に失敗しました。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQLデータベースへの接続に失敗しました。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            plugin.getServer().getPluginManager().disablePlugin(plugin); // 接続失敗時はプラグインを無効化
        }
    }

    // プレイヤーのインベントリ状態を保存します
    @Override
    public void savePlayerInventory(UUID playerUUID, String playerName, PlayerInventoryState inventoryState) {
        String sql = "INSERT INTO player_inventories (uuid, player_name, timestamp, inventory, armor_contents, extra_contents, ender_chest_contents) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString()); // UUID
            statement.setString(2, playerName); // プレイヤー名
            statement.setLong(3, inventoryState.getTimestamp()); // タイムスタンプ

            // 各インベントリデータをシリアライズして設定
            statement.setBytes(4, serializeInventory(inventoryState.getInventoryContents())); // メインインベントリ
            statement.setBytes(5, serializeInventory(inventoryState.getArmorContents()));     // 鎧
            statement.setBytes(6, serializeInventory(inventoryState.getExtraContents()));     // 追加内容
            statement.setBytes(7, serializeInventory(inventoryState.getEnderChestContents()));// エンダーチェスト

            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            String errorMessage = "プレイヤーインベントリの保存に失敗しました (MySQLDatabase.savePlayerInventory)。";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }

    // ロールバック前のプレイヤーインベントリ状態を保存します
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
            String errorMessage = "ロールバック前のプレイヤーインベントリの保存に失敗しました (MySQLDatabase.savePlayerInventoryBeforeRollback)。";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }

    // 指定されたタイムスタンプ以前のプレイヤーインベントリ状態のリストを取得します
    @Override
    public List<PlayerInventoryState> getPlayerInventory(UUID playerUUID, long timestamp) {
        List<PlayerInventoryState> inventoryStates = new ArrayList<>();
        String sql = "SELECT * FROM player_inventories WHERE uuid = ? AND timestamp <= ? ORDER BY timestamp DESC"; // SQLクエリ: UUIDとタイムスタンプで絞り込み、降順で取得
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString()); // プレイヤーUUIDを設定
            statement.setLong(2, timestamp); // タイムスタンプを設定
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) { // 結果セットをループ処理
                    long ts = rs.getLong("timestamp");
                    String playerName = rs.getString("player_name");
                    ItemStack[] inventoryContents = deserializeInventory(rs.getBytes("inventory"));
                    ItemStack[] armorContents = deserializeInventory(rs.getBytes("armor_contents"));
                    ItemStack[] extraContents = deserializeInventory(rs.getBytes("extra_contents"));
                    ItemStack[] enderChestContents = deserializeInventory(rs.getBytes("ender_chest_contents"));
                    // 取得したデータでPlayerInventoryStateオブジェクトを作成しリストに追加
                    inventoryStates.add(new PlayerInventoryState(ts, inventoryContents, playerName, armorContents, extraContents, enderChestContents));
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            String errorMessage = "プレイヤーインベントリの取得に失敗しました (MySQLDatabase.getPlayerInventory)。";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
        return inventoryStates;
    }

    // ロールバック前のプレイヤーインベントリ状態のリストを取得します
    @Override
    public List<PlayerInventoryState> getPlayerInventoryBeforeRollback(UUID playerUUID) {
        List<PlayerInventoryState> inventoryStates = new ArrayList<>();
        String sql = "SELECT * FROM player_inventories_before_rollback WHERE uuid = ? ORDER BY timestamp DESC"; // SQLクエリ
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString()); // プレイヤーUUIDを設定
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long ts = rs.getLong("timestamp");
                    String playerName = rs.getString("player_name");
                    ItemStack[] inventoryContents = deserializeInventory(rs.getBytes("inventory"));
                    ItemStack[] armorContents = deserializeInventory(rs.getBytes("armor_contents")); // 鎧コンテンツを取得・デシリアライズ
                    ItemStack[] extraContents = deserializeInventory(rs.getBytes("extra_contents")); // 追加コンテンツを取得・デシリアライズ
                    ItemStack[] enderChestContents = deserializeInventory(rs.getBytes("ender_chest_contents")); // エンダーチェストコンテンツを取得・デシリアライズ
                    // 取得したデータでPlayerInventoryStateオブジェクトを作成しリストに追加
                    inventoryStates.add(new PlayerInventoryState(ts, inventoryContents, playerName, armorContents, extraContents, enderChestContents));
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            String errorMessage = "ロールバック前のプレイヤーインベントリの取得に失敗しました (MySQLDatabase.getPlayerInventoryBeforeRollback)。";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
        return inventoryStates;
    }

    // データベース接続を閉じます
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQLデータベース接続のクローズに失敗しました。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
        }
    }

    // ItemStack配列をバイト配列にシリアライズします
    private byte[] serializeInventory(ItemStack[] inventoryContents) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(inventoryContents); // Bukkitのシリアライザーを使用
        }
        return baos.toByteArray();
    }

    // バイト配列をItemStack配列にデシリアライズします
    private ItemStack[] deserializeInventory(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) { // Bukkitのデシリアライザーを使用
            return (ItemStack[]) bois.readObject();
        }
    }

    // 指定されたプレイヤーとタイムスタンプのインベントリ状態を削除します
    @Override
    public void deleteInventoryState(UUID playerUUID, long timestamp) {
        String sql = "DELETE FROM player_inventories WHERE uuid = ? AND timestamp = ?"; // SQLクエリ
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());
            statement.setLong(2, timestamp);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリ状態の削除に失敗しました (MySQLDatabase.deleteInventoryState)。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
        }
    }

    // プレイヤーのインベントリ状態を更新します
    @Override
    public void updateInventoryState(UUID playerUUID, PlayerInventoryState inventoryState) {
        // 特定のタイムスタンプを持つ既存のレコードを更新
        String sql = "UPDATE player_inventories SET inventory = ?, armor_contents = ?, extra_contents = ?, ender_chest_contents = ?, timestamp = ? WHERE uuid = ? AND timestamp = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, serializeInventory(inventoryState.getInventoryContents()));
            statement.setBytes(2, serializeInventory(inventoryState.getArmorContents()));
            statement.setBytes(3, serializeInventory(inventoryState.getExtraContents()));
            statement.setBytes(4, serializeInventory(inventoryState.getEnderChestContents()));
            statement.setLong(5, inventoryState.getTimestamp()); // 更新後のタイムスタンプ (通常は変更しないが、必要に応じて)
            statement.setString(6, playerUUID.toString());
            statement.setLong(7, inventoryState.getTimestamp()); // 更新対象のレコードを特定するための元のタイムスタンプ

            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            String errorMessage = "インベントリ状態の更新に失敗しました (MySQLDatabase.updateInventoryState)。";
            if (e instanceof SQLException) {
                SQLException sqlEx = (SQLException) e;
                errorMessage += " SQLState: " + sqlEx.getSQLState() + ", ErrorCode: " + sqlEx.getErrorCode();
            }
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }

    // ロールバック前のインベントリ状態を削除します
    @Override
    public void deletePlayerInventoryBeforeRollback(UUID playerUUID, long timestamp) {
        String sql = "DELETE FROM player_inventories_before_rollback WHERE uuid = ? AND timestamp = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUUID.toString());
            statement.setLong(2, timestamp);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "ロールバック前のインベントリ状態の削除に失敗しました (MySQLDatabase.deletePlayerInventoryBeforeRollback)。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
        }
    }

    // ロールバックイベントを保存します
    @Override
    public void saveRollbackEvent(List<UUID> playerUUIDs) {
        String insertHistorySQL = "INSERT INTO rollback_history (rollback_timestamp) VALUES (?)";
        String insertPlayerSQL = "INSERT INTO rollback_history_players (history_id, player_uuid) VALUES (?, ?)";

        try {
            connection.setAutoCommit(false); // トランザクションを開始

            long historyId = -1;
            // rollback_historyテーブルに新しいイベントを記録
            try (PreparedStatement historyStmt = connection.prepareStatement(insertHistorySQL, Statement.RETURN_GENERATED_KEYS)) {
                historyStmt.setLong(1, System.currentTimeMillis()); // 現在時刻をタイムスタンプとして使用
                historyStmt.executeUpdate();
                try (ResultSet generatedKeys = historyStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        historyId = generatedKeys.getLong(1); // 生成されたhistory_idを取得
                    } else {
                        throw new SQLException("ロールバック履歴の作成に失敗しました。IDが取得できませんでした。");
                    }
                }
            }

            // rollback_history_playersテーブルに対象プレイヤーを記録
            if (historyId != -1) {
                try (PreparedStatement playerStmt = connection.prepareStatement(insertPlayerSQL)) {
                    for (UUID playerUUID : playerUUIDs) {
                        playerStmt.setLong(1, historyId);
                        playerStmt.setString(2, playerUUID.toString());
                        playerStmt.addBatch(); // バッチ処理に追加
                    }
                    playerStmt.executeBatch(); // バッチ処理を実行
                }
            }
            connection.commit(); // トランザクションをコミット
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "ロールバックイベントの保存に失敗しました (MySQLDatabase.saveRollbackEvent)。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            try {
                connection.rollback(); // エラー発生時はロールバック
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "ロールバックイベント保存時のトランザクションロールバックに失敗しました。SQLState: " + ex.getSQLState() + ", ErrorCode: " + ex.getErrorCode(), ex);
            }
        } finally {
            try {
                connection.setAutoCommit(true); // 自動コミットモードに戻す
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "ロールバックイベント保存後の自動コミットモード復元に失敗しました。SQLState: " + ex.getSQLState() + ", ErrorCode: " + ex.getErrorCode(), ex);
            }
        }
    }

    // 最新のロールバックイベントで影響を受けたプレイヤーのUUIDリストを取得します
    @Override
    public List<UUID> getMostRecentRollbackAffectedPlayers() {
        List<UUID> affectedPlayers = new ArrayList<>();
        String getHistoryIdSQL = "SELECT history_id FROM rollback_history ORDER BY rollback_timestamp DESC LIMIT 1"; // 最新のhistory_idを取得
        String getPlayersSQL = "SELECT player_uuid FROM rollback_history_players WHERE history_id = ?"; // 指定されたhistory_idのプレイヤーUUIDを取得
        long historyId = -1;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(getHistoryIdSQL)) {
            if (rs.next()) {
                historyId = rs.getLong("history_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "最新のロールバック履歴IDの取得に失敗しました (MySQLDatabase.getMostRecentRollbackAffectedPlayers)。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            return affectedPlayers; // エラー時は空のリストを返す
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
                plugin.getLogger().log(Level.SEVERE, "ロールバック履歴ID " + historyId + " の影響プレイヤー取得に失敗しました (MySQLDatabase.getMostRecentRollbackAffectedPlayers)。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            }
        }
        return affectedPlayers;
    }

    // 最新のロールバックイベントを削除します
    @Override
    public void deleteMostRecentRollbackEvent() {
        String getHistoryIdSQL = "SELECT history_id FROM rollback_history ORDER BY rollback_timestamp DESC LIMIT 1"; // 最新のhistory_idを取得
        String deleteHistorySQL = "DELETE FROM rollback_history WHERE history_id = ?"; // 指定されたhistory_idのイベントを削除 (CASCADEにより関連プレイヤーも削除)
        long historyId = -1;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(getHistoryIdSQL)) {
            if (rs.next()) {
                historyId = rs.getLong("history_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "削除対象の最新ロールバック履歴ID取得に失敗しました (MySQLDatabase.deleteMostRecentRollbackEvent)。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            return;
        }

        if (historyId != -1) {
            try (PreparedStatement pstmt = connection.prepareStatement(deleteHistorySQL)) {
                pstmt.setLong(1, historyId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    plugin.getLogger().info("最新のロールバックイベント（history_id: " + historyId + "）をMySQLデータベースから正常に削除しました。");
                } else {
                    plugin.getLogger().warning("削除するロールバックイベントが見つからないか、history_id " + historyId + " は既にMySQLデータベースから削除されています。");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "最新のロールバックイベント（history_id: " + historyId + "）の削除に失敗しました (MySQLDatabase.deleteMostRecentRollbackEvent)。SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode(), e);
            }
        } else {
            plugin.getLogger().info("削除するロールバック履歴がMySQLデータベースに見つかりませんでした (MySQLDatabase.deleteMostRecentRollbackEvent)。");
        }
    }
}
// TODO: updateInventoryStateメソッドのタイムスタンプに関するWHERE句は調整が必要かもしれません。
// inventoryState.getTimestamp()が*新しい*タイムスタンプである場合、WHERE句は更新されるレコードの*古い*タイムスタンプを使用する必要があります。
// あるいは、player_inventoriesが同じuuidで異なるタイムスタンプを持つ複数のエントリ（履歴として機能する）を持つことができる場合、
// UUIDと特定のタイムスタンプに基づいて更新するのは正しいです。
// 現在の実装では、特定の履歴エントリを更新しているか、uuid + timestampが意図した更新対象の一意のキーであると想定しています。
// プレイヤーの*最新の*エントリを更新することが目標である場合、SQLは異なる必要があるかもしれません。
// たとえば、UUIDの最新のタイムスタンプを見つけるためのサブクエリを使用するか、uuidが一意である場合はWHEREからタイムスタンプを削除するなどです。
// ただし、メソッドのシグネチャは完全なPlayerInventoryStateを取り、どのエントリを更新しているかを知っていることを意味します。
// `deleteInventoryState`（メインテーブル用）は`AND timestamp = ?`を使用しており、タイムスタンプがキーの一部であることを示唆しています。
// したがって、現在の`updateInventoryState`のロジックは`deleteInventoryState`と一致しているように見えます。
// 新しい`deletePlayerInventoryBeforeRollback`もタイムスタンプをキーの一部として使用しており、これは一貫しています。
/*
TODO: The updateInventoryState method's WHERE clause for timestamp might need adjustment.
If inventoryState.getTimestamp() is the *new* timestamp, then the WHERE clause
should use the *old* timestamp of the record being updated.
Or, if player_inventories can have multiple entries for the same uuid but different timestamps (acting as a history),
then updating based on UUID and a specific timestamp is correct.
The current implementation assumes we are updating a specific historical entry,
or that uuid + timestamp is a unique key for the intended update target.
If the goal is to update the *latest* entry for a player, the SQL might need to be different,
e.g., using a subquery to find the latest timestamp for the UUID, or simply removing timestamp from WHERE if uuid is unique.
However, the method signature takes a full PlayerInventoryState, implying we know which one we're updating.
The `deleteInventoryState` (for main table) uses `AND timestamp = ?`, suggesting timestamp is part of the key.
So, the current `updateInventoryState` logic seems consistent with `deleteInventoryState`.
The new `deletePlayerInventoryBeforeRollback` also uses timestamp as part of the key, which is consistent.
*/

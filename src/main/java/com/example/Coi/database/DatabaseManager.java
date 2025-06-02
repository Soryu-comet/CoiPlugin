package com.example.Coi.database;

import java.util.List;
import java.util.UUID;

import com.example.Coi.models.PlayerInventoryState;

// データベースマネージャー
public interface DatabaseManager {
    void initialize();    // 初期化
    void savePlayerInventory(UUID playerUUID, String playerName, PlayerInventoryState inventoryState);    // プレイヤーのインベントリ状態を保存
    List<PlayerInventoryState> getPlayerInventory(UUID playerUUID, long timestamp);    // プレイヤーのインベントリ状態を取得
    void updateInventoryState(UUID playerUUID, PlayerInventoryState inventoryState);    // プレイヤーのインベントリ状態を更新
    void deleteInventoryState(UUID playerUUID, long timestamp);    // プレイヤーのインベントリ状態を削除
    void savePlayerInventoryBeforeRollback(UUID playerUUID, PlayerInventoryState inventoryState);    // ロールバック前のプレイヤーのインベントリ状態を保存
    List<PlayerInventoryState> getPlayerInventoryBeforeRollback(UUID playerUUID);    // ロールバック前のインベントリ状態を取得
    /**
     * Deletes a specific inventory state from the 'player_inventories_before_rollback' table.
     * Used by the undo command to clear the state that was just restored.
     *
     * @param playerUUID The UUID of the player.
     * @param timestamp The timestamp of the inventory state to delete.
     */
    void deletePlayerInventoryBeforeRollback(UUID playerUUID, long timestamp);

    /**
     * Saves a rollback event, including all players affected by it.
     *
     * @param playerUUIDs A list of UUIDs for players affected by the rollback.
     */
    void saveRollbackEvent(List<UUID> playerUUIDs);

    /**
     * Retrieves the list of player UUIDs affected by the most recent rollback event.
     *
     * @return A list of UUIDs, or an empty list/null if no history is found.
     */
    List<UUID> getMostRecentRollbackAffectedPlayers();

    /**
     * Deletes the most recent rollback event from the history.
     * This is typically called after an undo operation.
     */
    void deleteMostRecentRollbackEvent();

    void close();    // データベースを閉じる
}

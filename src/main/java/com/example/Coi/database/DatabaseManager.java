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
    void close();    // データベースを閉じる
}

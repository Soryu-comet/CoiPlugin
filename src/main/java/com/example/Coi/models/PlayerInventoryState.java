package com.example.Coi.models;

import org.bukkit.inventory.ItemStack;

// プレイヤーのインベントリ状態
public class PlayerInventoryState {
    private final long timestamp;
    private final ItemStack[] inventoryContents;
    private String playerName;
    
    public PlayerInventoryState(long timestamp, ItemStack[] inventoryContents, String playerName) {
        this.timestamp = timestamp;    // タイムスタンプ
        this.inventoryContents = inventoryContents;    // インベントリ状態
        this.playerName = playerName;    // プレイヤー名
    }

    public long getTimestamp() {
        return timestamp;    // タイムスタンプを返す
    }

    public ItemStack[] getInventoryContents() {
        return inventoryContents;    // インベントリ状態を返す
    }

    public String getPlayerName() {
        return playerName;
    }
}

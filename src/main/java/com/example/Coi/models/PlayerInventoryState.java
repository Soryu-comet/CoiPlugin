package com.example.Coi.models;

import org.bukkit.inventory.ItemStack;

public class PlayerInventoryState {
    private final long timestamp;
    private final ItemStack[] inventoryContents;
    
    public PlayerInventoryState(long timestamp, ItemStack[] inventoryContents) {
        this.timestamp = timestamp;
        this.inventoryContents = inventoryContents;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ItemStack[] getInventoryContents() {
        return inventoryContents;
    }
}

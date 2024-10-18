package com.example.Coi.database;

import java.util.List;
import java.util.UUID;

import com.example.Coi.models.PlayerInventoryState;

public interface DatabaseManager {
    void initialize();
    void savePlayerInventory(UUID playerUUID, String playerName, PlayerInventoryState inventoryState);
    List<PlayerInventoryState> getPlayerInventory(UUID playerUUID, long timestamp);
    void close();
}

package com.example.Coi.models;

import org.bukkit.inventory.ItemStack;

// プレイヤーのインベントリ状態
public class PlayerInventoryState {
    private final long timestamp;
    private final ItemStack[] inventoryContents;
    private final String playerName; // Made final as it's set in constructor
    /**
     * The player's armor contents (helmet, chestplate, leggings, boots).
     */
    private final ItemStack[] armorContents;
    /**
     * The player's extra contents, typically including the off-hand item.
     * The exact contents can vary based on Bukkit API version.
     */
    private final ItemStack[] extraContents;
    /**
     * The contents of the player's Ender Chest.
     */
    private final ItemStack[] enderChestContents;

    /**
     * Constructs a new PlayerInventoryState.
     *
     * @param timestamp The timestamp when this state was recorded.
     * @param inventoryContents The main inventory contents.
     * @param playerName The name of the player.
     * @param armorContents The player's armor contents.
     * @param extraContents The player's extra contents (e.g., off-hand).
     * @param enderChestContents The contents of the player's Ender Chest.
     */
    public PlayerInventoryState(long timestamp, ItemStack[] inventoryContents, String playerName,
                                ItemStack[] armorContents, ItemStack[] extraContents, ItemStack[] enderChestContents) {
        this.timestamp = timestamp;
        this.inventoryContents = inventoryContents;
        this.playerName = playerName;
        this.armorContents = armorContents;
        this.extraContents = extraContents;
        this.enderChestContents = enderChestContents;
    }

    /**
     * Gets the timestamp when this inventory state was recorded.
     * @return The timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the main inventory contents.
     * @return An array of ItemStacks representing the main inventory.
     */
    public ItemStack[] getInventoryContents() {
        return inventoryContents;
    }

    /**
     * Gets the name of the player.
     * @return The player's name.
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Gets the player's armor contents.
     * @return An array of ItemStacks representing the armor contents.
     */
    public ItemStack[] getArmorContents() {
        return armorContents;
    }

    /**
     * Gets the player's extra contents (e.g., off-hand).
     * @return An array of ItemStacks representing the extra contents.
     */
    public ItemStack[] getExtraContents() {
        return extraContents;
    }

    /**
     * Gets the contents of the player's Ender Chest.
     * @return An array of ItemStacks representing the Ender Chest contents.
     */
    public ItemStack[] getEnderChestContents() {
        return enderChestContents;
    }
}

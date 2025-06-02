package com.example.Coi.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
// Removed: import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // Added
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;

import java.util.List;
import java.util.UUID;

// インベントリリスナー
public class InventoryListener implements Listener {
    private final CoiPlugin plugin;

    public InventoryListener(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles saving the player's full inventory state when they close any inventory.
     * This is preferred over InventoryClickEvent to reduce the frequency of saves,
     * as it captures the state once the player is done with a series of interactions.
     * Note: This will also trigger for chests, crafting tables, etc., not just player inventory view.
     * This is generally the desired behavior to capture the most up-to-date state
     * before a potential server issue or if the player logs off immediately after.
     * @param event The inventory close event.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getPlayer();

            // Construct the full inventory state.
            PlayerInventoryState state = new PlayerInventoryState(
                    System.currentTimeMillis(),
                    player.getInventory().getContents(),        // Main inventory
                    player.getName(),
                    player.getInventory().getArmorContents(),   // Armor contents
                    player.getInventory().getExtraContents(),   // Off-hand and other extra contents
                    player.getEnderChest().getContents()      // Ender Chest contents
            );

            // Save the state asynchronously to the database.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDatabaseManager() != null) { // Ensure DatabaseManager is available
                    plugin.getDatabaseManager().savePlayerInventory(
                            player.getUniqueId(),
                            player.getName(),
                            state
                    );
                }
            });
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Player joined, load their latest saved inventory state including armor, off-hand, and Ender Chest.
        UUID playerUUID = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis(); // Used to fetch the most recent state up to this point.

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerInventoryState> inventoryStates = plugin.getDatabaseManager().getPlayerInventory(playerUUID, now);
            if (!inventoryStates.isEmpty()) {
                PlayerInventoryState latestState = inventoryStates.get(0); // Get the most recent saved state.

                // Ensure inventory modifications are done on the main server thread.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    event.getPlayer().getInventory().setContents(latestState.getInventoryContents()); // Main inventory
                    event.getPlayer().getInventory().setArmorContents(latestState.getArmorContents()); // Armor
                    event.getPlayer().getInventory().setExtraContents(latestState.getExtraContents()); // Off-hand etc.
                    event.getPlayer().getEnderChest().setContents(latestState.getEnderChestContents()); // Ender Chest
                    // Consider sending a message to the player or logging that their inventory was restored.
                });
            }
            // else: No previous inventory state found, player starts fresh or with whatever Bukkit defaults to.
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Player quit, save their final inventory state including main inventory, armor, off-hand, and Ender Chest.
        org.bukkit.entity.Player player = event.getPlayer();
        PlayerInventoryState state = new PlayerInventoryState(
                System.currentTimeMillis(),
                player.getInventory().getContents(), // Main inventory
                player.getName(),
                player.getInventory().getArmorContents(), // Armor
                player.getInventory().getExtraContents(), // Off-hand and other extra contents
                player.getEnderChest().getContents() // Ender Chest
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().savePlayerInventory(
                    player.getUniqueId(),
                    player.getName(),
                    state
            );
        });
    }
}

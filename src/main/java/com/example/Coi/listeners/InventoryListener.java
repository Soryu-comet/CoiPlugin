package com.example.Coi.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    @EventHandler
    public void onInventoryChange(InventoryClickEvent event) {
        // プレイヤーのインベントリが変更されたときの処理
        PlayerInventoryState state = new PlayerInventoryState(
                System.currentTimeMillis(),
                event.getWhoClicked().getInventory().getContents(),
                event.getWhoClicked().getName()
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().savePlayerInventory(
                    event.getWhoClicked().getUniqueId(),
                    event.getWhoClicked().getName(),
                    state
            );
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // プレイヤーがサーバーに入った時の処理
        UUID uuid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerInventoryState> inventories = plugin.getDatabaseManager().getPlayerInventory(uuid, now);
            if (!inventories.isEmpty()) {
                PlayerInventoryState latest = inventories.get(0);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    event.getPlayer().getInventory().setContents(latest.getInventoryContents());
                });
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerInventoryState state = new PlayerInventoryState(
                System.currentTimeMillis(),
                event.getPlayer().getInventory().getContents(),
                event.getPlayer().getName()
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().savePlayerInventory(
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getName(),
                    state
            );
        });
    }
}

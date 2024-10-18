package com.example.Coi.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;

public class InventoryListener implements Listener {
    private final CoiPlugin plugin;

    public InventoryListener(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryChange(InventoryClickEvent event) {
        // プレイヤーのインベントリが変更されたときの処理
        plugin.getDatabaseManager().savePlayerInventory(
            event.getWhoClicked().getUniqueId(),
            event.getWhoClicked().getName(),
            new PlayerInventoryState(System.currentTimeMillis(), event.getWhoClicked().getInventory().getContents())
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // プレイヤーがサーバーに入った時の処理
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // プレイヤーがサーバーを退出した時の処理
    }
}

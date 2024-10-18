package com.example.Coi.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;
// インベントリリスナー
public class InventoryListener implements Listener {
    private final CoiPlugin plugin;

    public InventoryListener(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryChange(InventoryClickEvent event) {
        // プレイヤーのインベントリが変更されたときの処理
        plugin.getDatabaseManager().savePlayerInventory( // プレイヤーのインベントリを保存
            event.getWhoClicked().getUniqueId(), // プレイヤーのUUID
            event.getWhoClicked().getName(), // プレイヤーの名前
            new PlayerInventoryState(System.currentTimeMillis(), event.getWhoClicked().getInventory().getContents(), event.getWhoClicked().getName()) // プレイヤーのインベントリ状態
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // プレイヤーがサーバーに入った時の処理
        plugin.getDatabaseManager().getPlayerInventory(
            event.getPlayer().getUniqueId(),
            System.currentTimeMillis()
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // プレイヤーがサーバーを退出した時の処理
        plugin.getDatabaseManager().savePlayerInventory(
            event.getPlayer().getUniqueId(),
            event.getPlayer().getName(),
            new PlayerInventoryState(System.currentTimeMillis(), event.getPlayer().getInventory().getContents(), event.getPlayer().getName())
        );
    }
}

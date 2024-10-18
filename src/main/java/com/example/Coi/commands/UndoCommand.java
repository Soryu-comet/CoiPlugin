package com.example.Coi.commands;

import java.util.List;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;

public class UndoCommand implements CommandExecutor {

    private final CoiPlugin plugin;
    public UndoCommand(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみが使用できます。");
            return false;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        try {
            List<PlayerInventoryState> inventoryStates = plugin.getDatabaseManager().getPlayerInventory(playerUUID, System.currentTimeMillis());
            if (!inventoryStates.isEmpty()) {
                PlayerInventoryState lastState = inventoryStates.get(inventoryStates.size() - 1);
                player.getInventory().setContents(lastState.getInventoryContents());
                sender.sendMessage("前回のロールバックを取り消しました。");
            } else {
                sender.sendMessage("ロールバック可能なインベントリ状態が見つかりませんでした。");
            }
        } catch (Exception e) {
            sender.sendMessage("インベントリの取得中にエラーが発生しました。");
            plugin.getLogger().severe(String.format("インベントリ取得エラー: %s", e.getMessage()));
        }
        return true;
    }
}

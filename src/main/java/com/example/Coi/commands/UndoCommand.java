package com.example.Coi.commands;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.Coi.CoiPlugin;
import com.example.Coi.database.DatabaseManager;
import com.example.Coi.models.PlayerInventoryState;
// UNDOコマンド
public class UndoCommand implements CommandExecutor {

    private final DatabaseManager databaseManager;

    public UndoCommand(CoiPlugin plugin) {
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみが使用できます。");
            return false;
        }

        if (args.length != 0) {
            sendUsageMessage(sender);
            return false;
        }

        List<UUID> lastRollback = ((CoiPlugin) Bukkit.getPluginManager().getPlugin("CoiPlugin")).getLastRollback();
        if (lastRollback == null) {
            sender.sendMessage("元に戻す履歴がないか、履歴が空です。設定ファイルからmax-historyを変更してください。");
            return false;
        }

        for (UUID playerUUID : lastRollback) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                List<PlayerInventoryState> inventoryStates = databaseManager.getPlayerInventoryBeforeRollback(playerUUID);
                if (!inventoryStates.isEmpty()) {
                    PlayerInventoryState lastState = inventoryStates.get(0);
                    player.getInventory().setContents(lastState.getInventoryContents());
                    player.sendMessage("インベントリを元に戻しました。");
                } else {
                    player.sendMessage("もとに戻すインベントリ状態が見つかりませんでした。");
                }
            }
        }

        return true;
    }

    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage("§b§m------§r");
        sender.sendMessage("§bCoi §7- §r使用方法:");
        sender.sendMessage("§bCoi §7- §r/coiundo");
        sender.sendMessage("§b§m------§r");
    }
}

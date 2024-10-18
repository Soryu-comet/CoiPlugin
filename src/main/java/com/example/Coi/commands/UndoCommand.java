package com.example.Coi.commands;

import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.example.Coi.models.PlayerInventoryState;

public class UndoCommand implements CommandExecutor {

    private final RollbackCommand rollbackCommand;
    
    public UndoCommand(RollbackCommand rollbackCommand, Plugin plugin) {
        this.rollbackCommand = rollbackCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみが使用できます。");
            return false;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        PlayerInventoryState previousState = rollbackCommand.getPreviousInventory(playerUUID);

        if (previousState != null) {
            player.getInventory().setContents(previousState.getInventoryContents());
            sender.sendMessage("前回のロールバックを取り消しました。");
        } else {
            sender.sendMessage("ロールバック可能なインベントリ状態が見つかりませんでした。");
        }

        return true;
    }
}

package com.example.Coi.commands;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;

public class RollbackCommand implements CommandExecutor {

    private final CoiPlugin plugin;

    public RollbackCommand(CoiPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !args[0].startsWith("t:")) {
            sender.sendMessage("時間を指定してください。フォーマット: t:[time]");
            return false;
        }

        long rollbackTime = parseTime(args[0].substring(2));
        if (rollbackTime == -1) {
            sender.sendMessage("時間フォーマットが無効です。例: t:30m");
            return false;
        }

        // プレイヤーや範囲の指定を解析 (省略)
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            long rollbackTimestamp = System.currentTimeMillis() - rollbackTime;
            List<PlayerInventoryState> inventoryStates = plugin.getDatabaseManager().getPlayerInventory(playerUUID, rollbackTimestamp);
            if (!inventoryStates.isEmpty()) {
                PlayerInventoryState lastState = inventoryStates.get(inventoryStates.size() - 1);
                player.getInventory().setContents(lastState.getInventoryContents());
                sender.sendMessage(player.getName() + " のインベントリをロールバックしました。");
            }
        }
        return true;
    }

    private long parseTime(String timeArg) {
        long time = 0;
        String[] units = timeArg.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < units.length - 1; i += 2) {
            int value = Integer.parseInt(units[i]);
            String unit = units[i + 1];
            switch (unit) {
                case "w": time += TimeUnit.DAYS.toMillis(value * 7); break;
                case "d": time += TimeUnit.DAYS.toMillis(value); break;
                case "h": time += TimeUnit.HOURS.toMillis(value); break;
                case "m": time += TimeUnit.MINUTES.toMillis(value); break;
                case "s": time += TimeUnit.SECONDS.toMillis(value); break;
                default: return -1;
            }
        }
        return time;
    }
}

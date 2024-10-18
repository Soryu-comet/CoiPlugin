package com.example.Coi.commands;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;

public class RollbackCommand implements CommandExecutor {

    private final CoiPlugin plugin;
    private final Map<UUID, PlayerInventoryState> previousInventories = new HashMap<>();
    private int changedBlocks = 0;      
    private int changedChunks = 0;
    private int changedItems = 0;

    public RollbackCommand(CoiPlugin plugin) {
        this.plugin = plugin;
    }
    private long startTime;
    private final int radius = 0;
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

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            previousInventories.put(playerUUID, new PlayerInventoryState(System.currentTimeMillis(), player.getInventory().getContents()));
            long rollbackTimestamp = System.currentTimeMillis() - rollbackTime;
            List<PlayerInventoryState> inventoryStates = plugin.getDatabaseManager().getPlayerInventory(playerUUID, rollbackTimestamp);
            if (!inventoryStates.isEmpty()) {
                PlayerInventoryState lastState = inventoryStates.get(inventoryStates.size() - 1);
                player.getInventory().setContents(lastState.getInventoryContents());
                sender.sendMessage(player.getName() + " のインベントリをロールバックしました。");
                changedBlocks += player.getInventory().getSize(); // 仮に全インベントリが変更されたとする
                changedChunks++; // 仮に1チャンク変更とみなす
                changedItems += calculateChangedItems(player.getInventory().getContents(), lastState.getInventoryContents());
            } else {
                sender.sendMessage(player.getName() + " のロールバック可能なインベントリ状態が見つかりませんでした。");
            }
        }
        long timeTaken = (System.currentTimeMillis() - startTime) / 1000; // 処理時間（秒）
        logRollbackResult(sender, "world", radius, rollbackTime / 60000, changedBlocks, changedChunks, timeTaken, changedItems); // 結果のログ出力
        return true;
    }

    private int calculateChangedItems(ItemStack[] currentInventory, ItemStack[] previousInventory) {
        int changedItemCount = 0;
        for (int i = 0; i < currentInventory.length; i++) {
            ItemStack currentItem = currentInventory[i];
            ItemStack previousItem = previousInventory[i];
            if ((currentItem == null && previousItem != null) || 
                (currentItem != null && !currentItem.equals(previousItem))) {
                changedItemCount++;
            }
        }
        return changedItemCount;
    }

    public void logRollbackResult(CommandSender sender, String worldName, int radius, long timeMinutes, int changedBlocks, int changedChunks, long timeTaken, int changedItems) {
        sender.sendMessage("§b§m------§r");
        sender.sendMessage("§bCoi §7- §rRollbackは\"§b#" + worldName + "§r\"で開始されました。");
        sender.sendMessage("§bCoi §7- §r修正する " + changedChunks + " chunks が見つかりました。");
        sender.sendMessage("§b§m------§r");
        sender.sendMessage("§bCoi §7- §r\"" + worldName + "\" の Rollbackが完了しました。");
        sender.sendMessage("§bCoi §7- §r時間範囲: " + timeMinutes + " 分");
        sender.sendMessage("§bCoi §7- §r半径: " + radius + " blocks");
        sender.sendMessage("§bCoi §7- §r変更済み: " + changedBlocks + " blocks, " + changedChunks + " chunks");
        sender.sendMessage("§bCoi §7- §r変更したアイテム数: " + changedItems + " items");
        sender.sendMessage("§bCoi §7- §r所要時間: " + timeTaken + " seconds");
        sender.sendMessage("§b§m------§r");
    }
    
    public PlayerInventoryState getPreviousInventory(UUID playerUUID) {
        return previousInventories.get(playerUUID);
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

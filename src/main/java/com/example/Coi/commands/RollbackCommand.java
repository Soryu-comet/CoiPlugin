package com.example.Coi.commands;

import java.util.ArrayList;
import java.util.List;
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
// ロールバックコマンド
public class RollbackCommand implements CommandExecutor {

    private final CoiPlugin plugin;
    private int changedItems = 0;
    private long startTime;

    public RollbackCommand(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみが使用できます。");
            return false;
        }

        Player senderPlayer = (Player) sender;

        if (args.length < 1 || !args[0].startsWith("t:")) {    // 時間を指定
            sendUsageMessage(sender);
            return false;
        }

        long rollbackTime = parseTime(args[0].substring(2));    // 時間をパース
        if (rollbackTime == -1) {
            sender.sendMessage("時間フォーマットが無効です。例: t:30m");
            sendUsageMessage(sender);
            return false;
        }

        int radius = -1; // デフォルトで無制限
        List<String> specifiedPlayers = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("r:")) {
                try {
                    radius = Integer.parseInt(args[i].substring(2));
                } catch (NumberFormatException e) {
                    sender.sendMessage("半径フォーマットが無効です。例: r:100");
                    sendUsageMessage(sender);
                    return false;
                }
            } else if (args[i].startsWith("u:")) {
                specifiedPlayers.add(args[i].substring(2));
            }
        }

        startTime = System.currentTimeMillis();    // 開始時間を記録
        long rollbackTimestamp = System.currentTimeMillis() - rollbackTime;    // ロールバック時間を計算

        List<UUID> affectedPlayers = new ArrayList<>();

        for (Player targetPlayer : Bukkit.getOnlinePlayers()) {    // オンラインプレイヤーを取得
            if (!specifiedPlayers.isEmpty() && !specifiedPlayers.contains(targetPlayer.getName())) {
                continue; // 指定されたプレイヤーに含まれていない場合はスキップ
            }

            if (radius != -1 && senderPlayer.getLocation().distance(targetPlayer.getLocation()) > radius) {
                continue; // 指定された半径外のプレイヤーはスキップ
            }

            UUID targetPlayerUUID = targetPlayer.getUniqueId();
            
            // ロールバック前のインベントリを保存
            PlayerInventoryState currentState = new PlayerInventoryState(System.currentTimeMillis(), targetPlayer.getInventory().getContents(), targetPlayer.getName());
            plugin.getDatabaseManager().savePlayerInventoryBeforeRollback(targetPlayerUUID, currentState);
            
            List<PlayerInventoryState> inventoryStates = plugin.getDatabaseManager().getPlayerInventory(targetPlayerUUID, rollbackTimestamp);    // インベントリ状態を取得
            if (!inventoryStates.isEmpty()) {    // インベントリ状態が存在する場合
                PlayerInventoryState lastState = inventoryStates.get(0);    // 最後のインベントリ状態を取得
                changedItems += calculateChangedItems(targetPlayer.getInventory().getContents(), lastState.getInventoryContents());    // 変更されたアイテム数を計算
                targetPlayer.getInventory().setContents(lastState.getInventoryContents());    // インベントリをロールバック
                sender.sendMessage(targetPlayer.getName() + " のインベントリをロールバックしました。");    // ロールバック完了メッセージを送信
                affectedPlayers.add(targetPlayerUUID); // 影響を受けたプレイヤーを記録
            } else {
                sender.sendMessage(targetPlayer.getName() + " のロールバック可能なインベントリ状態が見つかりませんでした。");    // ロールバック可能なインベントリ状態が見つからない場合のメッセージを送信
            }
        }

        plugin.addRollbackHistory(affectedPlayers); // ロールバック履歴に追加
        logRollbackResult(sender, rollbackTime / 60000, changedItems);
        return true;
    }

    // 変更されたアイテム数を計算
    private int calculateChangedItems(ItemStack[] currentInventory, ItemStack[] previousInventory) {
        int changedItemCount = 0;
        for (int i = 0; i < currentInventory.length; i++) {
            ItemStack currentItem = currentInventory[i];
            ItemStack previousItem = previousInventory[i];
            if ((currentItem == null && previousItem != null) || 
                (currentItem != null && !currentItem.equals(previousItem))) {    // アイテムが変更されている場合
                changedItemCount++;
            }
        }
        return changedItemCount;
    }

    // ロールバック結果をログに出力
    public void logRollbackResult(CommandSender sender, long timeMinutes, int changedItems) {
        sender.sendMessage("§b§m------§r");
        sender.sendMessage("§bCoi §7- §rRollbackが完了しました。");
        sender.sendMessage("§bCoi §7- §r時間範囲: " + (timeMinutes * 60) + " 秒");
        sender.sendMessage("§bCoi §7- §r変更したアイテム数: " + changedItems + " items");
        sender.sendMessage("§bCoi §7- §r所要時間: " + String.format("%.5f", (System.currentTimeMillis() - startTime) / 1000.0) + " 秒");
        sender.sendMessage("§b§m------§r");
    }

    // 時間をパース
    private long parseTime(String timeArg) {
        long time = 0;
        String[] units = timeArg.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < units.length - 1; i += 2) {
            int value = Integer.parseInt(units[i]);
            String unit = units[i + 1];
            switch (unit) {
                case "w": time += TimeUnit.DAYS.toMillis(value * 7); break;    // 週
                case "d": time += TimeUnit.DAYS.toMillis(value); break;    // 日
                case "h": time += TimeUnit.HOURS.toMillis(value); break;    // 時
                case "m": time += TimeUnit.MINUTES.toMillis(value); break;    // 分
                case "s": time += TimeUnit.SECONDS.toMillis(value); break;    // 秒
                default: return -1;
            }
        }
        return time;
    }

    // 使用方法を表示
    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage("§b§m------§r");
        sender.sendMessage("§bCoi §7- §r使用方法:");
        sender.sendMessage("§bCoi §7- §r/rollback t:[time] (例: t:30m)");
        sender.sendMessage("§bCoi §7- §rオプション:");
        sender.sendMessage("§bCoi §7- §rr:[radius] (例: r:100)");
        sender.sendMessage("§bCoi §7- §ru:[player] (例: u:playername)");
        sender.sendMessage("§b§m------§r");
    }
}

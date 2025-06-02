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
import org.bukkit.OfflinePlayer; // Added for offline player handling

import com.example.Coi.CoiPlugin;
import com.example.Coi.models.PlayerInventoryState;
import java.util.logging.Level;

// ロールバックコマンド
public class RollbackCommand implements CommandExecutor {

    private final CoiPlugin plugin;
    // private int changedItems = 0; // 以前の変更アイテムカウンター、新しい統計に置き換えられます
    private long startTime;
    private static final String PREFIX_ERROR = "§c[CoiPluginError] ";
    private static final String PREFIX_INFO = "§a[CoiPlugin] ";

    public RollbackCommand(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // General try-catch block for unexpected exceptions
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX_ERROR + "このコマンドはプレイヤーのみが使用できます。");
                return false;
            }

            Player senderPlayer = (Player) sender;

            // Argument parsing: time
            if (args.length < 1 || !args[0].startsWith("t:")) {
                sendUsageMessage(sender);
                return false;
            }

            long rollbackTime = parseTime(args[0].substring(2));
            if (rollbackTime == -1) {
                sender.sendMessage(PREFIX_ERROR + "時間フォーマットが無効です。例: t:30m");
                sendUsageMessage(sender);
                return false;
            }

            // Argument parsing: radius and specified players
            int radius = -1; // Default: no radius limit
            List<String> specifiedPlayers = new ArrayList<>();

            for (int i = 1; i < args.length; i++) {
                if (args[i].startsWith("r:")) {
                    try {
                        radius = Integer.parseInt(args[i].substring(2));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(PREFIX_ERROR + "半径フォーマットが無効です。例: r:100");
                        sendUsageMessage(sender);
                        return false;
                    }
                } else if (args[i].startsWith("u:")) {
                    specifiedPlayers.add(args[i].substring(2));
                }
            }

            // Check if DatabaseManager is available
            if (plugin.getDatabaseManager() == null) {
                sender.sendMessage(PREFIX_ERROR + "データベースが利用できません。プラグインの初期化に失敗した可能性があります。");
                plugin.getLogger().log(Level.WARNING, "RollbackCommand: DatabaseManager is null. Database might not have initialized correctly.");
                return true; // Return true to prevent usage message spam if DB is down
            }

            startTime = System.currentTimeMillis(); // Record start time for performance measurement
            long rollbackTimestamp = System.currentTimeMillis() - rollbackTime; // Calculate the exact timestamp to roll back to

            List<UUID> affectedPlayers = new ArrayList<>();
            List<String> processedPlayerNames = new ArrayList<>(); // 処理済みのオンラインプレイヤー名を追跡
            int successfulRollbacks = 0;
            int totalChangedSlots = 0;       // 総変更スロット数
            long totalNetItemCountChange = 0L; // アイテム総数の実質増減

            // Iterate through online players to perform rollback
            for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
                boolean isSpecifiedUser = specifiedPlayers.contains(targetPlayer.getName());
                
                // If u:player is used, only process that player if they are online
                if (!specifiedPlayers.isEmpty() && !isSpecifiedUser) {
                    continue; 
                }

                // Skip if player is outside the specified radius (if radius is used AND no specific user is targeted)
                // If a specific user u:playername is given, radius r: is ignored for that user.
                if (specifiedPlayers.isEmpty() && radius != -1 && senderPlayer.getWorld().equals(targetPlayer.getWorld()) && senderPlayer.getLocation().distance(targetPlayer.getLocation()) > radius) {
                    continue;
                }

                UUID targetPlayerUUID = targetPlayer.getUniqueId();
                processedPlayerNames.add(targetPlayer.getName()); // Mark as processed
                // Save current full inventory state before rollback for potential undo
                PlayerInventoryState currentState = new PlayerInventoryState(
                        System.currentTimeMillis(),
                        targetPlayer.getInventory().getContents(),
                        targetPlayer.getName(),
                        targetPlayer.getInventory().getArmorContents(),
                        targetPlayer.getInventory().getExtraContents(),
                        targetPlayer.getEnderChest().getContents()
                );
                plugin.getDatabaseManager().savePlayerInventoryBeforeRollback(targetPlayerUUID, currentState);

                // Retrieve inventory states before or at the rollback timestamp
                List<PlayerInventoryState> inventoryStates = plugin.getDatabaseManager().getPlayerInventory(targetPlayerUUID, rollbackTimestamp);
                if (!inventoryStates.isEmpty()) {
                    PlayerInventoryState lastState = inventoryStates.get(0); // Get the most recent state matching the criteria
                    
                    // インベントリ各部分の変更を計算
                    InventoryChangeStats mainInvChanges = calculateInventoryPartChanges(targetPlayer.getInventory().getContents(), lastState.getInventoryContents());
                    InventoryChangeStats armorChanges = calculateInventoryPartChanges(targetPlayer.getInventory().getArmorContents(), lastState.getArmorContents());
                    InventoryChangeStats extraChanges = calculateInventoryPartChanges(targetPlayer.getInventory().getExtraContents(), lastState.getExtraContents());
                    InventoryChangeStats enderChestChanges = calculateInventoryPartChanges(targetPlayer.getEnderChest().getContents(), lastState.getEnderChestContents());

                    // 統計を合算
                    totalChangedSlots += mainInvChanges.changedSlots + armorChanges.changedSlots + extraChanges.changedSlots + enderChestChanges.changedSlots;
                    totalNetItemCountChange += mainInvChanges.netItemCountChange + armorChanges.netItemCountChange + extraChanges.netItemCountChange + enderChestChanges.netItemCountChange;
                    
                    // Perform rollback for all inventory parts
                    targetPlayer.getInventory().setContents(lastState.getInventoryContents());
                    targetPlayer.getInventory().setArmorContents(lastState.getArmorContents());
                    targetPlayer.getInventory().setExtraContents(lastState.getExtraContents());
                    targetPlayer.getEnderChest().setContents(lastState.getEnderChestContents());
                    
                    sender.sendMessage(PREFIX_INFO + targetPlayer.getName() + " のインベントリ(装備、エンダーチェスト含む)を " + formatTime(rollbackTime) + " 前の状態にロールバックしました。");
                    affectedPlayers.add(targetPlayerUUID);
                    successfulRollbacks++;
                } else {
                    sender.sendMessage(PREFIX_INFO + targetPlayer.getName() + " のロールバック可能なインベントリ状態が見つかりませんでした。");
                }
            }

            // Handle specified offline players
            if (!specifiedPlayers.isEmpty()) {
                for (String playerName : specifiedPlayers) {
                    if (processedPlayerNames.contains(playerName)) {
                        continue; // Already processed as an online player
                    }

                    @SuppressWarnings("deprecation") // Using deprecated getOfflinePlayer(String) for now
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

                    if (offlinePlayer != null && (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline())) { // isOnline check is redundant due to processedPlayerNames but safe
                        UUID targetUUID = offlinePlayer.getUniqueId();

                        // 1. Save current state for undo (fetch latest from DB)
                        List<PlayerInventoryState> currentStatesForUndo = plugin.getDatabaseManager().getPlayerInventory(targetUUID, System.currentTimeMillis());
                        if (!currentStatesForUndo.isEmpty()) {
                            PlayerInventoryState actualCurrentState = currentStatesForUndo.get(0);
                            // Ensure the timestamp for "before_rollback" is current, not the old one from the record
                             PlayerInventoryState stateToSaveForUndo = new PlayerInventoryState(
                                System.currentTimeMillis(),
                                actualCurrentState.getInventoryContents(),
                                offlinePlayer.getName(), // Use current name if available, else from record
                                actualCurrentState.getArmorContents(),
                                actualCurrentState.getExtraContents(),
                                actualCurrentState.getEnderChestContents()
                            );
                            plugin.getDatabaseManager().savePlayerInventoryBeforeRollback(targetUUID, stateToSaveForUndo);
                        } else {
                            sender.sendMessage(PREFIX_INFO + "オフラインプレイヤー " + playerName + " の現在の状態をバックアップ（undo用）できませんでした。データベースに記録がありません。");
                            // Continue to attempt rollback anyway, but undo might not be perfect for this player.
                        }
                        
                        // 2. Fetch state to roll back to
                        List<PlayerInventoryState> statesToRollbackTo = plugin.getDatabaseManager().getPlayerInventory(targetUUID, rollbackTimestamp);
                        if (!statesToRollbackTo.isEmpty()) {
                            PlayerInventoryState stateToApply = statesToRollbackTo.get(0);

                            // 3. Save this rolled-back state as the new "current" state for the offline player
                            // We use a *new* timestamp for this record to mark it as the latest.
                            PlayerInventoryState effectiveRolledBackState = new PlayerInventoryState(
                                    System.currentTimeMillis(), // New timestamp for this entry
                                    stateToApply.getInventoryContents(),
                                    offlinePlayer.getName() != null ? offlinePlayer.getName() : stateToApply.getPlayerName(), // Prefer current name
                                    stateToApply.getArmorContents(),
                                    stateToApply.getExtraContents(),
                                    stateToApply.getEnderChestContents()
                            );
                            plugin.getDatabaseManager().savePlayerInventory(targetUUID, effectiveRolledBackState.getPlayerName(), effectiveRolledBackState);
                            
                            sender.sendMessage(PREFIX_INFO + "オフラインプレイヤー " + playerName + " のインベントリがロールバックされました。次回ログイン時に適用されます。");
                            affectedPlayers.add(targetUUID);
                            successfulRollbacks++;
                            // Note: changedItems is not calculated for offline players for simplicity here
                        } else {
                            sender.sendMessage(PREFIX_INFO + "オフラインプレイヤー " + playerName + " のロールバック可能なインベントリ状態が見つかりませんでした。");
                        }
                    } else {
                        sender.sendMessage(PREFIX_ERROR + "指定されたプレイヤー '" + playerName + "' は見つからないか、一度もサーバーでプレイしていません。");
                    }
                }
            }

            if (successfulRollbacks > 0) {
                plugin.addRollbackHistory(affectedPlayers); // Add to rollback history for undo command
            }
            logRollbackResult(sender, rollbackTime, totalChangedSlots, totalNetItemCountChange, successfulRollbacks);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error executing rollback command", e);
            sender.sendMessage(PREFIX_ERROR + "ロールバックの実行中に予期せぬエラーが発生しました。詳細はサーバーログを確認してください。");
            return true; // Return true to indicate the command was handled, even if an error occurred
        }
    }

    // インベントリ変更統計情報を保持する内部クラス
    private static class InventoryChangeStats {
        int changedSlots; // 変更があったスロットの数
        long netItemCountChange; // アイテム総数の実質的な増減

        InventoryChangeStats(int changedSlots, long netItemCountChange) {
            this.changedSlots = changedSlots;
            this.netItemCountChange = netItemCountChange;
        }
    }

    /**
     * インベントリの特定の部分（メイン、鎧など）の変更を計算します。
     * @param currentPart 現在のアイテム配列
     * @param previousPart 以前のアイテム配列
     * @return InventoryChangeStats 変更されたスロット数とアイテム総数の実質増減
     */
    private InventoryChangeStats calculateInventoryPartChanges(ItemStack[] currentPart, ItemStack[] previousPart) {
        int changedSlots = 0;
        long netItemCountChange = 0;

        // nullの場合は空の配列として扱う
        currentPart = (currentPart == null) ? new ItemStack[0] : currentPart;
        previousPart = (previousPart == null) ? new ItemStack[0] : previousPart;

        int maxLength = Math.max(currentPart.length, previousPart.length);

        for (int i = 0; i < maxLength; i++) {
            ItemStack currentItem = (i < currentPart.length) ? currentPart[i] : null;
            ItemStack previousItem = (i < previousPart.length) ? previousPart[i] : null;

            boolean currentExists = currentItem != null && currentItem.getAmount() > 0;
            boolean previousExists = previousItem != null && previousItem.getAmount() > 0;

            // スロットの変更判定 (アイテムの種類、耐久度、エンチャントなどが異なるか、存在状態が変わったか)
            if (currentExists != previousExists) { // どちらか一方のみ存在
                changedSlots++;
            } else if (currentExists && !currentItem.isSimilar(previousItem)) { // 両方存在するが、内容が異なる (isSimilarは個数を含まない比較)
                changedSlots++;
            }
            
            // アイテム総数の実質増減を計算
            netItemCountChange += (currentExists ? currentItem.getAmount() : 0) - (previousExists ? previousItem.getAmount() : 0);
        }
        return new InventoryChangeStats(changedSlots, netItemCountChange);
    }

    // ロールバック結果をコマンド送信者にログ出力します
    public void logRollbackResult(CommandSender sender, long timeMillis, int totalChangedSlots, long totalNetItemCountChange, int successfulRollbacks) {
        sender.sendMessage("§e=====[ Coi Rollback 結果 ]=====");
        sender.sendMessage(PREFIX_INFO + "ロールバック処理が完了しました。");
        sender.sendMessage(PREFIX_INFO + "影響を受けたプレイヤー数: " + successfulRollbacks);
        sender.sendMessage(PREFIX_INFO + "ロールバック対象時間: " + formatTime(timeMillis) + " 前");
        sender.sendMessage(PREFIX_INFO + "変更されたスロット数 (全体): " + totalChangedSlots + " スロット");
        sender.sendMessage(PREFIX_INFO + "アイテム総数の実質増減 (全体): " + totalNetItemCountChange + " 個");
        sender.sendMessage(PREFIX_INFO + "処理所要時間: " + String.format("%.3f", (System.currentTimeMillis() - startTime) / 1000.0) + " 秒");
        sender.sendMessage("§e============================");
    }

    // 時間文字列 (例: "30m", "1h30m", "2d12h") をミリ秒にパースします
    private long parseTime(String timeArg) {
        // 時間文字列 (例: "30m", "1h30m", "2d12h") をミリ秒にパースします
        long totalMillis = 0;
        String currentNumber = "";
        for (char ch : timeArg.toCharArray()) {
            if (Character.isDigit(ch)) {
                currentNumber += ch;
            } else {
                if (currentNumber.isEmpty()) {
                    // 数字なしの単位 (例: "m", "hm") は無効
                    return -1;
                }
                long val;
                try {
                    val = Long.parseLong(currentNumber);
                } catch (NumberFormatException e) {
                    return -1; // 数字でなければ発生しないはず
                }
                currentNumber = ""; // 次の数字部分のためにリセット
                switch (Character.toLowerCase(ch)) {
                    case 'w': totalMillis += TimeUnit.DAYS.toMillis(val * 7); break; // 週
                    case 'd': totalMillis += TimeUnit.DAYS.toMillis(val); break;     // 日
                    case 'h': totalMillis += TimeUnit.HOURS.toMillis(val); break;    // 時
                    case 'm': totalMillis += TimeUnit.MINUTES.toMillis(val); break;  // 分
                    case 's': totalMillis += TimeUnit.SECONDS.toMillis(val); break;  // 秒
                    default: return -1; // 不明な単位
                }
            }
        }
        // 末尾に単位なしの数字が残っている場合はエラー (例: "30m10")
        if (!currentNumber.isEmpty()) {
            return -1;
        }
        return totalMillis > 0 ? totalMillis : -1; // 正の時間が指定されていることを確認
    }

    // 使用方法を表示
    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage("§e=====[ Coi Rollback ヘルプ ]====="); // Changed header
        sender.sendMessage(PREFIX_INFO + "使用方法: /coi t:<時間> [オプション]");
        sender.sendMessage(PREFIX_INFO + "例: /coi t:30m r:100 (半径100ブロック内のプレイヤーを30分ロールバック)");
        sender.sendMessage(PREFIX_INFO + "例: /coi t:1h u:プレイヤー名 (プレイヤー名を1時間ロールバック)");
        sender.sendMessage("§b時間単位:§r (組み合わせ可能)");
        sender.sendMessage("  §ew§r - 週, §ed§r - 日, §eh§r - 時間, §em§r - 分, §es§r - 秒");
        sender.sendMessage("  時間指定例: §et:1d12h30m§r (1日12時間30分)");
        sender.sendMessage("§bオプション:§r");
        sender.sendMessage("  §er:<半径>§r - コマンド実行者を中心とした半径 (オンラインプレイヤーのみ対象)");
        sender.sendMessage("  §eu:<プレイヤー名>§r - 特定のプレイヤー (オフラインでも可、一人まで指定可能)");
        sender.sendMessage("§e================================"); // Changed footer
    }

    // Helper method to format time from milliseconds to a readable string
    private String formatTime(long millis) {
        if (millis < 0) return "N/A";
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("日 ");
        if (hours > 0) sb.append(hours).append("時間 ");
        if (minutes > 0) sb.append(minutes).append("分 ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("秒");
        
        return sb.toString().trim().isEmpty() ? "0秒" : sb.toString().trim();
    }
}

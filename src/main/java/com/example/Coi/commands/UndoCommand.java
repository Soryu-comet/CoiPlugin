package com.example.Coi.commands;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer; // Added for offline player handling

import com.example.Coi.CoiPlugin;
import com.example.Coi.database.DatabaseManager;
import com.example.Coi.models.PlayerInventoryState;
import java.util.logging.Level;

// UNDOコマンド
public class UndoCommand implements CommandExecutor {

    private final CoiPlugin plugin;
    private final DatabaseManager databaseManager;
    private static final String PREFIX_ERROR = "§c[CoiPluginError] ";
    private static final String PREFIX_INFO = "§a[CoiPlugin] ";

    public UndoCommand(CoiPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // General try-catch block for unexpected exceptions
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX_ERROR + "このコマンドはプレイヤーのみが使用できます。");
                return false;
            }

            // Command argument validation
            if (args.length != 0) {
                sendUsageMessage(sender);
                return false;
            }

            // Check if DatabaseManager is available
            if (databaseManager == null) {
                sender.sendMessage(PREFIX_ERROR + "データベースが利用できません。プラグインの初期化に失敗した可能性があります。");
                plugin.getLogger().log(Level.WARNING, "UndoCommand: DatabaseManager is null. Database might not have initialized correctly.");
                return true;
            }

            // Retrieve the list of players affected by the last rollback
            List<UUID> lastRollbackAffectedPlayers = plugin.getLastRollback();
            if (lastRollbackAffectedPlayers == null || lastRollbackAffectedPlayers.isEmpty()) {
                sender.sendMessage(PREFIX_INFO + "元に戻すための直近のロールバック履歴が見つかりませんでした。(データベースに履歴がないか、取得に失敗しました)");
                return true;
            }

            int successfulUndos = 0;
            int attemptedUndos = lastRollbackAffectedPlayers.size();

            // Iterate through players from the last rollback and attempt to undo their inventory changes
            for (UUID playerUUID : lastRollbackAffectedPlayers) {
                Player player = Bukkit.getPlayer(playerUUID); // Get player object
                if (player != null && player.isOnline()) { // Ensure player is online
                    // Retrieve the inventory state saved before the rollback
                    List<PlayerInventoryState> inventoryStates = databaseManager.getPlayerInventoryBeforeRollback(playerUUID);
                    if (!inventoryStates.isEmpty()) {
                        PlayerInventoryState stateToRestore = inventoryStates.get(0); // Get the most recent pre-rollback state
                        
                        // Restore all parts of the inventory
                        player.getInventory().setContents(stateToRestore.getInventoryContents());
                        player.getInventory().setArmorContents(stateToRestore.getArmorContents());
                        player.getInventory().setExtraContents(stateToRestore.getExtraContents());
                        player.getEnderChest().setContents(stateToRestore.getEnderChestContents());
                        
                        player.sendMessage(PREFIX_INFO + "あなたのインベントリ(装備、エンダーチェスト含む)がロールバック前の状態に復元されました。");
                        successfulUndos++;
                    } else {
                        // This message goes to the player whose inventory couldn't be restored
                        player.sendMessage(PREFIX_ERROR + "あなたのインベントリを元に戻すための状態が見つかりませんでした。");
                        // Inform sender as well
                        sender.sendMessage(PREFIX_INFO + player.getName() + " のインベントリを元に戻すための状態が見つかりませんでした。");
                    }
                } else { // Player is offline or Bukkit.getPlayer(playerUUID) returned null
                    @SuppressWarnings("deprecation")
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                    String offlinePlayerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : playerUUID.toString();

                    List<PlayerInventoryState> inventoryStates = databaseManager.getPlayerInventoryBeforeRollback(playerUUID);
                    if (!inventoryStates.isEmpty()) {
                        PlayerInventoryState stateToRestore = inventoryStates.get(0);

                        // Save this "undone" state as the new current state for the offline player with a new timestamp
                        PlayerInventoryState effectiveUndoneState = new PlayerInventoryState(
                                System.currentTimeMillis(), // New timestamp for this "current" entry
                                stateToRestore.getInventoryContents(),
                                offlinePlayerName,
                                stateToRestore.getArmorContents(),
                                stateToRestore.getExtraContents(),
                                stateToRestore.getEnderChestContents()
                        );
                        databaseManager.savePlayerInventory(playerUUID, offlinePlayerName, effectiveUndoneState);
                        sender.sendMessage(PREFIX_INFO + "オフラインプレイヤー " + offlinePlayerName + " のインベントリがロールバック前の状態に復元され、データベースに保存されました。次回ログイン時に適用されます。");
                        successfulUndos++;
                    } else {
                        sender.sendMessage(PREFIX_ERROR + "オフラインプレイヤー " + offlinePlayerName + " のインベントリを元に戻すための状態が見つかりませんでした。");
                    }
                }
            }
            
            // Clear the last pre-rollback inventories for ALL processed (or attempted) players in the history.
            // This part needs to be carefully reviewed and potentially use a new DB method.
            // For now, it's deleting from the main 'player_inventories' table which is incorrect for this purpose.
            // This will be addressed in Phase 2 (adding deletePlayerInventoryBeforeRollback).
            // For this commit, I will comment out the problematic deletion and add a TODO.
            // TODO: Implement correct deletion from 'player_inventories_before_rollback' table. -> This is now done.
            for (UUID playerUUID : lastRollbackAffectedPlayers) {
                 // We need to fetch the state again to get its timestamp for deletion,
                 // or pass the state/timestamp from the loop above.
                 // For simplicity, let's assume the first state in the list is the one we restored.
                 // This assumes getPlayerInventoryBeforeRollback still returns them in order if multiple exist,
                 // and that we only ever restore and delete the most recent one.
                 List<PlayerInventoryState> statesAvailableForUndo = databaseManager.getPlayerInventoryBeforeRollback(playerUUID);
                 if (!statesAvailableForUndo.isEmpty()) {
                    // Delete the state that was just used for restoration from the "before_rollback" table.
                    databaseManager.deletePlayerInventoryBeforeRollback(playerUUID, statesAvailableForUndo.get(0).getTimestamp());
                 }
            }

            // Send summary message to the command sender
            if (attemptedUndos > 0) {
                sender.sendMessage(String.format(PREFIX_INFO + "%d 人のプレイヤーのインベントリ取り消し処理を試み、%d 人成功しました。", attemptedUndos, successfulUndos));
            }
            // Clear the history so it cannot be undone again
            plugin.clearLastRollback(); 

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error executing undo command", e);
            sender.sendMessage(PREFIX_ERROR + "undoコマンドの実行中に予期せぬエラーが発生しました。詳細はサーバーログを確認してください。");
            return true; // Return true to indicate the command was handled
        }
    }

    // Sends command usage instructions to the sender
    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage("§b§m---------------------[ Coi Undo Help ]---------------------§r");
        sender.sendMessage(PREFIX_INFO + "Usage: /coiundo");
        sender.sendMessage(PREFIX_INFO + "Attempts to undo the effects of the last /rollback command.");
        sender.sendMessage(PREFIX_INFO + "This restores inventories of affected players to the state they were in just before the rollback.");
        sender.sendMessage("§b§m----------------------------------------------------§r");
    }
}

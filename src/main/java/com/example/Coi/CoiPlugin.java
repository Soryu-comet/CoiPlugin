package com.example.Coi;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level; // Added for periodic save logging
// Removed Deque and LinkedList imports as they are no longer used for rollbackHistory

import org.bukkit.Bukkit; // Added for Bukkit.getOnlinePlayers()
import org.bukkit.entity.Player; // Added for Player type in periodic save
import org.bukkit.plugin.java.JavaPlugin;

import com.example.Coi.commands.RollbackCommand;
import com.example.Coi.commands.UndoCommand;
import com.example.Coi.models.PlayerInventoryState; // Added for periodic save
import com.example.Coi.database.DatabaseManager;
import com.example.Coi.database.MySQLDatabase;
import com.example.Coi.database.SQLiteDatabase;
import com.example.Coi.listeners.InventoryListener;
import com.example.Coi.tabcompleter.TabCompleter;

// プラグインクラス
public class CoiPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;    // データベースマネージャー
    // Removed: private final Deque<List<UUID>> rollbackHistory = new LinkedList<>();
    // Removed: private int maxHistory; 

    @Override
    public void onEnable() {
        saveDefaultConfig();    // デフォルトの設定ファイルを保存
        // Removed: maxHistory = getConfig().getInt("rollback.max-history", 5); 
        setupDatabase();    // データベースを初期化
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);    // インベントリリスナーを登録
        RollbackCommand rollbackCommand = new RollbackCommand(this);
        getCommand("coi").setExecutor(rollbackCommand);    // リカバリコマンドを登録
        getCommand("coi").setTabCompleter(new TabCompleter(this));    // タブ補完を登録
        UndoCommand undoCommand = new UndoCommand(this);    // UNDOコマンドを登録
        getCommand("coiundo").setExecutor(undoCommand);    // UNDOコマンドを登録
        getCommand("coiundo").setTabCompleter(new TabCompleter(this));    // タブ補完を登録
        
        setupPeriodicSaving(); // Setup periodic saving task

        getLogger().info("CoiPlugin has been enabled.");    // プラグインが有効になったことをログに出力
    }

    @Override
    public void onDisable() {
        getLogger().info("Cancelling CoiPlugin tasks...");
        Bukkit.getScheduler().cancelTasks(this); // Cancel all tasks scheduled by this plugin

        if (databaseManager != null) {
            databaseManager.close();    // データベースを閉じる
            getLogger().info("Database connection closed.");
        }
        getLogger().info("CoiPlugin has been disabled.");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;    // データベースマネージャーを返す
    }

    private void setupDatabase() {
        try {
            boolean useMySQL = getConfig().getBoolean("useMySQL");
            if (useMySQL) {
                this.databaseManager = new MySQLDatabase(this);    // MySQLデータベースを使用
            } else {
                this.databaseManager = new SQLiteDatabase(this);    // SQLiteデータベースを使用 
            }
            databaseManager.initialize();    // データベースを初期化
            getLogger().info("データベースに正常に接続しました。");    // データベースに正常に接続したことをログに出力
        } catch (Exception e) {
            getLogger().severe(String.format("データベースの初期化に失敗しました: %s", e.getMessage()));
            getLogger().severe(String.format("例外の詳細: %s", e.toString()));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void setupPeriodicSaving() {
        boolean periodicSaveEnabled = getConfig().getBoolean("saving.periodic_save.enabled", false);
        int saveIntervalMinutes = getConfig().getInt("saving.periodic_save.interval_minutes", 15);

        if (saveIntervalMinutes <= 0) {
            getLogger().warning("[Periodic Save] Invalid interval_minutes: " + saveIntervalMinutes + ". Defaulting to 15 minutes.");
            saveIntervalMinutes = 15;
        }

        if (periodicSaveEnabled) {
            long intervalTicks = saveIntervalMinutes * 60 * 20L; // minutes * seconds/minute * ticks/second

            this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (databaseManager == null) {
                    getLogger().warning("[Periodic Save] DatabaseManager not available. Skipping save cycle.");
                    return;
                }
                getLogger().info("[Periodic Save] Starting periodic inventory save for online players...");
                int savedCount = 0;
                // Iterate over a copy of the online players list to avoid ConcurrentModificationException
                // if players log in/out during the save operation.
                for (Player player : new java.util.ArrayList<>(Bukkit.getOnlinePlayers())) { 
                    try {
                        PlayerInventoryState state = new PlayerInventoryState(
                                System.currentTimeMillis(),
                                player.getInventory().getContents(),
                                player.getName(),
                                player.getInventory().getArmorContents(),
                                player.getInventory().getExtraContents(),
                                player.getEnderChest().getContents()
                        );
                        databaseManager.savePlayerInventory(player.getUniqueId(), player.getName(), state);
                        savedCount++;
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "[Periodic Save] Error saving inventory for player " + player.getName(), e);
                    }
                }
                getLogger().info("[Periodic Save] Finished. Saved inventories for " + savedCount + " players.");
            }, intervalTicks, intervalTicks); // Initial delay is the same as the period

            getLogger().info("[Periodic Save] Periodic inventory saving enabled. Interval: " + saveIntervalMinutes + " minutes.");
        } else {
            getLogger().info("[Periodic Save] Periodic inventory saving is disabled.");
        }
    }
    
    public void addRollbackHistory(List<UUID> playerUUIDs) {
        // Save the rollback event to the database
        if (this.databaseManager != null) {
            this.databaseManager.saveRollbackEvent(playerUUIDs);
        } else {
            getLogger().warning("DatabaseManager not initialized, cannot save rollback event.");
        }
        // Removed in-memory history management
    }

    public List<UUID> getLastRollback() {
        // Retrieve the most recent rollback event from the database
        if (this.databaseManager != null) {
            return this.databaseManager.getMostRecentRollbackAffectedPlayers();
        } else {
            getLogger().warning("DatabaseManager not initialized, cannot retrieve last rollback.");
            return null; // Or an empty list, depending on desired behavior for error state
        }
    }

    /**
     * Clears/deletes the most recent rollback event from the database.
     * This is typically called by the UndoCommand after successfully processing an undo.
     */
    public void clearLastRollback() {
        if (this.databaseManager != null) {
            this.databaseManager.deleteMostRecentRollbackEvent();
        } else {
            getLogger().warning("DatabaseManager not initialized, cannot clear last rollback event.");
        }
    }
}

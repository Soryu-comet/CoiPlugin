package com.example.Coi;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;

import com.example.Coi.commands.RollbackCommand;
import com.example.Coi.commands.UndoCommand;
import com.example.Coi.database.DatabaseManager;
import com.example.Coi.database.MySQLDatabase;
import com.example.Coi.database.SQLiteDatabase;
import com.example.Coi.listeners.InventoryListener;
import com.example.Coi.tabcompleter.TabCompleter;

// プラグインクラス
public class CoiPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;    // データベースマネージャー
    private final Deque<List<UUID>> rollbackHistory = new LinkedList<>(); // ロールバック履歴
    private int maxHistory; // 最大履歴数

    @Override
    public void onEnable() {
        saveDefaultConfig();    // デフォルトの設定ファイルを保存
        maxHistory = getConfig().getInt("rollback.max-history", 5); // 最大履歴数を設定
        setupDatabase();    // データベースを初期化
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);    // インベントリリスナーを登録
        RollbackCommand rollbackCommand = new RollbackCommand(this);    // リカバリコマンドを登録
        getCommand("coi").setExecutor(rollbackCommand);    // リカバリコマンドを登録
        getCommand("coi").setTabCompleter(new TabCompleter(this));    // タブ補完を登録
        UndoCommand undoCommand = new UndoCommand(this);    // UNDOコマンドを登録
        getCommand("coiundo").setExecutor(undoCommand);    // UNDOコマンドを登録
        getCommand("coiundo").setTabCompleter(new TabCompleter(this));    // タブ補完を登録
        getLogger().info("CoiPlugin has been enabled.");    // プラグインが有効になったことをログに出力
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();    // データベースを閉じる
        }
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

    public void addRollbackHistory(List<UUID> playerUUIDs) {
        rollbackHistory.push(playerUUIDs);
        if (rollbackHistory.size() > maxHistory) { // 履歴を設定された数に制限
            rollbackHistory.removeLast();
        }
    }

    public List<UUID> getLastRollback() {
        return rollbackHistory.isEmpty() ? null : rollbackHistory.pop();
    }
}

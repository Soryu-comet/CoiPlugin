package com.example.Coi;

import org.bukkit.plugin.java.JavaPlugin;

import com.example.Coi.commands.RollbackCommand;
import com.example.Coi.commands.UndoCommand;
import com.example.Coi.database.DatabaseManager;
import com.example.Coi.database.MySQLDatabase;
import com.example.Coi.database.SQLiteDatabase;
import com.example.Coi.listeners.InventoryListener;
import com.example.Coi.tabcompleter.TabCompleter;

public class CoiPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDatabase();
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getCommand("coi").setExecutor(new RollbackCommand(this));
        getCommand("coi").setTabCompleter(new TabCompleter(this));
        getCommand("coiundo").setExecutor(new UndoCommand(new RollbackCommand(this), this));

        getLogger().info("CoiPlugin has been enabled.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private void setupDatabase() {
        try {
            boolean useMySQL = getConfig().getBoolean("useMySQL");
            if (useMySQL) {
                this.databaseManager = new MySQLDatabase(this);
            } else {
                this.databaseManager = new SQLiteDatabase(this);
            }
            databaseManager.initialize();
            getLogger().info("Database connected successfully.");
        } catch (Exception e) {
            getLogger().severe(String.format("Failed to initialize the database: %s", e.getMessage()));
            getLogger().severe(String.format("Exception details: %s", e.toString()));
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}

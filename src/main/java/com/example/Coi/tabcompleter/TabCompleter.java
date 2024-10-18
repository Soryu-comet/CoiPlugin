package com.example.Coi.tabcompleter;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.Coi.CoiPlugin;

public class TabCompleter implements org.bukkit.command.TabCompleter {

    private final CoiPlugin plugin;

    public TabCompleter(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("t:");
            suggestions.add("r:");
            suggestions.add("u:");
        } else if (args[0].startsWith("u:") && args.length == 1) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                suggestions.add(player.getName());
            }
        }
        return suggestions;
    }
}

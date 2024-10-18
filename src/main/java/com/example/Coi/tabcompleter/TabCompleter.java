package com.example.Coi.tabcompleter;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.Coi.CoiPlugin;

// タブ補完
public class TabCompleter implements org.bukkit.command.TabCompleter {

    private final CoiPlugin plugin;

    public TabCompleter(CoiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            if (args[0].isEmpty() || "t:".startsWith(args[0])) {
                suggestions.add("t:");
            }
            if (args[0].equals("r")) {
                suggestions.add("r:");
            } else if (args[0].startsWith("t:")) {
                String numberPart = args[0].substring(2);
                if (numberPart.isEmpty()) {
                    for (int i = 0; i <= 9; i++) {
                        suggestions.add("t:" + i);
                    }
                } else if (numberPart.matches("\\d+[smhdw]")) {
                    return suggestions; // t:[数字][アルファベット]まで入力された場合、TAB補完をやめる
                } else {
                    suggestions.add("t:" + numberPart + "s");
                    suggestions.add("t:" + numberPart + "m");
                    suggestions.add("t:" + numberPart + "h");
                    suggestions.add("t:" + numberPart + "d");
                    suggestions.add("t:" + numberPart + "w");
                }
            } else if (args[0].startsWith("r:")) {
                String numberPart = args[0].substring(2);
                if (numberPart.isEmpty()) {
                    for (int i = 0; i <= 9; i++) {
                        suggestions.add("r:" + i);
                    }
                } else {
                    int baseNumber;
                    try {
                        baseNumber = Integer.parseInt(numberPart) * 10;
                    } catch (NumberFormatException e) {
                        return suggestions;
                    }
                    for (int i = baseNumber; i < baseNumber + 10; i++) {
                        suggestions.add("r:" + i);
                    }
                }
            } else {
                if (!containsPrefix(args, "r:")) {
                    suggestions.add("r:");
                }
                if (!containsPrefix(args, "u:")) {
                    if ("u:".startsWith(args[0])) {
                        suggestions.add("u:");
                    }
                }
            }
        } else {
            for (String arg : args) {
                if (arg.startsWith("u:")) {
                    String prefix = arg.substring(2).toLowerCase();
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(prefix)) {
                            suggestions.add("u:" + player.getName());
                        }
                    }
                }
            }
            if (!containsPrefix(args, "r:")) {
                suggestions.add("r:");
            }
            if (!containsPrefix(args, "u:")) {
                suggestions.add("u:");
            }
        }
        return suggestions;
    }

    private boolean containsPrefix(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

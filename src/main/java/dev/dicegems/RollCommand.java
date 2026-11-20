package dev.dicegems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * /roll                      re-roll yourself
 * /roll 100                  re-roll yourself on a d100
 * /roll <player|@a|@s>       re-roll that player / everyone / yourself
 * /roll <player|@a|@s> 100   same, on a d100
 * Any vanilla selector works too (e.g. @a[distance=..20]).
 */
public final class RollCommand implements CommandExecutor, TabCompleter {

    private final DiceGems plugin;

    public RollCommand(DiceGems plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("dicegems.roll")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to roll.");
            return true;
        }

        String targetArg = null;
        String maxArg = null;
        if (args.length == 1) {
            if (isInt(args[0])) maxArg = args[0];
            else targetArg = args[0];
        } else if (args.length >= 2) {
            targetArg = args[0];
            maxArg = args[1];
        }

        // ---- max
        Integer max = null;
        if (maxArg != null) {
            if (!isInt(maxArg)) {
                sender.sendMessage(ChatColor.RED + "'" + maxArg + "' is not a number. Usage: /roll [player|@a] [max]");
                return true;
            }
            int m = Integer.parseInt(maxArg);
            int cap = plugin.dice().maxAllowed();
            if (m < 1 || m > cap) {
                sender.sendMessage(ChatColor.RED + "The dice must be between 1 and " + cap + ".");
                return true;
            }
            max = m;
        }

        // ---- targets
        Set<Player> targets = new LinkedHashSet<>();
        if (targetArg == null) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(ChatColor.RED + "Console must name a target: /roll <player|@a> [max]");
                return true;
            }
            targets.add(self);
        } else if (targetArg.startsWith("@")) {
            try {
                for (Entity en : Bukkit.selectEntities(sender, targetArg)) {
                    if (en instanceof Player pl) targets.add(pl);
                }
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid selector: " + targetArg);
                return true;
            }
        } else {
            Player pl = Bukkit.getPlayerExact(targetArg);
            if (pl == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + targetArg + "' is not online.");
                return true;
            }
            targets.add(pl);
        }

        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No players matched.");
            return true;
        }

        boolean others = false;
        for (Player t : targets) if (!t.equals(sender)) others = true;
        if (others && !sender.hasPermission("dicegems.roll.others")) {
            sender.sendMessage(ChatColor.RED + "You can only roll for yourself.");
            return true;
        }

        for (Player t : targets) plugin.dice().roll(t, max);

        int shownMax = max != null ? max : plugin.dice().defaultMax();
        sender.sendMessage(ChatColor.GOLD + "Rolling a d" + shownMax + " for "
                + (targets.size() == 1 ? targets.iterator().next().getName() : targets.size() + " players") + "...");
        return true;
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("@a");
            out.add("@s");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            out.add("20");
            out.add("100");
        } else if (args.length == 2) {
            out.add("20");
            out.add("50");
            out.add("100");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}

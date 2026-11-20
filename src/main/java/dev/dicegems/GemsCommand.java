package dev.dicegems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /dicegems [mine|list [max]|info <gem>|trust <add|remove|list> [player]|give|clearcd|reload] */
public final class GemsCommand implements CommandExecutor, TabCompleter {

    private final DiceGems plugin;

    public GemsCommand(DiceGems plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "mine" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mine" -> mine(s);
            case "list" -> list(s, a);
            case "info" -> info(s, a);
            case "trust" -> trust(s, a);
            case "give" -> give(s, a);
            case "clearcd" -> clearcd(s, a);
            case "reload" -> {
                if (!admin(s)) return true;
                plugin.reloadConfig();
                s.sendMessage(ChatColor.GREEN + "DiceGems config reloaded.");
            }
            default -> help(s);
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("dicegems.admin")) return true;
        s.sendMessage(ChatColor.RED + "You don't have permission.");
        return false;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "DiceGems");
        s.sendMessage(ChatColor.YELLOW + "/dicegems mine" + ChatColor.GRAY + " - your gem");
        s.sendMessage(ChatColor.YELLOW + "/dicegems list [max]" + ChatColor.GRAY + " - ranking + which dice numbers give which gem");
        s.sendMessage(ChatColor.YELLOW + "/dicegems info <gem>" + ChatColor.GRAY + " - abilities of a gem");
        s.sendMessage(ChatColor.YELLOW + "/dicegems trust <add|remove|list> [player]" + ChatColor.GRAY + " - friends your abilities ignore / buff");
        s.sendMessage(ChatColor.YELLOW + "/roll [player|@a] [max]" + ChatColor.GRAY + " - re-roll (staff)");
        if (s.hasPermission("dicegems.admin")) {
            s.sendMessage(ChatColor.YELLOW + "/dicegems give <player> <gem>" + ChatColor.GRAY + " - set a gem directly");
            s.sendMessage(ChatColor.YELLOW + "/dicegems clearcd [player]" + ChatColor.GRAY + " - reset cooldowns");
            s.sendMessage(ChatColor.YELLOW + "/dicegems reload");
        }
    }

    // ------------------------------------------------------------ mine / info / list

    private void mine(CommandSender s) {
        if (!(s instanceof Player p)) {
            help(s);
            return;
        }
        Gem g = plugin.gems().getGem(p);
        if (g == null) {
            s.sendMessage(ChatColor.GRAY + "You haven't rolled a gem yet.");
            return;
        }
        printGem(s, g);
    }

    private void printGem(CommandSender s, Gem g) {
        s.sendMessage(g.displayName() + ChatColor.GRAY + "  rank #" + g.rank + " of " + plugin.registry().size());
        s.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + g.description);
        s.sendMessage(ChatColor.YELLOW + "Passive: " + ChatColor.GRAY + g.passiveText);
        if (g.primary != null) {
            s.sendMessage(ChatColor.GOLD + "Right-click - " + ChatColor.WHITE + g.primary.name()
                    + ChatColor.DARK_GRAY + " (" + g.primary.cooldownSeconds() + "s): " + ChatColor.GRAY + g.primary.description());
        }
        if (g.secondary != null) {
            s.sendMessage(ChatColor.GOLD + "Shift + Right-click - " + ChatColor.WHITE + g.secondary.name()
                    + ChatColor.DARK_GRAY + " (" + g.secondary.cooldownSeconds() + "s): " + ChatColor.GRAY + g.secondary.description());
        }
    }

    private void info(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(ChatColor.RED + "Usage: /dicegems info <gem>");
            return;
        }
        Gem g = plugin.registry().get(a[1]);
        if (g == null) {
            s.sendMessage(ChatColor.RED + "Unknown gem '" + a[1] + "'.");
            return;
        }
        printGem(s, g);
    }

    private void list(CommandSender s, String[] a) {
        int max = plugin.dice().defaultMax();
        if (a.length > 1) {
            try {
                max = Integer.parseInt(a[1]);
            } catch (NumberFormatException ex) {
                s.sendMessage(ChatColor.RED + "'" + a[1] + "' is not a number.");
                return;
            }
        }
        max = Math.max(1, Math.min(max, plugin.dice().maxAllowed()));

        int n = plugin.registry().size();
        int[] lo = new int[n + 1];
        int[] hi = new int[n + 1];
        for (int roll = 1; roll <= max; roll++) {
            int r = plugin.dice().rankFor(roll, max);
            if (lo[r] == 0) lo[r] = roll;
            hi[r] = roll;
        }

        s.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Gem ranking " + ChatColor.GRAY + "(best first, d" + max + ")");
        for (int r = n; r >= 1; r--) {
            Gem g = plugin.registry().byRank(r);
            String range;
            if (lo[r] == 0) range = ChatColor.DARK_GRAY + "unreachable";
            else if (lo[r] == hi[r]) range = ChatColor.WHITE + "" + lo[r];
            else range = ChatColor.WHITE + "" + lo[r] + "-" + hi[r];
            s.sendMessage(ChatColor.GOLD + "#" + r + " " + g.color + g.name + ChatColor.GRAY + "  rolls " + range
                    + ChatColor.DARK_GRAY + "  " + (g.primary != null ? g.primary.name() : "") + " / "
                    + (g.secondary != null ? g.secondary.name() : ""));
        }
    }

    // ------------------------------------------------------------ trust

    private void trust(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(ChatColor.RED + "Players only.");
            return;
        }
        if (a.length < 2) {
            s.sendMessage(ChatColor.RED + "Usage: /dicegems trust <add|remove|list> [player]");
            return;
        }
        TrustManager tm = plugin.trust();
        String action = a[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            List<String> names = new ArrayList<>();
            for (UUID u : tm.list(p)) {
                String name = Bukkit.getOfflinePlayer(u).getName();
                names.add(name != null ? name : u.toString());
            }
            s.sendMessage(ChatColor.YELLOW + "Trusted: " + ChatColor.WHITE + (names.isEmpty() ? "nobody" : String.join(", ", names)));
            return;
        }
        if (a.length < 3) {
            s.sendMessage(ChatColor.RED + "Usage: /dicegems trust " + action + " <player>");
            return;
        }
        if (action.equals("add")) {
            Player t = Bukkit.getPlayerExact(a[2]);
            if (t == null) {
                s.sendMessage(ChatColor.RED + "Player not online.");
                return;
            }
            if (t.equals(p)) {
                s.sendMessage(ChatColor.RED + "You already trust yourself.");
                return;
            }
            s.sendMessage(tm.add(p, t) ? ChatColor.GREEN + "You now trust " + t.getName() + "." : ChatColor.GRAY + "Already trusted.");
        } else if (action.equals("remove")) {
            UUID target = null;
            for (UUID u : tm.list(p)) {
                String name = Bukkit.getOfflinePlayer(u).getName();
                if (name != null && name.equalsIgnoreCase(a[2])) target = u;
            }
            if (target != null && tm.remove(p, target)) s.sendMessage(ChatColor.GREEN + "No longer trusting " + a[2] + ".");
            else s.sendMessage(ChatColor.GRAY + "You don't trust " + a[2] + ".");
        } else {
            s.sendMessage(ChatColor.RED + "Usage: /dicegems trust <add|remove|list> [player]");
        }
    }

    // ------------------------------------------------------------ admin

    private void give(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 3) {
            s.sendMessage(ChatColor.RED + "Usage: /dicegems give <player> <gem>");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        Gem g = plugin.registry().get(a[2]);
        if (t == null) {
            s.sendMessage(ChatColor.RED + "Player not online.");
            return;
        }
        if (g == null) {
            s.sendMessage(ChatColor.RED + "Unknown gem '" + a[2] + "'.");
            return;
        }
        plugin.gems().setGem(t, g);
        s.sendMessage(ChatColor.GREEN + "Gave " + t.getName() + " the " + g.name + " Gem.");
        t.sendMessage(ChatColor.GOLD + "You were given the " + g.displayName() + ChatColor.GOLD + "!");
    }

    private void clearcd(CommandSender s, String[] a) {
        if (!admin(s)) return;
        Player t = null;
        if (a.length > 1) t = Bukkit.getPlayerExact(a[1]);
        else if (s instanceof Player self) t = self;
        if (t == null) {
            s.sendMessage(ChatColor.RED + "Usage: /dicegems clearcd <player>");
            return;
        }
        plugin.cooldowns().clear(t.getUniqueId());
        s.sendMessage(ChatColor.GREEN + "Cooldowns cleared for " + t.getName() + ".");
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("mine", "list", "info", "trust"));
            if (s.hasPermission("dicegems.admin")) out.addAll(List.of("give", "clearcd", "reload"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "info" -> plugin.registry().all().forEach(g -> out.add(g.id));
                case "trust" -> out.addAll(List.of("add", "remove", "list"));
                case "give", "clearcd" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "list" -> out.addAll(List.of("20", "100"));
                default -> { }
            }
        } else if (a.length == 3) {
            String first = a[0].toLowerCase(Locale.ROOT);
            if (first.equals("give")) plugin.registry().all().forEach(g -> out.add(g.id));
            if (first.equals("trust")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}

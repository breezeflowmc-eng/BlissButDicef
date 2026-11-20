package dev.dicegems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** The dice: picks a number, plays the on-screen roll, then hands out the gem that number maps to. */
public final class DiceRoller {

    /** Ticks between each frame of the animation. Starts fast, ends slow like a real dice. */
    private static final int[] DELAYS = {2, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 7, 8, 10, 12, 14};

    private static final class Roll {
        final int max;
        final int result;
        final Gem gem;
        BukkitTask task;
        int frame = 0;

        Roll(int max, int result, Gem gem) {
            this.max = max;
            this.result = result;
            this.gem = gem;
        }
    }

    private final DiceGems plugin;
    private final Map<UUID, Roll> active = new HashMap<>();

    public DiceRoller(DiceGems plugin) { this.plugin = plugin; }

    public boolean isRolling(Player p) { return active.containsKey(p.getUniqueId()); }

    /** The highest number when nobody gave one. 0 in the config = number of gems. */
    public int defaultMax() {
        int cfg = plugin.getConfig().getInt("dice.default-max", 0);
        return cfg > 0 ? cfg : plugin.registry().size();
    }

    public int maxAllowed() {
        return Math.max(1, plugin.getConfig().getInt("dice.max-allowed", 100000));
    }

    /**
     * Number -> gem rank.
     * If the dice is as small as (or smaller than) the gem list, the number IS the rank
     * (a d6 can only ever give you ranks 1-6).
     * If the dice is bigger, the range is split into equal slices, one per gem
     * (a d100 with 18 gems: 1-5 = rank 1 ... 96-100 = rank 18).
     */
    public int rankFor(int roll, int max) {
        int gems = plugin.registry().size();
        if (max <= gems) return Math.min(roll, gems);
        return (int) Math.ceil(roll * (double) gems / max);
    }

    /** Starts a roll for this player. 'max' may be null for the default. */
    public void roll(Player p, Integer max) {
        int top = (max == null || max < 1) ? defaultMax() : Math.min(max, maxAllowed());
        UUID id = p.getUniqueId();

        Roll old = active.remove(id);
        if (old != null && old.task != null) old.task.cancel();

        int result = ThreadLocalRandom.current().nextInt(1, top + 1);
        Gem gem = plugin.registry().byRank(rankFor(result, top));
        Roll r = new Roll(top, result, gem);
        active.put(id, r);
        step(id, r);
    }

    private void step(UUID id, Roll r) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) {
            active.remove(id);
            return;
        }
        if (r.frame >= DELAYS.length) {
            finish(p, r);
            return;
        }

        int shown = ThreadLocalRandom.current().nextInt(1, r.max + 1);
        p.sendTitle(ChatColor.YELLOW + "[ " + ChatColor.WHITE + ChatColor.BOLD + shown + ChatColor.YELLOW + " ]",
                ChatColor.GRAY + "Rolling d" + r.max + "...", 0, 12, 0);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.8f + 0.04f * r.frame);

        long delay = DELAYS[r.frame];
        r.frame++;
        r.task = Bukkit.getScheduler().runTaskLater(plugin, () -> step(id, r), delay);
    }

    private void finish(Player p, Roll r) {
        active.remove(p.getUniqueId());
        Gem g = r.gem;
        plugin.gems().setGem(p, g);

        p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + r.result,
                g.color + g.name + " Gem " + ChatColor.GRAY + "(rank #" + g.rank + ")", 0, 80, 25);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Util.burst(p.getLocation().add(0, 1, 0), Particle.FIREWORK, 50, 0.6);

        boolean jackpot = g.rank == plugin.registry().size();
        p.sendMessage(ChatColor.GOLD + "You rolled " + ChatColor.WHITE + r.result + ChatColor.GRAY + "/" + r.max
                + ChatColor.GOLD + " and got the " + g.displayName() + ChatColor.GOLD + "!");
        p.sendMessage(ChatColor.GRAY + "Passive: " + g.passiveText);
        p.sendMessage(ChatColor.GRAY + "Right-click: " + ChatColor.WHITE + g.primary.name()
                + ChatColor.GRAY + "  |  Shift + Right-click: " + ChatColor.WHITE + g.secondary.name());

        if (plugin.getConfig().getBoolean("dice.broadcast-results", true)) {
            String msg = ChatColor.YELLOW + p.getName() + " rolled " + ChatColor.GOLD + r.result + ChatColor.GRAY + "/"
                    + r.max + ChatColor.YELLOW + " and got the " + g.displayName() + ChatColor.YELLOW + "!";
            if (jackpot) msg = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "JACKPOT! " + ChatColor.RESET + msg;
            Bukkit.broadcastMessage(msg);
        }
    }

    /** Skips the animation and applies the result now (used when someone leaves mid-roll). */
    public void finishNow(Player p) {
        Roll r = active.remove(p.getUniqueId());
        if (r == null) return;
        if (r.task != null) r.task.cancel();
        plugin.gems().setGem(p, r.gem);
    }

    public void finishAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) finishNow(p);
        }
        active.clear();
    }
}

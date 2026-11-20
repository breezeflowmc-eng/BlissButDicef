package dev.dicegems;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** One gem: a passive plus up to two abilities (right-click and shift + right-click). */
public final class Gem {

    /** Returns true if the ability was actually used (so the cooldown starts). */
    @FunctionalInterface public interface Cast { boolean run(Player p); }
    @FunctionalInterface public interface Tick { void run(Player p, int second); }
    @FunctionalInterface public interface Damaged { void run(Player victim, EntityDamageEvent e); }
    @FunctionalInterface public interface Hit { void run(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e); }
    @FunctionalInterface public interface Drops { void run(Player p, BlockDropItemEvent e); }

    public record Ability(String name, String description, int cooldownSeconds, Cast cast) {}

    public final String id;
    public final String name;
    public final String description;
    public final ChatColor color;
    public final Material material;

    /** 1 = weakest. Assigned by the registry in registration order. */
    public int rank;

    public String passiveText = "";
    public Ability primary;
    public Ability secondary;

    public Tick onTick;
    public Damaged onDamaged;
    public Hit onHit;
    public Drops onDrops;

    private Gem(String id, String name, ChatColor color, Material material, String description) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.material = material;
        this.description = description;
    }

    public static Gem of(String id, String name, ChatColor color, Material material, String description) {
        return new Gem(id, name, color, material, description);
    }

    public Gem passive(String text) { this.passiveText = text; return this; }
    public Gem tick(Tick t) { this.onTick = t; return this; }
    public Gem damaged(Damaged d) { this.onDamaged = d; return this; }
    public Gem hit(Hit h) { this.onHit = h; return this; }
    public Gem drops(Drops d) { this.onDrops = d; return this; }

    public Gem primary(String name, String desc, int cooldown, Cast cast) {
        this.primary = new Ability(name, desc, cooldown, cast);
        return this;
    }

    public Gem secondary(String name, String desc, int cooldown, Cast cast) {
        this.secondary = new Ability(name, desc, cooldown, cast);
        return this;
    }

    public String displayName() {
        return color + "" + ChatColor.BOLD + name + " Gem" + ChatColor.RESET;
    }
}

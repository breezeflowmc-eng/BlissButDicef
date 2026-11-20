package dev.dicegems;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small helpers shared by all gem abilities. */
public final class Util {

    private Util() {}

    // ---------- messages ----------

    public static void actionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }

    /** Shows a red message and returns false so abilities can write: return Util.fail(p, "..."). */
    public static boolean fail(Player p, String msg) {
        actionBar(p, ChatColor.RED + msg);
        return false;
    }

    // ---------- targeting ----------

    /** Can this entity be hurt by the owner's abilities? */
    public static boolean valid(Player owner, Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        if (e.equals(owner) || e instanceof ArmorStand || le.isDead()) return false;
        if (e instanceof Player t) {
            GameMode gm = t.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return false;
            if (DiceGems.get().trust().trusts(owner, t)) return false;
        }
        return true;
    }

    public static LivingEntity target(Player p, double range) {
        return target(p, p.getEyeLocation().getDirection(), range);
    }

    public static LivingEntity target(Player p, Vector dir, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.6,
                en -> valid(p, en));
        if (r == null || r.getHitEntity() == null) return null;
        return (LivingEntity) r.getHitEntity();
    }

    /** The point the player is aiming at (an enemy, a block, or the end of the range). */
    public static Location aimPoint(Player p, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.5,
                en -> valid(p, en));
        if (r != null) return r.getHitPosition().toLocation(p.getWorld());
        return eye.clone().add(eye.getDirection().multiply(range));
    }

    public static List<LivingEntity> enemies(Player p, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (valid(p, e) && e.getLocation().distanceSquared(center) <= radius * radius) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /** The player plus everyone they trust within the radius. */
    public static List<Player> allies(Player p, double radius) {
        List<Player> out = new ArrayList<>();
        out.add(p);
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player o && o.getGameMode() != GameMode.SPECTATOR
                    && DiceGems.get().trust().trusts(p, o)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Safe spot up to 'max' blocks along the player's view, or null. */
    public static Location blinkSpot(Player p, double max) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = p.getWorld().rayTraceBlocks(eye, dir, max, FluidCollisionMode.NEVER, true);
        double dist = r == null ? max : Math.max(0, r.getHitPosition().distance(eye.toVector()) - 0.7);
        for (double d = dist; d >= 1.5; d -= 0.5) {
            Location feet = eye.clone().add(dir.clone().multiply(d)).subtract(0, 1.5, 0);
            Block b = feet.getBlock();
            Block up = b.getRelative(0, 1, 0);
            if (b.isPassable() && up.isPassable() && !b.isLiquid() && !up.isLiquid()) {
                return new Location(feet.getWorld(), feet.getBlockX() + 0.5, feet.getBlockY(),
                        feet.getBlockZ() + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch());
            }
        }
        return null;
    }

    // ---------- effects on entities ----------

    /** Ability damage: ignores the 0.5s hit-immunity so multi-hit abilities all land. */
    public static void hurt(Player attacker, LivingEntity target, double dmg) {
        target.setNoDamageTicks(0);
        target.damage(dmg, attacker);
    }

    @SuppressWarnings("deprecation")
    public static void heal(LivingEntity e, double amount) {
        double max = e.getMaxHealth();
        e.setHealth(Math.min(max, e.getHealth() + amount));
    }

    /** A normal (visible) potion effect, used by abilities. */
    public static void fx(LivingEntity e, PotionEffectType type, int ticks, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    /** A quiet potion effect for passives; only refreshes when it is about to run out. */
    public static void passive(LivingEntity e, PotionEffectType type, int amplifier, int ticks) {
        PotionEffect cur = e.getPotionEffect(type);
        if (cur != null && cur.getAmplifier() > amplifier) return;
        if (cur == null || cur.getAmplifier() < amplifier || cur.getDuration() <= ticks / 2) {
            e.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
        }
    }

    public static void push(Location from, Entity e, double power, double lift) {
        Vector v = e.getLocation().toVector().subtract(from.toVector());
        v.setY(0);
        if (v.lengthSquared() < 0.0001) v = new Vector(0, 0, 0);
        else v.normalize();
        v.multiply(power);
        v.setY(lift);
        e.setVelocity(v);
    }

    // ---------- visuals ----------

    public static void beam(Player p, Location end, Particle particle) {
        Location start = p.getEyeLocation().subtract(0, 0.3, 0);
        Vector v = end.toVector().subtract(start.toVector());
        double len = v.length();
        if (len < 0.1) return;
        v.normalize();
        World w = p.getWorld();
        for (double d = 0.8; d < len; d += 0.6) {
            w.spawnParticle(particle, start.clone().add(v.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
    }

    public static void burst(Location l, Particle particle, int count, double spread) {
        l.getWorld().spawnParticle(particle, l, count, spread, spread, spread, 0.02);
    }

    public static void sound(Location l, Sound s, float volume, float pitch) {
        l.getWorld().playSound(l, s, volume, pitch);
    }

    // ---------- temporary blocks (cobwebs etc.) ----------

    private record Temp(BlockData old, Material placed) {}

    private static final Map<Block, Temp> TEMP = new HashMap<>();

    /** Places a block into air and puts the old state back later. */
    public static boolean tempBlock(Block b, Material m, long ticks) {
        if (!b.getType().isAir() || TEMP.containsKey(b)) return false;
        TEMP.put(b, new Temp(b.getBlockData(), m));
        b.setType(m, false);
        Bukkit.getScheduler().runTaskLater(DiceGems.get(), () -> restore(b), ticks);
        return true;
    }

    private static void restore(Block b) {
        Temp t = TEMP.remove(b);
        if (t != null && b.getType() == t.placed()) b.setBlockData(t.old(), false);
    }

    public static void restoreAll() {
        for (Block b : new ArrayList<>(TEMP.keySet())) restore(b);
    }
}

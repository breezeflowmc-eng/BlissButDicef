package dev.dicegems;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.bukkit.potion.PotionEffectType.*;

/**
 * All gems live here, registered from WEAKEST (rank 1) to STRONGEST (highest rank).
 * The dice number maps straight onto this order: low roll = early gem, high roll = late gem.
 * To add a gem, add(...) it in the spot that matches its power. Ranks renumber themselves.
 */
public final class GemRegistry {

    /** Hunter's "Mark Prey" target. */
    public record Mark(UUID target, long until) {}
    public static final Map<UUID, Mark> MARKS = new HashMap<>();

    private static final Set<Material> GRASSY = EnumSet.of(Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.PODZOL);

    private final List<Gem> ordered = new ArrayList<>();
    private final Map<String, Gem> byId = new HashMap<>();

    public GemRegistry() { define(); }

    private void add(Gem g) {
        g.rank = ordered.size() + 1;
        ordered.add(g);
        byId.put(g.id, g);
    }

    public int size() { return ordered.size(); }
    public List<Gem> all() { return Collections.unmodifiableList(ordered); }

    public Gem get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** 1 = weakest. Out-of-range values are clamped. */
    public Gem byRank(int rank) {
        int r = Math.max(1, Math.min(ordered.size(), rank));
        return ordered.get(r - 1);
    }

    @SuppressWarnings("deprecation")
    private void define() {

        // ---------------------------------------------------------------- 1
        add(Gem.of("pebble", "Pebble", ChatColor.GRAY, Material.FLINT,
                "A humble stone. Small, steady, reliable.")
            .passive("Sure Footing - take 50% less fall damage.")
            .damaged((p, e) -> {
                if (e.getCause() == DamageCause.FALL) e.setDamage(e.getDamage() * 0.5);
            })
            .primary("Skip Stone", "Throw a stone: 3 damage + Slowness I (3s).", 20, p -> {
                LivingEntity t = Util.target(p, 14);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.CRIT);
                Util.hurt(p, t, 3);
                Util.fx(t, SLOWNESS, 60, 0);
                return true;
            })
            .secondary("Stone Skin", "Resistance I for 6s.", 45, p -> {
                Util.fx(p, RESISTANCE, 120, 0);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.CRIT, 20, 0.5);
                return true;
            }));

        // ---------------------------------------------------------------- 2
        add(Gem.of("sprout", "Sprout", ChatColor.GREEN, Material.SLIME_BALL,
                "Life finds a way - especially on grass.")
            .passive("Green Thumb - Regeneration I while standing on grass, moss or podzol.")
            .tick((p, s) -> {
                Material below = p.getLocation().subtract(0, 0.1, 0).getBlock().getType();
                if (GRASSY.contains(below)) Util.passive(p, REGENERATION, 0, 60);
            })
            .primary("Bloom", "Heal yourself and trusted allies within 5 blocks for 4 HP.", 40, p -> {
                for (Player a : Util.allies(p, 5)) {
                    Util.heal(a, 4);
                    Util.burst(a.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 12, 0.5);
                }
                Util.sound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                return true;
            })
            .secondary("Thorn Snare", "Slowness II (3s) + Poison I (4s) on your target.", 45, p -> {
                LivingEntity t = Util.target(p, 12);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.HAPPY_VILLAGER);
                Util.fx(t, SLOWNESS, 60, 1);
                Util.fx(t, POISON, 80, 0);
                return true;
            }));

        // ---------------------------------------------------------------- 3
        add(Gem.of("ripple", "Ripple", ChatColor.AQUA, Material.PRISMARINE_SHARD,
                "The calm before the wave.")
            .passive("Gills - Water Breathing and Dolphin's Grace while in water.")
            .tick((p, s) -> {
                if (p.isInWater()) {
                    Util.passive(p, WATER_BREATHING, 0, 60);
                    Util.passive(p, DOLPHINS_GRACE, 0, 60);
                }
            })
            .primary("Tide Push", "Shove enemies within 5 blocks away and deal 2 damage.", 25, p -> {
                List<LivingEntity> list = Util.enemies(p, p.getLocation(), 5);
                if (list.isEmpty()) return Util.fail(p, "No enemies nearby");
                for (LivingEntity t : list) {
                    Util.hurt(p, t, 2);
                    Util.push(p.getLocation(), t, 1.4, 0.3);
                }
                Util.burst(p.getLocation().add(0, 1, 0), Particle.SPLASH, 60, 1.5);
                Util.sound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 0.8f);
                return true;
            })
            .secondary("Bubble Dash", "Dash forward and put yourself out if burning.", 30, p -> {
                Vector v = p.getEyeLocation().getDirection().multiply(1.3);
                v.setY(Math.max(0.15, v.getY()));
                p.setVelocity(v);
                p.setFireTicks(0);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.SPLASH, 40, 0.6);
                return true;
            }));

        // ---------------------------------------------------------------- 4
        add(Gem.of("lumen", "Lumen", ChatColor.YELLOW, Material.GLOWSTONE_DUST,
                "A pocket full of daylight.")
            .passive("Daybreak - permanent Night Vision.")
            .tick((p, s) -> Util.passive(p, NIGHT_VISION, 0, 300))
            .primary("Flare", "Enemies within 8 blocks glow for 8s and are blinded for 2s.", 45, p -> {
                List<LivingEntity> list = Util.enemies(p, p.getLocation(), 8);
                if (list.isEmpty()) return Util.fail(p, "No enemies nearby");
                for (LivingEntity t : list) {
                    Util.fx(t, GLOWING, 160, 0);
                    Util.fx(t, BLINDNESS, 40, 0);
                }
                Util.burst(p.getLocation().add(0, 1, 0), Particle.END_ROD, 60, 1.5);
                Util.sound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.2f);
                return true;
            })
            .secondary("Beacon Step", "Speed II for 5s.", 30, p -> {
                Util.fx(p, SPEED, 100, 1);
                Util.burst(p.getLocation().add(0, 0.2, 0), Particle.END_ROD, 20, 0.4);
                return true;
            }));

        // ---------------------------------------------------------------- 5
        add(Gem.of("ember", "Ember", ChatColor.GOLD, Material.BLAZE_POWDER,
                "A coal that never quite goes out.")
            .passive("Cinder Skin - permanent Fire Resistance.")
            .tick((p, s) -> Util.passive(p, FIRE_RESISTANCE, 0, 60))
            .primary("Cinder Shot", "Hitscan flame: 5 damage and sets the target on fire (4s).", 30, p -> {
                LivingEntity t = Util.target(p, 20);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.FLAME);
                Util.hurt(p, t, 5);
                t.setFireTicks(80);
                Util.sound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.2f);
                return true;
            })
            .secondary("Warm Aura", "Ignite enemies within 5 blocks (3s) and heal yourself 2 HP.", 50, p -> {
                List<LivingEntity> list = Util.enemies(p, p.getLocation(), 5);
                if (list.isEmpty()) return Util.fail(p, "No enemies nearby");
                for (LivingEntity t : list) {
                    Util.hurt(p, t, 1);
                    t.setFireTicks(60);
                }
                Util.heal(p, 2);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.FLAME, 50, 1.2);
                return true;
            }));

        // ---------------------------------------------------------------- 6
        add(Gem.of("gust", "Gust", ChatColor.WHITE, Material.FEATHER,
                "Light on its feet, light on the wind.")
            .passive("Featherfall - Jump Boost I and no fall damage.")
            .tick((p, s) -> Util.passive(p, JUMP_BOOST, 0, 60))
            .damaged((p, e) -> {
                if (e.getCause() == DamageCause.FALL) e.setCancelled(true);
            })
            .primary("Updraft", "Launch skyward and float down (Slow Falling 6s).", 35, p -> {
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX(), 1.3, v.getZ()));
                Util.fx(p, SLOW_FALLING, 120, 0);
                Util.burst(p.getLocation(), Particle.CLOUD, 30, 0.5);
                return true;
            })
            .secondary("Gale Slam", "Fling your target into the air for 3 damage.", 40, p -> {
                LivingEntity t = Util.target(p, 8);
                if (t == null) return Util.fail(p, "No target in range");
                Util.hurt(p, t, 3);
                t.setVelocity(new Vector(0, 1.0, 0));
                Util.burst(t.getLocation(), Particle.CLOUD, 30, 0.5);
                return true;
            }));

        // ---------------------------------------------------------------- 7
        add(Gem.of("gloom", "Gloom", ChatColor.DARK_GRAY, Material.COAL,
                "Comfortable wherever the torches aren't.")
            .passive("Shadowstep - Speed I in low light (light level 4 or less).")
            .tick((p, s) -> {
                if (p.getLocation().getBlock().getLightLevel() <= 4) Util.passive(p, SPEED, 0, 60);
            })
            .primary("Veil", "Turn invisible for 6s.", 50, p -> {
                Util.fx(p, INVISIBILITY, 120, 0);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.SMOKE, 40, 0.5);
                return true;
            })
            .secondary("Dread", "Blind (3s) and weaken (5s) your target.", 40, p -> {
                LivingEntity t = Util.target(p, 12);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.SMOKE);
                Util.fx(t, BLINDNESS, 60, 0);
                Util.fx(t, WEAKNESS, 100, 0);
                return true;
            }));

        // ---------------------------------------------------------------- 8
        add(Gem.of("static", "Static", ChatColor.DARK_AQUA, Material.COPPER_INGOT,
                "Don't touch the doorknob.")
            .passive("Grounded - Haste I and immunity to lightning damage.")
            .tick((p, s) -> Util.passive(p, HASTE, 0, 60))
            .damaged((p, e) -> {
                if (e.getCause() == DamageCause.LIGHTNING) e.setCancelled(true);
            })
            .primary("Arc", "Zap a target for 4 damage; it arcs to a nearby enemy for 3.", 30, p -> {
                LivingEntity t = Util.target(p, 16);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.ELECTRIC_SPARK);
                Util.hurt(p, t, 4);
                t.getWorld().strikeLightningEffect(t.getLocation());
                LivingEntity next = null;
                double best = 25;
                for (LivingEntity o : Util.enemies(p, t.getLocation(), 5)) {
                    double d = o.getLocation().distanceSquared(t.getLocation());
                    if (!o.equals(t) && d < best) { best = d; next = o; }
                }
                if (next != null) {
                    Util.hurt(p, next, 3);
                    Util.burst(next.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30, 0.4);
                }
                return true;
            })
            .secondary("Overcharge", "Speed II and Haste II for 6s.", 60, p -> {
                Util.fx(p, SPEED, 120, 1);
                Util.fx(p, HASTE, 120, 1);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 40, 0.6);
                return true;
            }));

        // ---------------------------------------------------------------- 9
        add(Gem.of("venom", "Venom", ChatColor.DARK_GREEN, Material.FERMENTED_SPIDER_EYE,
                "Every bite leaves a little something behind.")
            .passive("Immune - cannot be poisoned. Your hits have a 15% chance to poison (3s).")
            .damaged((p, e) -> {
                if (e.getCause() == DamageCause.POISON) e.setCancelled(true);
            })
            .tick((p, s) -> {
                if (p.hasPotionEffect(POISON)) p.removePotionEffect(POISON);
            })
            .hit((p, victim, e) -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.15 && Util.valid(p, victim)) {
                    Util.fx(victim, POISON, 60, 0);
                }
            })
            .primary("Spit", "Poison II (3s) and Slowness I (4s) on your target.", 30, p -> {
                LivingEntity t = Util.target(p, 15);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.HAPPY_VILLAGER);
                Util.fx(t, POISON, 60, 1);
                Util.fx(t, SLOWNESS, 80, 0);
                return true;
            })
            .secondary("Web Trap", "Wrap your target in cobwebs for 4s.", 60, p -> {
                LivingEntity t = Util.target(p, 12);
                if (t == null) return Util.fail(p, "No target in range");
                Block feet = t.getLocation().getBlock();
                boolean a = Util.tempBlock(feet, Material.COBWEB, 80);
                boolean b = Util.tempBlock(feet.getRelative(0, 1, 0), Material.COBWEB, 80);
                if (!a && !b) return Util.fail(p, "Can't place webs there");
                Util.sound(t.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.8f);
                return true;
            }));

        // ---------------------------------------------------------------- 10
        add(Gem.of("aegis", "Aegis", ChatColor.WHITE, Material.IRON_INGOT,
                "A wall you carry in your pocket.")
            .passive("Bulwark - take 8% less damage from everything.")
            .damaged((p, e) -> {
                DamageCause c = e.getCause();
                if (c == DamageCause.VOID || c == DamageCause.SUICIDE || c.name().equals("KILL")) return;
                e.setDamage(e.getDamage() * 0.92);
            })
            .primary("Bastion", "Absorption II (4 hearts) for 10s.", 60, p -> {
                Util.fx(p, ABSORPTION, 200, 1);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.CRIT, 40, 0.6);
                Util.sound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
                return true;
            })
            .secondary("Shield Bash", "Melee-range bash: 4 damage, knockback, Slowness II (2s).", 30, p -> {
                LivingEntity t = Util.target(p, 4);
                if (t == null) return Util.fail(p, "No target within reach");
                Util.hurt(p, t, 4);
                Util.push(p.getLocation(), t, 1.5, 0.3);
                Util.fx(t, SLOWNESS, 40, 1);
                Util.burst(t.getLocation().add(0, 1, 0), Particle.CRIT, 20, 0.4);
                return true;
            }));

        // ---------------------------------------------------------------- 11
        add(Gem.of("frost", "Frost", ChatColor.AQUA, Material.QUARTZ,
                "Winter, in a size that fits your hand.")
            .passive("Thick Skin - immune to freezing. Your hits have a 20% chance to slow (2s).")
            .damaged((p, e) -> {
                if (e.getCause() == DamageCause.FREEZE) e.setCancelled(true);
            })
            .tick((p, s) -> {
                if (p.getFreezeTicks() > 0) p.setFreezeTicks(0);
            })
            .hit((p, victim, e) -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.20 && Util.valid(p, victim)) {
                    Util.fx(victim, SLOWNESS, 40, 0);
                }
            })
            .primary("Cold Snap", "Enemies within 6 blocks: 3 damage + Slowness III (3s).", 40, p -> {
                List<LivingEntity> list = Util.enemies(p, p.getLocation(), 6);
                if (list.isEmpty()) return Util.fail(p, "No enemies nearby");
                for (LivingEntity t : list) {
                    Util.hurt(p, t, 3);
                    Util.fx(t, SLOWNESS, 60, 2);
                    t.setFreezeTicks(120);
                }
                Util.burst(p.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 80, 2);
                return true;
            })
            .secondary("Ice Lance", "Hitscan shard: 5 damage + Slowness II (3s).", 25, p -> {
                LivingEntity t = Util.target(p, 20);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.SNOWFLAKE);
                Util.hurt(p, t, 5);
                Util.fx(t, SLOWNESS, 60, 1);
                return true;
            }));

        // ---------------------------------------------------------------- 12
        add(Gem.of("mender", "Mender", ChatColor.LIGHT_PURPLE, Material.RABBIT_FOOT,
                "Stitches wounds shut. Yours, and sometimes your friends'.")
            .passive("Mend - heal 1 HP every 8 seconds.")
            .tick((p, s) -> {
                if (s % 8 == 0) Util.heal(p, 1);
            })
            .primary("Lifeline", "Heal yourself and trusted allies within 6 blocks for 4 HP.", 45, p -> {
                for (Player a : Util.allies(p, 6)) {
                    Util.heal(a, 4);
                    Util.burst(a.getLocation().add(0, 1.2, 0), Particle.HEART, 6, 0.4);
                }
                Util.sound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                return true;
            })
            .secondary("Siphon", "Drain your target: 4 damage, you heal 4 HP.", 40, p -> {
                LivingEntity t = Util.target(p, 12);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.HEART);
                Util.hurt(p, t, 4);
                Util.heal(p, 4);
                return true;
            }));

        // ---------------------------------------------------------------- 13
        add(Gem.of("rift", "Rift", ChatColor.DARK_PURPLE, Material.SHULKER_SHELL,
                "A tear in the world, folded small.")
            .passive("Void Touched - Endermen never target you.")
            .primary("Blink", "Teleport up to 8 blocks where you are looking.", 20, p -> {
                Location spot = Util.blinkSpot(p, 8);
                if (spot == null) return Util.fail(p, "No room to blink");
                Util.burst(p.getLocation().add(0, 1, 0), Particle.PORTAL, 40, 0.5);
                p.teleport(spot);
                p.setFallDistance(0);
                Util.burst(spot.clone().add(0, 1, 0), Particle.PORTAL, 40, 0.5);
                Util.sound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                return true;
            })
            .secondary("Swap", "Trade places with your target.", 60, p -> {
                LivingEntity t = Util.target(p, 15);
                if (t == null) return Util.fail(p, "No target in range");
                Location a = p.getLocation();
                Location b = t.getLocation();
                Location toP = b.clone();
                toP.setYaw(a.getYaw());
                toP.setPitch(a.getPitch());
                Location toT = a.clone();
                toT.setYaw(b.getYaw());
                toT.setPitch(b.getPitch());
                p.teleport(toP);
                t.teleport(toT);
                Util.burst(toP.clone().add(0, 1, 0), Particle.PORTAL, 40, 0.5);
                Util.burst(toT.clone().add(0, 1, 0), Particle.PORTAL, 40, 0.5);
                Util.sound(toP, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                return true;
            }));

        // ---------------------------------------------------------------- 14
        add(Gem.of("prospector", "Prospector", ChatColor.GOLD, Material.RAW_GOLD,
                "Somewhere down there is something shiny.")
            .passive("Pay Dirt - Haste I, and 20% of ore blocks drop double.")
            .tick((p, s) -> Util.passive(p, HASTE, 0, 60))
            .drops((p, e) -> {
                if (ThreadLocalRandom.current().nextDouble() >= 0.20) return;
                Material src = e.getBlockState().getType();
                if (!(src.name().endsWith("_ORE") || src == Material.ANCIENT_DEBRIS)) return;
                Location loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
                for (Item it : e.getItems()) {
                    ItemStack st = it.getItemStack();
                    // skip silk-touched ore blocks so this can't duplicate whole blocks
                    if (st.getType().name().endsWith("_ORE") || st.getType() == Material.ANCIENT_DEBRIS) continue;
                    it.getWorld().dropItemNaturally(loc, st.clone());
                }
                Util.burst(loc, Particle.HAPPY_VILLAGER, 8, 0.3);
            })
            .primary("Gold Rush", "Haste III and Speed I for 12s.", 90, p -> {
                Util.fx(p, HASTE, 240, 2);
                Util.fx(p, SPEED, 240, 0);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 25, 0.6);
                return true;
            })
            .secondary("Fool's Gold", "Mining Fatigue II (6s) and Weakness I (5s) on your target.", 50, p -> {
                LivingEntity t = Util.target(p, 12);
                if (t == null) return Util.fail(p, "No target in range");
                Util.beam(p, t.getEyeLocation(), Particle.HAPPY_VILLAGER);
                Util.fx(t, MINING_FATIGUE, 120, 1);
                Util.fx(t, WEAKNESS, 100, 0);
                return true;
            }));

        // ---------------------------------------------------------------- 15
        add(Gem.of("hunter", "Hunter", ChatColor.DARK_RED, Material.GHAST_TEAR,
                "Patience, then teeth.")
            .passive("Bloodlust - +10% damage against targets under half health.")
            .hit((p, victim, e) -> {
                double mult = 1.0;
                if (victim.getHealth() < victim.getMaxHealth() * 0.5) mult += 0.10;
                Mark m = MARKS.get(p.getUniqueId());
                if (m != null && m.until() > System.currentTimeMillis() && m.target().equals(victim.getUniqueId())) {
                    mult += 0.15;
                }
                if (mult > 1.0) e.setDamage(e.getDamage() * mult);
            })
            .primary("Mark Prey", "Mark a target for 10s: it glows and takes +15% damage from you.", 45, p -> {
                LivingEntity t = Util.target(p, 30);
                if (t == null) return Util.fail(p, "No target in range");
                MARKS.put(p.getUniqueId(), new Mark(t.getUniqueId(), System.currentTimeMillis() + 10_000L));
                Util.fx(t, GLOWING, 200, 0);
                Util.beam(p, t.getEyeLocation(), Particle.CRIT);
                Util.sound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
                return true;
            })
            .secondary("Frenzy", "Speed I and Strength I for 6s.", 70, p -> {
                Util.fx(p, SPEED, 120, 0);
                Util.fx(p, STRENGTH, 120, 0);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.CRIT, 40, 0.6);
                return true;
            }));

        // ---------------------------------------------------------------- 16
        add(Gem.of("titan", "Titan", ChatColor.DARK_GRAY, Material.NETHERITE_SCRAP,
                "Heavy. Slow to move, slower to stop.")
            .passive("Colossus - +2 hearts (Health Boost I).")
            .tick((p, s) -> Util.passive(p, HEALTH_BOOST, 0, 100))
            .primary("Quake", "Slam the ground: enemies within 5 blocks take 5 damage, are launched and slowed.", 60, p -> {
                List<LivingEntity> list = Util.enemies(p, p.getLocation(), 5);
                if (list.isEmpty()) return Util.fail(p, "No enemies nearby");
                for (LivingEntity t : list) {
                    Util.hurt(p, t, 5);
                    t.setVelocity(new Vector(0, 0.9, 0));
                    Util.fx(t, SLOWNESS, 60, 0);
                }
                Util.burst(p.getLocation().add(0, 0.2, 0), Particle.POOF, 60, 1.8);
                Util.sound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                return true;
            })
            .secondary("Iron Will", "Resistance II for 5s.", 90, p -> {
                Util.fx(p, RESISTANCE, 100, 1);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.CRIT, 40, 0.6);
                return true;
            }));

        // ---------------------------------------------------------------- 17
        add(Gem.of("wraith", "Wraith", ChatColor.DARK_PURPLE, Material.ECHO_SHARD,
                "Half here. Half somewhere else.")
            .passive("Phasing - 6% of incoming attacks pass straight through you.")
            .damaged((p, e) -> {
                if (e instanceof EntityDamageByEntityEvent && ThreadLocalRandom.current().nextDouble() < 0.06) {
                    e.setCancelled(true);
                    Util.burst(p.getLocation().add(0, 1, 0), Particle.SMOKE, 25, 0.4);
                    Util.sound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.6f);
                    Util.actionBar(p, ChatColor.DARK_PURPLE + "Phased!");
                }
            })
            .primary("Spectral Daggers", "Three daggers in a fan: 3 damage each.", 50, p -> {
                boolean any = false;
                Vector base = p.getEyeLocation().getDirection();
                for (int i = -1; i <= 1; i++) {
                    Vector dir = base.clone().rotateAroundY(Math.toRadians(i * 7));
                    LivingEntity t = Util.target(p, dir, 20);
                    Location end = p.getEyeLocation().add(dir.clone().multiply(20));
                    if (t != null) {
                        end = t.getEyeLocation();
                        Util.hurt(p, t, 3);
                        any = true;
                    }
                    Util.beam(p, end, Particle.SOUL);
                }
                if (!any) return Util.fail(p, "The daggers hit nothing");
                Util.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.4f);
                return true;
            })
            .secondary("Phase Shift", "Invisible with Speed II for 4s.", 80, p -> {
                Util.fx(p, INVISIBILITY, 80, 0);
                Util.fx(p, SPEED, 80, 1);
                Util.burst(p.getLocation().add(0, 1, 0), Particle.SMOKE, 50, 0.5);
                Util.sound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
                return true;
            }));

        // ---------------------------------------------------------------- 18
        add(Gem.of("tempest", "Tempest", ChatColor.YELLOW, Material.NETHER_STAR,
                "The storm, tamed just enough to carry.")
            .passive("Stormborn - Speed I and immunity to lightning damage.")
            .tick((p, s) -> Util.passive(p, SPEED, 0, 60))
            .damaged((p, e) -> {
                if (e.getCause() == DamageCause.LIGHTNING) e.setCancelled(true);
            })
            .primary("Thunderstrike", "Call lightning where you aim: 6 damage + Slowness II (2s) within 3.5 blocks.", 60, p -> {
                Location pt = Util.aimPoint(p, 25);
                List<LivingEntity> hit = Util.enemies(p, pt, 3.5);
                if (hit.isEmpty()) return Util.fail(p, "No enemies at the target point");
                pt.getWorld().strikeLightningEffect(pt);
                for (LivingEntity t : hit) {
                    Util.hurt(p, t, 6);
                    Util.fx(t, SLOWNESS, 40, 1);
                }
                return true;
            })
            .secondary("Storm Surge", "Allies within 6 blocks: Speed III + Haste II (5s). Enemies: Slowness II + 2 damage.", 90, p -> {
                for (Player a : Util.allies(p, 6)) {
                    Util.fx(a, SPEED, 100, 2);
                    Util.fx(a, HASTE, 100, 1);
                }
                for (LivingEntity t : Util.enemies(p, p.getLocation(), 6)) {
                    Util.hurt(p, t, 2);
                    Util.fx(t, SLOWNESS, 80, 1);
                }
                Util.burst(p.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 80, 2);
                Util.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
                return true;
            }));
    }
}

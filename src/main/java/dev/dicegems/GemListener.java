package dev.dicegems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GemListener implements Listener {

    private final DiceGems plugin;
    private final GemManager gems;
    private final GemRegistry registry;
    private final DiceRoller dice;
    private final Cooldowns cooldowns;
    private final Map<UUID, Long> lastCast = new HashMap<>();

    public GemListener(DiceGems plugin) {
        this.plugin = plugin;
        this.gems = plugin.gems();
        this.registry = plugin.registry();
        this.dice = plugin.dice();
        this.cooldowns = plugin.cooldowns();
    }

    // ================================================================ join / quit / death

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        int delay = plugin.getConfig().getInt("dice.join-delay-ticks", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            boolean every = plugin.getConfig().getBoolean("dice.roll-every-join", false);
            if (!gems.hasGem(p) || every) {
                dice.roll(p, null);
            } else {
                gems.ensure(p);
            }
        }, delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dice.finishNow(e.getPlayer());
        lastCast.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // Gems are soulbound: they never drop, they come back on respawn.
        e.getDrops().removeIf(gems::isGem);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) gems.ensure(p);
        }, 2L);
    }

    // ================================================================ using abilities

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        String id = gems.itemGemId(item);
        if (id == null) return;

        Player p = e.getPlayer();
        e.setUseItemInHand(Event.Result.DENY);

        // Let players still open chests, doors etc. (shift-click always counts as an ability).
        Block clicked = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null
                && clicked.getType().isInteractable() && !p.isSneaking()) {
            return;
        }

        Gem g = registry.get(id);
        if (g == null || !p.hasPermission("dicegems.use") || dice.isRolling(p)) return;
        if (!g.equals(gems.getGem(p))) return; // someone else's / outdated gem item

        long now = System.currentTimeMillis();
        Long last = lastCast.get(p.getUniqueId());
        if (last != null && now - last < 250) return; // main-hand + off-hand events both fire
        lastCast.put(p.getUniqueId(), now);

        if (p.isSneaking()) fire(p, g, g.secondary, "secondary");
        else fire(p, g, g.primary, "primary");
    }

    private void fire(Player p, Gem g, Gem.Ability ab, String slot) {
        if (ab == null) return;
        String key = g.id + ":" + slot;
        long left = cooldowns.remaining(p, key);
        if (left > 0) {
            Util.actionBar(p, ChatColor.RED + ab.name() + " is on cooldown: "
                    + (int) Math.ceil(left / 1000.0) + "s");
            return;
        }
        boolean used;
        try {
            used = ab.cast().run(p);
        } catch (Exception ex) {
            plugin.getLogger().warning("Ability " + ab.name() + " failed: " + ex);
            return;
        }
        if (used) {
            cooldowns.set(p, key, ab.cooldownSeconds());
            Util.actionBar(p, g.color + "" + ChatColor.BOLD + ab.name() + "!");
        }
    }

    // ================================================================ passives that hook events

    /** Runs for every damage event against a gem holder (also fires for entity-vs-entity damage). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Gem g = gems.held(p);
        if (g != null && g.onDamaged != null) g.onDamaged.run(p, e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player attacker = null;
        if (e.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || !(e.getEntity() instanceof LivingEntity victim)) return;
        Gem g = gems.held(attacker);
        if (g != null && g.onHit != null) g.onHit.run(attacker, victim, e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        Gem g = gems.held(e.getPlayer());
        if (g != null && g.onDrops != null) g.onDrops.run(e.getPlayer(), e);
    }

    /** Rift: Endermen leave you alone. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getEntity() instanceof Enderman && e.getTarget() instanceof Player p) {
            Gem g = gems.held(p);
            if (g != null && g.id.equals("rift")) e.setCancelled(true);
        }
    }

    // ================================================================ soulbound protection

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (gems.isGem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (gems.isGem(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove(); // the owner's real gem is re-created by ensure()
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) return; // player's own inventory

        Inventory clicked = e.getClickedInventory();
        boolean intoTop = false;
        if (clicked != null && clicked.equals(top) && gems.isGem(e.getCursor())) intoTop = true;
        if (e.isShiftClick() && clicked != null && !clicked.equals(top) && gems.isGem(e.getCurrentItem())) intoTop = true;
        if (clicked != null && clicked.equals(top)) {
            if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                    && gems.isGem(p.getInventory().getItem(e.getHotbarButton()))) intoTop = true;
            if (e.getClick() == ClickType.SWAP_OFFHAND && gems.isGem(p.getInventory().getItemInOffHand())) intoTop = true;
        }
        if (intoTop) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!gems.isGem(e.getOldCursor())) return;
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) return;
        for (int slot : e.getRawSlots()) {
            if (slot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        ItemStack inHand = e.getPlayer().getInventory().getItem(e.getHand());
        if (!gems.isGem(inHand)) return;
        Entity target = e.getRightClicked();
        if (target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Allay) {
            e.setCancelled(true);
        }
    }
}

package dev.dicegems;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Which gem a player has (stored on the player) and the soulbound gem item that represents it. */
public final class GemManager {

    private final DiceGems plugin;
    private final NamespacedKey playerKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey ownerKey;

    public GemManager(DiceGems plugin) {
        this.plugin = plugin;
        this.playerKey = new NamespacedKey(plugin, "player_gem");
        this.itemKey = new NamespacedKey(plugin, "gem_id");
        this.ownerKey = new NamespacedKey(plugin, "gem_owner");
    }

    // ---------- player data ----------

    public Gem getGem(Player p) {
        String id = p.getPersistentDataContainer().get(playerKey, PersistentDataType.STRING);
        return plugin.registry().get(id);
    }

    public boolean hasGem(Player p) { return getGem(p) != null; }

    /** Sets the player's gem, resets cooldowns and swaps the item in their inventory. */
    public void setGem(Player p, Gem g) {
        p.getPersistentDataContainer().set(playerKey, PersistentDataType.STRING, g.id);
        plugin.cooldowns().clear(p.getUniqueId());
        ensure(p);
    }

    // ---------- items ----------

    public ItemStack createItem(Gem g, Player owner) {
        ItemStack item = new ItemStack(g.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(g.displayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Rank " + ChatColor.WHITE + "#" + g.rank + ChatColor.GRAY
                + " of " + plugin.registry().size());
        lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + g.description);
        lore.add("");
        lore.add(ChatColor.YELLOW + "Passive: " + ChatColor.GRAY + g.passiveText);
        lore.add("");
        if (g.primary != null) {
            lore.add(ChatColor.GOLD + "Right-click: " + ChatColor.WHITE + g.primary.name()
                    + ChatColor.DARK_GRAY + " (" + g.primary.cooldownSeconds() + "s)");
            lore.add(ChatColor.GRAY + g.primary.description());
        }
        if (g.secondary != null) {
            lore.add(ChatColor.GOLD + "Shift + Right-click: " + ChatColor.WHITE + g.secondary.name()
                    + ChatColor.DARK_GRAY + " (" + g.secondary.cooldownSeconds() + "s)");
            lore.add(ChatColor.GRAY + g.secondary.description());
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Soulbound to " + owner.getName());
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(true);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemKey, PersistentDataType.STRING, g.id);
        pdc.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    public String itemGemId(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public boolean isGem(ItemStack it) { return itemGemId(it) != null; }

    private UUID itemOwner(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The player's gem if its item is in the hotbar or offhand (that is when passives work). */
    public Gem held(Player p) {
        Gem g = getGem(p);
        if (g == null) return null;
        PlayerInventory inv = p.getInventory();
        if (g.id.equals(itemGemId(inv.getItemInOffHand()))) return g;
        for (int i = 0; i < 9; i++) {
            if (g.id.equals(itemGemId(inv.getItem(i)))) return g;
        }
        return null;
    }

    /**
     * Makes sure the inventory holds exactly one valid gem item for the player's current gem:
     * removes stale/foreign/duplicate gem items and hands out the real one if it is missing.
     */
    public void ensure(Player p) {
        Gem g = getGem(p);
        PlayerInventory inv = p.getInventory();
        boolean found = false;

        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            String id = itemGemId(it);
            if (id == null) continue;
            boolean valid = g != null && !found && g.id.equals(id) && p.getUniqueId().equals(itemOwner(it));
            if (valid) {
                found = true;
            } else {
                inv.setItem(i, null);
            }
        }

        if (g != null && !found) {
            if (!inv.addItem(createItem(g, p)).isEmpty()) {
                p.sendMessage(ChatColor.RED + "Your inventory is full - free a slot and your "
                        + g.displayName() + ChatColor.RED + " will appear.");
            }
        }
    }
}

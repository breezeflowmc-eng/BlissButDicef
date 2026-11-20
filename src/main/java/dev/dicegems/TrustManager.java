package dev.dicegems;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** "Trusted" players are never hurt by your abilities and receive your buffs/heals. */
public final class TrustManager {

    private final DiceGems plugin;
    private final File file;
    private final Map<UUID, Set<UUID>> map = new HashMap<>();

    public TrustManager(DiceGems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trust.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                Set<UUID> set = new HashSet<>();
                for (String s : y.getStringList(key)) set.add(UUID.fromString(s));
                map.put(owner, set);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> en : map.entrySet()) {
            List<String> list = new ArrayList<>();
            for (UUID u : en.getValue()) list.add(u.toString());
            y.set(en.getKey().toString(), list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save trust.yml: " + ex.getMessage());
        }
    }

    public boolean trusts(Player owner, Player other) {
        Set<UUID> set = map.get(owner.getUniqueId());
        return set != null && set.contains(other.getUniqueId());
    }

    public boolean add(Player owner, Player other) {
        boolean changed = map.computeIfAbsent(owner.getUniqueId(), k -> new HashSet<>()).add(other.getUniqueId());
        if (changed) save();
        return changed;
    }

    public boolean remove(Player owner, UUID other) {
        Set<UUID> set = map.get(owner.getUniqueId());
        boolean changed = set != null && set.remove(other);
        if (changed) save();
        return changed;
    }

    public Set<UUID> list(Player owner) {
        return map.getOrDefault(owner.getUniqueId(), Collections.emptySet());
    }
}

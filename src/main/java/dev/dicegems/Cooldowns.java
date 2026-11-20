package dev.dicegems;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> map = new HashMap<>();

    /** Milliseconds left, or 0 if ready. */
    public long remaining(Player p, String key) {
        Map<String, Long> m = map.get(p.getUniqueId());
        if (m == null) return 0;
        Long until = m.get(key);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            m.remove(key);
            return 0;
        }
        return left;
    }

    public void set(Player p, String key, int seconds) {
        double mult = DiceGems.get().getConfig().getDouble("cooldown-multiplier", 1.0);
        long ms = (long) (seconds * 1000L * mult);
        map.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(key, System.currentTimeMillis() + ms);
    }

    public void clear(UUID id) { map.remove(id); }
}

package dev.dicegems;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiceGems extends JavaPlugin {

    private static DiceGems instance;

    private GemRegistry registry;
    private GemManager gems;
    private Cooldowns cooldowns;
    private TrustManager trust;
    private DiceRoller dice;
    private int second = 0;

    public static DiceGems get() { return instance; }

    public GemRegistry registry() { return registry; }
    public GemManager gems() { return gems; }
    public Cooldowns cooldowns() { return cooldowns; }
    public TrustManager trust() { return trust; }
    public DiceRoller dice() { return dice; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        registry = new GemRegistry();
        cooldowns = new Cooldowns();
        trust = new TrustManager(this);
        gems = new GemManager(this);
        dice = new DiceRoller(this);

        getServer().getPluginManager().registerEvents(new GemListener(this), this);

        RollCommand roll = new RollCommand(this);
        getCommand("roll").setExecutor(roll);
        getCommand("roll").setTabCompleter(roll);

        GemsCommand gc = new GemsCommand(this);
        getCommand("dicegems").setExecutor(gc);
        getCommand("dicegems").setTabCompleter(gc);

        // Passive loop: once per second
        Bukkit.getScheduler().runTaskTimer(this, this::passiveTick, 20L, 20L);

        getLogger().info("DiceGems enabled with " + registry.size() + " gems.");
    }

    @Override
    public void onDisable() {
        if (dice != null) dice.finishAll();
        Util.restoreAll();
        if (trust != null) trust.save();
    }

    private void passiveTick() {
        second++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (dice.isRolling(p)) continue;
            Gem g = gems.held(p);
            if (g != null && g.onTick != null) {
                try {
                    g.onTick.run(p, second);
                } catch (Exception ex) {
                    getLogger().warning("Passive error for gem " + g.id + ": " + ex);
                }
            }
            if (second % 10 == 0) gems.ensure(p);
        }
    }
}

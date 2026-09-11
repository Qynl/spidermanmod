package com.spiderman.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import com.spiderman.mod.SpiderManMod;

/**
 * Server-side JSON config ({@code config/spiderman.json}). All gameplay tuning
 * lives here so server owners can rebalance without rebuilding.
 */
public final class SpiderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SpiderConfig instance = new SpiderConfig();

    // Spider + bite
    public int spiderWeight = 4;
    public double biteChance = 1.0;
    public boolean spiderDiesAfterBite = true;

    // Ranges (blocks)
    public double webRange = 24.0;
    public double swingRange = 40.0;
    public double zipRange = 32.0;

    // Cooldowns (ticks)
    public int shotCooldown = 10;
    public int swingCooldown = 4;
    public int zipCooldown = 40;
    public int pullCooldown = 60;
    public int trapCooldown = 30;
    public int lineCooldown = 100;
    public int burstCooldown = 120;
    public int impactCooldown = 80;
    public int platformCooldown = 200;
    public int doubleCooldown = 60;

    // Damage
    public double shotDamage = 4.0;
    public double impactDamage = 10.0;
    public double burstDamage = 8.0;
    public double burstRadius = 5.0;

    // Combos
    public int comboWindow = 80;
    public double comboBonus = 0.25;
    public int comboCap = 4;

    // Physical boosts
    public double jumpMult = 1.35;
    public double speedMult = 1.15;
    public double strengthMult = 1.5;
    public double fallMult = 0.1;
    public boolean stepHeight = true;

    // Spider-sense
    public double senseRadiusBase = 10.0;
    public double senseRadiusPerStage = 4.0;

    // Web cleanup
    public int webLiveTicks = 100;
    public int maxTrapWebs = 24;

    // Progression
    public int[] stageThresholds = {100, 300, 700, 1400};
    public double masteryMult = 1.0;
    public boolean transformEffects = true;

    // Experimental powers (opt-in)
    public boolean experimentalDoubleJump = false;
    public boolean experimentalVenomBlast = false;

    private SpiderConfig() {
    }

    public static SpiderConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("spiderman.json");
        try {
            if (Files.exists(path)) {
                SpiderConfig loaded = GSON.fromJson(Files.readString(path), SpiderConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(instance));
            }
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] failed to load config, using defaults", e);
        }
    }

    public int cooldownFor(int ability) {
        switch (ability) {
            case 0: return shotCooldown;
            case 1: return swingCooldown;
            case 2: return zipCooldown;
            case 3: return pullCooldown;
            case 4: return trapCooldown;
            case 5: return lineCooldown;
            case 6: return burstCooldown;
            case 7: return impactCooldown;
            case 8: return platformCooldown;
            case 9: return doubleCooldown;
            default: return 20;
        }
    }
}

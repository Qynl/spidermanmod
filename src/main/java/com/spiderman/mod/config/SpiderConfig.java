package com.spiderman.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import com.spiderman.mod.SpiderManMod;

/**
 * OVERHAULED CONFIG - More options for the ultimate Spider-Man experience.
 * All gameplay tuning lives here.
 */
public final class SpiderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SpiderConfig instance = new SpiderConfig();

    // Spider + bite
    public int spiderWeight = 4;
    public double biteChance = 1.0;
    public boolean spiderDiesAfterBite = true;

    // Ranges — increased for better web-slinging
    public double webRange = 38.0;
    public double swingRange = 90.0;
    public double zipRange = 60.0;
    public double pullRange = 45.0;
    public double trapRange = 35.0;

    // Cooldowns (ticks) - reduced for more fun
    public int shotCooldown = 8;
    public int swingCooldown = 3;
    public int zipCooldown = 30;
    public int pullCooldown = 45;
    public int trapCooldown = 25;
    public int lineCooldown = 80;
    public int burstCooldown = 100;
    public int impactCooldown = 60;
    public int platformCooldown = 150;
    public int doubleCooldown = 40;

    // Damage - increased for more impact
    public double shotDamage = 5.0;
    public double impactDamage = 14.0;
    public double burstDamage = 10.0;
    public double burstRadius = 5.0; // Was 6.5, now 5.0 to prevent lag
    public double trapDamage = 3.0;
    public double pullDamage = 4.0;

    // Combos - more rewarding
    public int comboWindow = 100;
    public double comboBonus = 0.3;
    public int comboCap = 6;

    // Physical boosts - more superhuman
    public double jumpMult = 1.3; // Was 1.5
    public double speedMult = 1.15; // Was 1.25
    public double strengthMult = 1.5; // Was 1.8
    public double fallMult = 0.08; // Was 0.05
    public boolean stepHeight = true;
    public double wallRunSpeed = 1.08; // Was 1.15
    public double swingBoost = 1.15; // Was 1.3
    public double zipBoost = 1.2; // Was 1.4

    // Spider-sense - more immersive
    public double senseRadiusBase = 10.0;
    public double senseRadiusPerStage = 2.5; // Was 5.0, now 2.5 to prevent lag at stage 4
    public boolean senseSlowMo = true;
    public double senseSlowMoChance = 0.25;
    public int senseDuration = 60;

    // Web cleanup
    public int webLiveTicks = 120; // Was 150
    public int maxTrapWebs = 24; // Was 32
    public boolean websAreSolid = true;
    public boolean spidersIgnoreWebs = true;

    // Progression — faster and more rewarding
    public int[] stageThresholds = {80, 250, 600, 1200};
    public double masteryMult = 1.2;
    public boolean transformEffects = true;
    public int passiveMasteryPerSecond = 2;
    public int movementBonusMastery = 2;
    public int styleBonusMastery = 3;

    // Movement - new options
    public boolean enableWallRun = true;
    public boolean enableWallJumpChain = true;
    public boolean enableDive = true;
    public boolean enableSlingshot = true;
    public boolean enableAirTricks = true;
    public double diveSpeed = 2.5;
    public double wallJumpBoost = 1.2;

    // Visuals
    public boolean enableScreenEffects = true;
    public boolean enableParticles = true;
    public boolean enableWebGlow = true;
    public boolean comicHud = true;

    // Experimental
    public boolean experimentalDoubleJump = true;
    public boolean experimentalVenomBlast = false;
    public boolean experimentalWebWings = false;

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
        instance.sanitize();
    }

    private void sanitize() {
        spiderWeight = atLeast(spiderWeight, 0, 4);
        biteChance = Double.isFinite(biteChance) ? Math.min(1.0, Math.max(0.0, biteChance)) : 1.0;
        webRange = nonNegative(webRange, 38.0);
        swingRange = nonNegative(swingRange, 90.0);
        zipRange = nonNegative(zipRange, 60.0);
        pullRange = nonNegative(pullRange, 45.0);
        trapRange = nonNegative(trapRange, 35.0);
        shotCooldown = atLeast(shotCooldown, 0, 8);
        swingCooldown = atLeast(swingCooldown, 0, 3);
        zipCooldown = atLeast(zipCooldown, 0, 30);
        pullCooldown = atLeast(pullCooldown, 0, 45);
        trapCooldown = atLeast(trapCooldown, 0, 25);
        lineCooldown = atLeast(lineCooldown, 0, 80);
        burstCooldown = atLeast(burstCooldown, 0, 100);
        impactCooldown = atLeast(impactCooldown, 0, 60);
        platformCooldown = atLeast(platformCooldown, 0, 150);
        doubleCooldown = atLeast(doubleCooldown, 0, 40);
        shotDamage = nonNegative(shotDamage, 5.0);
        impactDamage = nonNegative(impactDamage, 14.0);
        burstDamage = nonNegative(burstDamage, 10.0);
        burstRadius = nonNegative(burstRadius, 6.5);
        trapDamage = nonNegative(trapDamage, 3.0);
        pullDamage = nonNegative(pullDamage, 4.0);
        comboWindow = atLeast(comboWindow, 0, 100);
        comboBonus = nonNegative(comboBonus, 0.3);
        comboCap = atLeast(comboCap, 0, 6);
        jumpMult = nonNegative(jumpMult, 1.5);
        speedMult = nonNegative(speedMult, 1.25);
        strengthMult = nonNegative(strengthMult, 1.8);
        fallMult = nonNegative(fallMult, 0.05);
        wallRunSpeed = nonNegative(wallRunSpeed, 1.15);
        swingBoost = nonNegative(swingBoost, 1.3);
        zipBoost = nonNegative(zipBoost, 1.4);
        senseRadiusBase = nonNegative(senseRadiusBase, 12.0);
        senseRadiusPerStage = nonNegative(senseRadiusPerStage, 5.0);
        senseSlowMoChance = Double.isFinite(senseSlowMoChance) ? Math.min(1.0, Math.max(0.0, senseSlowMoChance)) : 0.25;
        senseDuration = atLeast(senseDuration, 0, 60);
        webLiveTicks = atLeast(webLiveTicks, 0, 150);
        maxTrapWebs = atLeast(maxTrapWebs, 1, 32);
        if (stageThresholds == null || stageThresholds.length != 4) {
            stageThresholds = new int[]{80, 250, 600, 1200};
        } else {
            for (int i = 0; i < stageThresholds.length; i++) {
                if (stageThresholds[i] < 1) stageThresholds[i] = 1;
            }
            for (int i = 1; i < stageThresholds.length; i++) {
                if (stageThresholds[i] <= stageThresholds[i - 1]) {
                    stageThresholds[i] = stageThresholds[i - 1] + 50;
                }
            }
        }
        masteryMult = nonNegative(masteryMult, 1.2);
        if (masteryMult < 0.1) masteryMult = 0.1;
        passiveMasteryPerSecond = atLeast(passiveMasteryPerSecond, 0, 2);
        movementBonusMastery = atLeast(movementBonusMastery, 0, 2);
        styleBonusMastery = atLeast(styleBonusMastery, 0, 3);
        diveSpeed = nonNegative(diveSpeed, 2.5);
        wallJumpBoost = nonNegative(wallJumpBoost, 1.2);
    }

    private static int atLeast(int value, int min, int dflt) {
        return value < min ? dflt : value;
    }

    private static double nonNegative(double value, double dflt) {
        return !Double.isFinite(value) || value < 0.0 ? dflt : value;
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
            default: return 20;
        }
    }
}

package com.spiderman.mod.state;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spiderman.mod.config.SpiderConfig;

/**
 * Authoritative per-player powers state (server-side) - OVERHAULED.
 *
 * Persistent: hasPowers, stage, mastery, selected, timeWithPowers, style, perks
 * Transient: everything else including advanced movement state
 */
public class PlayerPowers {
    public boolean hasPowers;
    public int stage;
    public int mastery;
    public int selected;
    public long timeWithPowers;
    
    // Style and progression
    public int stylePoints;
    public int airTime;
    public int maxCombo;
    public long totalSwings;
    public long totalWallRuns;

    // Transient session state
    public final Map<Integer, Long> cooldowns = new HashMap<>();
    public int combo;
    public long comboUntil;
    public boolean swinging;
    public double swingX, swingY, swingZ;
    public double ropeLen;
    public int swingHand;
    public int zipTicks;
    public double zipX, zipY, zipZ;
    
    // Pull: hold to pull — continuous pull while pullTicks >0
    public int pullTicks;
    public double pullX, pullY, pullZ;
    public UUID pullTargetId;
    public boolean pullingPlayer;

    public boolean doubleJumpUsed;
    public boolean climbing;
    public long wallRunUntil;
    public int wallRunTicks;
    public int wallJumpChain;
    public long lastWallJumpTime;
    public int focusTicks;
    public long lastPassiveTick;
    
    // New movement
    public boolean diving;
    public int diveTicks;
    public boolean slingshotCharging;
    public int slingshotCharge;
    public double slingshotX, slingshotY, slingshotZ;
    public int airTricks;
    public long lastGroundedTime;
    public boolean wasInAir;
    public int consecutiveSwings;
    public long lastSwingTime;
    
    // Sense
    public boolean senseActive;
    public int senseTicks;
    public boolean slowMoActive;
    public int slowMoTicks;

    public boolean canUse(int ability, long time) {
        if (!hasPowers || !AbilityIds.valid(ability)) {
            return false;
        }
        if (stage < AbilityIds.MIN_STAGE[ability]) {
            return false;
        }
        if (!Double.isFinite(time)) return false;
        Long cd = cooldowns.get(ability);
        if (cd == null) return true;
        if (!Double.isFinite(cd)) {
            cooldowns.remove(ability);
            return true;
        }
        return time >= cd;
    }

    public void setCooldown(int ability, long time) {
        if (!AbilityIds.valid(ability)) return;
        if (!Double.isFinite(time)) return;
        try {
            int cd = SpiderConfig.get().cooldownFor(ability);
            if (cd < 0) cd = 0;
            // FIXED: Reduced cooldown reduction to prevent spam at high stage
            if (stylePoints > 200) {
                cd = (int)(cd * 0.92);
            }
            if (stylePoints > 600) {
                cd = (int)(cd * 0.88);
            }
            if (cd < 3) cd = 3; // Minimum 3 ticks cooldown
            cooldowns.put(ability, time + cd);
        } catch (Exception e) {
            cooldowns.put(ability, time + 20);
        }
    }

    public void resetTransient() {
        cooldowns.clear();
        combo = 0;
        comboUntil = 0;
        stopSwing();
        swingHand = 0;
        zipTicks = 0;
        pullTicks = 0;
        pullTargetId = null;
        pullingPlayer = false;
        doubleJumpUsed = false;
        climbing = false;
        wallRunUntil = 0;
        wallRunTicks = 0;
        wallJumpChain = 0;
        lastWallJumpTime = 0;
        focusTicks = 0;
        lastPassiveTick = 0;
        diving = false;
        diveTicks = 0;
        slingshotCharging = false;
        slingshotCharge = 0;
        airTricks = 0;
        lastGroundedTime = 0;
        wasInAir = false;
        consecutiveSwings = 0;
        lastSwingTime = 0;
        senseActive = false;
        senseTicks = 0;
        slowMoActive = false;
        slowMoTicks = 0;
    }

    public void stopSwing() {
        swinging = false;
        ropeLen = 0.0;
        // Track consecutive swings for style
        long now = System.currentTimeMillis();
        if (now - lastSwingTime < 3000) {
            consecutiveSwings++;
        } else {
            consecutiveSwings = 1;
        }
        lastSwingTime = now;
        totalSwings++;
    }

    public void stopPull() {
        pullTicks = 0;
        pullTargetId = null;
    }

    public void toNbt(NbtCompound nbt) {
        try {
            nbt.putBoolean("Powers", hasPowers);
            nbt.putInt("Stage", Math.max(0, Math.min(4, stage)));
            nbt.putInt("Mastery", Math.max(0, Math.min(100000, mastery)));
            nbt.putInt("Selected", AbilityIds.valid(selected) ? selected : 0);
            nbt.putLong("TimeWithPowers", Math.max(0, timeWithPowers));
            nbt.putInt("Style", Math.max(0, stylePoints));
            nbt.putInt("AirTime", Math.max(0, airTime));
            nbt.putInt("MaxCombo", Math.max(0, maxCombo));
            nbt.putLong("TotalSwings", Math.max(0, totalSwings));
            nbt.putLong("TotalWallRuns", Math.max(0, totalWallRuns));
        } catch (Exception ignored) {}
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            hasPowers = false;
            stage = 0;
            mastery = 0;
            selected = 0;
            timeWithPowers = 0;
            stylePoints = 0;
            return;
        }
        try {
            hasPowers = nbt.getBoolean("Powers");
            stage = Math.min(4, Math.max(0, nbt.getInt("Stage")));
            mastery = Math.max(0, Math.min(100000, nbt.getInt("Mastery")));
            int loaded = nbt.getInt("Selected");
            selected = AbilityIds.valid(loaded) ? loaded : 0;
            if (nbt.contains("TimeWithPowers")) {
                try {
                    timeWithPowers = Math.max(0, nbt.getLong("TimeWithPowers"));
                } catch (Exception e) {
                    try {
                        timeWithPowers = nbt.getInt("TimeWithPowers");
                    } catch (Exception ignored) {
                        timeWithPowers = 0;
                    }
                }
            } else {
                timeWithPowers = 0;
            }
            stylePoints = nbt.contains("Style") ? nbt.getInt("Style") : 0;
            airTime = nbt.contains("AirTime") ? nbt.getInt("AirTime") : 0;
            maxCombo = nbt.contains("MaxCombo") ? nbt.getInt("MaxCombo") : 0;
            totalSwings = nbt.contains("TotalSwings") ? nbt.getLong("TotalSwings") : 0;
            totalWallRuns = nbt.contains("TotalWallRuns") ? nbt.getLong("TotalWallRuns") : 0;
        } catch (Exception e) {
            hasPowers = false;
            stage = 0;
            mastery = 0;
            selected = 0;
            timeWithPowers = 0;
            stylePoints = 0;
        }
    }
}

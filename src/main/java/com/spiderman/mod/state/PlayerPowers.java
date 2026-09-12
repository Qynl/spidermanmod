package com.spiderman.mod.state;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spiderman.mod.config.SpiderConfig;

/**
 * Authoritative per-player powers state (server-side).
 *
 * Persistent: hasPowers, stage, mastery, selected, timeWithPowers
 * Transient: everything else including new pull state for hold-to-pull.
 */
public class PlayerPowers {
    public boolean hasPowers;
    public int stage;
    public int mastery;
    public int selected;
    public long timeWithPowers;

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
    public boolean pullingPlayer; // true if pulling self to target, false if pulling target to self

    public boolean doubleJumpUsed;
    public boolean climbing;
    public long wallRunUntil;
    public int focusTicks;
    public long lastPassiveTick;

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
        focusTicks = 0;
        lastPassiveTick = 0;
    }

    public void stopSwing() {
        swinging = false;
        ropeLen = 0.0;
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
        } catch (Exception ignored) {}
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            hasPowers = false;
            stage = 0;
            mastery = 0;
            selected = 0;
            timeWithPowers = 0;
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
        } catch (Exception e) {
            hasPowers = false;
            stage = 0;
            mastery = 0;
            selected = 0;
            timeWithPowers = 0;
        }
    }
}

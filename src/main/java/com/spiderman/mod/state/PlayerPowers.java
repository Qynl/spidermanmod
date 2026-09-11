package com.spiderman.mod.state;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;

import com.spiderman.mod.config.SpiderConfig;

/**
 * Authoritative per-player powers state (server-side).
 *
 * <p>Persistent fields ({@code hasPowers}, {@code stage}, {@code mastery},
 * {@code selected}) are saved to the world's {@code spiderman.dat}; everything
 * else is transient session state.
 */
public class PlayerPowers {
    public boolean hasPowers;
    public int stage;
    public int mastery;
    public int selected;

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
    public boolean doubleJumpUsed;
    public boolean climbing;
    public long wallRunUntil;
    public int focusTicks;

    public boolean canUse(int ability, long time) {
        if (!hasPowers || !AbilityIds.valid(ability)) {
            return false;
        }
        if (stage < AbilityIds.MIN_STAGE[ability]) {
            return false;
        }
        return time >= cooldowns.getOrDefault(ability, 0L);
    }

    public void setCooldown(int ability, long time) {
        cooldowns.put(ability, time + SpiderConfig.get().cooldownFor(ability));
    }

    /**
     * Clears transient session state (join/respawn). Persistent fields
     * (powers/stage/mastery/selected) are kept. This also prevents cooldowns
     * from locking out a fresh life: cooldowns are stored in entity-age ticks
     * and a respawned player starts at age 0 again.
     */
    public void resetTransient() {
        cooldowns.clear();
        combo = 0;
        comboUntil = 0;
        stopSwing();
        swingHand = 0;
        zipTicks = 0;
        doubleJumpUsed = false;
        climbing = false;
        wallRunUntil = 0;
        focusTicks = 0;
    }

    public void stopSwing() {
        swinging = false;
        ropeLen = 0.0;
    }

    public void toNbt(NbtCompound nbt) {
        nbt.putBoolean("Powers", hasPowers);
        nbt.putInt("Stage", stage);
        nbt.putInt("Mastery", mastery);
        nbt.putInt("Selected", selected);
    }

    public void fromNbt(NbtCompound nbt) {
        hasPowers = nbt.getBoolean("Powers");
        // Clamp: a corrupt spiderman.dat must never load an out-of-range
        // stage (array indexing), negative mastery, or invalid selection.
        stage = Math.min(4, Math.max(0, nbt.getInt("Stage")));
        mastery = Math.max(0, nbt.getInt("Mastery"));
        int loaded = nbt.getInt("Selected");
        selected = AbilityIds.valid(loaded) ? loaded : 0;
    }
}

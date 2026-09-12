package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Mastery XP feeding the staged power progression.
 * Now includes time-based progression: you gain mastery just by living with powers,
 * plus bonuses for active use. This makes stages progress naturally over time.
 */
public final class MasteryLogic {
    private MasteryLogic() {
    }

    public static void addMastery(ServerPlayerEntity player, int amount) {
        if (player == null || !player.isAlive()) {
            return;
        }
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers) {
            return;
        }
        if (powers.stage >= 4) {
            // At max stage, still track mastery for display but cap it
            if (powers.mastery < 10000) {
                powers.mastery += Math.max(1, (int) (amount * SpiderConfig.get().masteryMult * 0.2));
                if (powers.mastery > 10000) powers.mastery = 10000;
                ServerNetworking.sendPowers(player);
            }
            return;
        }
        if (amount <= 0) amount = 1;

        double mult = SpiderConfig.get().masteryMult;
        if (!Double.isFinite(mult) || mult < 0.0) mult = 1.0;
        int gain = Math.max(1, (int) (amount * mult));

        // Early stages get bonus to help new players feel progression
        if (powers.stage == 0) {
            gain = (int) (gain * 1.5);
            if (gain < 1) gain = 1;
        }

        // Prevent overflow
        if (powers.mastery > 100000) {
            powers.mastery = 100000;
        }

        powers.mastery += gain;

        // Try stage up — this also saves and syncs if it succeeds
        if (TransformLogic.tryStageUp(player) < 0) {
            // No stage up, just sync mastery
            ServerNetworking.sendPowers(player);
        }
    }

    /**
     * Time-based mastery: called every second for passive growth.
     * This is the "progresses with the time" requirement.
     */
    public static void addTimeMastery(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers || powers.stage >= 4) return;
        // Base time mastery + bonus based on stage (higher stages need more time)
        int base = 1;
        if (powers.stage == 0) base = 2; // Faster early game
        addMastery(player, base);
    }
}

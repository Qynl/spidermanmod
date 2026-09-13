package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * MASTERY - Fixed for no lag at high stage.
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
            if (powers.mastery < 10000) {
                // FIXED: Reduced gain at max stage, was causing save spam and lag
                int gain = Math.max(1, (int) (amount * SpiderConfig.get().masteryMult * 0.15));
                gain += Math.min(powers.stylePoints, 1000) / 200; // Was /100
                powers.mastery += gain;
                if (powers.mastery > 10000) powers.mastery = 10000;
                // FIXED: Only sync every 5th time at max stage to reduce packets
                if (player.age % 5 == 0) {
                    ServerNetworking.sendPowers(player);
                }
            }
            return;
        }
        if (amount <= 0) amount = 1;

        double mult = SpiderConfig.get().masteryMult;
        if (!Double.isFinite(mult) || mult < 0.0) mult = 1.2;
        int gain = Math.max(1, (int) (amount * mult));

        if (powers.stage == 0) {
            gain = (int) (gain * 1.6);
            if (gain < 2) gain = 2;
        } else if (powers.stage == 1) {
            gain = (int) (gain * 1.2);
        }

        gain += Math.min(powers.stylePoints, 500) / 300;
        
        if (powers.combo >= 3) {
            gain += Math.min(powers.combo, 6) / 2;
        }
        
        if (powers.airTime > 60) {
            gain += Math.min(powers.airTime, 200) / 80;
        }

        if (powers.mastery > 100000) {
            powers.mastery = 100000;
        }

        powers.mastery += gain;

        if (TransformLogic.tryStageUp(player) < 0) {
            ServerNetworking.sendPowers(player);
        }
    }

    public static void addTimeMastery(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers || powers.stage >= 4) return;
        int base = 1;
        if (powers.stage == 0) base = 2;
        base += Math.min(powers.stylePoints, 500) / 300;
        addMastery(player, base);
    }
    
    public static void addStyleMastery(ServerPlayerEntity player, int stylePoints) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers) return;
        int masteryGain = stylePoints / 10; // Was /5, now /10
        if (masteryGain > 0) {
            addMastery(player, Math.min(masteryGain, 5));
        }
    }
}

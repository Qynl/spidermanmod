package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * ULTIMATE MASTERY - Fast, rewarding, style-based progression.
 * - Time + movement + combat + style
 * - Early game bonus
 * - Style points integration
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
                int gain = Math.max(1, (int) (amount * SpiderConfig.get().masteryMult * 0.3));
                gain += powers.stylePoints / 100;
                powers.mastery += gain;
                if (powers.mastery > 10000) powers.mastery = 10000;
                ServerNetworking.sendPowers(player);
            }
            return;
        }
        if (amount <= 0) amount = 1;

        double mult = SpiderConfig.get().masteryMult;
        if (!Double.isFinite(mult) || mult < 0.0) mult = 1.2;
        int gain = Math.max(1, (int) (amount * mult));

        // Early stages bonus
        if (powers.stage == 0) {
            gain = (int) (gain * 1.8);
            if (gain < 2) gain = 2;
        } else if (powers.stage == 1) {
            gain = (int) (gain * 1.4);
        }

        // Style bonus
        gain += powers.stylePoints / 200;
        
        // Combo bonus
        if (powers.combo >= 3) {
            gain += powers.combo;
        }
        
        // Air time bonus
        if (powers.airTime > 40) {
            gain += powers.airTime / 40;
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
        int base = 2;
        if (powers.stage == 0) base = 3;
        base += powers.stylePoints / 150;
        addMastery(player, base);
    }
    
    public static void addStyleMastery(ServerPlayerEntity player, int stylePoints) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers) return;
        int masteryGain = stylePoints / 5;
        if (masteryGain > 0) {
            addMastery(player, masteryGain);
        }
    }
}

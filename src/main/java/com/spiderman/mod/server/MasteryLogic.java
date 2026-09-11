package com.spiderman.mod.server;

import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/** Mastery XP feeding the staged power progression. */
public final class MasteryLogic {
    private MasteryLogic() {
    }

    public static void addMastery(ServerPlayerEntity player, int amount) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers || powers.stage >= 4) {
            return;
        }
        powers.mastery += Math.max(1, (int) (amount * SpiderConfig.get().masteryMult));
        if (TransformLogic.tryStageUp(player) < 0) {
            ServerNetworking.sendPowers(player);
        }
    }
}

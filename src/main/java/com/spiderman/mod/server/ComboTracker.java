package com.spiderman.mod.server;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Web-hit combos: chained hits inside the combo window multiply web damage.
 */
public final class ComboTracker {
    private ComboTracker() {
    }

    public static void onWebHit(ServerPlayerEntity player, LivingEntity target, float damage) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        SpiderConfig cfg = SpiderConfig.get();
        long time = player.age;
        if (time > powers.comboUntil) {
            powers.combo = 0;
        }
        powers.combo++;
        powers.comboUntil = time + cfg.comboWindow;
        ServerNetworking.sendCombo(player, powers.combo);
        MasteryLogic.addMastery(player, 5);
    }

    /** Damage multiplier from the current combo (uncapped count, capped bonus). */
    public static float damageMult(PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        int capped = Math.min(powers.combo, cfg.comboCap);
        return (float) (1.0 + cfg.comboBonus * capped);
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (powers.combo > 0 && player.age > powers.comboUntil) {
            powers.combo = 0;
            ServerNetworking.sendCombo(player, 0);
        }
    }
}

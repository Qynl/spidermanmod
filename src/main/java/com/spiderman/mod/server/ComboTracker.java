package com.spiderman.mod.server;

import com.spiderman.mod.util.SoundUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * ULTIMATE COMBO SYSTEM - Style-based, rewarding, comic book flair.
 * - Combo window 100 ticks
 * - Damage mult up to 6 stacks
 * - Style points, mastery, effects
 * - Ranks: Nice, Great, Amazing, Spectacular, Ultimate
 */
public final class ComboTracker {
    private ComboTracker() {
    }

    public static void onWebHit(ServerPlayerEntity player, LivingEntity target, float damage) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        SpiderConfig cfg = SpiderConfig.get();
        long time = player.age;
        
        if (time > powers.comboUntil) {
            // Combo broke - check if it was good
            if (powers.combo >= 5) {
                player.sendMessage(Text.literal("§7Combo ended §8- §e" + powers.combo + " hits! §6Style +" + (powers.combo * 3)), true);
                powers.stylePoints += powers.combo * 3;
            }
            powers.combo = 0;
        }
        
        powers.combo++;
        powers.comboUntil = time + cfg.comboWindow;
        
        if (powers.combo > powers.maxCombo) {
            powers.maxCombo = powers.combo;
        }
        
        int styleGain = 1 + (Math.min(powers.combo, 6) / 3);
        powers.stylePoints += styleGain;
        MasteryLogic.addMastery(player, 2 + Math.min(powers.combo, 6) / 2);
        
        if (powers.combo >= 3 && powers.combo <= 8 && player.getWorld() instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 
                    Math.min(powers.combo, 6), 0.25, 0.25, 0.25, 0.15);
            } catch (Exception ignored) {}
        }
        
        if (powers.combo >= 5) {
            try {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0));
                player.playSound(SoundUtil.unwrap(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP), 1.0f, 1.0f + powers.combo * 0.05f);
            } catch (Exception ignored) {}
        }
        
        if (powers.combo >= 8) {
            try {
                if (player.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 
                        5, 0.3, 0.3, 0.3, 0.2);
                }
                player.sendMessage(Text.literal(getComboMessage(powers.combo)), true);
            } catch (Exception ignored) {}
        }
        
        ServerNetworking.sendCombo(player, powers.combo);
    }

    private static String getComboMessage(int combo) {
        if (combo >= 10) return "§6§l★ ULTIMATE COMBO x" + combo + "! ★ §e§lYOU ARE AMAZING!";
        if (combo >= 8) return "§c§lSPECTACULAR COMBO x" + combo + "! §fKeep it up!";
        if (combo >= 6) return "§5§lAMAZING COMBO x" + combo + "! §7Style!";
        if (combo >= 4) return "§a§lGREAT COMBO x" + combo + "!";
        if (combo >= 3) return "§eNice combo x" + combo + "!";
        return "§7Combo x" + combo;
    }

    public static float damageMult(PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        int capped = Math.min(powers.combo, cfg.comboCap);
        float mult = (float) (1.0 + cfg.comboBonus * capped);
        if (powers.stylePoints > 500) mult *= 1.05f;
        if (powers.stylePoints > 1000) mult *= 1.08f;
        if (mult > 2.5f) mult = 2.5f;
        return mult;
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (powers.combo > 0 && player.age > powers.comboUntil) {
            if (powers.combo >= 4) {
                // Save combo as style
                powers.stylePoints += powers.combo;
            }
            powers.combo = 0;
            ServerNetworking.sendCombo(player, 0);
        }
    }
    
    public static String getRank(int combo) {
        if (combo >= 10) return "ULTIMATE";
        if (combo >= 8) return "SPECTACULAR";
        if (combo >= 6) return "AMAZING";
        if (combo >= 4) return "GREAT";
        if (combo >= 2) return "NICE";
        return "";
    }
}

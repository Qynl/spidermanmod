package com.spiderman.mod.server;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import com.spiderman.mod.ModSounds;
import com.spiderman.mod.SpiderManMod;
import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * First bite, stage-ups and the physical-boost attributes. Fall damage itself
 * is handled by the entity mixin; everything else here is attribute-driven so
 * vanilla movement code (and client prediction) just works.
 *
 * Bughunt improvements:
 * - grantBite now resets transient state (cooldowns, swing, etc.) to avoid stuck states
 * - tryStageUp now loops to handle large mastery gains (multiple stage ups at once)
 * - stage thresholds are validated to be increasing
 * - attributes are clamped to safe ranges
 */
public final class TransformLogic {
    private TransformLogic() {
    }

    /** Grants powers from a radioactive bite. Returns false if already powered. */
    public static boolean grantBite(ServerPlayerEntity player) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (powers.hasPowers) {
            return false;
        }
        powers.hasPowers = true;
        powers.stage = 0;
        powers.mastery = 0;
        powers.resetTransient(); // Bughunt: clear any leftover transient state
        applyStageAttributes(player, powers);
        if (SpiderConfig.get().transformEffects) {
            try {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 220, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2));
            } catch (Exception e) {
                // Status effect might fail if player is in weird state — don't crash bite
                SpiderManMod.LOGGER.warn("[spiderman] transform effects failed", e);
            }
        }
        try {
            player.playSound(ModSounds.TRANSFORM, 1.0f, 1.0f);
        } catch (Exception ignored) {}
        player.sendMessage(Text.literal("§c§lA radioactive spider bites you! You feel... different."), false);
        player.sendMessage(Text.literal("§7Press §eG§7 to attune your spider-sense. Hold §eG§7 + move mouse to select webs. Powers grow with time and use."), false);
        grantAdvancement(player, "transformation");
        ServerNetworking.sendPowers(player);
        SpiderState.save(player.getServer());
        SpiderManMod.LOGGER.info("[spiderman] {} was bitten", player.getGameProfile().getName());
        return true;
    }

    /**
     * Stages up when mastery crosses the next threshold.
     * Now loops: if you gain 500 mastery at once, you can jump multiple stages.
     * Returns new stage or -1 if no stage up.
     */
    public static int tryStageUp(ServerPlayerEntity player) {
        if (player == null) return -1;
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers || powers.stage < 0 || powers.stage >= 4) {
            return -1;
        }
        int[] thresholds = SpiderConfig.get().stageThresholds;
        if (thresholds == null || thresholds.length != 4) {
            thresholds = new int[]{100, 300, 700, 1400};
        }

        int stagedUpTo = -1;
        // Loop to handle large mastery gains
        while (powers.stage >= 0 && powers.stage < 4 && powers.stage < thresholds.length) {
            int needed = thresholds[powers.stage];
            if (needed < 1) needed = 1;
            if (powers.mastery >= needed) {
                powers.stage++;
                stagedUpTo = powers.stage;
                applyStageAttributes(player, powers);
                try {
                    player.playSound(ModSounds.STAGE_UP, 1.0f, 1.0f);
                    player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
                } catch (Exception ignored) {}
                player.sendMessage(Text.literal("§a§lSpider powers advanced to stage "
                        + powers.stage + ": " + stageName(powers.stage)), false);
                player.sendMessage(Text.literal("§7Mastery: " + powers.mastery + " — keep using powers and time will make you stronger!"), false);
                grantAdvancement(player, "stage_" + powers.stage);
                // Don't save every loop iteration — save after loop
            } else {
                break;
            }
        }
        if (stagedUpTo >= 0) {
            ServerNetworking.sendPowers(player);
            SpiderState.save(player.getServer());
            return stagedUpTo;
        }
        return -1;
    }

    /** Strips powers (admin reset). Restores vanilla attributes. */
    public static void resetPowers(ServerPlayerEntity player) {
        if (player == null) return;
        PlayerPowers powers = SpiderState.get(player.getUuid());
        powers.hasPowers = false;
        powers.stage = 0;
        powers.mastery = 0;
        powers.resetTransient();
        try {
            setBase(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1);
            setBase(player, EntityAttributes.GENERIC_JUMP_STRENGTH, 0.42);
            setBase(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0);
            setBase(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 3.0);
            setBase(player, EntityAttributes.GENERIC_STEP_HEIGHT, 0.6);
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] reset attributes failed", e);
        }
        ServerNetworking.sendPowers(player);
        ServerNetworking.sendSwingOff(player);
        SpiderState.save(player.getServer());
    }

    public static void applyStageAttributes(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.hasPowers) {
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        double tier = Math.max(0, Math.min(5, powers.stage + 1));
        try {
            double speed = 0.1 * (1.0 + (cfg.speedMult - 1.0) * tier);
            double jump = 0.42 * (1.0 + (cfg.jumpMult - 1.0) * tier);
            double strength = 1.0 + (cfg.strengthMult - 1.0) * tier;
            double safeFall = 3.0 + 4.0 * tier;
            // Clamp to prevent absurd values from bad config
            speed = Math.max(0.05, Math.min(0.5, speed));
            jump = Math.max(0.2, Math.min(2.0, jump));
            strength = Math.max(1.0, Math.min(20.0, strength));
            safeFall = Math.max(3.0, Math.min(100.0, safeFall));

            setBase(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, speed);
            setBase(player, EntityAttributes.GENERIC_JUMP_STRENGTH, jump);
            setBase(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, strength);
            setBase(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, safeFall);
            setBase(player, EntityAttributes.GENERIC_STEP_HEIGHT, cfg.stepHeight ? 1.0 : 0.6);
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] applyStageAttributes failed", e);
        }
    }

    private static void setBase(ServerPlayerEntity player, RegistryEntry attribute, double value) {
        try {
            EntityAttributeInstance inst = player.getAttributeInstance(attribute);
            if (inst != null) {
                inst.setBaseValue(value);
            }
        } catch (Exception e) {
            // Attribute might not exist in some environments — don't crash
        }
    }

    private static String stageName(int stage) {
        switch (stage) {
            case 0: return "Latent Senses";
            case 1: return "Wall Crawler";
            case 2: return "Web Slinger";
            case 3: return "Sky Dancer";
            case 4: return "Spider Master";
            default: return "Unknown";
        }
    }

    public static void grantAdvancement(ServerPlayerEntity player, String path) {
        try {
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            AdvancementEntry entry = server.getAdvancementLoader()
                    .get(Identifier.of(SpiderManMod.MOD_ID, path));
            if (entry != null) {
                player.getAdvancementTracker().grantCriterion(entry, "trigger");
            }
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] advancement grant failed: {}", path, e);
        }
    }
}

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
 */
public final class TransformLogic {
    private TransformLogic() {
    }

    /** Grants powers from a radioactive bite. Returns false if already powered. */
    public static boolean grantBite(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (powers.hasPowers) {
            return false;
        }
        powers.hasPowers = true;
        powers.stage = 0;
        powers.mastery = 0;
        applyStageAttributes(player, powers);
        if (SpiderConfig.get().transformEffects) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 220, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2));
        }
        player.playSound(ModSounds.TRANSFORM, 1.0f, 1.0f);
        player.sendMessage(Text.literal("§c§lA radioactive spider bites you! You feel... different."), false);
        player.sendMessage(Text.literal("§7Press §eG§7 to attune your spider-sense. Powers grow with mastery."), false);
        grantAdvancement(player, "transformation");
        ServerNetworking.sendPowers(player);
        SpiderState.save(player.getServer());
        SpiderManMod.LOGGER.info("[spiderman] {} was bitten", player.getGameProfile().getName());
        return true;
    }

    /** Stages up when mastery crosses the next threshold. Returns new stage or -1. */
    public static int tryStageUp(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers || powers.stage >= 4) {
            return -1;
        }
        int[] thresholds = SpiderConfig.get().stageThresholds;
        if (powers.stage < thresholds.length && powers.mastery >= thresholds[powers.stage]) {
            powers.stage++;
            applyStageAttributes(player, powers);
            player.playSound(ModSounds.STAGE_UP, 1.0f, 1.0f);
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            player.sendMessage(Text.literal("§a§lSpider powers advanced to stage "
                    + powers.stage + ": " + stageName(powers.stage)), false);
            grantAdvancement(player, "stage_" + powers.stage);
            ServerNetworking.sendPowers(player);
            SpiderState.save(player.getServer());
            return powers.stage;
        }
        return -1;
    }

    /** Strips powers (admin reset). Restores vanilla attributes. */
    public static void resetPowers(ServerPlayerEntity player) {
        PlayerPowers powers = SpiderState.get(player.getUuid());
        powers.hasPowers = false;
        powers.stage = 0;
        powers.mastery = 0;
        powers.stopSwing();
        setBase(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1);
        setBase(player, EntityAttributes.GENERIC_JUMP_STRENGTH, 0.42);
        setBase(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0);
        setBase(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 3.0);
        setBase(player, EntityAttributes.GENERIC_STEP_HEIGHT, 0.6);
        ServerNetworking.sendPowers(player);
        ServerNetworking.sendSwingOff(player);
        SpiderState.save(player.getServer());
    }

    public static void applyStageAttributes(ServerPlayerEntity player, PlayerPowers powers) {
        if (!powers.hasPowers) {
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        double tier = powers.stage + 1;
        setBase(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1 * (1.0 + (cfg.speedMult - 1.0) * tier));
        setBase(player, EntityAttributes.GENERIC_JUMP_STRENGTH, 0.42 * (1.0 + (cfg.jumpMult - 1.0) * tier));
        setBase(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0 + (cfg.strengthMult - 1.0) * tier);
        setBase(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 3.0 + 4.0 * tier);
        setBase(player, EntityAttributes.GENERIC_STEP_HEIGHT, cfg.stepHeight ? 1.0 : 0.6);
    }

    private static void setBase(ServerPlayerEntity player, RegistryEntry attribute, double value) {
        EntityAttributeInstance inst = player.getAttributeInstance(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
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

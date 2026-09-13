package com.spiderman.mod.server;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
 * ULTIMATE TRANSFORMATION - Cinematic Spider-Man origin.
 * - Epic effects, sounds, particles
 * - Comic book messages
 * - Style points and mastery
 */
public final class TransformLogic {
    private TransformLogic() {
    }

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
        powers.stylePoints = 0;
        powers.resetTransient();
        applyStageAttributes(player, powers);
        
        if (SpiderConfig.get().transformEffects) {
            try {
                // Epic transformation effects
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 260, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 120, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 240, 3));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
                
                // Particles
                if (player.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1, player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
                    sw.spawnParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.5, 0.5, 0.2);
                    sw.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
                }
                
                // Sounds
                player.playSound(SoundEvents.ENTITY_SPIDER_HURT, 1.0f, 0.5f);
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), 
                    SoundEvents.ENTITY_SPIDER_DEATH, player.getSoundCategory(), 0.8f, 0.6f);
                
            } catch (Exception e) {
                SpiderManMod.LOGGER.warn("[spiderman] transform effects failed", e);
            }
        }
        
        try {
            player.playSound(ModSounds.TRANSFORM, 1.0f, 1.0f);
            player.playSound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
        } catch (Exception ignored) {}
        
        // Epic comic book messages
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§c§l§nRADIOACTIVE BITE!"), false);
        player.sendMessage(Text.literal("§fA §c§lradioactive spider §fsinks its fangs into you!"), false);
        player.sendMessage(Text.literal("§7Venom courses through your veins... your DNA rewrites itself..."), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§e§lYou feel... §c§lDIFFERENT§e§l!"), false);
        player.sendMessage(Text.literal("§7Press §e§lG§7 to feel your spider-sense. §eHold G§7 + §emove mouse§7 to choose webs."), false);
        player.sendMessage(Text.literal("§7Climb walls, swing, fight — §etime and style§7 make you stronger!"), false);
        player.sendMessage(Text.literal(""), false);
        
        grantAdvancement(player, "transformation");
        ServerNetworking.sendPowers(player);
        SpiderState.save(player.getServer());
        SpiderManMod.LOGGER.info("[spiderman] {} was bitten - ultimate origin!", player.getGameProfile().getName());
        return true;
    }

    public static int tryStageUp(ServerPlayerEntity player) {
        if (player == null) return -1;
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers || powers.stage < 0 || powers.stage >= 4) {
            return -1;
        }
        int[] thresholds = SpiderConfig.get().stageThresholds;
        if (thresholds == null || thresholds.length != 4) {
            thresholds = new int[]{80, 250, 600, 1200};
        }

        int stagedUpTo = -1;
        while (powers.stage >= 0 && powers.stage < 4 && powers.stage < thresholds.length) {
            int needed = thresholds[powers.stage];
            if (needed < 1) needed = 1;
            if (powers.mastery >= needed) {
                int oldStage = powers.stage;
                powers.stage++;
                stagedUpTo = powers.stage;
                applyStageAttributes(player, powers);
                
                // EPIC STAGE UP
                try {
                    player.playSound(ModSounds.STAGE_UP, 1.2f, 1.0f);
                    player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                    player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
                    
                    if (player.getWorld() instanceof ServerWorld sw) {
                        sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
                        sw.spawnParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 1, player.getZ(), 30, 0.8, 0.5, 0.8, 0.15);
                        sw.spawnParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1, player.getZ(), 15, 0.3, 0.5, 0.3, 0.1);
                    }
                    
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, 0));
                    
                } catch (Exception ignored) {}
                
                // Epic messages
                String newName = stageName(powers.stage);
                String oldName = stageName(oldStage);
                
                player.sendMessage(Text.literal(""), false);
                player.sendMessage(Text.literal("§6§l§n★ STAGE UP! ★"), false);
                player.sendMessage(Text.literal("§7" + oldName + " §8→ §a§l" + newName + " §7(Stage " + powers.stage + ")"), false);
                player.sendMessage(Text.literal("§f" + getStageDescription(powers.stage)), false);
                player.sendMessage(Text.literal("§7Mastery: §e" + powers.mastery + " §7| §6Style: §e" + powers.stylePoints), false);
                
                if (powers.stage == 1) {
                    player.sendMessage(Text.literal("§a§lNEW: §fWall crawling! Touch walls to stick, sprint to wall-run, jump to wall-jump!"), false);
                } else if (powers.stage == 2) {
                    player.sendMessage(Text.literal("§a§lNEW: §fWeb Slinger! Shot, Swing, Zip, Pull, Trap unlocked! Hold G for wheel!"), false);
                } else if (powers.stage == 3) {
                    player.sendMessage(Text.literal("§a§lNEW: §fSky Dancer! Line, Burst, Impact, Platform + ultimate swinging!"), false);
                } else if (powers.stage == 4) {
                    player.sendMessage(Text.literal("§6§lULTIMATE: §e§lSPIDER MASTER! §fAll powers maxed, style bonuses, ultimate movement!"), false);
                    player.sendMessage(Text.literal("§7You are now a true Spider-Man. Swing, fight, be amazing!"), false);
                }
                player.sendMessage(Text.literal(""), false);
                
                grantAdvancement(player, "stage_" + powers.stage);
                
                // Style bonus for stage up
                powers.stylePoints += powers.stage * 25;
                
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

    public static void resetPowers(ServerPlayerEntity player) {
        if (player == null) return;
        PlayerPowers powers = SpiderState.get(player.getUuid());
        powers.hasPowers = false;
        powers.stage = 0;
        powers.mastery = 0;
        powers.stylePoints = 0;
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
        
        player.sendMessage(Text.literal("§c§lPowers reset - you are no longer Spider-Man"), false);
    }

    public static void applyStageAttributes(ServerPlayerEntity player, PlayerPowers powers) {
        if (player == null || !powers.hasPowers) {
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        double tier = Math.max(0, Math.min(5, powers.stage + 1));
        try {
            double speed = 0.1 * (1.0 + (cfg.speedMult - 1.0) * tier * 1.1);
            double jump = 0.42 * (1.0 + (cfg.jumpMult - 1.0) * tier * 1.1);
            double strength = 1.0 + (cfg.strengthMult - 1.0) * tier * 1.1;
            double safeFall = 3.0 + 5.0 * tier;
            
            // Style bonus
            double styleMult = 1.0 + (powers.stylePoints * 0.0002);
            if (styleMult > 1.2) styleMult = 1.2;
            speed *= styleMult;
            jump *= styleMult;
            
            speed = Math.max(0.05, Math.min(0.6, speed));
            jump = Math.max(0.2, Math.min(2.2, jump));
            strength = Math.max(1.0, Math.min(25.0, strength));
            safeFall = Math.max(3.0, Math.min(150.0, safeFall));

            setBase(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, speed);
            setBase(player, EntityAttributes.GENERIC_JUMP_STRENGTH, jump);
            setBase(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, strength);
            setBase(player, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, safeFall);
            setBase(player, EntityAttributes.GENERIC_STEP_HEIGHT, cfg.stepHeight ? 1.0 : 0.6);
            
            // Extra: attack speed and luck for Spider-Man
            try {
                setBase(player, EntityAttributes.GENERIC_ATTACK_SPEED, 4.0 + tier * 0.3);
                setBase(player, EntityAttributes.GENERIC_LUCK, tier * 0.5);
            } catch (Exception ignored) {}
            
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
    
    private static String getStageDescription(int stage) {
        switch (stage) {
            case 0: return "Your senses tingle... time will make you stronger";
            case 1: return "Superhuman agility - walls are now your playground";
            case 2: return "Web shooters online - swing through the city!";
            case 3: return "Master of momentum - the sky is yours";
            case 4: return "With great power... you are truly amazing!";
            default: return "";
        }
    }

    public static void grantAdvancement(ServerPlayerEntity player, String path) {
        try {
            MinecraftServer server = player.getServer();
            if (server == null) return;
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

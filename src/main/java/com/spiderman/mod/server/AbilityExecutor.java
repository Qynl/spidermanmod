package com.spiderman.mod.server;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

import com.spiderman.mod.ModSounds;
import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.entity.WebShotEntity;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.AbilityIds;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * ULTIMATE ABILITY EXECUTOR - Every web ability is now epic, distinct, and fun.
 * - White lines for all
 * - Unique mechanics per ability
 * - Style points, particles, sounds
 * - Combo integration
 */
public final class AbilityExecutor {
    private AbilityExecutor() {
    }

    public static void use(ServerPlayerEntity player, int ability, int hand) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (powers == null || !powers.canUse(ability, player.age)) return;
        hand = hand == 1 ? 1 : 0;
        try {
            switch (ability) {
                case AbilityIds.SHOT: fireShot(player, powers, hand, false); break;
                case AbilityIds.SWING: toggleSwing(player, powers, hand); break;
                case AbilityIds.ZIP: zip(player, powers, hand); break;
                case AbilityIds.PULL: pull(player, powers, hand); break;
                case AbilityIds.TRAP: trap(player, powers, hand); break;
                case AbilityIds.LINE: line(player, powers, hand); break;
                case AbilityIds.BURST: burst(player, powers); break;
                case AbilityIds.IMPACT: fireShot(player, powers, hand, true); break;
                case AbilityIds.PLATFORM: platform(player, powers); break;
                default: break;
            }
        } catch (Exception e) {
            com.spiderman.mod.SpiderManMod.LOGGER.warn("[spiderman] ability {} failed for {}", ability, player.getGameProfile().getName(), e);
        }
    }

    // ---------- SHOT - Fast white line, instant impact ----------
    private static void fireShot(ServerPlayerEntity player, PlayerPowers powers, int hand, boolean heavy) {
        SpiderConfig cfg = SpiderConfig.get();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null || !Double.isFinite(eye.x)) return;
        if (look.lengthSquared() < 0.001) look = new Vec3d(0, 0, 1);

        Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 0.001 || !Double.isFinite(right.x)) right = new Vec3d(1.0, 0.0, 0.0);
        right = right.normalize();

        float damage = (float) ((heavy ? cfg.impactDamage : cfg.shotDamage) * ComboTracker.damageMult(powers));
        if (heavy) {
            damage += powers.stage * 2.5f;
            damage += powers.stylePoints * 0.01f;
        } else {
            damage += powers.combo * 0.5f;
        }

        World world = player.getWorld();
        if (world == null) return;

        Vec3d origin = offsetOrigin(eye, right, hand == 1 ? -0.38 : 0.38);
        Vec3d target = eye.add(look.x * cfg.webRange, look.y * cfg.webRange, look.z * cfg.webRange);
        BlockHitResult hit = null;
        try {
            hit = world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        } catch (Exception ignored) {}
        Vec3d lineEnd = (hit != null && hit.getType() != HitResult.Type.MISS) ? hit.getPos() : target;

        // White line - longer for heavy
        ServerNetworking.sendSwing(player, true, lineEnd.x, lineEnd.y, lineEnd.z, hand, heavy ? 25 : 15);

        // Fast projectile - no gravity, high speed
        WebShotEntity.shoot(world, player, origin, look, damage, heavy);

        powers.setCooldown(heavy ? AbilityIds.IMPACT : AbilityIds.SHOT, player.age);
        MasteryLogic.addMastery(player, heavy ? 5 : 2);
        
        if (!heavy) powers.stylePoints += 1;
        else powers.stylePoints += 5;

        if (heavy && world instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, origin.x, origin.y, origin.z, 25, 0.25, 0.25, 0.25, 0.12);
                sw.spawnParticles(ParticleTypes.CRIT, lineEnd.x, lineEnd.y, lineEnd.z, 15, 0.4, 0.4, 0.4, 0.25);
                sw.spawnParticles(ParticleTypes.EXPLOSION, lineEnd.x, lineEnd.y, lineEnd.z, 1, 0.1, 0.1, 0.1, 0.0);
            } catch (Exception ignored) {}
            try {
                world.playSound(null, lineEnd.x, lineEnd.y, lineEnd.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6f, 1.3f);
            } catch (Exception ignored) {}
        } else if (world instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, origin.x, origin.y, origin.z, 8, 0.15, 0.15, 0.15, 0.08);
            } catch (Exception ignored) {}
        }
    }

    private static Vec3d offsetOrigin(Vec3d eye, Vec3d right, double side) {
        if (eye == null || right == null) return new Vec3d(0, 1, 0);
        return eye.add(right.x * side, -0.18, right.z * side);
    }

    // ---------- SWING - Ultimate momentum swinging ----------
    private static void toggleSwing(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        if (powers.swinging) {
            SwingPhysics.detach(player, powers, true);
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        double range = cfg.swingRange * 1.6;
        Vec3d anchor = SwingPhysics.findAnchor(player, range);
        if (anchor == null) {
            anchor = SwingPhysics.findAnchor(player, range * 1.4);
        }
        if (anchor == null) {
            actionbar(player, "§7No anchor! Look at tall buildings, range " + (int)range + " §8[Tip: Look slightly up]");
            return;
        }
        SwingPhysics.attach(player, powers, anchor, hand);
        double dist = anchor.distanceTo(player.getPos());
        powers.ropeLen = Math.max(4.0, Math.min(dist * 0.88, cfg.swingRange * 1.3));

        // Initial boost - more dynamic
        Vec3d look = player.getRotationVector();
        double boostPower = 0.35 + (powers.consecutiveSwings * 0.05) + (powers.stylePoints * 0.0005);
        if (boostPower > 0.8) boostPower = 0.8;
        Vec3d boost = new Vec3d(look.x * boostPower, 0.15, look.z * boostPower);
        Vec3d vel = player.getVelocity().add(boost);
        SwingPhysics.push(player, vel);

        powers.setCooldown(AbilityIds.SWING, player.age);
        MasteryLogic.addMastery(player, 4);
        
        if (powers.consecutiveSwings >= 3) {
            actionbar(player, "§aSwing x" + powers.consecutiveSwings + "! §eStyle +" + powers.stylePoints + " §7[Look up to go fast!]");
        } else {
            actionbar(player, "§aSwinging! §7Up=fast, Down=arc, Sprint=boost, Jump=launch");
        }
    }

    // ---------- LINE - Web bridge you can walk/zip on ----------
    private static void line(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        double range = cfg.swingRange * 2.0;
        Vec3d anchor = SwingPhysics.findAnchor(player, range);
        if (anchor == null) {
            actionbar(player, "§7No line anchor (" + (int)range + " blocks) §8[Look at high point]");
            return;
        }
        if (powers.swinging) SwingPhysics.detach(player, powers, false);

        SwingPhysics.attach(player, powers, anchor, hand);
        double dist = anchor.distanceTo(player.getPos());
        powers.ropeLen = Math.min(dist + 10.0, cfg.swingRange * 1.8);

        // Epic launch
        Vec3d look = player.getRotationVector();
        double launch = 0.7 + (powers.stylePoints * 0.001);
        if (launch > 1.2) launch = 1.2;
        Vec3d vel = player.getVelocity().multiply(1.9).add(look.x * launch, 0.4, look.z * launch);
        if (vel.length() > 4.0) vel = vel.normalize().multiply(4.0);
        SwingPhysics.push(player, vel);

        // Create EPIC web line bridge
        createWebLine(player, player.getEyePos(), anchor, true);

        powers.setCooldown(AbilityIds.LINE, player.age);
        MasteryLogic.addMastery(player, 6);
        powers.stylePoints += 12;
        actionbar(player, "§b§lWEB LINE! §fHigh-speed traverse + walkable bridge");
    }

    private static void createWebLine(ServerPlayerEntity player, Vec3d start, Vec3d end, boolean epic) {
        if (start == null || end == null) return;
        double dist = start.distanceTo(end);
        if (dist > 90 || dist < 2) return;
        int steps = (int) (dist / 1.8);
        if (steps < 4) steps = 4;
        if (steps > 30) steps = 30;

        ServerWorld world = player.getServerWorld();
        for (int i = 1; i < steps; i++) {
            double t = (double) i / (double) steps;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            try {
                if (world.getBlockState(pos).isAir()) {
                    WebCleanup.place(player, pos);
                }
                // Walkable - place below every other
                BlockPos below = pos.down();
                if (world.getBlockState(below).isAir() && i % 2 == 0) {
                    WebCleanup.place(player, below);
                }
                // Side supports for epic line
                if (epic && i % 3 == 0) {
                    BlockPos side1 = pos.add(1, 0, 0);
                    BlockPos side2 = pos.add(-1, 0, 0);
                    BlockPos side3 = pos.add(0, 0, 1);
                    BlockPos side4 = pos.add(0, 0, -1);
                    if (world.getBlockState(side1).isAir() && Math.random() < 0.3) WebCleanup.place(player, side1);
                    if (world.getBlockState(side2).isAir() && Math.random() < 0.3) WebCleanup.place(player, side2);
                    if (world.getBlockState(side3).isAir() && Math.random() < 0.3) WebCleanup.place(player, side3);
                    if (world.getBlockState(side4).isAir() && Math.random() < 0.3) WebCleanup.place(player, side4);
                }
            } catch (Exception ignored) {}
        }
        if (world != null) {
            try {
                world.spawnParticles(ParticleTypes.ITEM_COBWEB, start.x, start.y, start.z, steps * 3,
                        (end.x - start.x) * 0.4, (end.y - start.y) * 0.4, (end.z - start.z) * 0.4, 0.06);
                if (epic) {
                    world.spawnParticles(ParticleTypes.CRIT, start.x, start.y, start.z, 10, 0.3, 0.3, 0.3, 0.1);
                }
            } catch (Exception ignored) {}
        }
    }

    // ---------- ZIP - White line dash with style ----------
    private static void zip(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;
        if (look.lengthSquared() < 0.001) look = new Vec3d(0, 0, 1);

        double range = cfg.zipRange * 1.5;
        Vec3d to = eye.add(look.x * range, look.y * range, look.z * range);
        BlockHitResult hit = null;
        try {
            hit = player.getWorld().raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        } catch (Exception ignored) {}
        Vec3d target = (hit != null && hit.getType() != HitResult.Type.MISS) ? hit.getPos() : to;
        if (target == null || !Double.isFinite(target.x)) return;

        if (powers.swinging) SwingPhysics.detach(player, powers, false);
        powers.climbing = false;
        powers.zipX = target.x;
        powers.zipY = target.y;
        powers.zipZ = target.z;
        powers.zipTicks = 20;
        try {
            player.setNoGravity(true);
        } catch (Exception ignored) {}
        try {
            player.playSound(ModSounds.WEB_ZIP, 1.0f, 1.3f);
            player.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
        } catch (Exception ignored) {}

        ServerNetworking.sendSwing(player, true, target.x, target.y, target.z, hand, 22);

        if (player.getWorld() instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, eye.x, eye.y, eye.z, 20,
                        (target.x - eye.x) * 0.2, (target.y - eye.y) * 0.2, (target.z - eye.z) * 0.2, 0.06);
                sw.spawnParticles(ParticleTypes.CLOUD, eye.x, eye.y, eye.z, 5, 0.2, 0.2, 0.2, 0.05);
            } catch (Exception ignored) {}
        }

        powers.setCooldown(AbilityIds.ZIP, player.age);
        MasteryLogic.addMastery(player, 4);
        powers.stylePoints += 6;
    }

    // ---------- PULL - Hold to pull with style ----------
    private static void pull(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        LivingEntity target = pickTarget(player, cfg.pullRange, 3.0);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;

        if (target == null) {
            double range = cfg.pullRange;
            Vec3d to = eye.add(look.x * range, look.y * range, look.z * range);
            BlockHitResult hit = null;
            try {
                hit = player.getWorld().raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            } catch (Exception ignored) {}
            Vec3d blockTarget = (hit != null && hit.getType() != HitResult.Type.MISS) ? hit.getPos() : to;

            powers.pullTicks = 35;
            powers.pullX = blockTarget.x;
            powers.pullY = blockTarget.y;
            powers.pullZ = blockTarget.z;
            powers.pullingPlayer = true;
            powers.pullTargetId = null;
            try {
                player.setNoGravity(true);
            } catch (Exception ignored) {}
            ServerNetworking.sendSwing(player, true, blockTarget.x, blockTarget.y, blockTarget.z, hand, 35);
            try {
                player.playSound(ModSounds.WEB_ZIP, 1.0f, 0.8f);
            } catch (Exception ignored) {}
            powers.setCooldown(AbilityIds.PULL, player.age);
            MasteryLogic.addMastery(player, 3);
            powers.stylePoints += 4;
            actionbar(player, "§e§lPULLING §fto block - hold!");
            return;
        }

        if (target.isSpectator() || !target.isAlive()) {
            actionbar(player, "§7Can't pull that");
            return;
        }

        powers.pullTicks = 50;
        powers.pullX = target.getX();
        powers.pullY = target.getY() + target.getHeight() * 0.5;
        powers.pullZ = target.getZ();
        powers.pullTargetId = target.getUuid();
        powers.pullingPlayer = target instanceof ServerPlayerEntity;

        if (powers.pullingPlayer) {
            try {
                player.setNoGravity(true);
            } catch (Exception ignored) {}
        }

        ServerNetworking.sendSwing(player, true, target.getX(), target.getY() + 1.0, target.getZ(), hand, 50);
        try {
            player.playSound(ModSounds.WEB_ZIP, 1.0f, 0.8f);
            player.playSound(SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.7f);
        } catch (Exception ignored) {}
        powers.setCooldown(AbilityIds.PULL, player.age);
        MasteryLogic.addMastery(player, 4);
        powers.stylePoints += 8;
        actionbar(player, "§e§lPULLING §f" + target.getName().getString() + " §7- yank!");
    }

    // ---------- TRAP - Ultimate cluster webs ----------
    private static void trap(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        LivingEntity victim = pickTarget(player, cfg.trapRange, 3.0);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;

        if (victim != null && !(victim instanceof ServerPlayerEntity) && victim.isAlive() && !victim.isSpectator()) {
            try {
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 250, 4));
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 150, 2));
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
                // Damage from trap
                victim.damage(player.getWorld().getDamageSources().playerAttack(player), (float)cfg.trapDamage);
            } catch (Exception ignored) {}

            BlockPos center = victim.getBlockPos();
            int placed = 0;
            for (int i = 0; i < 16; i++) {
                int dx = (int) (Math.random() * 7) - 3;
                int dy = (int) (Math.random() * 4) - 1;
                int dz = (int) (Math.random() * 7) - 3;
                BlockPos p = center.add(dx, dy, dz);
                if (WebCleanup.place(player, p)) placed++;
            }
            WebCleanup.place(player, center);
            WebCleanup.place(player, center.up());
            WebCleanup.place(player, center.up(2));

            try {
                player.playSound(ModSounds.WEB_SHOT, 1.0f, 0.7f);
                player.playSound(ModSounds.WEB_SPLAT, 1.0f, 0.8f);
                player.playSound(SoundEvents.BLOCK_COBWEB_PLACE, 1.0f, 0.9f);
            } catch (Exception ignored) {}
            ServerNetworking.sendSwing(player, true, victim.getX(), victim.getY() + 1.0, victim.getZ(), hand, 30);

            if (player.getWorld() instanceof ServerWorld sw) {
                try {
                    sw.spawnParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY() + 1, victim.getZ(), 40, 1.8, 1.0, 1.8, 0.12);
                    sw.spawnParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1, victim.getZ(), 10, 0.5, 0.5, 0.5, 0.2);
                } catch (Exception ignored) {}
            }

            powers.setCooldown(AbilityIds.TRAP, player.age);
            MasteryLogic.addMastery(player, 6);
            powers.stylePoints += 10;
            actionbar(player, "§a§lTRAPPED §f" + victim.getName().getString() + " §7in web cluster!");
            return;
        }

        Vec3d to = eye.add(look.x * cfg.trapRange, look.y * cfg.trapRange, look.z * cfg.trapRange);
        BlockHitResult hit = null;
        try {
            hit = player.getWorld().raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        } catch (Exception ignored) {}
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            actionbar(player, "§7Nothing to web §8[Aim at enemy or block]");
            return;
        }

        BlockPos base = hit.getBlockPos().offset(hit.getSide());
        int placed = 0;
        for (int i = 0; i < 14; i++) {
            int dx = (int) (Math.random() * 7) - 3;
            int dy = (int) (Math.random() * 4) - 1;
            int dz = (int) (Math.random() * 7) - 3;
            BlockPos p = base.add(dx, dy, dz);
            if (WebCleanup.place(player, p)) placed++;
        }
        if (placed == 0) {
            if (!WebCleanup.place(player, base)) {
                actionbar(player, "§7Web won't stick");
                return;
            }
        }

        try {
            player.playSound(ModSounds.WEB_SHOT, 1.0f, 0.7f);
            player.playSound(ModSounds.WEB_SPLAT, 1.0f, 0.8f);
        } catch (Exception ignored) {}
        Vec3d pos = hit.getPos();
        if (pos != null && Double.isFinite(pos.x)) {
            ServerNetworking.sendSwing(player, true, pos.x, pos.y, pos.z, hand, 30);
        }

        if (player.getWorld() instanceof ServerWorld sw && pos != null) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, pos.x, pos.y, pos.z, 30, 1.2, 1.0, 1.2, 0.12);
            } catch (Exception ignored) {}
        }

        powers.setCooldown(AbilityIds.TRAP, player.age);
        MasteryLogic.addMastery(player, 5);
        powers.stylePoints += 6;
    }

    // ---------- BURST - Most epic ----------
    private static void burst(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        boolean venom = cfg.experimentalVenomBlast;
        double radius = cfg.burstRadius * (venom ? 1.8 : 1.0) + powers.stage * 1.0 + powers.stylePoints * 0.002;
        if (radius > 12) radius = 12;
        float damage = (float) (cfg.burstDamage * ComboTracker.damageMult(powers) * (venom ? 2.5 : 1.0) + powers.stage * 2.0f + powers.combo * 1.0f);
        Vec3d center = player.getPos().add(0.0, 1.0, 0.0);
        World world = player.getWorld();
        if (world == null) return;
        DamageSources sources = world.getDamageSources();
        Box box = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> caught = null;
        try {
            caught = world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != null && e.isAlive() && e != player && !e.isSpectator());
        } catch (Exception e) {
            caught = List.of();
        }

        for (LivingEntity e : caught) {
            try {
                e.damage(sources.playerAttack(player), damage);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 3));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 2));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
                Vec3d away = e.getPos().subtract(center);
                if (away.lengthSquared() < 0.01) away = new Vec3d(0.0, 1.0, 0.0);
                away = away.normalize();
                if (Double.isFinite(away.x)) {
                    double power = venom ? 2.5 : 2.0;
                    power += powers.stylePoints * 0.002;
                    e.setVelocity(away.x * power, Math.max(0.9, away.y * power + 0.7), away.z * power);
                    e.velocityModified = true;
                }
            } catch (Exception ignored) {}
        }

        if (world instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, venom ? 100 : 70, radius * 0.7, radius * 0.6, radius * 0.7, 0.25);
                sw.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 5, 0.0, 0.0, 0.0, 0.0);
                sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.1, 0.1, 0.1, 0.0);
                sw.spawnParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 30, radius * 0.4, radius * 0.4, radius * 0.4, 0.35);
                if (venom) {
                    sw.spawnParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 20, radius * 0.5, radius * 0.5, radius * 0.5, 0.1);
                }
            } catch (Exception ignored) {}

            for (int i = 0; i < 16; i++) {
                double ang = Math.random() * Math.PI * 2;
                double dist = Math.random() * radius * 0.9;
                BlockPos p = BlockPos.ofFloored(center.x + Math.cos(ang) * dist, center.y + (Math.random() - 0.5) * 3, center.z + Math.sin(ang) * dist);
                try {
                    if (sw.getBlockState(p).isAir()) WebCleanup.place(player, p);
                } catch (Exception ignored) {}
            }
        }

        try {
            world.playSound(null, center.x, center.y, center.z,
                    (SoundEvent) SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
            world.playSound(null, center.x, center.y, center.z, ModSounds.WEB_SPLAT, SoundCategory.PLAYERS, 1.2f, 0.6f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_SPIDER_HURT, SoundCategory.PLAYERS, 0.8f, 0.5f);
            player.playSound(ModSounds.WEB_SPLAT, 1.0f, 0.5f);
        } catch (Exception ignored) {}

        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI * 2 / 12;
            double x = center.x + Math.cos(ang) * radius;
            double z = center.z + Math.sin(ang) * radius;
            double y = center.y + (Math.random() - 0.5) * 2;
            ServerNetworking.sendSwing(player, true, x, y, z, i % 2, 20);
        }

        powers.setCooldown(AbilityIds.BURST, player.age);
        MasteryLogic.addMastery(player, 8);
        powers.stylePoints += 20;
        if (caught.size() >= 3) powers.stylePoints += 15;
        
        actionbar(player, "§c§lBURST! §f" + caught.size() + " enemies §7| §eStyle +" + powers.stylePoints);
    }

    // ---------- PLATFORM - Ultimate standable webs ----------
    private static void platform(ServerPlayerEntity player, PlayerPowers powers) {
        BlockPos base = player.getBlockPos().down();
        if (base == null) return;

        int placed = 0;
        ServerWorld world = player.getServerWorld();
        int size = 2 + (powers.stage / 2);
        if (size > 4) size = 4;

        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                if (Math.abs(dx) == size && Math.abs(dz) == size && size > 2) continue; // Round corners
                BlockPos p = base.add(dx, 0, dz);
                BlockPos pUp = p.up();
                try {
                    if (world.getBlockState(p).isAir()) {
                        if (WebCleanup.place(player, p)) placed++;
                    } else if (world.getBlockState(p).isOf(Blocks.COBWEB)) {
                        placed++;
                    }
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        if (world.getBlockState(pUp).isAir()) {
                            WebCleanup.place(player, pUp);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (placed == 0) {
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    BlockPos p = player.getBlockPos().add(dx, -1, dz);
                    if (WebCleanup.place(player, p)) placed++;
                }
            }
        }

        if (placed == 0) {
            actionbar(player, "§7No room for platform - try in air");
            return;
        }

        try {
            player.playSound(ModSounds.WEB_SPLAT, 1.0f, 1.1f);
            player.playSound(SoundEvents.BLOCK_WOOL_PLACE, 0.9f, 0.9f);
            player.playSound(SoundEvents.BLOCK_COBWEB_PLACE, 1.0f, 1.2f);
        } catch (Exception ignored) {}

        try {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 1));
        } catch (Exception ignored) {}

        if (world != null) {
            try {
                world.spawnParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY(), player.getZ(), 30, size, 0.5, size, 0.1);
                world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() - 1, player.getZ(), 10, size * 0.5, 0.2, size * 0.5, 0.05);
            } catch (Exception ignored) {}
        }

        powers.setCooldown(AbilityIds.PLATFORM, player.age);
        MasteryLogic.addMastery(player, 5);
        powers.stylePoints += 8;
        actionbar(player, "§a§lWEB PLATFORM §7" + size*2+1 + "x" + size*2+1 + " §fStandable! Spiders ignore you");
    }

    private static LivingEntity pickTarget(ServerPlayerEntity player, double range, double tolerance) {
        if (player == null) return null;
        World world = player.getWorld();
        if (world == null) return null;
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null || !Double.isFinite(eye.x)) return null;
        if (look.lengthSquared() < 0.001) return null;
        Box box = new Box(eye.x - range, eye.y - range, eye.z - range, eye.x + range, eye.y + range, eye.z + range);
        List<LivingEntity> candidates = null;
        try {
            candidates = world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != null && e.isAlive() && e != player && !e.isSpectator());
        } catch (Exception e) {
            return null;
        }
        if (candidates == null) return null;
        LivingEntity best = null;
        double bestT = range;
        for (LivingEntity e : candidates) {
            try {
                Vec3d to = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0).subtract(eye);
                double t = to.dotProduct(look);
                if (t < 1.0 || t > range) continue;
                double perp = to.subtract(look.x * t, look.y * t, look.z * t).length();
                if (!Double.isFinite(perp)) continue;
                if (perp < tolerance && t < bestT) {
                    bestT = t;
                    best = e;
                }
            } catch (Exception ignored) {}
        }
        return best;
    }

    private static void actionbar(ServerPlayerEntity player, String message) {
        if (player == null || message == null) return;
        try {
            player.sendMessage(Text.literal(message), true);
        } catch (Exception ignored) {}
    }
}

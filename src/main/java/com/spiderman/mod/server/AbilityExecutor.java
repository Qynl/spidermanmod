package com.spiderman.mod.server;

import com.spiderman.mod.util.SoundUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
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
 * COMPLETE REMAKE: All abilities distinct, reliable, no lag, web walking works.
 */
public final class AbilityExecutor {
    private AbilityExecutor() {}

    public static void use(ServerPlayerEntity player, int ability, int hand) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (powers == null || !powers.canUse(ability, player.age)) return;
        hand = hand == 1 ? 1 : 0;
        try {
            switch (ability) {
                case AbilityIds.SHOT: shot(player, powers, hand, false); break;
                case AbilityIds.SWING: swing(player, powers, hand); break;
                case AbilityIds.ZIP: zip(player, powers, hand); break;
                case AbilityIds.PULL: pull(player, powers, hand); break;
                case AbilityIds.TRAP: trap(player, powers, hand); break;
                case AbilityIds.LINE: line(player, powers, hand); break;
                case AbilityIds.BURST: burst(player, powers); break;
                case AbilityIds.IMPACT: shot(player, powers, hand, true); break;
                case AbilityIds.PLATFORM: platform(player, powers); break;
                default: break;
            }
        } catch (Exception e) {
            com.spiderman.mod.SpiderManMod.LOGGER.warn("[spiderman] ability {} failed for {}", ability, player.getGameProfile().getName(), e);
        }
    }

    // SHOT: Simple, fast, reliable web projectile
    private static void shot(ServerPlayerEntity player, PlayerPowers powers, int hand, boolean heavy) {
        SpiderConfig cfg = SpiderConfig.get();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null || !Double.isFinite(eye.x)) return;
        if (look.lengthSquared() < 0.001) look = new Vec3d(0, 0, 1);

        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 0.001) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        float damage = (float) ((heavy ? cfg.impactDamage : cfg.shotDamage) * ComboTracker.damageMult(powers));
        damage += Math.min(powers.stage, 3) * 1.0f;

        World world = player.getWorld();
        if (world == null) return;

        Vec3d origin = eye.add(right.x * (hand == 1 ? -0.35 : 0.35), -0.15, right.z * (hand == 1 ? -0.35 : 0.35));
        Vec3d target = eye.add(look.x * cfg.webRange, look.y * cfg.webRange, look.z * cfg.webRange);
        BlockHitResult hit = null;
        try {
            hit = world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        } catch (Exception ignored) {}
        Vec3d lineEnd = (hit != null && hit.getType() != HitResult.Type.MISS) ? hit.getPos() : target;

        ServerNetworking.sendSwing(player, true, lineEnd.x, lineEnd.y, lineEnd.z, hand, heavy ? 18 : 10);
        WebShotEntity.shoot(world, player, origin, look, damage, heavy);

        powers.setCooldown(heavy ? AbilityIds.IMPACT : AbilityIds.SHOT, player.age);
        MasteryLogic.addMastery(player, heavy ? 3 : 1);
        
        if (world instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, origin.x, origin.y, origin.z, heavy ? 8 : 4, 0.1, 0.1, 0.1, 0.03);
            } catch (Exception ignored) {}
        }
    }

    // SWING: Stable pendulum, no random fling
    private static void swing(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        if (powers.swinging) {
            SwingPhysics.detach(player, powers, true);
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        double range = cfg.swingRange * 1.3;
        Vec3d anchor = SwingPhysics.findAnchor(player, range);
        if (anchor == null) {
            anchor = SwingPhysics.findAnchor(player, range * 1.2);
        }
        if (anchor == null) {
            actionbar(player, "§7No anchor! Look up at buildings");
            return;
        }
        SwingPhysics.attach(player, powers, anchor, hand);
        double dist = anchor.distanceTo(player.getPos());
        powers.ropeLen = Math.max(5.0, Math.min(dist * 0.9, cfg.swingRange));

        // Small initial boost, not extreme
        Vec3d look = player.getRotationVector();
        Vec3d boost = new Vec3d(look.x * 0.25, 0.1, look.z * 0.25);
        SwingPhysics.push(player, player.getVelocity().add(boost));

        powers.setCooldown(AbilityIds.SWING, player.age);
        MasteryLogic.addMastery(player, 2);
        actionbar(player, "§aSwinging! §7W/S reel, Sprint boost, Jump to launch");
    }

    // LINE: Walkable web bridge - you can actually walk on it
    private static void line(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        double range = cfg.swingRange * 1.5;
        Vec3d anchor = SwingPhysics.findAnchor(player, range);
        if (anchor == null) {
            actionbar(player, "§7No anchor for line");
            return;
        }
        if (powers.swinging) SwingPhysics.detach(player, powers, false);

        SwingPhysics.attach(player, powers, anchor, hand);
        double dist = anchor.distanceTo(player.getPos());
        powers.ropeLen = Math.min(dist + 5.0, cfg.swingRange * 1.2);

        Vec3d look = player.getRotationVector();
        Vec3d vel = player.getVelocity().multiply(1.2).add(look.x * 0.4, 0.2, look.z * 0.4);
        if (vel.length() > 2.5) vel = vel.normalize().multiply(2.5);
        SwingPhysics.push(player, vel);

        createWebLine(player, player.getEyePos(), anchor);

        powers.setCooldown(AbilityIds.LINE, player.age);
        MasteryLogic.addMastery(player, 3);
        actionbar(player, "§bWeb Line - walkable bridge created!");
    }

    private static void createWebLine(ServerPlayerEntity player, Vec3d start, Vec3d end) {
        if (start == null || end == null) return;
        double dist = start.distanceTo(end);
        if (dist > 70 || dist < 3) return;
        int steps = (int) (dist / 2.0);
        if (steps < 3) steps = 3;
        if (steps > 15) steps = 15;

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
                // Make it walkable: place blocks below for walking
                BlockPos below = pos.down();
                if (world.getBlockState(below).isAir() && i % 2 == 0) {
                    WebCleanup.place(player, below);
                }
            } catch (Exception ignored) {}
        }
        if (world != null) {
            try {
                world.spawnParticles(ParticleTypes.ITEM_COBWEB, start.x, start.y, start.z, steps * 2,
                        (end.x - start.x) * 0.2, (end.y - start.y) * 0.2, (end.z - start.z) * 0.2, 0.03);
            } catch (Exception ignored) {}
        }
    }

    // ZIP: Fast, controlled dash
    private static void zip(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;
        if (look.lengthSquared() < 0.001) look = new Vec3d(0, 0, 1);

        double range = cfg.zipRange;
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
        powers.zipTicks = 15; // Shorter, more responsive
        try { player.setNoGravity(true); } catch (Exception ignored) {}
        try {
            player.playSound(ModSounds.WEB_ZIP, 1.0f, 1.2f);
        } catch (Exception ignored) {}

        ServerNetworking.sendSwing(player, true, target.x, target.y, target.z, hand, 15);

        if (player.getWorld() instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, eye.x, eye.y, eye.z, 6,
                        (target.x - eye.x) * 0.1, (target.y - eye.y) * 0.1, (target.z - eye.z) * 0.1, 0.02);
            } catch (Exception ignored) {}
        }

        powers.setCooldown(AbilityIds.ZIP, player.age);
        MasteryLogic.addMastery(player, 2);
    }

    // PULL: Simple, reliable pull
    private static void pull(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        LivingEntity target = pickTarget(player, cfg.pullRange, 2.5);
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

            powers.pullTicks = 25;
            powers.pullX = blockTarget.x;
            powers.pullY = blockTarget.y;
            powers.pullZ = blockTarget.z;
            powers.pullingPlayer = true;
            powers.pullTargetId = null;
            try { player.setNoGravity(true); } catch (Exception ignored) {}
            ServerNetworking.sendSwing(player, true, blockTarget.x, blockTarget.y, blockTarget.z, hand, 25);
            powers.setCooldown(AbilityIds.PULL, player.age);
            actionbar(player, "§ePulling to block");
            return;
        }

        if (target.isSpectator() || !target.isAlive()) return;

        powers.pullTicks = 35;
        powers.pullX = target.getX();
        powers.pullY = target.getY() + target.getHeight() * 0.5;
        powers.pullZ = target.getZ();
        powers.pullTargetId = target.getUuid();
        powers.pullingPlayer = target instanceof ServerPlayerEntity;

        if (powers.pullingPlayer) {
            try { player.setNoGravity(true); } catch (Exception ignored) {}
        }

        ServerNetworking.sendSwing(player, true, target.getX(), target.getY() + 1.0, target.getZ(), hand, 35);
        try {
            player.playSound(ModSounds.WEB_ZIP, 1.0f, 0.8f);
        } catch (Exception ignored) {}
        powers.setCooldown(AbilityIds.PULL, player.age);
        MasteryLogic.addMastery(player, 2);
        actionbar(player, "§ePulling " + target.getName().getString());
    }

    // TRAP: Simple web cluster that slows
    private static void trap(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        LivingEntity victim = pickTarget(player, cfg.trapRange, 2.5);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;

        if (victim != null && !(victim instanceof ServerPlayerEntity) && victim.isAlive()) {
            try {
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 3));
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1));
                victim.damage(player.getWorld().getDamageSources().playerAttack(player), (float)cfg.trapDamage);
            } catch (Exception ignored) {}

            BlockPos center = victim.getBlockPos();
            for (int i = 0; i < 8; i++) {
                int dx = (int) (Math.random() * 5) - 2;
                int dy = (int) (Math.random() * 3) - 1;
                int dz = (int) (Math.random() * 5) - 2;
                BlockPos p = center.add(dx, dy, dz);
                WebCleanup.place(player, p);
            }
            WebCleanup.place(player, center);
            WebCleanup.place(player, center.up());

            try {
                player.playSound(ModSounds.WEB_SHOT, 1.0f, 0.7f);
            } catch (Exception ignored) {}
            ServerNetworking.sendSwing(player, true, victim.getX(), victim.getY() + 1.0, victim.getZ(), hand, 20);

            if (player.getWorld() instanceof ServerWorld sw) {
                try {
                    sw.spawnParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY() + 1, victim.getZ(), 15, 0.8, 0.5, 0.8, 0.05);
                } catch (Exception ignored) {}
            }

            powers.setCooldown(AbilityIds.TRAP, player.age);
            MasteryLogic.addMastery(player, 3);
            actionbar(player, "§aTrapped " + victim.getName().getString());
            return;
        }

        Vec3d to = eye.add(look.x * cfg.trapRange, look.y * cfg.trapRange, look.z * cfg.trapRange);
        BlockHitResult hit = null;
        try {
            hit = player.getWorld().raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        } catch (Exception ignored) {}
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            actionbar(player, "§7Aim at enemy or block");
            return;
        }

        BlockPos base = hit.getBlockPos().offset(hit.getSide());
        int placed = 0;
        for (int i = 0; i < 8; i++) {
            int dx = (int) (Math.random() * 5) - 2;
            int dy = (int) (Math.random() * 3) - 1;
            int dz = (int) (Math.random() * 5) - 2;
            BlockPos p = base.add(dx, dy, dz);
            if (WebCleanup.place(player, p)) placed++;
        }
        if (placed == 0) WebCleanup.place(player, base);

        Vec3d pos = hit.getPos();
        if (pos != null && Double.isFinite(pos.x)) {
            ServerNetworking.sendSwing(player, true, pos.x, pos.y, pos.z, hand, 20);
        }

        powers.setCooldown(AbilityIds.TRAP, player.age);
        MasteryLogic.addMastery(player, 2);
    }

    // BURST: Small, controlled AoE, no lag
    private static void burst(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        double radius = Math.min(cfg.burstRadius + Math.min(powers.stage, 3) * 0.4, 6.5);
        float damage = (float) (cfg.burstDamage * ComboTracker.damageMult(powers) + Math.min(powers.stage, 3) * 0.8f);
        Vec3d center = player.getPos().add(0.0, 0.8, 0.0);
        World world = player.getWorld();
        if (world == null) return;
        Box box = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> caught = null;
        try {
            caught = world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e != null && e.isAlive() && e != player && !e.isSpectator());
        } catch (Exception e) {
            caught = List.of();
        }

        // Limit to 8 entities max to prevent lag
        int count = 0;
        for (LivingEntity e : caught) {
            if (count >= 8) break;
            try {
                e.damage(world.getDamageSources().playerAttack(player), damage);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
                Vec3d away = e.getPos().subtract(center);
                if (away.lengthSquared() < 0.01) away = new Vec3d(0.0, 1.0, 0.0);
                away = away.normalize();
                if (Double.isFinite(away.x)) {
                    double power = 1.0;
                    e.setVelocity(away.x * power, Math.max(0.5, away.y * power + 0.4), away.z * power);
                    e.velocityModified = true;
                }
                count++;
            } catch (Exception ignored) {}
        }

        if (world instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, 20, radius * 0.4, radius * 0.3, radius * 0.4, 0.08);
                sw.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.05, 0.05, 0.05, 0.0);
            } catch (Exception ignored) {}

            for (int i = 0; i < 6; i++) {
                double ang = Math.random() * Math.PI * 2;
                double dist = Math.random() * radius * 0.6;
                BlockPos p = BlockPos.ofFloored(center.x + Math.cos(ang) * dist, center.y + (Math.random() - 0.5) * 1.5, center.z + Math.sin(ang) * dist);
                try {
                    if (sw.getBlockState(p).isAir()) WebCleanup.place(player, p);
                } catch (Exception ignored) {}
            }
        }

        try {
            world.playSound(null, center.x, center.y, center.z,
                    SoundUtil.unwrap(SoundEvents.ENTITY_GENERIC_EXPLODE), SoundCategory.PLAYERS, 0.7f, 1.1f);
        } catch (Exception ignored) {}

        powers.setCooldown(AbilityIds.BURST, player.age);
        MasteryLogic.addMastery(player, 3);
        actionbar(player, "§cBurst! " + count + " enemies hit");
    }

    // PLATFORM: Solid, walkable platform
    private static void platform(ServerPlayerEntity player, PlayerPowers powers) {
        BlockPos base = player.getBlockPos().down();
        if (base == null) return;

        int placed = 0;
        ServerWorld world = player.getServerWorld();
        int size = 2 + (powers.stage / 2);
        if (size > 3) size = 3;

        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                if (Math.abs(dx) == size && Math.abs(dz) == size && size > 2) continue;
                BlockPos p = base.add(dx, 0, dz);
                try {
                    if (world.getBlockState(p).isAir() || world.getBlockState(p).isOf(Blocks.COBWEB)) {
                        if (WebCleanup.place(player, p)) placed++;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (placed == 0) {
            actionbar(player, "§7No room for platform");
            return;
        }

        try {
            player.playSound(ModSounds.WEB_SPLAT, 1.0f, 1.0f);
        } catch (Exception ignored) {}

        if (world != null) {
            try {
                world.spawnParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY(), player.getZ(), 12, size * 0.5, 0.2, size * 0.5, 0.05);
            } catch (Exception ignored) {}
        }

        powers.setCooldown(AbilityIds.PLATFORM, player.age);
        MasteryLogic.addMastery(player, 2);
        actionbar(player, "§aWeb Platform " + (size*2+1) + "x" + (size*2+1) + " - walkable!");
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

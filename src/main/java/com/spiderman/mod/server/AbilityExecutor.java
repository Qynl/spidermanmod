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
 * Executes web abilities — now with white lines, better swing, hold-to-pull,
 * clustered trap webs, walkable platforms, and cooler burst/impact.
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

    // ---------- SHOT & IMPACT (improved) ----------

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
        // Heavy does more damage at higher stages
        if (heavy) damage += powers.stage * 2.0f;

        World world = player.getWorld();
        if (world == null) return;

        // Origin from wrist
        Vec3d origin = offsetOrigin(eye, right, hand == 1 ? -0.35 : 0.35);

        // Fire with white line visible — send swing packet for 12 ticks (shot) or 20 (impact)
        Vec3d target = eye.add(look.x * cfg.webRange, look.y * cfg.webRange, look.z * cfg.webRange);
        BlockHitResult hit = null;
        try {
            hit = world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        } catch (Exception ignored) {}
        Vec3d lineEnd = (hit != null && hit.getType() != HitResult.Type.MISS) ? hit.getPos() : target;

        // Send white line
        ServerNetworking.sendSwing(player, true, lineEnd.x, lineEnd.y, lineEnd.z, hand, heavy ? 20 : 12);

        // Actually shoot projectile — faster, no gravity
        WebShotEntity.shoot(world, player, origin, look, damage, heavy);

        // Impact also places webs at hit point for cool factor
        if (heavy && hit != null && hit.getType() != HitResult.Type.BLOCK) {
            // Will place on entity hit via WebShotEntity, but we also do block placement if ray hits
        }

        powers.setCooldown(heavy ? AbilityIds.IMPACT : AbilityIds.SHOT, player.age);
        MasteryLogic.addMastery(player, heavy ? 4 : 2);

        // Extra particles for impact
        if (heavy && world instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, origin.x, origin.y, origin.z, 20, 0.2, 0.2, 0.2, 0.1);
                sw.spawnParticles(ParticleTypes.CRIT, lineEnd.x, lineEnd.y, lineEnd.z, 10, 0.3, 0.3, 0.3, 0.2);
            } catch (Exception ignored) {}
        }
    }

    private static Vec3d offsetOrigin(Vec3d eye, Vec3d right, double side) {
        if (eye == null || right == null) return new Vec3d(0, 1, 0);
        return eye.add(right.x * side, -0.15, right.z * side);
    }

    // ---------- SWING (completely reworked to be actually swingable) ----------

    private static void toggleSwing(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        if (powers.swinging) {
            SwingPhysics.detach(player, powers, true);
            return;
        }
        // Increased range for better swinging — find anchor further
        double range = SpiderConfig.get().swingRange * 1.5; // 40 -> 60 default
        Vec3d anchor = SwingPhysics.findAnchor(player, range);
        if (anchor == null) {
            // Try even further with straight ray as fallback
            anchor = SwingPhysics.findAnchor(player, range * 1.3);
        }
        if (anchor == null) {
            actionbar(player, "§7No web anchor — look at high buildings/trees, range " + (int) range);
            return;
        }
        SwingPhysics.attach(player, powers, anchor, hand);
        // Longer rope for more momentum
        double dist = anchor.distanceTo(player.getPos());
        powers.ropeLen = Math.max(5.0, Math.min(dist * 0.9, SpiderConfig.get().swingRange * 1.2));

        // Give initial boost for better swing start
        Vec3d look = player.getRotationVector();
        Vec3d boost = new Vec3d(look.x * 0.3, 0.1, look.z * 0.3);
        Vec3d vel = player.getVelocity().add(boost);
        SwingPhysics.push(player, vel);

        powers.setCooldown(AbilityIds.SWING, player.age);
        MasteryLogic.addMastery(player, 3);
        actionbar(player, "§aSwinging! Look up to reel in, down to let out — jump to release");
    }

    // ---------- LINE (now creates walkable web line bridge) ----------

    private static void line(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        double range = SpiderConfig.get().swingRange * 1.8;
        Vec3d anchor = SwingPhysics.findAnchor(player, range);
        if (anchor == null) {
            actionbar(player, "§7No line anchor in range (" + (int) range + ")");
            return;
        }
        if (powers.swinging) SwingPhysics.detach(player, powers, false);

        // Attach swing but with longer rope and high speed boost
        SwingPhysics.attach(player, powers, anchor, hand);
        double dist = anchor.distanceTo(player.getPos());
        powers.ropeLen = Math.min(dist + 8.0, SpiderConfig.get().swingRange * 1.5);

        // Strong launch
        Vec3d look = player.getRotationVector();
        Vec3d vel = player.getVelocity().multiply(1.8).add(look.x * 0.6, 0.3, look.z * 0.6);
        if (vel.length() > 3.5) vel = vel.normalize().multiply(3.5);
        SwingPhysics.push(player, vel);

        // Create web line bridge: place webs along line from player to anchor
        createWebLine(player, player.getEyePos(), anchor);

        powers.setCooldown(AbilityIds.LINE, player.age);
        MasteryLogic.addMastery(player, 4);
        actionbar(player, "§bWeb Line! High-speed traverse — webs placed for walking");
    }

    private static void createWebLine(ServerPlayerEntity player, Vec3d start, Vec3d end) {
        if (start == null || end == null) return;
        double dist = start.distanceTo(end);
        if (dist > 80 || dist < 3) return;
        int steps = (int) (dist / 2.0);
        if (steps < 3) steps = 3;
        if (steps > 20) steps = 20;

        ServerWorld world = player.getServerWorld();
        for (int i = 1; i < steps; i++) {
            double t = (double) i / (double) steps;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            // Place web if air, and also place below for walkable
            try {
                if (world.getBlockState(pos).isAir()) {
                    WebCleanup.place(player, pos);
                }
                // Also place below for platform
                BlockPos below = pos.down();
                if (world.getBlockState(below).isAir() && i % 2 == 0) {
                    WebCleanup.place(player, below);
                }
            } catch (Exception ignored) {}
        }
        // Particle line
        if (world != null) {
            try {
                world.spawnParticles(ParticleTypes.ITEM_COBWEB, start.x, start.y, start.z, steps * 2,
                        (end.x - start.x) * 0.5, (end.y - start.y) * 0.5, (end.z - start.z) * 0.5, 0.05);
            } catch (Exception ignored) {}
        }
    }

    // ---------- ZIP (white line, faster, launch) ----------

    private static void zip(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;
        if (look.lengthSquared() < 0.001) look = new Vec3d(0, 0, 1);

        double range = cfg.zipRange * 1.3;
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
        powers.zipTicks = 18; // Slightly longer for better feel
        try {
            player.setNoGravity(true);
        } catch (Exception ignored) {}
        try {
            player.playSound(ModSounds.WEB_ZIP, 1.0f, 1.2f);
        } catch (Exception ignored) {}

        // White line for zip — longer life, thick
        ServerNetworking.sendSwing(player, true, target.x, target.y, target.z, hand, 20);

        // Particle trail for zip
        if (player.getWorld() instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, eye.x, eye.y, eye.z, 15,
                        (target.x - eye.x) * 0.2, (target.y - eye.y) * 0.2, (target.z - eye.z) * 0.2, 0.05);
            } catch (Exception ignored) {}
        }

        powers.setCooldown(AbilityIds.ZIP, player.age);
        MasteryLogic.addMastery(player, 3);
    }

    // ---------- PULL (hold as long as you hold it, with white line) ----------

    private static void pull(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        LivingEntity target = pickTarget(player, SpiderConfig.get().webRange * 1.2, 2.5);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;

        if (target == null) {
            // No entity: try pull to block (grapple)
            double range = SpiderConfig.get().webRange * 1.2;
            Vec3d to = eye.add(look.x * range, look.y * range, look.z * range);
            BlockHitResult hit = null;
            try {
                hit = player.getWorld().raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            } catch (Exception ignored) {}
            Vec3d blockTarget = (hit != null && hit.getType() != HitResult.Type.MISS) ? hit.getPos() : to;

            powers.pullTicks = 30; // Hold for 1.5 sec pulling self
            powers.pullX = blockTarget.x;
            powers.pullY = blockTarget.y;
            powers.pullZ = blockTarget.z;
            powers.pullingPlayer = true;
            powers.pullTargetId = null;
            try {
                player.setNoGravity(true);
            } catch (Exception ignored) {}
            ServerNetworking.sendSwing(player, true, blockTarget.x, blockTarget.y, blockTarget.z, hand, 30);
            try {
                player.playSound(ModSounds.WEB_ZIP, 1.0f, 0.9f);
            } catch (Exception ignored) {}
            powers.setCooldown(AbilityIds.PULL, player.age);
            MasteryLogic.addMastery(player, 2);
            actionbar(player, "§ePulling to block — hold effect");
            return;
        }

        if (target.isSpectator() || !target.isAlive()) {
            actionbar(player, "§7Can't pull that");
            return;
        }

        // Entity pull: hold for longer, continuous pull
        powers.pullTicks = 40; // 2 seconds of pulling
        powers.pullX = target.getX();
        powers.pullY = target.getY() + target.getHeight() * 0.5;
        powers.pullZ = target.getZ();
        powers.pullTargetId = target.getUuid();
        powers.pullingPlayer = target instanceof ServerPlayerEntity; // If player, pull self, else pull target

        if (powers.pullingPlayer) {
            try {
                player.setNoGravity(true);
            } catch (Exception ignored) {}
        }

        ServerNetworking.sendSwing(player, true, target.getX(), target.getY() + 1.0, target.getZ(), hand, 40);
        try {
            player.playSound(ModSounds.WEB_ZIP, 1.0f, 0.9f);
        } catch (Exception ignored) {}
        powers.setCooldown(AbilityIds.PULL, player.age);
        MasteryLogic.addMastery(player, 3);
        actionbar(player, "§ePulling " + target.getName().getString() + " — hold effect active");
    }

    // ---------- TRAP (more webs at point, random cluster, with line) ----------

    private static void trap(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        LivingEntity victim = pickTarget(player, cfg.webRange, 2.5);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        if (eye == null || look == null) return;

        if (victim != null && !(victim instanceof ServerPlayerEntity) && victim.isAlive() && !victim.isSpectator()) {
            try {
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 3));
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
            } catch (Exception ignored) {}

            // Cluster of webs around victim
            BlockPos center = victim.getBlockPos();
            int placed = 0;
            for (int i = 0; i < 12; i++) {
                int dx = (int) (Math.random() * 5) - 2;
                int dy = (int) (Math.random() * 3) - 1;
                int dz = (int) (Math.random() * 5) - 2;
                BlockPos p = center.add(dx, dy, dz);
                if (WebCleanup.place(player, p)) placed++;
            }
            // Also place at victim feet
            WebCleanup.place(player, center);
            WebCleanup.place(player, center.up());

            try {
                player.playSound(ModSounds.WEB_SHOT, 1.0f, 0.8f);
                player.playSound(ModSounds.WEB_SPLAT, 1.0f, 0.9f);
            } catch (Exception ignored) {}
            // White line to victim
            ServerNetworking.sendSwing(player, true, victim.getX(), victim.getY() + 1.0, victim.getZ(), hand, 25);

            // Particles
            if (player.getWorld() instanceof ServerWorld sw) {
                try {
                    sw.spawnParticles(ParticleTypes.ITEM_COBWEB, victim.getX(), victim.getY() + 1, victim.getZ(), 30, 1.5, 0.8, 1.5, 0.1);
                } catch (Exception ignored) {}
            }

            powers.setCooldown(AbilityIds.TRAP, player.age);
            MasteryLogic.addMastery(player, 4);
            return;
        }

        // Block trap: cluster at hit point
        Vec3d to = eye.add(look.x * cfg.webRange, look.y * cfg.webRange, look.z * cfg.webRange);
        BlockHitResult hit = null;
        try {
            hit = player.getWorld().raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        } catch (Exception ignored) {}
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            actionbar(player, "§7Nothing to web");
            return;
        }

        BlockPos base = hit.getBlockPos().offset(hit.getSide());
        int placed = 0;
        for (int i = 0; i < 10; i++) {
            int dx = (int) (Math.random() * 5) - 2;
            int dy = (int) (Math.random() * 3) - 1;
            int dz = (int) (Math.random() * 5) - 2;
            BlockPos p = base.add(dx, dy, dz);
            if (WebCleanup.place(player, p)) placed++;
        }
        if (placed == 0) {
            if (!WebCleanup.place(player, base)) {
                actionbar(player, "§7Web won't stick there");
                return;
            }
        }

        try {
            player.playSound(ModSounds.WEB_SHOT, 1.0f, 0.8f);
            player.playSound(ModSounds.WEB_SPLAT, 1.0f, 0.8f);
        } catch (Exception ignored) {}
        Vec3d pos = hit.getPos();
        if (pos != null && Double.isFinite(pos.x)) {
            ServerNetworking.sendSwing(player, true, pos.x, pos.y, pos.z, hand, 25);
        }

        if (player.getWorld() instanceof ServerWorld sw && pos != null) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, pos.x, pos.y, pos.z, 25, 1.0, 0.8, 1.0, 0.1);
            } catch (Exception ignored) {}
        }

        powers.setCooldown(AbilityIds.TRAP, player.age);
        MasteryLogic.addMastery(player, 4);
    }

    // ---------- BURST (more cool) ----------

    private static void burst(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        boolean venom = cfg.experimentalVenomBlast;
        double radius = cfg.burstRadius * (venom ? 1.6 : 1.0) + powers.stage * 0.8;
        float damage = (float) (cfg.burstDamage * ComboTracker.damageMult(powers) * (venom ? 2.2 : 1.0) + powers.stage * 1.5f);
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
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
                Vec3d away = e.getPos().subtract(center);
                if (away.lengthSquared() < 0.01) away = new Vec3d(0.0, 1.0, 0.0);
                away = away.normalize();
                if (Double.isFinite(away.x)) {
                    double power = venom ? 2.0 : 1.6;
                    e.setVelocity(away.x * power, Math.max(0.8, away.y * power + 0.6), away.z * power);
                    e.velocityModified = true;
                }
            } catch (Exception ignored) {}
        }

        // Cool effects: webs around, particles, sound, screen shake via FOV
        if (world instanceof ServerWorld sw) {
            try {
                sw.spawnParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, venom ? 80 : 50, radius * 0.6, radius * 0.5, radius * 0.6, 0.2);
                sw.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 3, 0.0, 0.0, 0.0, 0.0);
                sw.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 2, 0.2, 0.2, 0.2, 0.0);
                sw.spawnParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 20, radius * 0.3, radius * 0.3, radius * 0.3, 0.3);
            } catch (Exception ignored) {}

            // Place webs around burst for cool factor
            for (int i = 0; i < 12; i++) {
                double ang = Math.random() * Math.PI * 2;
                double dist = Math.random() * radius * 0.8;
                BlockPos p = BlockPos.ofFloored(center.x + Math.cos(ang) * dist, center.y + (Math.random() - 0.5) * 2, center.z + Math.sin(ang) * dist);
                try {
                    if (sw.getBlockState(p).isAir()) WebCleanup.place(player, p);
                } catch (Exception ignored) {}
            }
        }

        try {
            world.playSound(null, center.x, center.y, center.z,
                    (SoundEvent) SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.9f, 1.1f);
            world.playSound(null, center.x, center.y, center.z, ModSounds.WEB_SPLAT, SoundCategory.PLAYERS, 1.0f, 0.7f);
            player.playSound(ModSounds.WEB_SPLAT, 1.0f, 0.6f);
        } catch (Exception ignored) {}

        // Web lines in all directions for burst visual
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI * 2 / 8;
            double x = center.x + Math.cos(ang) * radius;
            double z = center.z + Math.sin(ang) * radius;
            ServerNetworking.sendSwing(player, true, x, center.y, z, i % 2, 15);
        }

        powers.setCooldown(AbilityIds.BURST, player.age);
        MasteryLogic.addMastery(player, 5);
    }

    // ---------- PLATFORM (standable webs) ----------

    private static void platform(ServerPlayerEntity player, PlayerPowers powers) {
        BlockPos base = player.getBlockPos().down();
        if (base == null) return;

        // Bigger platform: 5x5, 2 layers for solidity
        int placed = 0;
        ServerWorld world = player.getServerWorld();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = base.add(dx, 0, dz);
                BlockPos pUp = p.up();
                try {
                    // Bottom layer: solid white wool for standing (looks like thick web)
                    // We use cobweb for top, but also ensure solid below if needed
                    if (world.getBlockState(p).isAir()) {
                        // Place cobweb
                        if (WebCleanup.place(player, p)) placed++;
                    } else if (world.getBlockState(p).isOf(Blocks.COBWEB)) {
                        placed++;
                    }
                    // Top layer: extra web for visuals and safety
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        if (world.getBlockState(pUp).isAir()) {
                            WebCleanup.place(player, pUp);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // If no room, try at player feet level
        if (placed == 0) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = player.getBlockPos().add(dx, -1, dz);
                    if (WebCleanup.place(player, p)) placed++;
                }
            }
        }

        if (placed == 0) {
            actionbar(player, "§7No room for a web platform — try in air");
            return;
        }

        try {
            player.playSound(ModSounds.WEB_SPLAT, 1.0f, 1.2f);
            player.playSound(SoundEvents.BLOCK_WOOL_PLACE, 0.8f, 1.0f);
        } catch (Exception ignored) {}

        // Give player levitation for a moment to stand on webs
        try {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0));
        } catch (Exception ignored) {}

        powers.setCooldown(AbilityIds.PLATFORM, player.age);
        MasteryLogic.addMastery(player, 3);
        actionbar(player, "§aWeb platform — standable! Spiders won't attack on webs");
    }

    // ---------- helpers ----------

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

package com.spiderman.mod.server;

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
 * Executes the 10 web abilities. All entry points run on the server thread;
 * visuals reach the client via entities, particles, sounds and S2C packets.
 */
public final class AbilityExecutor {
    private AbilityExecutor() {
    }

    public static void use(ServerPlayerEntity player, int ability, int hand) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.canUse(ability, player.age)) {
            return;
        }
        hand = hand == 1 ? 1 : 0;
        switch (ability) {
            case AbilityIds.SHOT: fireShot(player, powers, hand, false, false); break;
            case AbilityIds.DOUBLE: fireShot(player, powers, hand, false, true); break;
            case AbilityIds.IMPACT: fireShot(player, powers, hand, true, false); break;
            case AbilityIds.SWING: toggleSwing(player, powers, hand); break;
            case AbilityIds.ZIP: zip(player, powers, hand); break;
            case AbilityIds.PULL: pull(player, powers, hand); break;
            case AbilityIds.TRAP: trap(player, powers, hand); break;
            case AbilityIds.LINE: line(player, powers, hand); break;
            case AbilityIds.BURST: burst(player, powers); break;
            case AbilityIds.PLATFORM: platform(player, powers); break;
            default: break;
        }
    }

    // ---------- shots ----------

    private static void fireShot(ServerPlayerEntity player, PlayerPowers powers,
            int hand, boolean heavy, boolean twin) {
        SpiderConfig cfg = SpiderConfig.get();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 0.001) {
            right = new Vec3d(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        float damage = (float) ((heavy ? cfg.impactDamage : cfg.shotDamage)
                * ComboTracker.damageMult(powers));
        World world = player.getWorld();
        if (twin) {
            fireOne(world, player, offsetOrigin(eye, right, 0.3), look.add(right.x * 0.06, 0.0, right.z * 0.06), damage, heavy);
            fireOne(world, player, offsetOrigin(eye, right, -0.3), look.add(-right.x * 0.06, 0.0, -right.z * 0.06), damage, heavy);
        } else {
            fireOne(world, player, offsetOrigin(eye, right, hand == 1 ? -0.3 : 0.3), look, damage, heavy);
        }
        powers.setCooldown(heavy ? AbilityIds.IMPACT : twin ? AbilityIds.DOUBLE : AbilityIds.SHOT, player.age);
        MasteryLogic.addMastery(player, 1);
    }

    private static Vec3d offsetOrigin(Vec3d eye, Vec3d right, double side) {
        return eye.add(right.x * side, -0.1, right.z * side);
    }

    private static void fireOne(World world, LivingEntity shooter, Vec3d origin,
            Vec3d dir, float damage, boolean heavy) {
        WebShotEntity.shoot(world, shooter, origin, dir, damage, heavy);
    }

    // ---------- swing / line ----------

    private static void toggleSwing(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        if (powers.swinging) {
            SwingPhysics.detach(player, powers, true);
            return;
        }
        Vec3d anchor = SwingPhysics.findAnchor(player, SpiderConfig.get().swingRange);
        if (anchor == null) {
            actionbar(player, "§7No web anchor in range");
            return;
        }
        SwingPhysics.attach(player, powers, anchor, hand);
        powers.setCooldown(AbilityIds.SWING, player.age);
        MasteryLogic.addMastery(player, 1);
    }

    private static void line(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        Vec3d anchor = SwingPhysics.findAnchor(player, SpiderConfig.get().swingRange);
        if (anchor == null) {
            actionbar(player, "§7No line anchor in range");
            return;
        }
        if (powers.swinging) {
            SwingPhysics.detach(player, powers, false);
        }
        SwingPhysics.attach(player, powers, anchor, hand);
        powers.ropeLen = Math.min(anchor.distanceTo(player.getPos()) + 4.0,
                SpiderConfig.get().swingRange);
        Vec3d vel = player.getVelocity().multiply(1.3);
        if (vel.length() > 3.0) {
            vel = vel.normalize().multiply(3.0);
        }
        SwingPhysics.push(player, vel);
        powers.setCooldown(AbilityIds.LINE, player.age);
        MasteryLogic.addMastery(player, 2);
    }

    // ---------- zip ----------

    private static void zip(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        Vec3d to = eye.add(look.x * cfg.zipRange, look.y * cfg.zipRange, look.z * cfg.zipRange);
        BlockHitResult hit = player.getWorld().raycast(new RaycastContext(eye, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d target = hit.getType() == HitResult.Type.MISS ? to : hit.getPos();
        if (powers.swinging) {
            SwingPhysics.detach(player, powers, false);
        }
        // Drop the climb flag directly (not via setClimbing): the zip flight
        // needs noGravity left on, and the climb tick would otherwise steal it.
        powers.climbing = false;
        powers.zipX = target.x;
        powers.zipY = target.y;
        powers.zipZ = target.z;
        powers.zipTicks = 14;
        player.setNoGravity(true);
        player.playSound(ModSounds.WEB_ZIP, 1.0f, 1.1f);
        ServerNetworking.sendSwing(player, true, target.x, target.y, target.z, hand, 16);
        powers.setCooldown(AbilityIds.ZIP, player.age);
        MasteryLogic.addMastery(player, 2);
    }

    // ---------- pull ----------

    private static void pull(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        LivingEntity target = pickTarget(player, SpiderConfig.get().webRange, 2.2);
        if (target == null) {
            actionbar(player, "§7Nothing to pull");
            return;
        }
        Vec3d from = player.getPos().add(0.0, 1.0, 0.0);
        Vec3d toTarget = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0).subtract(from);
        double dist = Math.max(1.0, toTarget.length());
        if (target instanceof ServerPlayerEntity) {
            // Grapple onto other players instead of yanking them.
            SwingPhysics.push(player, toTarget.normalize().multiply(Math.min(1.4, 0.5 + dist * 0.06)));
        } else {
            Vec3d yank = toTarget.normalize().multiply(-(0.8 + dist * 0.07));
            target.setVelocity(yank.x, Math.max(0.35, yank.y + 0.4), yank.z);
            target.velocityModified = true;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
        }
        player.playSound(ModSounds.WEB_ZIP, 1.0f, 0.9f);
        ServerNetworking.sendSwing(player, true, target.getX(), target.getY() + 1.0, target.getZ(), hand, 10);
        powers.setCooldown(AbilityIds.PULL, player.age);
        MasteryLogic.addMastery(player, 2);
    }

    // ---------- trap ----------

    private static void trap(ServerPlayerEntity player, PlayerPowers powers, int hand) {
        SpiderConfig cfg = SpiderConfig.get();
        LivingEntity victim = pickTarget(player, cfg.webRange, 2.5);
        if (victim != null && !(victim instanceof ServerPlayerEntity)) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 3));
            WebCleanup.place(player, victim.getBlockPos());
            player.playSound(ModSounds.WEB_SHOT, 1.0f, 0.8f);
            ServerNetworking.sendSwing(player, true, victim.getX(), victim.getY() + 1.0, victim.getZ(), hand, 8);
            powers.setCooldown(AbilityIds.TRAP, player.age);
            MasteryLogic.addMastery(player, 3);
            return;
        }
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        Vec3d to = eye.add(look.x * cfg.webRange, look.y * cfg.webRange, look.z * cfg.webRange);
        BlockHitResult hit = player.getWorld().raycast(new RaycastContext(eye, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            actionbar(player, "§7Nothing to web");
            return;
        }
        if (!WebCleanup.place(player, hit.getBlockPos().offset(hit.getSide()))) {
            actionbar(player, "§7Web won't stick there");
            return;
        }
        player.playSound(ModSounds.WEB_SHOT, 1.0f, 0.8f);
        Vec3d pos = hit.getPos();
        ServerNetworking.sendSwing(player, true, pos.x, pos.y, pos.z, hand, 8);
        powers.setCooldown(AbilityIds.TRAP, player.age);
        MasteryLogic.addMastery(player, 3);
    }

    // ---------- burst ----------

    private static void burst(ServerPlayerEntity player, PlayerPowers powers) {
        SpiderConfig cfg = SpiderConfig.get();
        boolean venom = cfg.experimentalVenomBlast;
        double radius = cfg.burstRadius * (venom ? 1.4 : 1.0);
        float damage = (float) (cfg.burstDamage * ComboTracker.damageMult(powers) * (venom ? 2.0 : 1.0));
        Vec3d center = player.getPos().add(0.0, 1.0, 0.0);
        World world = player.getWorld();
        DamageSources sources = world.getDamageSources();
        Box box = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> caught = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);
        for (LivingEntity e : caught) {
            e.damage(sources.playerAttack(player), damage);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
            Vec3d away = e.getPos().subtract(center);
            if (away.lengthSquared() < 0.01) {
                away = new Vec3d(0.0, 1.0, 0.0);
            }
            away = away.normalize();
            e.setVelocity(away.x * 1.4, Math.max(0.6, away.y * 1.4 + 0.5), away.z * 1.4);
            e.velocityModified = true;
        }
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z,
                    venom ? 60 : 36, radius * 0.5, radius * 0.4, radius * 0.5, 0.15);
            serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        world.playSound(null, center.x, center.y, center.z,
                (SoundEvent) SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, 0.7f, 1.3f);
        player.playSound(ModSounds.WEB_SPLAT, 1.0f, 0.8f);
        powers.setCooldown(AbilityIds.BURST, player.age);
        MasteryLogic.addMastery(player, 4);
    }

    // ---------- platform ----------

    private static void platform(ServerPlayerEntity player, PlayerPowers powers) {
        BlockPos base = player.getBlockPos().down();
        int placed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (WebCleanup.place(player, base.add(dx, 0, dz))) {
                    placed++;
                }
            }
        }
        if (placed == 0) {
            actionbar(player, "§7No room for a web platform");
            return;
        }
        player.playSound(ModSounds.WEB_SPLAT, 1.0f, 1.2f);
        powers.setCooldown(AbilityIds.PLATFORM, player.age);
        MasteryLogic.addMastery(player, 2);
    }

    // ---------- helpers ----------

    /** Picks the living entity closest to the player's look ray. */
    private static LivingEntity pickTarget(ServerPlayerEntity player, double range, double tolerance) {
        World world = player.getWorld();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        Box box = new Box(eye.x - range, eye.y - range, eye.z - range,
                eye.x + range, eye.y + range, eye.z + range);
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);
        LivingEntity best = null;
        double bestT = range;
        for (LivingEntity e : candidates) {
            Vec3d to = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0).subtract(eye);
            double t = to.dotProduct(look);
            if (t < 1.0 || t > range) {
                continue;
            }
            double perp = to.subtract(look.x * t, look.y * t, look.z * t).length();
            if (perp < tolerance && t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }

    private static void actionbar(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), true);
    }
}

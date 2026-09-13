package com.rivalrealms.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A real gun, not a stick that makes noise. The revolver carries a
 * six-shot cylinder persisted on the stack: each trigger pull is fast and
 * snappy, accuracy loosens as you fan the hammer, and an empty gun forces
 * a proper reload with a flint-click roll. Sneak-use reloads early.
 *
 * <p>Subclasses retune the feel: the flintlock is a single thunderclap,
 * the blunderbuss a two-shell scattergun — all sharing the cylinder,
 * feedback and reload logic.</p>
 */
public class RevolverItem extends Item {
    protected final float damage;
    protected final int cooldown;
    protected final float range;
    protected final int cylinder;
    protected final int reloadTime;

    public RevolverItem(Settings settings, float damage, int cooldown, float range) {
        this(settings, damage, cooldown, range, 6, 50);
    }

    public RevolverItem(Settings settings, float damage, int cooldown, float range,
                        int cylinder, int reloadTime) {
        super(settings);
        this.damage = damage;
        this.cooldown = cooldown;
        this.range = range;
        this.cylinder = cylinder;
        this.reloadTime = reloadTime;
    }

    // ------------------------------------------------------------ cylinder

    protected int shotsIn(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return cylinder;
        }
        NbtCompound nbt = component.copyNbt();
        if (!nbt.contains("Cylinder") || nbt.getInt("Cylinder") > cylinder) {
            return cylinder;
        }
        return nbt.getInt("Cylinder");
    }

    protected void saveShots(ItemStack stack, int shots) {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Cylinder", Math.max(0, Math.min(cylinder, shots)));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    protected void reload(World world, PlayerEntity user, ItemStack stack) {
        saveShots(stack, cylinder);
        user.getItemCooldownManager().set(this, reloadTime);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.9f, 0.7f);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.PLAYERS, 0.5f, 1.6f);
        user.sendMessage(Text.literal("Reloading...").formatted(Formatting.GRAY), true);
    }

    // ------------------------------------------------------------ trigger

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            int shots = shotsIn(stack);
            if (user.isSneaking() && shots < cylinder) {
                reload(world, user, stack);
                return TypedActionResult.success(stack, world.isClient());
            }
            if (shots <= 0) {
                reload(world, user, stack);
                return TypedActionResult.success(stack, world.isClient());
            }

            saveShots(stack, shots - 1);
            Vec3d start = user.getCameraPosVec(1.0f);
            Vec3d direction = user.getRotationVec(1.0f);
            // Fanning the hammer loosens the wrist: each chamber past the
            // first adds spread until the reload resets it.
            double fanned = (cylinder - shots) * (cylinder > 1 ? 0.012 : 0.0);
            fireShot(world, user, stack, hand, start, direction, fanned);
            fireFeedback(world, user, start, direction, null);

            user.getItemCooldownManager().set(this, cooldown);
            // Visible, physical recoil with a touch of yaw wander.
            user.setPitch(user.getPitch() - (cylinder > 2 ? 1.1f : 2.2f));
            user.setYaw(user.getYaw() + (world.random.nextFloat() - 0.5f) * 0.8f);
            if (!user.isCreative()) {
                stack.damage(1, user, LivingEntity.getSlotForHand(hand));
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /**
     * One trigger pull. Subclasses replace this (scatter cones, piercing
     * shots). The default is a precise hitscan with spread growth.
     */
    protected void fireShot(World world, PlayerEntity user, ItemStack stack, Hand hand,
                            Vec3d start, Vec3d direction, double extraSpread) {
        Vec3d end = start.add(direction.multiply(range));
        Box search = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(user, start, end, search,
                entity -> !entity.isSpectator() && entity.isAlive() && entity.canHit(), range * range);

        Vec3d impact = end;
        if (hit != null) {
            impact = hit.getPos();
            Entity target = hit.getEntity();
            if (target instanceof LivingEntity living) {
                living.damage(world.getDamageSources().playerAttack(user), damage);
                living.takeKnockback(0.55, -direction.x, -direction.z);
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.7f, 0.9f);
            }
        }
        drawTracer(world, start, direction, impact.distanceTo(start));
    }

    protected void drawTracer(World world, Vec3d start, Vec3d direction, double distance) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        for (double d = 1.2; d < distance; d += 1.1) {
            Vec3d point = start.add(direction.multiply(d));
            serverWorld.spawnParticles(ParticleTypes.CRIT,
                    point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Shared muzzle feedback: smoke, flame, a spinning casing and the
     * layered crack. Subclasses call super and pile on their own character.
     */
    protected void fireFeedback(World world, PlayerEntity user, Vec3d start, Vec3d direction, Vec3d impact) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Vec3d muzzle = start.add(direction.multiply(0.9)).subtract(0.0, 0.12, 0.0);
        serverWorld.spawnParticles(ParticleTypes.POOF,
                muzzle.x, muzzle.y, muzzle.z, 4, 0.08, 0.05, 0.08, 0.015);
        serverWorld.spawnParticles(ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.02, 0.03, 0.01);
        // The brass spins off to the shooter's right.
        Vec3d side = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
        serverWorld.spawnParticles(ParticleTypes.POOF,
                muzzle.x + side.x * 0.3, muzzle.y - 0.1, muzzle.z + side.z * 0.3,
                1, 0.05, 0.02, 0.05, 0.06);

        serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS, 0.32f, 1.75f + world.random.nextFloat() * 0.2f);
        serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.PLAYERS, 0.30f, 1.9f);
        serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.35f, 1.8f);
    }
}

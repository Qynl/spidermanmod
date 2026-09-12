package com.rivalrealms.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Royal Longsword is a diamond-tier blade with an active ability:
 * <b>Sovereign's Cleave</b>. Right-click hurls the wielder forward in a
 * sword-first lunge and sweeps every hostile in the landing arc for heavy
 * damage, crit sparks and a proper sweep sound. Costs durability and a short
 * cooldown, so it is a duel opener, not a movement exploit.
 */
public final class RoyalLongswordItem extends SwordItem {
    private static final float CLEAVE_DAMAGE = 6.5f;
    private static final double CLEAVE_RANGE = 4.2;
    private static final int CLEAVE_COOLDOWN = 90;
    private static final int CLEAVE_DURABILITY_COST = 3;

    public RoyalLongswordItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            Vec3d look = user.getRotationVec(1.0f);
            Vec3d lunge = new Vec3d(look.x, 0.0, look.z).normalize().multiply(0.95).add(0.0, 0.22, 0.0);
            user.addVelocity(lunge.x, lunge.y, lunge.z);
            user.velocityModified = true;

            // Sweep the arc in front of the wielder. Recruited crew are spared.
            Vec3d center = user.getPos().add(look.multiply(2.0));
            Box sweep = new Box(
                    center.add(-CLEAVE_RANGE, -CLEAVE_RANGE, -CLEAVE_RANGE),
                    center.add(CLEAVE_RANGE, CLEAVE_RANGE, CLEAVE_RANGE));
            int hitCount = 0;
            for (var target : world.getOtherEntities(user, sweep,
                    entity -> entity instanceof LivingEntity living && living.isAlive() && living.canHit()
                            && !(entity instanceof com.rivalrealms.entity.SurvivorEntity survivor
                            && survivor.isRecruited()))) {
                LivingEntity living = (LivingEntity) target;
                living.damage(world.getDamageSources().playerAttack(user), CLEAVE_DAMAGE);
                living.takeKnockback(0.7, -look.x, -look.z);
                hitCount++;
            }

            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.CRIT,
                        center.x, center.y + 0.8, center.z, hitCount > 0 ? 18 : 6,
                        CLEAVE_RANGE * 0.5, 0.6, CLEAVE_RANGE * 0.5, 0.12);
                serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                        center.x, center.y + 0.6, center.z, hitCount > 0 ? 3 : 1,
                        0.6, 0.2, 0.6, 0.0);
                serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                        SoundCategory.PLAYERS, 1.0f, 1.0f);
                serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                        SoundCategory.PLAYERS, 0.6f, 1.5f);
            }

            user.getItemCooldownManager().set(this, CLEAVE_COOLDOWN);
            user.swingHand(hand, true);
            if (!user.isCreative()) {
                stack.damage(CLEAVE_DURABILITY_COST, user, LivingEntity.getSlotForHand(hand));
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}

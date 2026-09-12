package com.rivalrealms.item;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * A true polearm: long reach through real attribute modifiers, heavy cut,
 * and an active sweeping thrust that shoves a whole rank of enemies back
 * and leaves them weakened. Slow, deliberate, and terrifying in a gate.
 */
public class HalberdItem extends Item {
    private static final Identifier REACH_ID = com.rivalrealms.RivalRealms.id("halberd_reach");
    private static final Identifier DAMAGE_ID = com.rivalrealms.RivalRealms.id("halberd_damage");
    private static final Identifier SPEED_ID = com.rivalrealms.RivalRealms.id("halberd_speed");

    public HalberdItem(Settings settings) {
        super(settings.attributeModifiers(createAttributes()));
    }

    private static AttributeModifiersComponent createAttributes() {
        return AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                        new EntityAttributeModifier(DAMAGE_ID, 8.0,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED,
                        new EntityAttributeModifier(SPEED_ID, -3.2,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
                        new EntityAttributeModifier(REACH_ID, 1.5,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .build();
    }

    /** Active thrust: every enemy in a cone ahead is struck and driven back. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            Vec3d look = user.getRotationVec(1.0f).normalize();
            Vec3d eye = user.getPos().add(0.0, user.getStandingEyeHeight(), 0.0);
            List<Entity> targets = world.getOtherEntities(user,
                    user.getBoundingBox().expand(4.5),
                    e -> e instanceof LivingEntity living && living.isAlive()
                            && !e.isSpectator()
                            && e.getPos().add(0.0, 1.0, 0.0).subtract(eye).normalize().dotProduct(look) > 0.45);
            int hits = 0;
            for (Entity target : targets) {
                LivingEntity living = (LivingEntity) target;
                living.damage(user.getDamageSources().playerAttack(user), 5.0f);
                living.takeKnockback(1.3, look.x, look.z);
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0));
                hits++;
            }
            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.7f);
            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 0.55f);
            serverWorld.spawnParticles(ParticleTypes.CRIT,
                    eye.x + look.x * 3.0, eye.y - 0.3, eye.z + look.z * 3.0, 4, 0.6, 0.2, 0.6, 0.1);
            if (hits > 0) {
                stack.damage(1, user, EquipmentSlot.MAINHAND);
            }
            user.getItemCooldownManager().set(this, 20);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}

package com.spiderman.mod.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spiderman.mod.state.ClientPowers;

/**
 * ULTIMATE ARM ANIMATIONS - Cinematic Spider-Man poses.
 * - Thwip pose after shot
 * - Arms-up while swinging
 * - Wall run arms
 * - Dive pose
 * - Style-based variations
 */
@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin {
    @Shadow
    public ModelPart rightArm;
    @Shadow
    public ModelPart leftArm;
    @Shadow
    public ModelPart rightLeg;
    @Shadow
    public ModelPart leftLeg;
    @Shadow
    public ModelPart head;
    @Shadow
    public ModelPart body;

    @Inject(method = "positionRightArm", at = @At("TAIL"))
    private void spm$poseRightArm(LivingEntity entity, CallbackInfo ci) {
        poseArm(entity, rightArm, 0, true);
    }

    @Inject(method = "positionLeftArm", at = @At("TAIL"))
    private void spm$poseLeftArm(LivingEntity entity, CallbackInfo ci) {
        poseArm(entity, leftArm, 1, false);
    }
    
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void spm$fullBodyPose(LivingEntity entity, float limbAngle, float limbDistance, 
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof ClientPlayerEntity) || !ClientPowers.has) {
            return;
        }
        
        // Swinging pose - full body
        if (ClientPowers.swingActive) {
            // Body leans into swing
            body.pitch = -0.3f;
            body.yaw = (float)Math.sin(animationProgress * 0.1) * 0.1f;
            
            // Legs tucked for aerodynamics
            rightLeg.pitch = -0.5f;
            leftLeg.pitch = -0.5f;
            rightLeg.yaw = 0.1f;
            leftLeg.yaw = -0.1f;
            
            // Head looks forward
            head.pitch = -0.2f;
        }
        
        // Diving pose
        if (ClientPowers.diving) {
            body.pitch = 1.2f; // Face down
            rightArm.pitch = -0.2f;
            leftArm.pitch = -0.2f;
            rightLeg.pitch = 0.1f;
            leftLeg.pitch = 0.1f;
            head.pitch = 0.5f;
        }
        
        // Wall running pose
        if (ClientPowers.wallRunning) {
            body.yaw = (float)Math.sin(animationProgress * 0.15) * 0.2f;
            rightArm.pitch = -0.8f;
            leftArm.pitch = -0.8f;
        }
    }

    @Unique
    private static void poseArm(LivingEntity entity, ModelPart arm, int hand, boolean isRight) {
        if (!(entity instanceof ClientPlayerEntity) || !ClientPowers.has) {
            return;
        }
        
        long sinceShot = ClientPowers.clientTick - ClientPowers.lastShotTick;
        
        if (ClientPowers.swingActive) {
            // Ultimate swinging pose - arms up, web line
            arm.pitch = -2.9f;
            arm.yaw = hand == 1 ? 0.35f : -0.35f;
            arm.roll = hand == 1 ? 0.2f : -0.2f;
        } else if (ClientPowers.diving) {
            // Dive - arms back
            arm.pitch = 0.3f;
            arm.yaw = isRight ? -0.2f : 0.2f;
        } else if (ClientPowers.wallRunning) {
            // Wall run - arms pumping
            float pump = (float)Math.sin(ClientPowers.clientTick * 0.3 + hand) * 0.3f;
            arm.pitch = -1.0f + pump;
            arm.yaw = isRight ? 0.15f : -0.15f;
        } else if (sinceShot >= 0 && sinceShot < 12 && ClientPowers.lastShotHand == hand) {
            // Thwip - epic web shot pose
            float progress = sinceShot / 12.0f;
            arm.pitch = -1.8f - progress * 0.5f;
            arm.yaw = isRight ? -0.1f : 0.1f;
            arm.roll = isRight ? -0.2f : 0.2f;
        }
    }
}

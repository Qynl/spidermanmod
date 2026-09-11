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
 * Third-person arm animation: thwip pose right after firing (correct wrist),
 * arms-up while holding a swing line.
 */
@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin {
    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Inject(method = "positionRightArm", at = @At("TAIL"))
    private void spm$poseRightArm(LivingEntity entity, CallbackInfo ci) {
        poseArm(entity, rightArm, 0);
    }

    @Inject(method = "positionLeftArm", at = @At("TAIL"))
    private void spm$poseLeftArm(LivingEntity entity, CallbackInfo ci) {
        poseArm(entity, leftArm, 1);
    }

    @Unique
    private static void poseArm(LivingEntity entity, ModelPart arm, int hand) {
        if (!(entity instanceof ClientPlayerEntity) || !ClientPowers.has) {
            return;
        }
        long sinceShot = ClientPowers.clientTick - ClientPowers.lastShotTick;
        if (ClientPowers.swingActive) {
            arm.pitch = -2.7f;
            arm.yaw = hand == 1 ? 0.25f : -0.25f;
        } else if (sinceShot >= 0 && sinceShot < 10 && ClientPowers.lastShotHand == hand) {
            arm.pitch = -1.5f;
            arm.yaw = 0.0f;
        }
    }
}

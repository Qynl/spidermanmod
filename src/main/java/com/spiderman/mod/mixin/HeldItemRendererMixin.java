package com.spiderman.mod.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spiderman.mod.state.ClientPowers;

/**
 * First-person wrist animation: the firing arm punches slightly forward
 * after a shot; both arms lift while holding a swing line.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "renderArm", at = @At("HEAD"))
    private void spm$raiseArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, Arm arm, CallbackInfo ci) {
        if (!ClientPowers.has) {
            return;
        }
        boolean shotArm = arm == (ClientPowers.lastShotHand == 1 ? Arm.LEFT : Arm.RIGHT);
        long sinceShot = ClientPowers.clientTick - ClientPowers.lastShotTick;
        if (ClientPowers.swingActive) {
            matrices.translate(0.0, 0.18, -0.12);
        } else if (shotArm && sinceShot >= 0 && sinceShot < 8) {
            matrices.translate(0.0, 0.1, -0.25);
        }
    }
}

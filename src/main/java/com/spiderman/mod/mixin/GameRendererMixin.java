package com.spiderman.mod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spiderman.mod.client.WheelScreen;
import com.spiderman.mod.state.ClientPowers;

/**
 * Dynamic FOV: speed kick while swinging/fast, focus zoom while the ability
 * wheel is open.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void spm$fovKick(Camera camera, float tickDelta, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !ClientPowers.has) {
            return;
        }
        double speed = client.player.getVelocity().horizontalLength();
        double boost = 0.0;
        if (ClientPowers.swingActive) {
            boost += 10.0 + Math.min(10.0, speed * 4.0);
        } else if (speed > 0.45) {
            boost += Math.min(8.0, (speed - 0.45) * 6.0);
        }
        if (client.currentScreen instanceof WheelScreen) {
            boost -= 18.0;
        }
        if (boost != 0.0) {
            cir.setReturnValue(cir.getReturnValue() + boost);
        }
    }
}

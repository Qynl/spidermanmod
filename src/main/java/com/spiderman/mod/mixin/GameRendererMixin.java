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
 * ULTIMATE FOV AND EFFECTS - Cinematic Spider-Man feel.
 * - Speed FOV while swinging, zipping, wall-running
 * - Focus zoom for wheel
 * - Dive FOV
 * - Slow-mo sense effect
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
        
        double speed = 0;
        try {
            speed = client.player.getVelocity().length();
        } catch (Exception ignored) {}
        
        double boost = 0.0;
        
        // Swing FOV - epic speed feel
        if (ClientPowers.swingActive) {
            boost += 15.0 + Math.min(15.0, speed * 5.0);
            if (client.options.sprintKey.isPressed()) {
                boost += 8.0; // Sprint boost FOV
            }
        } else if (ClientPowers.slowMoActive) {
            // Slow-mo sense - reduce FOV for focus
            boost -= 10.0;
        } else if (speed > 0.5) {
            // General speed FOV
            boost += Math.min(12.0, (speed - 0.5) * 7.0);
        }
        
        // Wall running FOV
        if (ClientPowers.wallRunning) {
            boost += 8.0;
        }
        
        // Diving FOV
        if (ClientPowers.diving) {
            boost += 12.0 + Math.min(10.0, speed * 3.0);
        }
        
        // Wheel focus zoom
        if (client.currentScreen instanceof WheelScreen) {
            boost -= 20.0;
        }
        
        // Cinematic zoom
        if (ClientPowers.cinematicTicks > 0) {
            boost -= 5.0 + Math.sin(ClientPowers.cinematicTicks * 0.2) * 3.0;
        }
        
        if (boost != 0.0) {
            cir.setReturnValue(cir.getReturnValue() + boost);
        }
    }
}

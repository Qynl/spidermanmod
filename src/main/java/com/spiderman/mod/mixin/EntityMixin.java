package com.spiderman.mod.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Stage-scaled fall resistance + walkable webs (no slow for spider-man).
 */
@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void spm$fallResist(float fallDistance, float damageMultiplier,
            DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld().isClient) {
            return;
        }
        if (!(self instanceof ServerPlayerEntity player)) {
            return;
        }
        PlayerPowers powers = SpiderState.get(player.getUuid());
        if (!powers.hasPowers) {
            return;
        }
        float safe = 3.0f + 4.0f * Math.min(powers.stage + 1, 4);
        float stageMult = powers.stage >= 2 ? 0.0f : (powers.stage == 1 ? 0.15f : 0.3f);
        float damage = Math.max(0.0f, fallDistance - safe) * damageMultiplier * stageMult
                * (float) (SpiderConfig.get().fallMult * 10.0);
        if (damage > 0.0f) {
            player.damage(damageSource, damage);
        }
        cir.setReturnValue(damage > 0.0f);
        cir.cancel();
    }

    // Make webs walkable and not slow for Spider-Man
    @Inject(method = "slowMovement", at = @At("HEAD"), cancellable = true)
    private void spm$noWebSlow(BlockState state, Vec3d multiplier, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        try {
            if (state.isOf(Blocks.COBWEB)) {
                if (self instanceof ServerPlayerEntity player) {
                    PlayerPowers powers = SpiderState.get(player.getUuid());
                    if (powers.hasPowers) {
                        // Spider-Man walks on webs, no slow
                        ci.cancel();
                        return;
                    }
                } else if (self.getWorld().isClient) {
                    // Client side check via ClientPowers
                    try {
                        if (com.spiderman.mod.state.ClientPowers.has) {
                            ci.cancel();
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
}

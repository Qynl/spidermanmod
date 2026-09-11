package com.spiderman.mod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.state.PlayerPowers;
import com.spiderman.mod.state.SpiderState;

/**
 * Stage-scaled fall resistance: higher safe distance plus a damage fraction
 * that shrinks per stage until impacts are fully ignored. Server-side only
 * (fall damage is server-authoritative).
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
        float safe = 3.0f + 5.0f * (powers.stage + 1);
        float stageMult = powers.stage >= 2 ? 0.0f : (powers.stage == 1 ? 0.25f : 0.5f);
        float damage = Math.max(0.0f, fallDistance - safe) * damageMultiplier * stageMult
                * (float) (SpiderConfig.get().fallMult * 10.0);
        if (damage > 0.0f) {
            player.damage(damageSource, damage);
        }
        cir.setReturnValue(damage > 0.0f);
        cir.cancel();
    }
}

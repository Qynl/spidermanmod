package com.spiderman.mod.server;

import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.net.ServerNetworking;
import com.spiderman.mod.state.PlayerPowers;

/**
 * Directional spider-sense: scans for nearby hostiles, incoming projectiles
 * and dangerous falls, then pings the client with threat positions.
 */
public final class SenseLogic {
    private SenseLogic() {
    }

    public static void tick(ServerPlayerEntity player, PlayerPowers powers) {
        if (!powers.hasPowers) {
            return;
        }
        SpiderConfig cfg = SpiderConfig.get();
        double radius = cfg.senseRadiusBase + cfg.senseRadiusPerStage * powers.stage;
        Vec3d pos = player.getPos();
        Box box = new Box(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
        ServerWorld world = player.getServerWorld();

        List<HostileEntity> mobs = world.getEntitiesByClass(HostileEntity.class, box,
                e -> e.isAlive() && e.squaredDistanceTo(player) < radius * radius);
        int sent = 0;
        for (HostileEntity mob : mobs) {
            if (sent++ >= 5) {
                break;
            }
            ServerNetworking.sendSense(player, mob.getX(), mob.getY(), mob.getZ(), 0);
        }

        List<ProjectileEntity> shots = world.getEntitiesByClass(ProjectileEntity.class, box,
                e -> e.isAlive() && approaching(e, pos));
        for (ProjectileEntity shot : shots) {
            if (sent++ >= 8) {
                break;
            }
            ServerNetworking.sendSense(player, shot.getX(), shot.getY(), shot.getZ(), 1);
        }

        if (player.fallDistance > 6.0f && player.getVelocity().y < -0.5 && player.age % 20 == 0) {
            ServerNetworking.sendSense(player, pos.x, pos.y - 4.0, pos.z, 2);
        }
    }

    private static boolean approaching(ProjectileEntity shot, Vec3d target) {
        Vec3d to = target.subtract(shot.getPos());
        return shot.getVelocity().dotProduct(to) > 0.0;
    }
}

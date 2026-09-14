package com.manhunt.client;

import com.manhunt.ManhuntSounds;
import com.manhunt.entity.HunterEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * The dread layer: a vignette that tightens as he nears, a heartbeat that
 * speeds up with proximity, a sub-drone under sixteen blocks, and the whistle
 * motif that plays only while he is close and unseen - and dies the instant
 * you look at him.
 */
public final class DreadLayer {

    private static int heartbeatCooldown;
    private static int droneCooldown;
    private static int motifCooldown;
    private static int breathCooldown;

    private DreadLayer() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWorld world = client.world;
            ClientPlayerEntity player = client.player;
            if (world == null || player == null) {
                return;
            }
            HunterEntity hunter = nearest(world, player);
            if (hunter == null) {
                return;
            }
            double distance = Math.sqrt(hunter.squaredDistanceTo(player));
            boolean seen = lookingAt(player, hunter) && distance < 48;
            tickHeartbeat(player, distance);
            tickDrone(player, distance);
            tickMotif(player, hunter, distance, seen);
            tickBreath(player, distance, seen);
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderVignette(context));
    }

    private static HunterEntity nearest(ClientWorld world, ClientPlayerEntity player) {
        return world.getEntitiesByClass(HunterEntity.class,
                        player.getBoundingBox().expand(96), hunter -> hunter.isAlive()).stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)))
                .orElse(null);
    }

    private static boolean lookingAt(ClientPlayerEntity player, HunterEntity hunter) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d towards = hunter.getPos().add(0, 1.4, 0).subtract(player.getEyePos()).normalize();
        return look.dotProduct(towards) > 0.8;
    }

    private static void tickHeartbeat(ClientPlayerEntity player, double distance) {
        if (distance > 40 || --heartbeatCooldown > 0) {
            return;
        }
        float closeness = (float) (1.0 - distance / 40.0);
        heartbeatCooldown = (int) (28 - 17 * closeness);
        player.playSound(ManhuntSounds.HEARTBEAT, 0.15f + 0.75f * closeness, 1.0f);
    }

    private static void tickDrone(ClientPlayerEntity player, double distance) {
        if (distance > 16 || --droneCooldown > 0) {
            return;
        }
        droneCooldown = 76;
        float closeness = (float) (1.0 - distance / 16.0);
        player.playSound(ManhuntSounds.DRONE, 0.1f + 0.5f * closeness, 1.0f);
    }

    /** Close and unseen: the lullaby. Seen: silence, forever until you look away. */
    private static void tickMotif(ClientPlayerEntity player, HunterEntity hunter,
                                  double distance, boolean seen) {
        if (motifCooldown > 0) {
            motifCooldown--;
        }
        if (seen) {
            motifCooldown = Math.max(motifCooldown, 40);
            return;
        }
        if (distance > 28 || motifCooldown > 0) {
            return;
        }
        motifCooldown = 260;
        player.playSound(ManhuntSounds.MOTIF, 0.5f, 1.0f);
    }

    private static void tickBreath(ClientPlayerEntity player, double distance, boolean seen) {
        if (breathCooldown > 0) {
            breathCooldown--;
            return;
        }
        if (distance > 10 || seen) {
            return;
        }
        breathCooldown = 90 + player.getRandom().nextInt(60);
        player.playSound(ManhuntSounds.BREATH, 0.4f, 1.0f);
    }

    /** Radial darkness, alpha by proximity: the world closes in with him. */
    private static void renderVignette(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }
        HunterEntity hunter = nearest(world, player);
        if (hunter == null) {
            return;
        }
        double distance = Math.sqrt(hunter.squaredDistanceTo(player));
        if (distance > 40) {
            return;
        }
        float dread = (float) (1.0 - distance / 40.0);
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int rings = 10;
        for (int ring = 0; ring < rings; ring++) {
            float strength = (ring + 1) / (float) rings;
            int alpha = (int) (34 * dread * strength * strength);
            if (alpha <= 0) {
                continue;
            }
            int color = (alpha << 24);
            int inset = ring * 6;
            context.fill(0, 0, width, inset + 4, color);
            context.fill(0, height - inset - 4, width, height, color);
            context.fill(0, inset, inset + 4, height - inset, color);
            context.fill(width - inset - 4, inset, width, height - inset, color);
        }
    }
}

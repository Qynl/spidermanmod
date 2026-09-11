package com.spiderman.mod.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spiderman.mod.SpiderManMod;

/**
 * Server-side store for all players' powers. Persisted to
 * {@code <world>/spiderman.dat} on disconnect and periodically.
 */
public final class SpiderState {
    private static final Map<UUID, PlayerPowers> POWERS = new HashMap<>();
    private static boolean loaded;

    private SpiderState() {
    }

    public static PlayerPowers get(UUID id) {
        return POWERS.computeIfAbsent(id, k -> new PlayerPowers());
    }

    public static void drop(UUID id) {
        POWERS.remove(id);
    }

    public static void ensureLoaded(MinecraftServer server) {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = savePath(server);
            if (Files.exists(path)) {
                NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
                for (Object o : root.getKeys()) {
                    String key = (String) o;
                    try {
                        UUID id = UUID.fromString(key);
                        PlayerPowers powers = new PlayerPowers();
                        powers.fromNbt(root.getCompound(key));
                        POWERS.put(id, powers);
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed keys.
                    }
                }
                SpiderManMod.LOGGER.info("[spiderman] loaded powers for {} players", POWERS.size());
            }
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] failed to load spiderman.dat", e);
        }
    }

    public static void save(MinecraftServer server) {
        try {
            Path path = savePath(server);
            Files.createDirectories(path.getParent());
            NbtCompound root = new NbtCompound();
            for (Map.Entry<UUID, PlayerPowers> entry : POWERS.entrySet()) {
                NbtCompound nbt = new NbtCompound();
                entry.getValue().toNbt(nbt);
                root.put(entry.getKey().toString(), nbt);
            }
            NbtIo.writeCompressed(root, path);
        } catch (Exception e) {
            SpiderManMod.LOGGER.warn("[spiderman] failed to save spiderman.dat", e);
        }
    }

    private static Path savePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("spiderman.dat");
    }
}

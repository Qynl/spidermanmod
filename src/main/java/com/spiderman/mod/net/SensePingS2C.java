package com.spiderman.mod.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.spiderman.mod.SpiderManMod;

/**
 * Directional spider-sense ping. {@code kind}: 0 = hostile, 1 = projectile,
 * 2 = falling danger.
 */
public record SensePingS2C(double x, double y, double z, int kind) implements CustomPayload {
    public static final CustomPayload.Id<SensePingS2C> ID =
            new CustomPayload.Id<>(Identifier.of(SpiderManMod.MOD_ID, "sense_ping"));
    public static final PacketCodec<RegistryByteBuf, SensePingS2C> CODEC =
            CustomPayload.codecOf(SensePingS2C::write, SensePingS2C::new);

    private SensePingS2C(RegistryByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(kind);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

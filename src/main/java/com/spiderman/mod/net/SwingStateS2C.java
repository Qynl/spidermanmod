package com.spiderman.mod.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.spiderman.mod.SpiderManMod;

/**
 * Tells the client to render a web strand from the given wrist to the anchor.
 * {@code life} is 0 for a persistent swing line, otherwise the strand fades
 * after that many client ticks (zip/pull/shot tracers).
 */
public record SwingStateS2C(boolean active, double x, double y, double z, int hand, int life)
        implements CustomPayload {
    public static final CustomPayload.Id<SwingStateS2C> ID =
            new CustomPayload.Id<>(Identifier.of(SpiderManMod.MOD_ID, "swing_state"));
    public static final PacketCodec<RegistryByteBuf, SwingStateS2C> CODEC =
            CustomPayload.codecOf(SwingStateS2C::write, SwingStateS2C::new);

    private SwingStateS2C(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(hand);
        buf.writeVarInt(life);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

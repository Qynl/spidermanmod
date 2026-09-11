package com.spiderman.mod.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.spiderman.mod.SpiderManMod;

/**
 * Full powers sync: sent on join, on bite, on stage-up and on selection change.
 */
public record PowersSyncS2C(boolean has, int stage, int mastery, int selected) implements CustomPayload {
    public static final CustomPayload.Id<PowersSyncS2C> ID =
            new CustomPayload.Id<>(Identifier.of(SpiderManMod.MOD_ID, "powers_sync"));
    public static final PacketCodec<RegistryByteBuf, PowersSyncS2C> CODEC =
            CustomPayload.codecOf(PowersSyncS2C::write, PowersSyncS2C::new);

    private PowersSyncS2C(RegistryByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(has);
        buf.writeVarInt(stage);
        buf.writeVarInt(mastery);
        buf.writeVarInt(selected);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

package com.spiderman.mod.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.spiderman.mod.SpiderManMod;

/** Client tells the server which ability the wheel selected. */
public record WheelSelectC2S(int ability) implements CustomPayload {
    public static final CustomPayload.Id<WheelSelectC2S> ID =
            new CustomPayload.Id<>(Identifier.of(SpiderManMod.MOD_ID, "wheel_select"));
    public static final PacketCodec<PacketByteBuf, WheelSelectC2S> CODEC =
            CustomPayload.codecOf(WheelSelectC2S::write, WheelSelectC2S::new);

    private WheelSelectC2S(PacketByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(ability);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

package com.spiderman.mod.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.spiderman.mod.SpiderManMod;

/** Combo counter update for the HUD. */
public record ComboS2C(int combo) implements CustomPayload {
    public static final CustomPayload.Id<ComboS2C> ID =
            new CustomPayload.Id<>(Identifier.of(SpiderManMod.MOD_ID, "combo"));
    public static final PacketCodec<RegistryByteBuf, ComboS2C> CODEC =
            CustomPayload.codecOf(ComboS2C::write, ComboS2C::new);

    private ComboS2C(RegistryByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(combo);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

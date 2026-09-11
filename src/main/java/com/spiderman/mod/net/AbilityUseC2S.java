package com.spiderman.mod.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.spiderman.mod.SpiderManMod;

/**
 * Client requests firing an ability. {@code hand} is 0 (main/right) or 1
 * (off/left) and selects which wrist the web fires from.
 */
public record AbilityUseC2S(int ability, int hand) implements CustomPayload {
    public static final CustomPayload.Id<AbilityUseC2S> ID =
            new CustomPayload.Id<>(Identifier.of(SpiderManMod.MOD_ID, "ability_use"));
    public static final PacketCodec<PacketByteBuf, AbilityUseC2S> CODEC =
            CustomPayload.codecOf(AbilityUseC2S::write, AbilityUseC2S::new);

    private AbilityUseC2S(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(ability);
        buf.writeVarInt(hand);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

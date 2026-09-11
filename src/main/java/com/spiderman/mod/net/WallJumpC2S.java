package com.spiderman.mod.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.spiderman.mod.SpiderManMod;

/** Client requests a wall jump (jump key pressed while clinging). */
public record WallJumpC2S() implements CustomPayload {
    public static final CustomPayload.Id<WallJumpC2S> ID =
            new CustomPayload.Id<>(Identifier.of(SpiderManMod.MOD_ID, "wall_jump"));
    public static final PacketCodec<PacketByteBuf, WallJumpC2S> CODEC =
            CustomPayload.codecOf(WallJumpC2S::write, WallJumpC2S::new);

    private WallJumpC2S(PacketByteBuf buf) {
        this();
    }

    private void write(PacketByteBuf buf) {
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

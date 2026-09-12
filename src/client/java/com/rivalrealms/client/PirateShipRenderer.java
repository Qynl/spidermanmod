package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** The Freebooter Raider: tar-black hull and a bone-white emblem on black canvas. */
public final class PirateShipRenderer extends SailingShipRenderer {
    private static final Identifier TEXTURE = RivalRealms.id("textures/entity/pirate_ship.png");

    public PirateShipRenderer(EntityRendererFactory.Context context) {
        super(context, TEXTURE, 1.0f);
    }
}

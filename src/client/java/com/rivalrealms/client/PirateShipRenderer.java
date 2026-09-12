package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Identifier;

/** Regular boat geometry with a dark freebooter hull and red sail texture. */
public final class PirateShipRenderer extends BoatEntityRenderer {
    public PirateShipRenderer(EntityRendererFactory.Context context) {
        super(context, false);
    }

    @Override
    public Identifier getTexture(BoatEntity boat) {
        return RivalRealms.id("textures/entity/pirate_ship.png");
    }
}

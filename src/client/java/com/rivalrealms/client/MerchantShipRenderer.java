package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Identifier;

/** Chest-boat geometry with the custom royal jewellery convoy texture. */
public final class MerchantShipRenderer extends BoatEntityRenderer {
    public MerchantShipRenderer(EntityRendererFactory.Context context) {
        super(context, true);
    }

    @Override
    public Identifier getTexture(BoatEntity boat) {
        return RivalRealms.id("textures/entity/merchant_ship.png");
    }
}

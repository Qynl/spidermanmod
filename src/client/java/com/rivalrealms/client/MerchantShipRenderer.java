package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** The Royal Jewelry Trader: pale oak hull, cream-and-royal-blue sails. */
public final class MerchantShipRenderer extends SailingShipRenderer {
    private static final Identifier TEXTURE = RivalRealms.id("textures/entity/merchant_ship.png");

    public MerchantShipRenderer(EntityRendererFactory.Context context) {
        super(context, TEXTURE, 1.0f);
    }
}

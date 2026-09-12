package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** The player-sailable sloop: the cog geometry at sloop scale, teal sails. */
public final class SloopRenderer extends SailingShipRenderer {
    private static final Identifier TEXTURE = RivalRealms.id("textures/entity/sloop.png");

    public SloopRenderer(EntityRendererFactory.Context context) {
        super(context, TEXTURE, 0.55f);
    }
}

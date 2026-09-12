package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Identifier;

/** Gives the flying boat its own copper, canvas, and signal-colour texture. */
public final class AirshipRenderer extends BoatEntityRenderer {
    private static final Identifier TEXTURE = RivalRealms.id("textures/entity/airship.png");

    public AirshipRenderer(EntityRendererFactory.Context context) {
        super(context, false);
    }

    @Override
    public Identifier getTexture(BoatEntity boat) {
        return TEXTURE;
    }
}

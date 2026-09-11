package com.spiderman.mod.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.SpiderEntityRenderer;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.util.Identifier;

/** Vanilla spider model with the radioactive texture. */
public class RadioactiveSpiderRenderer extends SpiderEntityRenderer {
    private static final Identifier TEXTURE =
            Identifier.of("spiderman", "textures/entity/radioactive_spider.png");

    public RadioactiveSpiderRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(SpiderEntity entity) {
        return TEXTURE;
    }
}

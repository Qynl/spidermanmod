package com.manhunt.client;

import com.manhunt.Manhunt;
import com.manhunt.entity.HunterEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/**
 * The hunter wears the vanilla player model, armor and held-item layers - he
 * should read as a player at a glance - but with his own scarred skin.
 */
public final class ManhuntClient implements ClientModInitializer {

    public static final Identifier HUNTER_SKIN =
            Identifier.of(Manhunt.MOD_ID, "textures/entity/hunter.png");

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Manhunt.HUNTER, HunterRenderer::new);
    }

    private static final class HunterRenderer
            extends BipedEntityRenderer<HunterEntity, PlayerEntityModel<HunterEntity>> {

        private HunterRenderer(EntityRendererFactory.Context context) {
            super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
            addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
            addFeature(new ArmorFeatureRenderer<>(this,
                    new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                    new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
        }

        @Override
        public Identifier getTexture(HunterEntity entity) {
            return HUNTER_SKIN;
        }

    }
}

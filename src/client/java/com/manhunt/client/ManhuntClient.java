package com.manhunt.client;

import com.manhunt.Manhunt;
import com.manhunt.entity.HunterEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Identifier;

/**
 * The hunter wears the vanilla player model, armor and held-item layers - he
 * should read as a player at a glance - but with his own scarred skin.
 */
public final class ManhuntClient implements ClientModInitializer {

    public static final Identifier HUNTER_SKIN =
            Identifier.of(Manhunt.MOD_ID, "textures/entity/hunter.png");
    public static final Identifier HUNTER_PALE =
            Identifier.of(Manhunt.MOD_ID, "textures/entity/hunter_pale.png");

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Manhunt.HUNTER, HunterRenderer::new);
        DreadLayer.init();
    }

    private static final class HunterRenderer
            extends LivingEntityRenderer<HunterEntity, BipedEntityModel<HunterEntity>> {

        private HunterRenderer(EntityRendererFactory.Context context) {
            super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
            addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
            addFeature(new ArmorFeatureRenderer<>(this,
                    new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                    new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
        }

        /** Far away he wears your own face. Closer, the hood. Closer still, the pale one. */
        @Override
        public Identifier getTexture(HunterEntity entity) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity player = client.player;
            if (player != null && entity.squaredDistanceTo(player) > 900) {
                return player.getSkinTextures().texture();
            }
            if (entity.phase() == HunterEntity.Phase.STALK
                    || entity.phase() == HunterEntity.Phase.HUNT) {
                return HUNTER_PALE;
            }
            return HUNTER_SKIN;
        }


    }
}

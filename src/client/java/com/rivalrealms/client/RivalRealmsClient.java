package com.rivalrealms.client;

import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Client entrypoint: registers the custom model layers, the hand-built
 * vehicle renderers, the cannonball renderer and the piloting input bridge.
 */
public final class RivalRealmsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Model layers first: the renderers need their baked geometry.
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.AIRSHIP, AirshipEntityModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.SAILING_SHIP, SailingShipEntityModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.SLOOP, SailingShipEntityModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.GALLEON, GalleonEntityModel::getTexturedModelData);

        EntityRendererRegistry.register(ModEntities.SURVIVOR, SurvivorRenderer::new);
        EntityRendererRegistry.register(ModEntities.AIRSHIP, AirshipRenderer::new);
        EntityRendererRegistry.register(ModEntities.MERCHANT_SHIP, MerchantShipRenderer::new);
        EntityRendererRegistry.register(ModEntities.PIRATE_SHIP, PirateShipRenderer::new);
        EntityRendererRegistry.register(ModEntities.SLOOP, SloopRenderer::new);
        EntityRendererRegistry.register(ModEntities.GALLEON, GalleonRenderer::new);
        EntityRendererRegistry.register(ModEntities.CANNONBALL, FlyingItemEntityRenderer::new);

        VehicleInputProxy.register();

        // Cutout blocks: see-through slots let you sight (and shoot) through.
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ARROW_SLIT, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WEAPON_RACK, RenderLayer.getCutout());
    }
}

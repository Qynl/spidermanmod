package com.rivalrealms.client;

import com.rivalrealms.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class RivalRealmsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.SURVIVOR, SurvivorRenderer::new);
        EntityRendererRegistry.register(ModEntities.AIRSHIP, context -> new BoatEntityRenderer(context, false));
    }
}

package com.spiderman.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;

import com.spiderman.mod.client.ClientTickHandler;
import com.spiderman.mod.client.HudRenderer;
import com.spiderman.mod.client.Keybinds;
import com.spiderman.mod.client.RadioactiveSpiderRenderer;
import com.spiderman.mod.client.StrandRenderer;
import com.spiderman.mod.client.WebShotRenderer;
import com.spiderman.mod.net.ClientNetworking;
import com.spiderman.mod.state.ClientPowers;

/**
 * Client entrypoint: renderers, keybinds, HUD, world-render hooks and the
 * client-side powers mirror ({@link ClientPowers}).
 */
public class SpiderManClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpiderManMod.LOGGER.info("[spiderman] initializing (client)");
        Keybinds.register();
        ClientNetworking.register();
        EntityRendererRegistry.register(ModEntities.RADIOACTIVE_SPIDER, RadioactiveSpiderRenderer::new);
        EntityRendererRegistry.register(ModEntities.WEB_SHOT, WebShotRenderer::new);
        // Vanilla only tint-registers its own spawn eggs; ours needs it
        // explicit. (Explicit class, not a lambda: offline javac rejects
        // the lambda form against the stubs.)
        ColorProviderRegistry.ITEM.register(new SpawnEggTint(), ModItems.RADIOACTIVE_SPIDER_SPAWN_EGG);
        HudRenderCallback.EVENT.register(HudRenderer::onHudRender);
        WorldRenderEvents.AFTER_ENTITIES.register(StrandRenderer::afterEntities);
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickHandler::onEndTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientPowers.reset());
        SpiderManMod.LOGGER.info("[spiderman] client init complete");
    }

    /** Tints the radioactive-spider spawn egg with its two shell colors. */
    private static final class SpawnEggTint implements ItemColorProvider {
        @Override
        public int getColor(ItemStack stack, int tintIndex) {
            return ((SpawnEggItem) stack.getItem()).getColor(tintIndex);
        }
    }
}

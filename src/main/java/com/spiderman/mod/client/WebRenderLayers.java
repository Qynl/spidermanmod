package com.spiderman.mod.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Custom render layers for realistic Spider-Man webs - Insomniac level.
 */
public class WebRenderLayers {
    private static final Identifier WEB_LINE_TEXTURE = Identifier.of("spiderman", "textures/entity/web_line.png");
    private static final Identifier WEB_THREAD_TEXTURE = Identifier.of("spiderman", "textures/entity/web_thread.png");

    public static final RenderLayer WEB_THREAD = RenderLayer.of(
            "spiderman_web_thread",
            VertexFormats.POSITION_COLOR_TEXTURE_LIGHT,
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                    .texture(new RenderPhase.Texture(WEB_LINE_TEXTURE, false, false))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                    .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                    .build(false)
    );

    public static final RenderLayer WEB_GLOW = RenderLayer.of(
            "spiderman_web_glow",
            VertexFormats.POSITION_COLOR_TEXTURE_LIGHT,
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                    .texture(new RenderPhase.Texture(WEB_THREAD_TEXTURE, false, false))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                    .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                    .build(false)
    );
}

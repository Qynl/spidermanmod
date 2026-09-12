package com.rivalrealms.client;

import com.rivalrealms.RivalRealms;
import net.minecraft.client.render.entity.model.EntityModelLayer;

/**
 * Custom model layers for every hand-built Rival Realms vehicle. Each layer
 * is registered against code-authored geometry in the matching model class —
 * no vanilla model is reused anywhere.
 */
public final class ModModelLayers {
    public static final EntityModelLayer AIRSHIP = new EntityModelLayer(RivalRealms.id("airship"), "main");
    public static final EntityModelLayer SAILING_SHIP = new EntityModelLayer(RivalRealms.id("sailing_ship"), "main");
    public static final EntityModelLayer SLOOP = new EntityModelLayer(RivalRealms.id("sloop"), "main");

    private ModModelLayers() {
    }
}

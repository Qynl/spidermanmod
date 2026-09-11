package com.spiderman.mod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

import com.spiderman.mod.config.SpiderConfig;
import com.spiderman.mod.entity.RadioactiveSpiderEntity;
import com.spiderman.mod.entity.WebShotEntity;

/**
 * Entity types owned by this mod.
 */
public final class ModEntities {
    public static EntityType<RadioactiveSpiderEntity> RADIOACTIVE_SPIDER;
    public static EntityType<WebShotEntity> WEB_SHOT;

    private ModEntities() {
    }

    public static void register() {
        RADIOACTIVE_SPIDER = Registry.register(Registries.ENTITY_TYPE,
                Identifier.of(SpiderManMod.MOD_ID, "radioactive_spider"),
                EntityType.Builder.create(RadioactiveSpiderEntity::new, SpawnGroup.MONSTER)
                        .dimensions(1.4f, 0.9f)
                        .maxTrackingRange(8)
                        .build("radioactive_spider"));
        WEB_SHOT = Registry.register(Registries.ENTITY_TYPE,
                Identifier.of(SpiderManMod.MOD_ID, "web_shot"),
                EntityType.Builder.create(WebShotEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25f, 0.25f)
                        .maxTrackingRange(4)
                        .trackingTickInterval(2)
                        .build("web_shot"));

        FabricDefaultAttributeRegistry.register(RADIOACTIVE_SPIDER,
                RadioactiveSpiderEntity.createSpiderAttributes()
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                        .build());

        SpawnRestriction.register(RADIOACTIVE_SPIDER, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER,
                RADIOACTIVE_SPIDER, SpiderConfig.get().spiderWeight, 1, 1);
    }
}

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
 * Radioactive spider now has custom model (RadioactiveSpiderModel) and distinct attributes.
 */
public final class ModEntities {
    public static EntityType<RadioactiveSpiderEntity> RADIOACTIVE_SPIDER;
    public static EntityType<WebShotEntity> WEB_SHOT;

    private ModEntities() {
    }

    public static void register() {
        // Custom radioactive spider: slightly smaller than vanilla spider (more agile)
        // but with larger hitbox for abdomen, distinct from vanilla spider model
        RADIOACTIVE_SPIDER = Registry.register(Registries.ENTITY_TYPE,
                Identifier.of(SpiderManMod.MOD_ID, "radioactive_spider"),
                EntityType.Builder.create(RadioactiveSpiderEntity::new, SpawnGroup.MONSTER)
                        .dimensions(1.2f, 0.8f) // More compact, agile — custom model is 1.1 scale
                        .maxTrackingRange(10) // See further than vanilla (8 -> 10)
                        .build("radioactive_spider"));
        WEB_SHOT = Registry.register(Registries.ENTITY_TYPE,
                Identifier.of(SpiderManMod.MOD_ID, "web_shot"),
                EntityType.Builder.create(WebShotEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25f, 0.25f)
                        .maxTrackingRange(6) // Increased from 4 for better multiplayer visibility
                        .trackingTickInterval(2)
                        .build("web_shot"));

        // Custom attributes: radioactive spider is stronger, faster, more health than vanilla spider
        // Vanilla spider: health 16, speed 0.3, damage 2
        // Radioactive: health 24, speed 0.35, damage 4 — a mini-boss feel, rare but dangerous
        FabricDefaultAttributeRegistry.register(RADIOACTIVE_SPIDER,
                RadioactiveSpiderEntity.createSpiderAttributes()
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0) // Sees further
                        .add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.2)
                        .build());

        SpawnRestriction.register(RADIOACTIVE_SPIDER, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        int spiderWeight;
        try {
            spiderWeight = SpiderConfig.get().spiderWeight;
        } catch (Exception e) {
            spiderWeight = 4;
        }
        if (spiderWeight > 0) {
            BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER,
                    RADIOACTIVE_SPIDER, spiderWeight, 1, 1);
        }
    }
}

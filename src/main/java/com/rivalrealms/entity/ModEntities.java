package com.rivalrealms.entity;

import com.rivalrealms.RivalRealms;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<SurvivorEntity> SURVIVOR = register(
            "survivor",
            EntityType.Builder.create(SurvivorEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 1.8f)
                    .maxTrackingRange(80)
                    .trackingTickInterval(2)
    );

    public static final EntityType<AirshipEntity> AIRSHIP = register(
            "airship",
            EntityType.Builder.create(AirshipEntity::new, SpawnGroup.MISC)
                    .dimensions(1.4f, 0.7f)
                    .maxTrackingRange(96)
                    .trackingTickInterval(1)
    );

    public static final EntityType<MerchantShipEntity> MERCHANT_SHIP = register(
            "merchant_ship",
            EntityType.Builder.create(MerchantShipEntity::new, SpawnGroup.MISC)
                    .dimensions(1.6f, 0.8f)
                    .maxTrackingRange(96)
                    .trackingTickInterval(1)
    );

    public static final EntityType<PirateShipEntity> PIRATE_SHIP = register(
            "pirate_ship",
            EntityType.Builder.create(PirateShipEntity::new, SpawnGroup.MISC)
                    .dimensions(1.6f, 0.8f)
                    .maxTrackingRange(96)
                    .trackingTickInterval(1)
    );

    private ModEntities() {
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        Identifier id = RivalRealms.id(name);
        return Registry.register(Registries.ENTITY_TYPE, id, builder.build(id.toString()));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(SURVIVOR, SurvivorEntity.createAttributes());
        RivalRealms.LOGGER.info("Registered Rival Realms survivors, airships, merchant ships and pirate raiders.");
    }
}

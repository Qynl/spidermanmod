package com.rivalrealms.item;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item RECRUITMENT_CONTRACT = register("recruitment_contract",
            new Item(new Item.Settings().maxCount(16)));
    public static final Item REVOLVER = register("revolver",
            new RevolverItem(new Item.Settings().maxCount(1).maxDamage(160), 9.0f, 14, 28.0f));
    public static final Item FLINTLOCK = register("flintlock",
            new FlintlockItem(new Item.Settings().maxCount(1).maxDamage(80)));
    public static final Item PIRATE_BOAT = register("pirate_boat",
            new BoatItem(false, BoatEntity.Type.SPRUCE, new Item.Settings().maxCount(1)));
    public static final Item ROYAL_LONGSWORD = register("royal_longsword",
            new SwordItem(ToolMaterials.DIAMOND, new Item.Settings().maxCount(1).maxDamage(720)));
    public static final Item ROYAL_COIN = register("royal_coin",
            new Item(new Item.Settings().maxCount(64)));
    public static final Item ROYAL_JEWELRY = register("royal_jewelry",
            new Item(new Item.Settings().maxCount(16)));
    public static final Item MEDIEVAL_MAP = register("medieval_map",
            new MedievalMapItem(new Item.Settings().maxCount(1)));
    public static final Item SURVIVOR_SPAWN_EGG = register("survivor_spawn_egg",
            new SurvivorSpawnEggItem(new Item.Settings().maxCount(16)));
    public static final Item KNIGHT_SPAWN_EGG = register("knight_spawn_egg",
            new SurvivorSpawnEggItem(new Item.Settings().maxCount(16), Archetype.KNIGHT));
    public static final Item PIRATE_SPAWN_EGG = register("pirate_spawn_egg",
            new SurvivorSpawnEggItem(new Item.Settings().maxCount(16), Archetype.PIRATE));
    public static final Item OUTLAW_SPAWN_EGG = register("outlaw_spawn_egg",
            new SurvivorSpawnEggItem(new Item.Settings().maxCount(16), Archetype.OUTLAW));
    public static final Item SKY_CAPTAIN_SPAWN_EGG = register("sky_captain_spawn_egg",
            new SurvivorSpawnEggItem(new Item.Settings().maxCount(16), Archetype.SKY_CAPTAIN));
    public static final Item AIRSHIP_SPAWN_EGG = register("airship_spawn_egg",
            new AirshipSpawnEggItem(new Item.Settings().maxCount(16)));
    public static final Item MERCHANT_SHIP_SPAWN_EGG = register("merchant_ship_spawn_egg",
            new ShipSpawnEggItem(new Item.Settings().maxCount(16), ModEntities.MERCHANT_SHIP));
    public static final Item PIRATE_SHIP_SPAWN_EGG = register("pirate_ship_spawn_egg",
            new ShipSpawnEggItem(new Item.Settings().maxCount(16), ModEntities.PIRATE_SHIP));

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, RivalRealms.id(name), item);
    }

    private static Item registerBlockItem(String name, net.minecraft.block.Block block) {
        return register(name, new BlockItem(block, new Item.Settings()));
    }

    public static void register() {
        registerBlockItem("crown_brick", ModBlocks.CROWN_BRICK);
        registerBlockItem("castle_stone", ModBlocks.CASTLE_STONE);
        registerBlockItem("castle_tiles", ModBlocks.CASTLE_TILES);
        registerBlockItem("royal_wood", ModBlocks.ROYAL_WOOD);
        registerBlockItem("ship_planks", ModBlocks.SHIP_PLANKS);
        registerBlockItem("frontier_planks", ModBlocks.FRONTIER_PLANKS);
        registerBlockItem("airship_metal", ModBlocks.AIRSHIP_METAL);
        registerBlockItem("realm_banner", ModBlocks.REALM_BANNER);
        ModItemGroups.register();
        RivalRealms.LOGGER.info("Registered contracts, guns, boats, spawn eggs and settlement blocks.");
    }
}

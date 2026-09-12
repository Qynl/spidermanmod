package com.rivalrealms.item;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.entity.ModEntities;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
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
    public static final Item SURVIVOR_SPAWN_EGG = register("survivor_spawn_egg",
            new SpawnEggItem(ModEntities.SURVIVOR, 0x6f4b3e, 0xd0a75b, new Item.Settings()));

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
        registerBlockItem("ship_planks", ModBlocks.SHIP_PLANKS);
        registerBlockItem("frontier_planks", ModBlocks.FRONTIER_PLANKS);
        registerBlockItem("airship_metal", ModBlocks.AIRSHIP_METAL);
        registerBlockItem("realm_banner", ModBlocks.REALM_BANNER);
        RivalRealms.LOGGER.info("Registered contracts, guns, boats, spawn eggs and settlement blocks.");
    }
}

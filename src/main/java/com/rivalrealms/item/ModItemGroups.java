package com.rivalrealms.item;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

/**
 * One obvious creative tab for every Rival Realms item and building piece,
 * ordered the way you would actually use them: blocks, gear, vehicles, crew.
 */
public final class ModItemGroups {
    private static final RegistryKey<ItemGroup> KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP, RivalRealms.id("rival_realms"));

    public static final ItemGroup RIVAL_REALMS = Registry.register(Registries.ITEM_GROUP, KEY,
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.rivalrealms.rival_realms"))
                    .icon(() -> new ItemStack(ModBlocks.REALM_BANNER))
                    .entries((context, entries) -> {
                        // Building palettes
                        entries.add(ModBlocks.CROWN_BRICK);
                        entries.add(ModBlocks.CASTLE_STONE);
                        entries.add(ModBlocks.CASTLE_TILES);
                        entries.add(ModBlocks.ROYAL_WOOD);
                        entries.add(ModBlocks.SHIP_PLANKS);
                        entries.add(ModBlocks.FRONTIER_PLANKS);
                        entries.add(ModBlocks.AIRSHIP_METAL);
                        entries.add(ModBlocks.REALM_BANNER);
                        // Armory
                        entries.add(ModItems.ROYAL_LONGSWORD);
                        entries.add(ModItems.REVOLVER);
                        entries.add(ModItems.FLINTLOCK);
                        entries.add(ModItems.FARMER_HOE);
                        entries.add(ModItems.CANNON);
                        entries.add(ModItems.BLUNDERBUSS);
                        entries.add(ModItems.HALBERD);
                        entries.add(ModItems.WARHORN);
                        entries.add(ModItems.MARAUDER_SPAWN_EGG);
                        entries.add(ModItems.HEARTHFOLK_SPAWN_EGG);
                        entries.add(ModItems.HARDTACK);
                        entries.add(ModItems.FRONTIER_STEW);
                        entries.add(ModItems.MEAD);
                        entries.add(ModItems.CANNONBALL);
                        // Vehicles
                        entries.add(ModItems.AIRSHIP_KIT);
                        entries.add(ModItems.SHIP_IN_A_BOTTLE);
                        entries.add(ModItems.MERCHANT_SHIP_SPAWN_EGG);
                        entries.add(ModItems.PIRATE_SHIP_SPAWN_EGG);
                        entries.add(ModItems.GALLEON_SHIP_SPAWN_EGG);
                        // Trade & paperwork
                        entries.add(ModItems.ROYAL_COIN);
                        entries.add(ModItems.ROYAL_JEWELRY);
                        entries.add(ModItems.MEDIEVAL_MAP);
                        entries.add(ModItems.RECRUITMENT_CONTRACT);
                        // Crew
                        entries.add(ModItems.SURVIVOR_SPAWN_EGG);
                        entries.add(ModItems.KNIGHT_SPAWN_EGG);
                        entries.add(ModItems.PIRATE_SPAWN_EGG);
                        entries.add(ModItems.OUTLAW_SPAWN_EGG);
                        entries.add(ModItems.SKY_CAPTAIN_SPAWN_EGG);
                    })
                    .build());

    private ModItemGroups() {
    }

    public static void register() {
        RivalRealms.LOGGER.info("Registered the Rival Realms creative tab with blocks, gear, vehicles and crew.");
    }
}

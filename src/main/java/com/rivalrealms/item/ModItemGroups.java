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

/** One obvious creative tab for every Rival Realms item and building piece. */
public final class ModItemGroups {
    private static final RegistryKey<ItemGroup> KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP, RivalRealms.id("rival_realms"));

    public static final ItemGroup RIVAL_REALMS = Registry.register(Registries.ITEM_GROUP, KEY,
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.rivalrealms.rival_realms"))
                    .icon(() -> new ItemStack(ModBlocks.REALM_BANNER))
                    .entries((context, entries) -> {
                        entries.add(ModBlocks.CROWN_BRICK);
                        entries.add(ModBlocks.SHIP_PLANKS);
                        entries.add(ModBlocks.FRONTIER_PLANKS);
                        entries.add(ModBlocks.AIRSHIP_METAL);
                        entries.add(ModBlocks.REALM_BANNER);
                        entries.add(ModItems.RECRUITMENT_CONTRACT);
                        entries.add(ModItems.REVOLVER);
                        entries.add(ModItems.FLINTLOCK);
                        entries.add(ModItems.PIRATE_BOAT);
                        entries.add(ModItems.SURVIVOR_SPAWN_EGG);
                        entries.add(ModItems.AIRSHIP_SPAWN_EGG);
                    })
                    .build());

    private ModItemGroups() {
    }

    public static void register() {
        RivalRealms.LOGGER.info("Registered the Rival Realms creative tab with all blocks, gear, vehicles and spawn items.");
    }
}

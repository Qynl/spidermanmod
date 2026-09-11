package com.spiderman.mod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Items owned by this mod: a creative-mode spawn egg for the radioactive
 * spider (handy for testing the bite + transformation).
 *
 * <p>Must register after {@link ModEntities} (the egg points at its type).
 *
 * <p>PROBE-3 (diagnostic): anonymous class instead of lambda for the
 * creative-tab hook, to isolate a possible javac inference edge.
 */
public final class ModItems {
    public static SpawnEggItem RADIOACTIVE_SPIDER_SPAWN_EGG;

    private ModItems() {
    }

    public static void register() {
        RADIOACTIVE_SPIDER_SPAWN_EGG = Registry.register(Registries.ITEM,
                Identifier.of(SpiderManMod.MOD_ID, "radioactive_spider_spawn_egg"),
                new SpawnEggItem(ModEntities.RADIOACTIVE_SPIDER, 0x1A1A1A, 0x39FF14,
                        new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
                .register(new ItemGroupEvents.ModifyEntries() {
                    @Override
                    public void modifyEntries(ItemGroup.Entries entries) {
                        entries.add(ModItems.RADIOACTIVE_SPIDER_SPAWN_EGG);
                    }
                });
    }
}

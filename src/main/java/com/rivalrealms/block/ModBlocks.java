package com.rivalrealms.block;

import com.rivalrealms.RivalRealms;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block CROWN_BRICK = register("crown_brick", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.DEEPSLATE_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(4.5f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block SHIP_PLANKS = register("ship_planks", new Block(AbstractBlock.Settings.copy(Blocks.SPRUCE_PLANKS)
            .mapColor(MapColor.SPRUCE_BROWN)));
    public static final Block FRONTIER_PLANKS = register("frontier_planks", new Block(AbstractBlock.Settings.copy(Blocks.ACACIA_PLANKS)
            .mapColor(MapColor.ORANGE)));
    public static final Block AIRSHIP_METAL = register("airship_metal", new Block(AbstractBlock.Settings.copy(Blocks.CUT_COPPER)
            .mapColor(MapColor.LIGHT_BLUE).strength(5.0f).requiresTool()));
    public static final Block REALM_BANNER = register("realm_banner", new RealmBannerBlock(AbstractBlock.Settings.copy(Blocks.WHITE_WOOL)
            .mapColor(MapColor.PURPLE).strength(1.0f).sounds(BlockSoundGroup.WOOL)));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, RivalRealms.id(name), block);
    }

    public static void register() {
        RivalRealms.LOGGER.info("Registered settlement palettes and realm banner.");
    }
}

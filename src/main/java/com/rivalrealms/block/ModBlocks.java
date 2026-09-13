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
    public static final Block CASTLE_STONE = register("castle_stone", new Block(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS)
            .mapColor(MapColor.STONE_GRAY).strength(4.8f).requiresTool()));
    public static final Block CASTLE_TILES = register("castle_tiles", new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE_TILES)
            .mapColor(MapColor.DEEPSLATE_GRAY).strength(5.0f).requiresTool()));
    public static final Block ROYAL_WOOD = register("royal_wood", new Block(AbstractBlock.Settings.copy(Blocks.DARK_OAK_PLANKS)
            .mapColor(MapColor.BROWN)));
    public static final Block SHIP_PLANKS = register("ship_planks", new Block(AbstractBlock.Settings.copy(Blocks.SPRUCE_PLANKS)
            .mapColor(MapColor.SPRUCE_BROWN)));
    public static final Block FRONTIER_PLANKS = register("frontier_planks", new Block(AbstractBlock.Settings.copy(Blocks.ACACIA_PLANKS)
            .mapColor(MapColor.ORANGE)));
    public static final Block AIRSHIP_METAL = register("airship_metal", new Block(AbstractBlock.Settings.copy(Blocks.CUT_COPPER)
            .mapColor(MapColor.LIGHT_BLUE).strength(5.0f).requiresTool()));
    public static final Block GILDED_BRICK = register("gilded_brick", new Block(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS)
            .mapColor(MapColor.GOLD).strength(5.5f).requiresTool()));
    public static final Block CROWN_PILLAR = register("crown_pillar", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(5.5f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block WAR_TABLE = register("war_table", new Block(AbstractBlock.Settings.copy(Blocks.DARK_OAK_PLANKS)
            .mapColor(MapColor.BROWN).strength(2.8f)));
    public static final Block WEAPON_RACK = register("weapon_rack", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.OAK_TAN).instrument(NoteBlockInstrument.BASS)
            .strength(1.6f).sounds(BlockSoundGroup.WOOD).nonOpaque()));
    public static final Block TROPHY_SKULL = register("trophy_skull", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.PALE_YELLOW).instrument(NoteBlockInstrument.XYLOPHONE)
            .strength(1.8f).sounds(BlockSoundGroup.BONE)));
    public static final Block HEARTH_LANTERN = register("hearth_lantern", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.ORANGE).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(2.0f).sounds(BlockSoundGroup.LANTERN).luminance(state -> 15)));
    public static final Block SUPPLY_CRATE = register("supply_crate", new Block(AbstractBlock.Settings.copy(Blocks.BARREL)
            .mapColor(MapColor.SPRUCE_BROWN).strength(2.4f)));
    public static final Block ROAD_STONE = register("road_stone", new Block(AbstractBlock.Settings.copy(Blocks.COBBLESTONE)
            .mapColor(MapColor.GRAY).strength(3.2f).requiresTool()));
    public static final Block ARROW_SLIT = register("arrow_slit", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(4.5f).requiresTool().nonOpaque()));
    public static final Block NOTICE_BOARD = register("notice_board", new NoticeBoardBlock(AbstractBlock.Settings.copy(Blocks.DARK_OAK_PLANKS)
            .mapColor(MapColor.BROWN).strength(2.2f).sounds(BlockSoundGroup.WOOD)));
    public static final Block CANNON = register("cannon", new CannonBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.IRON_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
            .strength(5.5f).requiresTool().sounds(BlockSoundGroup.COPPER)));
    public static final Block REALM_BANNER = register("realm_banner", new RealmBannerBlock(AbstractBlock.Settings.copy(Blocks.WHITE_WOOL)
            .mapColor(MapColor.PURPLE).strength(1.0f).sounds(BlockSoundGroup.WOOL).nonOpaque().noCollision()));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, RivalRealms.id(name), block);
    }

    public static void register() {
        RivalRealms.LOGGER.info("Registered settlement palettes and realm banner.");
    }
}

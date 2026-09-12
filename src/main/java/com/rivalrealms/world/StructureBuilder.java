package com.rivalrealms.world;

import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.inventory.Inventory;

/**
 * Small hand-authored set pieces. They are intentionally made from normal
 * blocks so a player can walk through, raid, repair, and expand every building.
 * The command is also a deterministic test harness for the culture content.
 */
public final class StructureBuilder {
    private static final int FLAG_HEIGHT = 8;

    private StructureBuilder() {
    }

    public static void build(ServerWorld world, BlockPos origin, BuildStyle style) {
        BlockPos base = origin.add(4, 0, 4);
        switch (style) {
            case KNIGHT -> buildKnightFortress(world, base);
            case PIRATE -> buildPirateHarbor(world, base);
            case WESTERN -> buildWesternTown(world, base);
            case SKY -> buildSkyDock(world, base);
            case CUSTOM -> buildCommonExpansion(world, base, 1);
        }
        world.playSound(null, base, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.65f, 1.15f);
    }

    /**
     * Adds a new physical district whenever the settlement economy can pay for
     * it. Each level is intentionally visible in the world: farms, workshops,
     * storage yards, towers, and culture-specific landmarks appear around the
     * original set piece rather than merely changing a number in NBT.
     */
    public static void expand(ServerWorld world, BlockPos center, BuildStyle style, int level) {
        switch (style) {
            case KNIGHT -> expandKnight(world, center, level);
            case PIRATE -> expandPirate(world, center, level);
            case WESTERN -> expandWestern(world, center, level);
            case SKY -> expandSky(world, center, level);
            case CUSTOM -> buildCommonExpansion(world, center, level);
        }
        world.playSound(null, center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.45f, 1.35f);
    }

    public static void buildScattered(ServerWorld world, BlockPos center, BuildStyle style, SettlementVariant variant) {
        switch (variant) {
            case FORTRESS -> buildScatteredFortress(world, center);
            case TOWN -> buildScatteredTown(world, center, style);
            case HARBOR -> buildScatteredHarbor(world, center);
            case SKYPORT -> buildScatteredSkyport(world, center);
            case OUTPOST -> buildScatteredOutpost(world, center, style);
        }
        world.playSound(null, center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.55f, 0.9f);
    }

    private static void buildScatteredFortress(ServerWorld world, BlockPos base) {
        Block stone = Blocks.STONE_BRICKS;
        Block trim = ModBlocks.CROWN_BRICK;
        fill(world, base.add(-16, 0, -12), 33, 1, 25, trim);
        wall(world, base.add(-16, 1, -12), 33, 7, 25, stone);
        opening(world, base.add(-2, 1, -12), 5, 5);
        tower(world, base.add(-16, 1, -12), stone, trim);
        tower(world, base.add(14, 1, -12), stone, trim);
        tower(world, base.add(-16, 1, 10), stone, trim);
        tower(world, base.add(14, 1, 10), stone, trim);
        keep(world, base.add(-5, 1, -4), trim, stone);
        house(world, base.add(8, 0, -3), Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS);
        house(world, base.add(8, 0, 5), ModBlocks.FRONTIER_PLANKS, Blocks.OAK_LOG, Blocks.OAK_STAIRS);
        farmPlot(world, base.add(-13, 0, 13), 11, 7);
        fill(world, base.add(-5, 0, 4), 10, 1, 5, Blocks.POLISHED_ANDESITE);
        set(world, base.add(0, 1, -12), ModBlocks.REALM_BANNER);
        set(world, base.add(0, 1, 0), Blocks.CHEST);
        stockChest(world, base.add(0, 1, 0), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD),
                new ItemStack(Items.IRON_INGOT, 12), new ItemStack(ModItems.RECRUITMENT_CONTRACT));
    }

    private static void buildScatteredTown(ServerWorld world, BlockPos base, BuildStyle style) {
        Block road = style == BuildStyle.WESTERN ? Blocks.COARSE_DIRT : Blocks.COBBLESTONE;
        Block wood = style == BuildStyle.WESTERN ? ModBlocks.FRONTIER_PLANKS : Blocks.OAK_PLANKS;
        fill(world, base.add(-2, 0, -17), 5, 1, 35, road);
        fill(world, base.add(-17, 0, -2), 35, 1, 5, road);
        house(world, base.add(-14, 0, -13), wood, Blocks.OAK_LOG, Blocks.OAK_STAIRS);
        house(world, base.add(7, 0, -13), Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS);
        house(world, base.add(-14, 0, 7), wood, Blocks.OAK_LOG, Blocks.DARK_OAK_STAIRS);
        warehouse(world, base.add(7, 1, 7), wood, Blocks.OAK_LOG);
        saloon(world, base.add(-4, 0, -7), wood);
        well(world, base.add(-1, 0, 2));
        farmPlot(world, base.add(14, 0, -7), 9, 8);
        lampPost(world, base.add(-8, 1, -1));
        lampPost(world, base.add(8, 1, -1));
        lampPost(world, base.add(-8, 1, 7));
        lampPost(world, base.add(8, 1, 7));
        set(world, base.add(0, 1, 0), ModBlocks.REALM_BANNER);
        set(world, base.add(2, 1, 2), Blocks.CHEST);
        stockChest(world, base.add(2, 1, 2), new ItemStack(Items.BREAD, 8), new ItemStack(Items.IRON_NUGGET, 8),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
    }

    private static void buildScatteredHarbor(ServerWorld world, BlockPos base) {
        buildPirateHarbor(world, base);
        tavern(world, base.add(-18, 0, 6));
        pierCrane(world, base.add(14, 0, -5));
        storageYard(world, base.add(13, 0, 10), ModBlocks.SHIP_PLANKS);
        set(world, base.add(-15, 2, 8), Blocks.CHEST);
        stockChest(world, base.add(-15, 2, 8), new ItemStack(ModItems.FLINTLOCK),
                new ItemStack(Items.COOKED_COD, 8), new ItemStack(Items.GOLD_NUGGET, 12));
    }

    private static void buildScatteredSkyport(ServerWorld world, BlockPos base) {
        buildSkyDock(world, base);
        fill(world, base.add(-19, 8, -5), 9, 1, 9, ModBlocks.AIRSHIP_METAL);
        fill(world, base.add(11, 8, 3), 9, 1, 9, ModBlocks.AIRSHIP_METAL);
        house(world, base.add(-18, 8, -4), Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS);
        workshop(world, base.add(10, 8, 4), ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG);
        set(world, base.add(0, 10, 0), ModBlocks.REALM_BANNER);
    }

    private static void buildScatteredOutpost(ServerWorld world, BlockPos base, BuildStyle style) {
        Block wallBlock = style == BuildStyle.WESTERN ? ModBlocks.FRONTIER_PLANKS : ModBlocks.CROWN_BRICK;
        Block log = style == BuildStyle.WESTERN ? Blocks.OAK_LOG : Blocks.SPRUCE_LOG;
        fill(world, base.add(-8, 0, -8), 17, 1, 17, style == BuildStyle.WESTERN ? Blocks.COARSE_DIRT : Blocks.GRAVEL);
        tower(world, base.add(-2, 0, -2), wallBlock, log);
        house(world, base.add(6, 0, -6), wallBlock, log, Blocks.DARK_OAK_STAIRS);
        campfire(world, base.add(-6, 1, 5));
        farmPlot(world, base.add(5, 0, 5), 7, 6);
        for (int x = -8; x <= 8; x += 4) {
            set(world, base.add(x, 1, -8), Blocks.OAK_FENCE);
            set(world, base.add(x, 1, 8), Blocks.OAK_FENCE);
        }
        set(world, base.add(0, 1, 0), ModBlocks.REALM_BANNER);
        set(world, base.add(4, 1, -5), Blocks.CHEST);
        stockChest(world, base.add(4, 1, -5), new ItemStack(Items.IRON_AXE), new ItemStack(Items.BREAD, 4),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
    }

    private static void expandKnight(ServerWorld world, BlockPos base, int level) {
        switch (level) {
            case 2 -> {
                farmPlot(world, base.add(-22, 0, 10), 11, 8);
                workshop(world, base.add(13, 0, 10), ModBlocks.CROWN_BRICK, Blocks.STONE_BRICKS);
            }
            case 3 -> tower(world, base.add(-15, 1, 12), Blocks.STONE_BRICKS, ModBlocks.CROWN_BRICK);
            case 4 -> {
                wall(world, base.add(-17, 0, 12), 35, 5, 1, ModBlocks.CROWN_BRICK);
                set(world, base.add(0, 1, 12), ModBlocks.REALM_BANNER);
            }
            case 5 -> keep(world, base.add(14, 1, 12), ModBlocks.CROWN_BRICK, Blocks.STONE_BRICKS);
            default -> {
            }
        }
    }

    private static void expandPirate(ServerWorld world, BlockPos base, int level) {
        switch (level) {
            case 2 -> {
                warehouse(world, base.add(14, 1, 8), ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG);
                storageYard(world, base.add(-17, 0, 7), Blocks.SPRUCE_PLANKS);
            }
            case 3 -> {
                fill(world, base.add(-17, 0, -7), 35, 1, 4, ModBlocks.SHIP_PLANKS);
                fill(world, base.add(-15, -1, -6), 31, 1, 2, Blocks.SPRUCE_LOG);
                set(world, base.add(0, 1, -7), ModBlocks.REALM_BANNER);
            }
            case 4 -> workshop(world, base.add(14, 1, -7), ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG);
            case 5 -> flag(world, base.add(0, 4, -10), Blocks.RED_WOOL, Blocks.DARK_OAK_LOG);
            default -> {
            }
        }
    }

    private static void expandWestern(ServerWorld world, BlockPos base, int level) {
        switch (level) {
            case 2 -> farmPlot(world, base.add(-22, 0, 6), 12, 9);
            case 3 -> workshop(world, base.add(14, 0, 8), ModBlocks.FRONTIER_PLANKS, Blocks.OAK_LOG);
            case 4 -> {
                storageYard(world, base.add(-21, 0, -9), Blocks.OAK_PLANKS);
                fill(world, base.add(-20, 0, -11), 24, 1, 1, Blocks.OAK_FENCE);
            }
            case 5 -> {
                tower(world, base.add(14, 1, -12), Blocks.RED_SANDSTONE, Blocks.OAK_LOG);
                set(world, base.add(15, 4, -12), Blocks.LANTERN);
            }
            default -> {
            }
        }
    }

    private static void expandSky(ServerWorld world, BlockPos base, int level) {
        switch (level) {
            case 2 -> {
                fill(world, base.add(-16, 8, -4), 14, 1, 9, ModBlocks.AIRSHIP_METAL);
                storageYard(world, base.add(-14, 9, -2), Blocks.SPRUCE_PLANKS);
            }
            case 3 -> {
                fill(world, base.add(10, 0, -8), 1, 12, 1, Blocks.CHAIN);
                fill(world, base.add(10, 11, -8), 7, 1, 1, Blocks.SPRUCE_PLANKS);
                set(world, base.add(13, 12, -8), Blocks.LANTERN);
            }
            case 4 -> workshop(world, base.add(-6, 8, 11), ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG);
            case 5 -> flag(world, base.add(0, 17, -10), Blocks.CYAN_WOOL, ModBlocks.AIRSHIP_METAL);
            default -> {
            }
        }
    }

    private static void buildCommonExpansion(ServerWorld world, BlockPos base, int level) {
        if (level >= 2) {
            farmPlot(world, base.add(-14, 0, 10), 10, 8);
        }
        if (level >= 3) {
            workshop(world, base.add(8, 0, 8), Blocks.OAK_PLANKS, Blocks.OAK_LOG);
        }
        if (level >= 4) {
            storageYard(world, base.add(-14, 0, -8), Blocks.OAK_PLANKS);
        }
        if (level >= 5) {
            flag(world, base.add(0, 4, 0), Blocks.PURPLE_WOOL, Blocks.OAK_LOG);
        }
    }

    private static void buildKnightFortress(ServerWorld world, BlockPos base) {
        Block brick = ModBlocks.CROWN_BRICK;
        Block stone = Blocks.STONE_BRICKS;
        fill(world, base.add(-10, 0, -7), 21, 1, 15, brick);
        wall(world, base.add(-10, 1, -7), 21, 6, 15, stone);
        opening(world, base.add(0, 1, -7), 3, 4);
        tower(world, base.add(-10, 1, -7), stone, brick);
        tower(world, base.add(10, 1, -7), stone, brick);
        tower(world, base.add(-10, 1, 7), stone, brick);
        tower(world, base.add(10, 1, 7), stone, brick);
        keep(world, base.add(-4, 1, -2), brick, stone);
        set(world, base.add(0, 2, 2), Blocks.CHEST);
        stockChest(world, base.add(0, 2, 2), new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.SHIELD), new ItemStack(Items.IRON_INGOT, 8),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
        roof(world, base.add(-4, 8, -2), 9, 7, brick);
        flag(world, base.add(0, 9, 0), Blocks.BLUE_WOOL, brick);

        house(world, base.add(-17, 0, -4), Blocks.OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
        house(world, base.add(15, 0, -4), Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS);
        house(world, base.add(-16, 0, 10), ModBlocks.FRONTIER_PLANKS, Blocks.OAK_LOG, Blocks.OAK_STAIRS);
        fill(world, base.add(-2, 0, -13), 5, 1, 6, Blocks.POLISHED_ANDESITE);
        set(world, base.add(0, 1, -12), ModBlocks.REALM_BANNER);
    }

    private static void buildPirateHarbor(ServerWorld world, BlockPos base) {
        Block plank = ModBlocks.SHIP_PLANKS;
        // A real water basin makes the harbor boat rideable instead of leaving
        // a decorative boat stranded on terrain.
        fill(world, base.add(-9, 0, -8), 19, 1, 6, Blocks.WATER);
        fill(world, base.add(-12, 0, -2), 25, 1, 6, Blocks.SPRUCE_PLANKS);
        fill(world, base.add(-12, -1, 0), 25, 1, 2, Blocks.SPRUCE_LOG);
        for (int x = -10; x <= 10; x += 4) {
            fill(world, base.add(x, -2, 0), 2, 3, 2, Blocks.SPRUCE_LOG);
        }
        fill(world, base.add(-5, 1, -1), 11, 1, 4, plank);
        fill(world, base.add(-5, 2, -1), 1, 5, 4, Blocks.SPRUCE_LOG);
        fill(world, base.add(5, 2, -1), 1, 5, 4, Blocks.SPRUCE_LOG);
        fill(world, base.add(0, 3, -1), 1, 11, 1, Blocks.DARK_OAK_LOG);
        fill(world, base.add(-4, 5, -1), 9, 5, 1, Blocks.WHITE_WOOL);
        fill(world, base.add(-3, 5, 0), 7, 5, 1, Blocks.RED_WOOL);
        warehouse(world, base.add(-11, 1, 5), plank, Blocks.DARK_OAK_LOG);
        warehouse(world, base.add(7, 1, 5), plank, Blocks.SPRUCE_LOG);
        set(world, base.add(-8, 2, 7), Blocks.CHEST);
        stockChest(world, base.add(-8, 2, 7), new ItemStack(ModItems.FLINTLOCK),
                new ItemStack(Items.IRON_NUGGET, 12), new ItemStack(Items.COOKED_COD, 4),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
        set(world, base.add(0, 1, -2), ModBlocks.REALM_BANNER);
        spawnWorkingBoat(world, base.add(0, 0, -5));
    }

    private static void buildWesternTown(ServerWorld world, BlockPos base) {
        Block wood = ModBlocks.FRONTIER_PLANKS;
        fill(world, base.add(-15, 0, -1), 31, 1, 5, Blocks.SAND);
        fill(world, base.add(-2, 0, -13), 5, 1, 27, Blocks.COARSE_DIRT);
        saloon(world, base.add(-12, 0, -7), wood);
        saloon(world, base.add(7, 0, -7), Blocks.OAK_PLANKS);
        sheriffOffice(world, base.add(-12, 0, 6), wood);
        stockChest(world, base.add(-11, 1, 10), new ItemStack(ModItems.REVOLVER),
                new ItemStack(Items.GOLD_NUGGET, 12), new ItemStack(Items.BREAD, 4),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
        mine(world, base.add(7, 0, 6));
        for (int x = -14; x <= 14; x += 4) {
            set(world, base.add(x, 1, 3), Blocks.OAK_FENCE);
            set(world, base.add(x, 2, 3), Blocks.LANTERN);
        }
        set(world, base.add(0, 1, -2), ModBlocks.REALM_BANNER);
        set(world, base.add(13, 1, -10), Blocks.CACTUS);
        set(world, base.add(-16, 1, 9), Blocks.CACTUS);
    }

    private static void buildSkyDock(ServerWorld world, BlockPos base) {
        Block metal = ModBlocks.AIRSHIP_METAL;
        Block wood = Blocks.SPRUCE_PLANKS;
        fill(world, base.add(-9, 8, -9), 19, 1, 19, metal);
        fill(world, base.add(-7, 9, -7), 15, 1, 15, wood);
        for (int x = -8; x <= 8; x += 4) {
            fill(world, base.add(x, 0, -8), 1, 9, 1, Blocks.CHAIN);
            fill(world, base.add(x, 0, 8), 1, 9, 1, Blocks.CHAIN);
        }
        fill(world, base.add(-4, 10, -3), 9, 2, 6, metal);
        fill(world, base.add(-3, 12, -2), 7, 2, 4, Blocks.CYAN_WOOL);
        fill(world, base.add(-2, 14, -1), 5, 2, 2, Blocks.WHITE_WOOL);
        fill(world, base.add(0, 10, 0), 1, 8, 1, Blocks.DARK_OAK_LOG);
        fill(world, base.add(-5, 15, 0), 11, 1, 1, Blocks.WHITE_WOOL);
        set(world, base.add(0, 10, 1), ModBlocks.REALM_BANNER);
        set(world, base.add(-5, 10, -5), Blocks.CHEST);
        stockChest(world, base.add(-5, 10, -5), new ItemStack(Items.CROSSBOW),
                new ItemStack(Items.FIREWORK_ROCKET, 8), new ItemStack(Items.GOLD_INGOT, 4),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
        for (int x = -6; x <= 6; x += 3) {
            set(world, base.add(x, 10, -7), Blocks.LANTERN);
        }
        spawnAirship(world, base.add(0, 17, 0));
    }

    private static void well(ServerWorld world, BlockPos base) {
        fill(world, base, 5, 1, 5, Blocks.STONE_BRICKS);
        fill(world, base.add(1, 1, 1), 3, 1, 3, Blocks.WATER);
        for (int x : new int[]{0, 4}) {
            for (int z : new int[]{0, 4}) {
                fill(world, base.add(x, 1, z), 1, 3, 1, Blocks.STONE_BRICKS);
            }
        }
        fill(world, base.add(0, 4, 0), 5, 1, 1, Blocks.SPRUCE_SLAB);
        fill(world, base.add(0, 4, 4), 5, 1, 1, Blocks.SPRUCE_SLAB);
    }

    private static void lampPost(ServerWorld world, BlockPos base) {
        fill(world, base, 1, 3, 1, Blocks.OAK_FENCE);
        set(world, base.add(0, 3, 0), Blocks.LANTERN);
    }

    private static void tavern(ServerWorld world, BlockPos base) {
        saloon(world, base, ModBlocks.SHIP_PLANKS);
        set(world, base.add(3, 1, 2), Blocks.BARREL);
        set(world, base.add(4, 1, 2), Blocks.BARREL);
        set(world, base.add(3, 1, 4), Blocks.CHEST);
        stockChest(world, base.add(3, 1, 4), new ItemStack(Items.COOKED_COD, 8),
                new ItemStack(Items.GOLD_NUGGET, 8));
    }

    private static void pierCrane(ServerWorld world, BlockPos base) {
        fill(world, base, 1, 8, 1, Blocks.DARK_OAK_LOG);
        fill(world, base.add(0, 7, 0), 7, 1, 1, Blocks.DARK_OAK_LOG);
        fill(world, base.add(5, 0, 0), 1, 7, 1, Blocks.CHAIN);
        set(world, base.add(5, 0, 0), Blocks.BARREL);
        set(world, base.add(0, 7, 0), Blocks.LANTERN);
    }

    private static void campfire(ServerWorld world, BlockPos base) {
        set(world, base, Blocks.CAMPFIRE);
        set(world, base.add(1, 0, 0), Blocks.OAK_LOG);
        set(world, base.add(-1, 0, 0), Blocks.OAK_LOG);
    }

    private static void farmPlot(ServerWorld world, BlockPos base, int width, int depth) {
        fill(world, base, width, 1, depth, Blocks.FARMLAND);
        for (int x = 1; x < width - 1; x += 2) {
            for (int z = 1; z < depth - 1; z += 2) {
                set(world, base.add(x, 1, z), Blocks.WHEAT);
            }
        }
        for (int z = 0; z < depth; z++) {
            set(world, base.add(width / 2, 1, z), Blocks.WATER);
        }
        set(world, base.add(width / 2 + 2, 1, depth / 2), Blocks.COMPOSTER);
    }

    private static void workshop(ServerWorld world, BlockPos base, Block wall, Block log) {
        house(world, base, wall, log, Blocks.DARK_OAK_STAIRS);
        set(world, base.add(2, 1, 2), Blocks.SMITHING_TABLE);
        set(world, base.add(4, 1, 2), Blocks.BLAST_FURNACE);
        set(world, base.add(3, 1, 4), Blocks.CRAFTING_TABLE);
    }

    private static void storageYard(ServerWorld world, BlockPos base, Block floor) {
        fill(world, base, 7, 1, 5, floor);
        for (int x = 1; x < 6; x += 2) {
            set(world, base.add(x, 1, 1), Blocks.BARREL);
            set(world, base.add(x, 1, 3), Blocks.CHEST);
        }
        set(world, base.add(3, 1, 0), Blocks.LANTERN);
    }

    private static void tower(ServerWorld world, BlockPos base, Block wall, Block trim) {
        fill(world, base, 3, 9, 3, wall);
        fill(world, base.add(1, 1, 1), 1, 7, 1, Blocks.AIR);
        for (int y = 9; y <= 10; y++) {
            fill(world, base.add(-1, y, -1), 5, 1, 5, trim);
        }
    }

    private static void keep(ServerWorld world, BlockPos base, Block wall, Block trim) {
        fill(world, base, 9, 7, 7, wall);
        fill(world, base.add(1, 1, 1), 7, 5, 5, Blocks.AIR);
        fill(world, base.add(2, 1, 1), 5, 1, 5, trim);
        set(world, base.add(4, 1, 0), Blocks.LANTERN);
        set(world, base.add(4, 1, 6), Blocks.LANTERN);
    }

    private static void house(ServerWorld world, BlockPos base, Block wall, Block log, Block roof) {
        fill(world, base, 7, 1, 6, wall);
        wall(world, base, 7, 4, 6, log);
        fill(world, base.add(1, 1, 1), 5, 3, 4, Blocks.AIR);
        roof(world, base.add(0, 5, 0), 7, 6, roof);
        set(world, base.add(3, 2, 0), Blocks.LANTERN);
    }

    private static void warehouse(ServerWorld world, BlockPos base, Block wall, Block log) {
        fill(world, base, 7, 1, 5, wall);
        wall(world, base, 7, 4, 5, log);
        fill(world, base.add(1, 1, 1), 5, 3, 3, Blocks.AIR);
        roof(world, base.add(0, 5, 0), 7, 5, Blocks.DARK_OAK_PLANKS);
        set(world, base.add(3, 1, 0), Blocks.BARREL);
        set(world, base.add(4, 1, 0), Blocks.BARREL);
    }

    private static void saloon(ServerWorld world, BlockPos base, Block wall) {
        fill(world, base, 7, 1, 6, wall);
        wall(world, base, 7, 4, 6, Blocks.OAK_LOG);
        fill(world, base.add(1, 1, 1), 5, 3, 4, Blocks.AIR);
        roof(world, base.add(0, 5, 0), 7, 6, Blocks.RED_SANDSTONE);
        fill(world, base.add(2, 1, 2), 3, 1, 1, Blocks.SPRUCE_PLANKS);
        set(world, base.add(3, 4, 0), Blocks.LANTERN);
    }

    private static void sheriffOffice(ServerWorld world, BlockPos base, Block wall) {
        saloon(world, base, wall);
        set(world, base.add(3, 2, 0), Blocks.IRON_BARS);
        set(world, base.add(1, 1, 4), Blocks.CHEST);
    }

    private static void mine(ServerWorld world, BlockPos base) {
        fill(world, base, 7, 1, 6, Blocks.RED_SANDSTONE);
        wall(world, base, 7, 4, 6, Blocks.STRIPPED_OAK_LOG);
        fill(world, base.add(1, 1, 1), 5, 3, 4, Blocks.AIR);
        roof(world, base.add(0, 5, 0), 7, 6, Blocks.DARK_OAK_PLANKS);
        fill(world, base.add(2, 1, 0), 3, 3, 1, Blocks.BLACKSTONE);
        set(world, base.add(3, 1, 1), Blocks.RAIL);
    }

    private static void roof(ServerWorld world, BlockPos base, int width, int depth, Block block) {
        for (int y = 0; y < 3; y++) {
            int inset = y;
            fill(world, base.add(inset, y, inset), width - inset * 2, 1, depth - inset * 2, block);
        }
    }

    private static void flag(ServerWorld world, BlockPos base, Block flagBlock, Block pole) {
        fill(world, base, 1, FLAG_HEIGHT, 1, pole);
        fill(world, base.add(1, FLAG_HEIGHT - 2, 0), 4, 2, 1, flagBlock);
    }

    private static void wall(ServerWorld world, BlockPos base, int width, int height, int depth, Block block) {
        fill(world, base, width, height, 1, block);
        fill(world, base.add(0, 0, depth - 1), width, height, 1, block);
        fill(world, base, 1, height, depth, block);
        fill(world, base.add(width - 1, 0, 0), 1, height, depth, block);
    }

    private static void opening(ServerWorld world, BlockPos base, int width, int height) {
        fill(world, base, width, height, 1, Blocks.AIR);
    }

    private static void fill(ServerWorld world, BlockPos start, int width, int height, int depth, Block block) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    set(world, start.add(x, y, z), block);
                }
            }
        }
    }

    private static void stockChest(ServerWorld world, BlockPos pos, ItemStack... stacks) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof Inventory inventory)) {
            return;
        }
        for (int slot = 0; slot < stacks.length && slot < inventory.size(); slot++) {
            inventory.setStack(slot, stacks[slot].copy());
        }
        inventory.markDirty();
    }

    private static void set(ServerWorld world, BlockPos pos, Block block) {
        // Commands can be used at world-height extremes. Never hand an invalid
        // Y coordinate to the chunk/section code while drawing a structure.
        if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) {
            return;
        }
        world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static void spawnWorkingBoat(ServerWorld world, BlockPos pos) {
        BoatEntity boat = EntityType.BOAT.create(world);
        if (boat != null) {
            boat.setVariant(BoatEntity.Type.SPRUCE);
            boat.refreshPositionAndAngles(pos, 0.0f, 0.0f);
            world.spawnEntity(boat);
        }
    }

    private static void spawnAirship(ServerWorld world, BlockPos pos) {
        var airship = ModEntities.AIRSHIP.create(world);
        if (airship != null) {
            airship.refreshPositionAndAngles(pos, 0.0f, 0.0f);
            world.spawnEntity(airship);
        }
    }
}

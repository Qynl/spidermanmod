package com.rivalrealms.world;

import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.entity.ModEntities;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

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
        }
        world.playSound(null, base, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.65f, 1.15f);
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
        for (int x = -6; x <= 6; x += 3) {
            set(world, base.add(x, 10, -7), Blocks.LANTERN);
        }
        spawnAirship(world, base.add(0, 17, 0));
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

    private static void set(ServerWorld world, BlockPos pos, Block block) {
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

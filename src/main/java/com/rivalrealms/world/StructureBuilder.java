package com.rivalrealms.world;

import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * The handcrafted architecture behind every Rival Realms settlement. Nothing
 * here is a floating label: these are walkable, raidable, expandable builds
 * with round towers, crenellated curtain walls, furnished interiors, working
 * farms, piers and airship moorings.
 *
 * <p>Every build is terrain-adaptive: it samples the surface, stamps a packed
 * foundation under each column, and clears stray vegetation inside its
 * footprint, so fortresses sit correctly on slopes instead of floating.</p>
 */
public final class StructureBuilder {
    private static final int MAX_FOUNDATION_DEPTH = 14;

    private StructureBuilder() {
    }

    // ------------------------------------------------------------------ API

    public static void build(ServerWorld world, BlockPos origin, BuildStyle style) {
        BlockPos base = origin.add(4, 0, 4);
        switch (style) {
            case KNIGHT -> buildKnightFortress(world, base);
            case PIRATE -> buildPirateHarbor(world, base);
            case WESTERN -> buildWesternTown(world, base);
            case SKY -> buildSkyDock(world, base);
            case MARAUDER -> buildMarauderCamp(world, base);
            case HEARTHFOLK -> buildHearthHamlet(world, base);
            case CUSTOM -> buildCommonExpansion(world, base, 1);
        }
        world.playSound(null, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.65f, 1.15f);
    }

    /**
     * Adds a new physical district whenever the settlement economy can pay for
     * it: farms, workshops, storage yards, towers and culture landmarks appear
     * around the original set piece rather than merely changing a number.
     */
    public static void expand(ServerWorld world, BlockPos center, BuildStyle style, int level) {
        switch (style) {
            case KNIGHT -> expandKnight(world, center, level);
            case PIRATE -> expandPirate(world, center, level);
            case WESTERN -> expandWestern(world, center, level);
            case SKY -> expandSky(world, center, level);
            case MARAUDER, HEARTHFOLK -> buildCommonExpansion(world, center, level);
            case CUSTOM -> buildCommonExpansion(world, center, level);
        }
        world.playSound(null, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.45f, 1.35f);
    }

    public static void buildScattered(ServerWorld world, BlockPos center, BuildStyle style, SettlementVariant variant) {
        switch (variant) {
            case FORTRESS -> buildScatteredFortress(world, center);
            case CITADEL -> buildCitadel(world, center);
            case TOWN -> buildScatteredTown(world, center, style);
            case ROYAL_CITY -> buildRoyalCity(world, center);
            case HARBOR -> buildScatteredHarbor(world, center);
            case SHIPYARD -> buildShipyard(world, center);
            case SKYPORT -> buildScatteredSkyport(world, center);
            case AIRSHIP_YARD -> buildAirshipYard(world, center);
            case OUTPOST -> buildScatteredOutpost(world, center, style);
            case MILL -> buildScatteredMill(world, center);
            case RUIN -> buildScatteredRuin(world, center);
            case GRAVEYARD -> buildScatteredGraveyard(world, center);
            case FARMSTEAD -> buildScatteredFarmstead(world, center);
            case TEMPLE -> buildScatteredTemple(world, center);
            case HERMITAGE -> buildScatteredHermitage(world, center);
            case ANOMALY -> buildScatteredAnomaly(world, center);
        }
        world.playSound(null, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.55f, 0.9f);
    }

    // ------------------------------------------------------- variant builds

    private static void buildScatteredFortress(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 43, 37, ModBlocks.CASTLE_TILES);
        Block stone = ModBlocks.CASTLE_STONE;
        Block trim = ModBlocks.CROWN_BRICK;

        // Curtain wall with crenellated walk and a real gatehouse.
        curtainWall(world, base.west(16).north(12), 33, 25, 7, stone, trim, Direction.SOUTH);
        roundTower(world, base.add(-16, 0, -12), 3, 10, stone, trim, true);
        roundTower(world, base.add(16, 0, -12), 3, 10, stone, trim, true);
        roundTower(world, base.add(-16, 0, 12), 3, 11, stone, trim, true);
        roundTower(world, base.add(16, 0, 12), 3, 11, stone, trim, true);
        gatehouse(world, base.add(0, 0, -12), 5, 8, stone, trim);
        brazier(world, base.add(-4, 0, -11));
        brazier(world, base.add(4, 0, -11));
        set(world, base.add(-16, 0, -12), ModBlocks.REALM_BANNER);
        set(world, base.add(16, 0, -12), ModBlocks.REALM_BANNER);

        // Great keep with a furnished hall and a throne.
        keep(world, base.add(-5, 0, -4), 11, 8, 10, stone, trim);
        house(world, base.add(8, 0, -3), 6, 7, ModBlocks.ROYAL_WOOD, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, ModBlocks.CASTLE_TILES);
        farmPlot(world, base.add(-8, 0, 8), 6, 5);
        farmhouse(world, base.add(-14, 0, 8));
        scarecrow(world, base.add(-8, 0, 15));
        fill(world, base.add(-2, y, -11), 5, 1, 8, ModBlocks.ROAD_STONE);
        set(world, base.add(-4, y + 1, -11), ModBlocks.HEARTH_LANTERN);
        set(world, base.add(4, y + 1, -11), ModBlocks.HEARTH_LANTERN);
        set(world, base.add(7, y + 1, -3), ModBlocks.WEAPON_RACK);
        set(world, base.add(7, y + 1, -2), ModBlocks.WEAPON_RACK);
        set(world, base.add(0, y + 1, -6), ModBlocks.REALM_BANNER);
        stockChest(world, base.add(-3, y + 1, -1), new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.SHIELD), new ItemStack(Items.IRON_INGOT, 12),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT), new ItemStack(ModItems.ROYAL_COIN, 3));
        // A cobbled way leads from the gate to the keep door; the courtyard
        // well keeps a siege from becoming a thirst.
        fill(world, base.add(-1, y, -11), 3, 1, 9, ModBlocks.ROAD_STONE);
        well(world, base.add(8, 0, 7));
        // The soldiers' garden and a message post by the gate.
        gardenPatch(world, base.add(11, 0, -8));
        brazier(world, base.add(-2, 0, 4));
        set(world, base.add(-8, y + 1, -9), ModBlocks.WAR_TABLE);
        lightYard(world, base, y, 14);
        spawnGuard(world, base, y, BuildStyle.KNIGHT);
    }

    private static void buildScatteredTown(ServerWorld world, BlockPos base, BuildStyle style) {
        boolean western = style == BuildStyle.WESTERN;
        Block road = western ? Blocks.COARSE_DIRT : Blocks.COBBLESTONE;
        Block wood = western ? ModBlocks.FRONTIER_PLANKS : Blocks.OAK_PLANKS;
        Block log = Blocks.OAK_LOG;
        int y = plateau(world, base, 35, 35, road);

        // Crossroads with a market heart.
        fill(world, base.add(-2, y, -17), 5, 1, 35, road);
        fill(world, base.add(-17, y, -2), 35, 1, 5, road);
        if (!western) {
            // The old world paves its market heart in dressed stone.
            fill(world, base.add(-4, y, -4), 9, 1, 9, ModBlocks.ROAD_STONE);
        }
        house(world, base.add(-15, 0, -14), 7, 8, wood, log,
                western ? Blocks.ACACIA_STAIRS : Blocks.OAK_STAIRS, wood);
        house(world, base.add(8, 0, -14), 8, 7, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, wood);
        house(world, base.add(-15, 0, 8), 7, 8, wood, log,
                western ? Blocks.ACACIA_STAIRS : Blocks.DARK_OAK_STAIRS, wood);
        warehouse(world, base.add(8, 0, 8), 8, 9, wood, log);
        saloon(world, base.add(-5, 0, -8), 6, 9, wood, log);
        well(world, base.add(-1, 0, 2));
        fountain(world, base.add(1, 0, 3));
        brazier(world, base.add(4, 0, -1));
        brazier(world, base.add(4, 0, 7));
        marketStall(world, base.add(6, 0, 1), western ? Blocks.ORANGE_WOOL : Blocks.RED_WOOL, log);
        marketStall(world, base.add(6, 0, 5), western ? Blocks.LIME_WOOL : Blocks.BLUE_WOOL, log);
        farmPlot(world, base.add(-17, 0, -17), 9, 7);
        farmhouse(world, base.add(-19, 0, -6));
        scarecrow(world, base.add(-13, 0, -14));
        lampPost(world, base.add(-8, 0, -1));
        lampPost(world, base.add(8, 0, -1));
        set(world, base.add(7, y + 1, -1), ModBlocks.NOTICE_BOARD);
        set(world, base.add(-7, y + 1, 4), ModBlocks.NOTICE_BOARD);
        lampPost(world, base.add(-8, 0, 7));
        lampPost(world, base.add(8, 0, 7));
        set(world, base.add(9, y + 1, 6), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(10, y + 1, 6), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(-5, y + 1, 3), ModBlocks.HEARTH_LANTERN);
        set(world, base.add(6, y + 1, -3), ModBlocks.HEARTH_LANTERN);
        set(world, base.add(0, y + 1, -2), ModBlocks.REALM_BANNER);
        stockChest(world, base.add(2, y + 1, 2), new ItemStack(Items.BREAD, 8),
                new ItemStack(Items.IRON_NUGGET, 8), new ItemStack(ModItems.RECRUITMENT_CONTRACT),
                new ItemStack(ModItems.ROYAL_COIN, 2));
        // The richer quarter: a two-storey townhouse and a chapel.
        townhouse(world, base.add(9, 0, -7), 7, 7);
        chapel(world, base.add(-16, 0, 10));
        gardenPatch(world, base.add(9, 0, 2));
        gardenPatch(world, base.add(-16, 0, -8));
        spawnGuard(world, base, y, style);
    }

    private static void buildScatteredHarbor(ServerWorld world, BlockPos base) {
        buildPirateHarbor(world, base);
        int y = groundAt(world, base.getX(), base.getZ());
        tavern(world, base.add(-18, 0, 6));
        farmhouse(world, base.add(-19, 0, -6));
        vegPlot(world, base.add(-13, 0, -8), 6, 5);
        pier(world, base.add(10, 0, -2), 12, Direction.EAST);
        pierCrane(world, base.add(14, 0, -5));
        storageYard(world, base.add(13, 0, 10), ModBlocks.SHIP_PLANKS);
        stockChest(world, base.add(-15, y + 2, 8), new ItemStack(ModItems.FLINTLOCK),
                new ItemStack(Items.COOKED_COD, 8), new ItemStack(Items.GOLD_NUGGET, 12),
                new ItemStack(ModItems.CANNONBALL, 2));
        mooredBoat(world, base.add(16, 0, -6));
        // A hulk aground at the harbor mouth: someone's luck ran out.
        hulk(world, base.add(-14, 0, -14));
        set(world, base.add(-6, groundAt(world, base.getX() - 6, base.getZ() + 14) + 1, 14),
                Blocks.SPRUCE_SLAB);
        set(world, base.add(-6, groundAt(world, base.getX() - 6, base.getZ() + 14) + 2, 14),
                Blocks.SPRUCE_LOG);
        set(world, base.add(11, groundAt(world, base.getX() + 11, base.getZ() + 13) + 1, 13),
                Blocks.BARREL);
        set(world, base.add(11, groundAt(world, base.getX() + 11, base.getZ() + 13) + 2, 13),
                Blocks.BARREL);
    }

    private static void buildScatteredSkyport(ServerWorld world, BlockPos base) {
        // A proper ground-hugging port: one dirt terrace with a workshop row,
        // a farm strip, and a single mooring apron. Only the ships own the sky.
        int y = plateau(world, base, 33, 29, Blocks.COARSE_DIRT);
        fill(world, base.add(-6, y, -7), 13, 1, 9, ModBlocks.AIRSHIP_METAL);
        house(world, base.add(-16, 0, -4), 6, 6, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, ModBlocks.AIRSHIP_METAL);
        house(world, base.add(11, 0, -6), 6, 6, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
        workshop(world, base.add(10, 0, 4), 6, 7, ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG);
        farmhouse(world, base.add(-16, 0, 8));
        vegPlot(world, base.add(-6, 0, 10), 7, 5);
        scarecrow(world, base.add(2, 0, 12));
        set(world, base.add(0, y + 1, 3), ModBlocks.REALM_BANNER);
        stockChest(world, base.add(-3, y + 1, -5), new ItemStack(ModItems.AIRSHIP_KIT),
                new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.COPPER_INGOT, 3));
        // Freight waiting for the next lift, and a windsock on a pole.
        set(world, base.add(-4, y + 1, -6), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(-4, y + 2, -6), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(-3, y + 1, -6), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(4, y + 1, -6), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(4, y + 1, -5), Blocks.BARREL);
        set(world, base.add(5, y + 1, 2), Blocks.OAK_FENCE);
        set(world, base.add(5, y + 2, 2), Blocks.OAK_FENCE);
        set(world, base.add(5, y + 3, 2), Blocks.WHITE_WOOL);
        lampPost(world, base.add(-4, 0, 3));
        lampPost(world, base.add(4, 0, 3));
        mooredAirship(world, base.add(0, 0, -2));
        spawnGuard(world, base, y, BuildStyle.SKY);
    }

    private static void buildScatteredOutpost(ServerWorld world, BlockPos base, BuildStyle style) {
        boolean western = style == BuildStyle.WESTERN;
        Block wallBlock = western ? ModBlocks.FRONTIER_PLANKS : ModBlocks.CROWN_BRICK;
        Block log = western ? Blocks.OAK_LOG : Blocks.SPRUCE_LOG;
        int y = plateau(world, base, 23, 27, western ? Blocks.COARSE_DIRT : Blocks.GRAVEL);

        // Palisade ring with a watch tower in one corner.
        palisade(world, base.add(-8, 0, -8), 17, 17, wallBlock, log);
        roundTower(world, base.add(-6, 0, -6), 2, 9, wallBlock, log, true);
        house(world, base.add(4, 0, -6), 5, 6, wallBlock, log, Blocks.DARK_OAK_STAIRS, wallBlock);
        campfire(world, base.add(-5, 0, 4));
        // A supply lean-to and a spiked barricade facing the road.
        fill(world, base.add(5, y + 2, -3), 4, 1, 1, Blocks.WHITE_WOOL);
        set(world, base.add(5, y + 1, -2), Blocks.OAK_FENCE);
        set(world, base.add(8, y + 1, -2), Blocks.OAK_FENCE);
        set(world, base.add(6, y + 1, -4), Blocks.BARREL);
        farmPlot(world, base.add(3, 0, 3), 6, 5);
        farmhouse(world, base.add(-7, 0, 9));
        scarecrow(world, base.add(7, 0, 5));
        set(world, base.add(0, y + 1, 0), ModBlocks.REALM_BANNER);
        set(world, base.add(-1, y + 1, 0), ModBlocks.WAR_TABLE);
        set(world, base.add(-3, y + 1, 0), ModBlocks.WEAPON_RACK);
        set(world, base.add(-1, y + 1, 2), ModBlocks.SUPPLY_CRATE);
        stockChest(world, base.add(4, y + 1, -4), new ItemStack(Items.IRON_AXE),
                new ItemStack(Items.BREAD, 4), new ItemStack(ModItems.RECRUITMENT_CONTRACT),
                new ItemStack(ModItems.CANNONBALL, 1));
        spawnGuard(world, base, y, style);
    }

    /** A stone windmill with canvas sails over a wheat terrace. */
    private static void buildScatteredMill(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 29, 29, Blocks.GRASS_BLOCK);

        roundTower(world, base.add(-3, 0, -3), 3, 11, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, true);
        // Pinwheel of fence arms and canvas panels on the tower's south face.
        BlockPos hub = base.add(0, y + 10, 4);
        set(world, hub, Blocks.OAK_FENCE);
        for (int i = 1; i <= 4; i++) {
            set(world, hub.add(i, 0, 0), Blocks.OAK_FENCE);
            set(world, hub.add(-i, 0, 0), Blocks.OAK_FENCE);
            set(world, hub.add(0, i, 0), Blocks.OAK_FENCE);
            set(world, hub.add(0, -i, 0), Blocks.OAK_FENCE);
            if (i <= 3) {
                set(world, hub.add(i, 1, 0), Blocks.WHITE_WOOL);
                set(world, hub.add(-i, -1, 0), Blocks.WHITE_WOOL);
            }
        }

        house(world, base.add(7, 0, -4), 6, 7, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
        farmPlot(world, base.add(-11, 0, 4), 9, 8);
        farmPlot(world, base.add(4, 0, 8), 8, 6);
        vegPlot(world, base.add(-13, 0, -6), 7, 5);
        farmhouse(world, base.add(-4, 0, -8));
        scarecrow(world, base.add(-6, 0, 6));
        campfire(world, base.add(3, 0, 2));
        // A loaded hay cart and the watering trough beside it.
        int cy = groundAt(world, base.getX() + 5, base.getZ() - 3);
        set(world, base.add(5, cy + 1, -3), Blocks.HAY_BLOCK);
        set(world, base.add(6, cy + 1, -3), Blocks.HAY_BLOCK);
        set(world, base.add(5, cy + 1, -4), Blocks.OAK_FENCE);
        set(world, base.add(6, cy + 1, -4), Blocks.OAK_FENCE);
        set(world, base.add(8, cy + 1, -3), Blocks.STONE_BRICK_SLAB);
        set(world, base.add(8, cy, -3), Blocks.WATER);
        stockChest(world, base.add(8, y + 2, -2), new ItemStack(Items.WHEAT, 12),
                new ItemStack(Items.BREAD, 6), new ItemStack(ModItems.ROYAL_COIN, 1));
        spawnGuard(world, base, y, BuildStyle.KNIGHT);
    }

    /** A shattered watchtower camp claimed by squatters — rubble, cobwebs, firelight. */
    private static void buildScatteredRuin(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 21, 21, Blocks.COARSE_DIRT);

        roundTower(world, base.add(-4, 0, -4), 3, 6, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, false);
        // Shell the ring: breach the walls like the tower lost an old siege.
        for (int i = 0; i < 30; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            int x = base.getX() - 4 + (int) Math.round(Math.cos(angle) * 3.0);
            int z = base.getZ() - 4 + (int) Math.round(Math.sin(angle) * 3.0);
            clearColumn(world, x, z, y + 1 + world.random.nextInt(3), y + 6);
        }
        // Rubble drifts and a squatter camp under a wool lean-to.
        for (int i = 0; i < 14; i++) {
            int x = base.getX() + world.random.nextInt(19) - 9;
            int z = base.getZ() + world.random.nextInt(19) - 9;
            set(world, base.add(x - base.getX(), y + 1, z - base.getZ()),
                    world.random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
        }
        // The old colonnade, toppled and half swallowed by the moss.
        for (int i = 0; i < 4; i++) {
            set(world, base.add(-2 + i, y + 1, 6), Blocks.COBBLESTONE_WALL);
            set(world, base.add(-2 + i, y + 1, 7), i < 2 ? Blocks.OAK_WOOD
                    : Blocks.MOSS_CARPET);
        }
        for (int i = 0; i < 3; i++) {
            set(world, base.add(6, y + 1, -6 + i), Blocks.MOSS_CARPET);
        }
        fill(world, base.add(4, y + 1, 5), 4, 2, 1, Blocks.WHITE_WOOL);
        set(world, base.add(4, y + 1, 4), Blocks.OAK_FENCE);
        set(world, base.add(7, y + 1, 4), Blocks.OAK_FENCE);
        campfire(world, base.add(2, 0, 2));
        set(world, base.add(-8, y + 2, 8), Blocks.COBWEB);
        set(world, base.add(8, y + 1, -8), Blocks.COBWEB);
        set(world, base.add(0, y + 1, -3), ModBlocks.CROWN_PILLAR);
        set(world, base.add(1, y + 1, -3), ModBlocks.GILDED_BRICK);
        set(world, base.add(-2, y + 1, 4), ModBlocks.TROPHY_SKULL);
        stockChest(world, base.add(5, y + 2, 6), new ItemStack(Items.IRON_NUGGET, 5),
                new ItemStack(Items.BREAD, 2), new ItemStack(ModItems.RECRUITMENT_CONTRACT));
        spawnGuard(world, base, y, BuildStyle.CUSTOM);
    }

    /** A quiet memorial ground: broken ring wall, headstones, and one keeper. */
    private static void buildScatteredGraveyard(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 19, 19, Blocks.PODZOL);

        // Low ring wall, weathered open at the corners.
        wall(world, base.add(-8, y, -8), 17, 2, 1, Blocks.COBBLESTONE);
        wall(world, base.add(-8, y, 8), 17, 2, 1, Blocks.COBBLESTONE);
        wall(world, base.add(-8, y, -8), 1, 2, 17, Blocks.COBBLESTONE);
        wall(world, base.add(8, y, -8), 1, 2, 17, Blocks.COBBLESTONE);
        clearColumn(world, base.getX() - 8, base.getZ() - 8, y + 1, y + 2);
        clearColumn(world, base.getX() + 8, base.getZ() + 8, y + 1, y + 2);

        // Two rows of graves with varied markers.
        for (int gx = -5; gx <= 4; gx += 3) {
            for (int gz = -4; gz <= 2; gz += 6) {
                BlockPos grave = base.add(gx, y + 1, gz);
                set(world, grave, Blocks.COBBLESTONE);
                set(world, grave.up(), world.random.nextInt(3) == 0 ? Blocks.OAK_FENCE
                        : world.random.nextBoolean() ? Blocks.COBBLESTONE_SLAB : Blocks.STONE_BRICK_SLAB);
            }
        }
        for (int i = 0; i < 5; i++) {
            set(world, base.add(world.random.nextInt(15) - 7, y + 1, world.random.nextInt(15) - 7),
                    Blocks.DEAD_BUSH);
        }
        // The keeper's crypt: stone, a barred window, a slatted roof.
        for (int h = 0; h <= 3; h++) {
            for (int x = 3; x <= 6; x++) {
                for (int z = 3; z <= 5; z++) {
                    boolean edge = x == 3 || x == 6 || z == 3 || z == 5 || h == 0;
                    set(world, base.add(x, y + h, z), h == 3 || !edge
                            ? (h == 3 ? Blocks.STONE_BRICK_SLAB
                            : Blocks.AIR) : Blocks.COBBLESTONE);
                }
            }
        }
        set(world, base.add(4, y + 1, 5), Blocks.AIR);
        set(world, base.add(4, y + 2, 5), Blocks.AIR);
        set(world, base.add(4, y + 2, 3), Blocks.GLASS_PANE);
        set(world, base.add(5, y + 1, 4), Blocks.SOUL_LANTERN);
        set(world, base.add(0, y + 1, -6), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 2, -6), Blocks.SOUL_LANTERN);
        stockChest(world, base.add(6, y + 1, 6), new ItemStack(Items.IRON_NUGGET, 8),
                new ItemStack(ModItems.ROYAL_COIN, 2), new ItemStack(Items.GOLD_NUGGET, 6));
        spawnGuard(world, base, y, BuildStyle.CUSTOM);
    }

    private static void buildCitadel(ServerWorld world, BlockPos base) {
        // The outer curtain stands OUTSIDE the fortress pad, so claim the
        // full ring first - walls and corner towers share one honest floor.
        int y = plateau(world, base, 51, 45, ModBlocks.CASTLE_TILES);
        buildScatteredFortress(world, base);
        Block stone = ModBlocks.CASTLE_STONE;
        Block trim = ModBlocks.CROWN_BRICK;

        // Second, taller curtain with a barbican bridge.
        wall(world, base.add(-22, y, -18), 45, 6, 1, trim);
        wall(world, base.add(-22, y, 18), 45, 6, 1, trim);
        wall(world, base.add(-22, y, -18), 1, 6, 37, trim);
        wall(world, base.add(22, y, -18), 1, 6, 37, trim);
        crenellate(world, base.add(-22, y + 6, -18), 45, 1, trim);
        crenellate(world, base.add(-22, y + 6, 18), 45, 1, trim);
        // A gilded course crowns the second curtain.
        fill(world, base.add(-22, y + 5, -18), 45, 1, 1, ModBlocks.GILDED_BRICK);
        fill(world, base.add(-22, y + 5, 18), 45, 1, 1, ModBlocks.GILDED_BRICK);
        fill(world, base.add(-22, y + 5, -18), 1, 1, 37, ModBlocks.GILDED_BRICK);
        fill(world, base.add(22, y + 5, -18), 1, 1, 37, ModBlocks.GILDED_BRICK);
        set(world, base.add(6, y + 1, 12), ModBlocks.WAR_TABLE);
        set(world, base.add(7, y + 1, 12), ModBlocks.WEAPON_RACK);
        roundTower(world, base.add(-22, 0, -18), 3, 13, stone, trim, true);
        roundTower(world, base.add(22, 0, -18), 3, 13, stone, trim, true);
        roundTower(world, base.add(-22, 0, 18), 3, 13, stone, trim, true);
        roundTower(world, base.add(22, 0, 18), 3, 13, stone, trim, true);
        gatehouse(world, base.add(0, 0, -18), 7, 10, trim, stone);
        stable(world, base.add(15, 0, 2), 6, 7);
        bridge(world, base.add(-7, y - 1, -22), 15, Blocks.POLISHED_ANDESITE);
        set(world, base.add(0, y + 1, -19), ModBlocks.REALM_BANNER);
    }

    private static void buildRoyalCity(ServerWorld world, BlockPos base) {
        // The capital sprawls past the town grid: park first, then build.
        plateau(world, base, 45, 45, Blocks.GRASS_BLOCK);
        buildScatteredTown(world, base, BuildStyle.KNIGHT);
        palace(world, base.add(-6, 0, 10), 13, 9, 12);
        marketStall(world, base.add(-16, 0, 1), Blocks.RED_WOOL, Blocks.OAK_LOG);
        marketStall(world, base.add(10, 0, 1), Blocks.BLUE_WOOL, Blocks.OAK_LOG);
        marketStall(world, base.add(-16, 0, 5), Blocks.YELLOW_WOOL, Blocks.OAK_LOG);
        marketStall(world, base.add(10, 0, 5), Blocks.WHITE_WOOL, Blocks.OAK_LOG);
        stable(world, base.add(14, 0, -16), 6, 7);
        brazier(world, base.add(-6, 0, 9));
        brazier(world, base.add(6, 0, 9));
        chapel(world, base.add(14, 0, 8));
        lampPost(world, base.add(-16, 0, -4));
        lampPost(world, base.add(16, 0, -4));
        set(world, base.add(0, groundAt(world, base.getX(), base.getZ()) + 1, 12), ModBlocks.REALM_BANNER);
    }

    private static void buildShipyard(ServerWorld world, BlockPos base) {
        buildScatteredHarbor(world, base);
        int y = groundAt(world, base.getX(), base.getZ());
        pierCrane(world, base.add(-14, 0, -5));
        pier(world, base.add(-24, 0, -2), 12, Direction.WEST);
        warehouse(world, base.add(-16, 0, 9), 9, 8, ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG);
        warehouse(world, base.add(9, 0, 9), 8, 8, ModBlocks.SHIP_PLANKS, Blocks.SPRUCE_LOG);
        fill(world, base.add(-16, y, -14), 33, 1, 3, ModBlocks.SHIP_PLANKS);
        set(world, base.add(0, y + 1, -13), ModBlocks.REALM_BANNER);
        mooredBoat(world, base.add(-18, 0, -8));
        mooredBoat(world, base.add(20, 0, -8));
    }

    private static void buildAirshipYard(ServerWorld world, BlockPos base) {
        // The shipwright's yard: two grounded hangars flanking a service
        // apron and one mast. No floating slabs - the ships do the flying.
        int y = plateau(world, base, 37, 33, Blocks.COARSE_DIRT);
        hangar(world, base.add(-15, 0, -12), 8, 10);
        hangar(world, base.add(7, 0, -12), 8, 10);
        fill(world, base.add(-15, y, 1), 31, 1, 5, ModBlocks.AIRSHIP_METAL);
        set(world, base.add(0, y + 1, 3), ModBlocks.REALM_BANNER);
        workshop(world, base.add(-15, 0, 9), 7, 6, ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG);
        farmhouse(world, base.add(9, 0, 9));
        lampPost(world, base.add(-5, 0, 3));
        lampPost(world, base.add(5, 0, 3));
        mooredAirship(world, base.add(0, 0, 3));
        spawnGuard(world, base, y, BuildStyle.SKY);
    }

    // ------------------------------------------------------- culture builds

    private static void buildKnightFortress(ServerWorld world, BlockPos base) {
        buildScatteredFortress(world, base);
    }

    private static void buildPirateHarbor(ServerWorld world, BlockPos base) {
        // Terraced sand-and-plank harbor town - wide enough for its tavern,
        // farmstead and storage yard to stand on the pad, not the slope.
        int y = plateau(world, base, 41, 35, ModBlocks.SHIP_PLANKS);
        fill(world, base.add(-2, y, -10), 5, 1, 21, Blocks.COARSE_DIRT);
        tavern(world, base.add(-10, 0, -8));
        warehouse(world, base.add(6, 0, -8), 7, 8, ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG);
        house(world, base.add(-10, 0, 3), 6, 7, ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG,
                Blocks.SPRUCE_STAIRS, ModBlocks.SHIP_PLANKS);
        pier(world, base.add(-2, 0, 10), 14, Direction.SOUTH);
        pier(world, base.add(6, 0, 10), 10, Direction.SOUTH);
        pierCrane(world, base.add(-2, 0, 8));
        mastFlag(world, base.add(2, 0, 4), Blocks.BLACK_WOOL);
        set(world, base.add(0, y + 1, -4), ModBlocks.REALM_BANNER);
        stockChest(world, base.add(-4, y + 1, 2), new ItemStack(ModItems.FLINTLOCK),
                new ItemStack(Items.GOLD_NUGGET, 6), new ItemStack(ModItems.CANNONBALL, 3));
        lampPost(world, base.add(-4, 0, 8));
        lampPost(world, base.add(4, 0, 8));
        mooredBoat(world, base.add(-4, 0, 14));
        mooredBoat(world, base.add(8, 0, 14));
        spawnGuard(world, base, y, BuildStyle.PIRATE);
    }

    private static void buildWesternTown(ServerWorld world, BlockPos base) {
        buildScatteredTown(world, base, BuildStyle.WESTERN);
    }

    private static void buildSkyDock(ServerWorld world, BlockPos base) {
        // One grounded dock: a dirt terrace, a metal landing apron and the
        // mast. Nothing floats; the sky belongs to the ships, not the town.
        int y = plateau(world, base, 25, 21, Blocks.COARSE_DIRT);
        fill(world, base.add(-6, y, -6), 12, 1, 8, ModBlocks.AIRSHIP_METAL);
        roundTower(world, base.add(-8, 0, 3), 3, 10, ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG, false);
        workshop(world, base.add(5, 0, -6), 7, 7, ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG);
        house(world, base.add(6, 0, 3), 6, 6, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, ModBlocks.AIRSHIP_METAL);
        set(world, base.add(0, y + 1, 4), ModBlocks.REALM_BANNER);
        stockChest(world, base.add(3, y + 1, 1), new ItemStack(ModItems.AIRSHIP_KIT),
                new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.COPPER_INGOT, 4));
        lampPost(world, base.add(-4, 0, 3));
        lampPost(world, base.add(4, 0, 3));
        mooredAirship(world, base.add(0, 0, -2));
        spawnGuard(world, base, y, BuildStyle.SKY);
    }

    private static void buildCommonExpansion(ServerWorld world, BlockPos base, int level) {
        int y = plateau(world, base, 17, 17, Blocks.COBBLESTONE);
        house(world, base.add(-6, 0, -6), 7, 8, Blocks.OAK_PLANKS, Blocks.OAK_LOG,
                Blocks.OAK_STAIRS, Blocks.COBBLESTONE);
        farmPlot(world, base.add(3, 0, 3), 7, 6);
        well(world, base.add(-1, 0, 1));
        set(world, base.add(2, y + 1, -2), ModBlocks.REALM_BANNER);
        stockChest(world, base.add(-4, y + 1, 2), new ItemStack(Items.BREAD, 4),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
        spawnGuard(world, base, y, BuildStyle.CUSTOM);
    }

    // ------------------------------------------------------------- expands

    private static void expandKnight(ServerWorld world, BlockPos base, int level) {
        switch (level) {
            case 2 -> {
                plateau(world, base.add(-24, 0, 8), 14, 11, Blocks.GRASS_BLOCK);
                farmPlot(world, base.add(-22, 0, 10), 10, 7);
                workshop(world, base.add(13, 0, 10), 7, 7, ModBlocks.CROWN_BRICK, Blocks.STONE_BRICKS);
            }
            case 3 -> roundTower(world, base.add(-15, 0, 12), 3, 12, Blocks.STONE_BRICKS, ModBlocks.CROWN_BRICK, true);
            case 4 -> {
                int y = plateau(world, base.add(0, 0, 13), 37, 5, Blocks.GRASS_BLOCK);
                wall(world, base.add(-17, y, 12), 35, 5, 1, ModBlocks.CROWN_BRICK);
                crenellate(world, base.add(-17, y + 5, 12), 35, 1, ModBlocks.CROWN_BRICK);
                set(world, base.add(0, y + 1, 12), ModBlocks.REALM_BANNER);
            }
            case 5 -> keep(world, base.add(12, 0, 12), 10, 8, 9, ModBlocks.CROWN_BRICK, Blocks.STONE_BRICKS);
            default -> {
            }
        }
    }

    private static void expandPirate(ServerWorld world, BlockPos base, int level) {
        switch (level) {
            case 2 -> {
                warehouse(world, base.add(12, 0, 8), 8, 7, ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG);
                storageYard(world, base.add(-17, 0, 7), Blocks.SPRUCE_PLANKS);
            }
            case 3 -> {
                int y = groundAt(world, base.getX(), base.getZ());
                pier(world, base.add(-8, 0, 10), 12, Direction.SOUTH);
                mastFlag(world, base.add(0, 0, 10), Blocks.RED_WOOL);
            }
            case 4 -> workshop(world, base.add(12, 0, -8), 7, 6, ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG);
            case 5 -> mastFlag(world, base.add(0, 0, -10), Blocks.BLACK_WOOL);
            default -> {
            }
        }
    }

    private static void expandWestern(ServerWorld world, BlockPos base, int level) {
        switch (level) {
            case 2 -> {
                plateau(world, base.add(-24, 0, 4), 15, 12, Blocks.COARSE_DIRT);
                farmPlot(world, base.add(-22, 0, 6), 11, 8);
            }
            case 3 -> workshop(world, base.add(13, 0, 8), 7, 6, ModBlocks.FRONTIER_PLANKS, Blocks.OAK_LOG);
            case 4 -> {
                int y = plateau(world, base.add(-21, 0, -10), 13, 11, Blocks.COARSE_DIRT);
                storageYard(world, base.add(-20, 0, -9), Blocks.OAK_PLANKS);
                fill(world, base.add(-20, y, -11), 24, 1, 1, Blocks.OAK_FENCE);
            }
            case 5 -> {
                roundTower(world, base.add(13, 0, -12), 2, 10, Blocks.RED_SANDSTONE, Blocks.OAK_LOG, true);
                lampPost(world, base.add(15, 0, -12));
            }
            default -> {
            }
        }
    }

    private static void expandSky(ServerWorld world, BlockPos base, int level) {
        // Growth stays grounded: sheds, hangars and workshops on the dirt,
        // never platforms on stilts.
        switch (level) {
            case 2 -> {
                storageYard(world, base.add(8, 0, -8), Blocks.SPRUCE_PLANKS);
                lampPost(world, base.add(7, 0, -4));
            }
            case 3 -> hangar(world, base.add(-12, 0, -10), 8, 9);
            case 4 -> workshop(world, base.add(8, 0, 6), 7, 6, ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG);
            case 5 -> mooredAirship(world, base.add(-4, 0, -10));
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ terracing

    /** Flattens and packs a build site; returns the working surface height. */
    private static int plateau(ServerWorld world, BlockPos center, int sizeX, int sizeZ, Block surface) {
        int x0 = center.getX() - sizeX / 2;
        int z0 = center.getZ() - sizeZ / 2;
        // Floor at the LOWEST column in the footprint: every structure on the
        // pad then shares one honest floor and nothing can hang in the air.
        // Slopes become earthen embankments instead of floating edges.
        int y = Integer.MAX_VALUE;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                y = Math.min(y, groundAt(world, x0 + x, z0 + z));
            }
        }
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int px = x0 + x;
                int pz = z0 + z;
                int columnTop = groundAt(world, px, pz);
                if (columnTop > y) {
                    clearColumn(world, px, pz, y + 1, columnTop);
                }
                set(world, new BlockPos(px, y, pz), surface);
                foundation(world, px, pz, y - 1, surface);
                clearColumn(world, px, pz, y + 1, y + 14);
            }
        }
        return y;
    }

    /** Lowest ground under a rectangle: how shells avoid straddling slopes. */
    private static int lowestCorner(ServerWorld world, int x, int z, int sizeX, int sizeZ) {
        return Math.min(Math.min(groundAt(world, x, z),
                groundAt(world, x + sizeX - 1, z)),
                Math.min(groundAt(world, x, z + sizeZ - 1),
                        groundAt(world, x + sizeX - 1, z + sizeZ - 1)));
    }

    private static int groundAt(ServerWorld world, int x, int z) {
        BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(x, world.getBottomY(), z));
        int y = top.getY() - 1;
        int bottom = world.getBottomY() + 1;
        int ceiling = world.getTopY() - 1;
        return Math.max(bottom, Math.min(ceiling, y));
    }

    /** Stamps packed fill downward until solid ground (or the depth limit). */
    private static void foundation(ServerWorld world, int x, int z, int startY, Block fill) {
        int bottom = Math.max(world.getBottomY() + 1, startY - MAX_FOUNDATION_DEPTH);
        for (int y = startY; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            boolean soft = state.isAir() || state.isReplaceable() || !world.getFluidState(pos).isEmpty();
            if (!soft) {
                break;
            }
            set(world, pos, fill);
        }
    }

    private static void clearColumn(ServerWorld world, int x, int z, int fromY, int toY) {
        int ceiling = world.getTopY() - 1;
        for (int y = Math.max(0, fromY); y <= Math.min(ceiling, toY); y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.isReplaceable() || state.getBlock() == Blocks.WATER) {
                set(world, pos, Blocks.AIR);
            }
        }
    }

    // -------------------------------------------------------- architecture

    /** Circular stone tower with arrow slits, a furnished floor and a conical roof. */
    private static void roundTower(ServerWorld world, BlockPos base, int radius, int height,
                                   Block wall, Block trim, boolean roofed) {
        int y = lowestCorner(world, base.getX() - radius - 1, base.getZ() - radius - 1,
                2 * radius + 3, 2 * radius + 3);
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        packUnder(world, origin.add(-radius - 1, 0, -radius - 1), 2 * radius + 3,
                2 * radius + 3, y, wall);
        float radiusF = radius + 0.5f;

        for (int h = 0; h <= height; h++) {
            for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    BlockPos pos = origin.add(dx, h, dz);
                    if (dist <= radiusF && dist > radiusF - 1.15) {
                        boolean merlon = h == height && (dx + dz) % 2 == 0 && !roofed;
                        set(world, pos, merlon ? trim : wall);
                        if (h == 0) {
                            foundation(world, pos.getX(), pos.getZ(), y - 1, wall);
                        }
                    } else if (h == 0 && dist <= radiusF - 1.15) {
                        set(world, pos, ModBlocks.CASTLE_TILES);
                        foundation(world, pos.getX(), pos.getZ(), y - 1, wall);
                    } else if (h > 0 && h < height && dist <= radiusF - 1.15) {
                        set(world, pos, Blocks.AIR);
                    }
                }
            }
            // Arrow slits on the cardinal faces at mid height.
            if (h == height / 2 || h == height / 2 + 1) {
                set(world, origin.add(0, h, radius), Blocks.AIR);
                set(world, origin.add(0, h, -radius), Blocks.AIR);
                set(world, origin.add(radius, h, 0), Blocks.AIR);
                set(world, origin.add(-radius, h, 0), Blocks.AIR);
            }
        }

        if (roofed) {
            // Conical roof from stair rings.
            for (int ring = radius; ring >= 0; ring--) {
                int h = height + 1 + (radius - ring);
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist <= ring + 0.5 && dist > ring - 0.6) {
                            BlockState stair = Blocks.SPRUCE_STAIRS.getDefaultState()
                                    .with(HorizontalFacingBlock.FACING, facingFor(dx, dz));
                            set(world, origin.add(dx, h, dz), stair);
                        }
                    }
                }
            }
            set(world, origin.add(0, height + radius + 2, 0), Blocks.SPRUCE_SLAB);
        }

        // Tower interior: lantern, bedroll corner, stocked crate.
        int inner = height - 1;
        set(world, origin.add(0, inner, 0), Blocks.LANTERN);
        set(world, origin.add(1, inner, 0), Blocks.BARREL);
        stockChest(world, origin.add(-1, inner, 1), new ItemStack(Items.ARROW, 8),
                new ItemStack(Items.BREAD, 3), new ItemStack(ModItems.ROYAL_COIN, 1));
        if (height >= 8) {
            // Crenellated platform access ladder.
            for (int h = 1; h <= inner; h++) {
                set(world, origin.add(radius - 1, h, radius - 1), Blocks.LADDER);
            }
        }
    }

    private static Direction facingFor(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Rectangular curtain wall with walk height, used as the fortress ring. */
    private static void curtainWall(ServerWorld world, BlockPos corner, int sizeX, int sizeZ,
                                    int height, Block wall, Block trim, Direction gateSide) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        for (int x = 0; x < sizeX; x++) {
            wallColumn(world, corner.add(x, 0, 0), y, height, wall, trim);
            wallColumn(world, corner.add(x, 0, sizeZ - 1), y, height, wall, trim);
        }
        for (int z = 0; z < sizeZ; z++) {
            wallColumn(world, corner.add(0, 0, z), y, height, wall, trim);
            wallColumn(world, corner.add(sizeX - 1, 0, z), y, height, wall, trim);
        }
        crenellate(world, corner.add(0, y + height, 0), sizeX, 1, trim);
        crenellate(world, corner.add(0, y + height, sizeZ - 1), sizeX, 1, trim);
        for (int z = 0; z < sizeZ; z++) {
            crenellate(world, corner.add(0, y + height, z), 1, 1, trim);
            crenellate(world, corner.add(sizeX - 1, y + height, z), 1, 1, trim);
        }
        // Gate opening (south edge center) with a fence-gate portcullis.
        if (gateSide == Direction.SOUTH) {
            int gateX = sizeX / 2;
            for (int h = 1; h <= 3; h++) {
                set(world, corner.add(gateX, y + h, sizeZ - 1), Blocks.AIR);
            }
            set(world, corner.add(gateX, y + 1, sizeZ - 1),
                    Blocks.OAK_FENCE_GATE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        }
    }

    private static void wallColumn(ServerWorld world, BlockPos pos, int y, int height, Block wall, Block trim) {
        for (int h = 0; h <= height; h++) {
            set(world, pos.add(0, y + h, 0), wall);
        }
        // Packed foundation per column keeps the wall grounded on slopes.
        foundation(world, pos.getX(), pos.getZ(), y - 1, wall);
    }

    /** Twin-tower gatehouse with an arched entry and a banner. */
    private static void gatehouse(ServerWorld world, BlockPos center, int width, int height, Block wall, Block trim) {
        int y = groundAt(world, center.getX(), center.getZ());
        BlockPos origin = new BlockPos(center.getX(), y, center.getZ());
        int half = width / 2;
        for (int h = 0; h <= height; h++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean towerLeg = Math.abs(dx) >= half - 1;
                    boolean arch = !towerLeg && h >= 1 && h <= 3 && dz == 0;
                    BlockPos pos = origin.add(dx, h, dz);
                    if (arch) {
                        set(world, pos, Blocks.AIR);
                    } else if (towerLeg) {
                        set(world, pos, h == height ? trim : wall);
                    } else if (h == 0 || h == 4 || h == height) {
                        set(world, pos, wall);
                    } else {
                        set(world, pos, Blocks.AIR);
                    }
                }
            }
        }
        set(world, origin.add(-half, height + 1, 0), trim);
        set(world, origin.add(half, height + 1, 0), trim);
        set(world, origin.add(0, height + 1, 0), ModBlocks.REALM_BANNER);
        set(world, origin.add(-half + 1, height + 1, 0), Blocks.LANTERN);
        set(world, origin.add(half - 1, height + 1, 0), Blocks.LANTERN);
    }

    /** Rectangular great hall: pillars, throne, glass windows, chandelier, stairs. */
    /** A hidden community outside every faction: mossy cottage, warm lantern, a hermit. */
    private static void buildScatteredHermitage(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 9, 9, Blocks.MOSS_BLOCK);
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(world, origin.add(x, 1, z), shell ? Blocks.MOSSY_STONE_BRICKS : Blocks.AIR);
                if (shell) {
                    foundation(world, origin.getX() + x, origin.getZ() + z, y, Blocks.MOSSY_STONE_BRICKS);
                }
                set(world, origin.add(x, 0, z), Blocks.MOSSY_STONE_BRICKS);
            }
        }
        set(world, origin.add(0, 1, 2), Blocks.AIR);
        set(world, origin.add(0, 2, 2), Blocks.AIR);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                set(world, origin.add(x, 3, z), Blocks.MOSS_BLOCK);
                set(world, origin.add(x, 4, z), Math.abs(x) < 2 && Math.abs(z) < 2 ? Blocks.MOSS_CARPET : Blocks.AIR);
            }
        }
        set(world, origin.add(-1, 1, -1), Blocks.CRAFTING_TABLE);
        set(world, origin.add(1, 1, -1), ModBlocks.HEARTH_LANTERN);
        stockChest(world, origin.add(0, 2, -1), new ItemStack(Items.GOLDEN_CARROT, 3),
                new ItemStack(ModItems.ROYAL_JEWELRY, 1), new ItemStack(Items.EMERALD, 5));
        set(world, origin.add(3, 1, 0), ModBlocks.HEARTH_LANTERN);
        set(world, origin.add(-3, 1, 2), Blocks.BARREL);
        set(world, origin.add(4, 1, 4), Blocks.COBBLESTONE_SLAB);
        set(world, origin.add(4, 1, 5), Blocks.OAK_FENCE);
    }

    /** A place where the rules of the world bent: craters, strange groves, haunted stones. */
    private static void buildScatteredAnomaly(ServerWorld world, BlockPos base) {
        int kind = (int) (Math.floorMod(base.asLong(), 31L) % 3L);
        int y = plateau(world, base, 15, 15, Blocks.GRASS_BLOCK);
        if (kind == 0) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    BlockPos at = base.add(dx, y, dz);
                    if (dist <= 1.5) {
                        set(world, at, Blocks.CRYING_OBSIDIAN);
                    } else if (dist <= 3.5) {
                        set(world, at, Blocks.MAGMA_BLOCK);
                    } else if (dist <= 5.2) {
                        set(world, at, Blocks.COBBLESTONE);
                    }
                }
            }
            set(world, base.add(0, y + 1, 3), ModBlocks.TROPHY_SKULL);
            set(world, base.add(3, y + 1, 0), ModBlocks.TROPHY_SKULL);
        } else if (kind == 1) {
            for (int ring = 0; ring < 2; ring++) {
                int r = 3 + ring * 2;
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12.0;
                    int x = (int) Math.round(Math.cos(angle) * r);
                    int z = (int) Math.round(Math.sin(angle) * r);
                    set(world, base.add(x, y + 1, z), ring == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
                }
            }
            set(world, base.add(0, y + 1, 0), Blocks.AZALEA);
            fill(world, base.add(-2, y + 1, -2), 5, 1, 1, Blocks.MOSS_CARPET);
            set(world, base.add(0, y + 2, 0), Blocks.GLOW_LICHEN);
        } else {
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8.0;
                int x = (int) Math.round(Math.cos(angle) * 4);
                int z = (int) Math.round(Math.sin(angle) * 4);
                set(world, base.add(x, y + 1, z), Blocks.COBBLESTONE_WALL);
                set(world, base.add(x, y + 2, z), world.random.nextBoolean() ? Blocks.COBBLESTONE_WALL : Blocks.AIR);
            }
            set(world, base.add(0, y + 1, 0), Blocks.SOUL_LANTERN);
            set(world, base.add(2, y + 1, 1), Blocks.COBWEB);
            set(world, base.add(-2, y + 1, -1), Blocks.COBWEB);
            set(world, base.add(1, y + 1, -2), ModBlocks.TROPHY_SKULL);
        }
        stockChest(world, base.add(-3, y + 2, 3), new ItemStack(Items.AMETHYST_SHARD, 4),
                new ItemStack(ModItems.ROYAL_COIN, 2));
        spawnGuard(world, base, y, BuildStyle.CUSTOM);
    }

    private static void keep(ServerWorld world, BlockPos corner, int sizeX, int height, int sizeZ,
                             Block wall, Block trim) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, sizeX, sizeZ, y, wall);
        for (int h = 0; h <= height; h++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    boolean shell = x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1;
                    BlockPos pos = origin.add(x, h, z);
                    if (!shell) {
                        set(world, pos, h == 0 ? ModBlocks.CASTLE_TILES : Blocks.AIR);
                        continue;
                    }
                    if (h == 0) {
                        set(world, pos, ModBlocks.CASTLE_TILES);
                        foundation(world, pos.getX(), pos.getZ(), y - 1, wall);
                    } else if (h == height) {
                        set(world, pos, trim);
                    } else {
                        boolean pillar = (x == 2 || x == sizeX - 3) && (z == 2 || z == sizeZ - 3) && (x + z) % 2 == 0;
                        set(world, pos, pillar ? trim : wall);
                    }
                }
            }
            // Windows on the south face.
            if (h >= 3 && h <= 4) {
                for (int x = 2; x < sizeX - 2; x += 3) {
                    set(world, origin.add(x, h, sizeZ - 1), Blocks.GLASS_PANE);
                    set(world, origin.add(x, h, 0), h == 3 ? ModBlocks.ARROW_SLIT : Blocks.GLASS_PANE);
                }
            }
            // Door gap on the south face.
            if (h <= 2) {
                set(world, origin.add(sizeX / 2, h, sizeZ - 1), Blocks.AIR);
            }
        }
        // Crenellated parapet.
        crenellate(world, origin.add(0, height + 1, 0), sizeX, 1, trim);
        crenellate(world, origin.add(0, height + 1, sizeZ - 1), sizeX, 1, trim);

        // Furnish: throne dais, long table, chandelier, armory corner.
        int floor = y + 1;
        set(world, origin.add(sizeX / 2, floor, 1), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        set(world, origin.add(sizeX / 2, floor + 1, 0), ModBlocks.REALM_BANNER);
        for (int x = 2; x < sizeX - 2; x += 2) {
            set(world, origin.add(x, floor, sizeZ / 2), Blocks.SPRUCE_SLAB);
            set(world, origin.add(x, floor + 1, sizeZ / 2), x % 4 == 0 ? Blocks.CANDLE : Blocks.AIR);
        }
        set(world, origin.add(sizeX / 2, floor + 4, sizeZ / 2), Blocks.LANTERN);
        set(world, origin.add(sizeX / 2, floor + 3, sizeZ / 2), Blocks.CHAIN);
        set(world, origin.add(1, floor, sizeZ / 2 - 1), Blocks.ANVIL);
        set(world, origin.add(1, floor, sizeZ / 2 + 1), Blocks.BARREL);
        set(world, origin.add(2, floor, sizeZ / 2 - 2), ModBlocks.WAR_TABLE);
        set(world, origin.add(1, floor, sizeZ / 2 - 3), ModBlocks.WEAPON_RACK);
        set(world, origin.add(3, floor, sizeZ / 2 - 3), ModBlocks.WEAPON_RACK);
        stockChest(world, origin.add(sizeX - 2, floor, sizeZ / 2), new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.SHIELD), new ItemStack(Items.GOLD_INGOT, 2),
                new ItemStack(ModItems.ROYAL_LONGSWORD));
        set(world, origin.add(sizeX / 2 + 1, floor, 1), Blocks.WHITE_CARPET);
        set(world, origin.add(sizeX / 2 - 1, floor, 1), Blocks.WHITE_CARPET);
    }

    /** Small cottage with a pitched roof, glass windows and a furnished interior. */
    private static void house(ServerWorld world, BlockPos corner, int sizeX, int sizeZ,
                              Block wall, Block log, Block stair, Block roofAccent) {
        // Pad on the LOWEST corner so a slope never leaves the floor agape;
        // foundations close every gap beneath.
        int y = Math.min(Math.min(groundAt(world, corner.getX(), corner.getZ()),
                groundAt(world, corner.getX() + sizeX - 1, corner.getZ())),
                Math.min(groundAt(world, corner.getX(), corner.getZ() + sizeZ - 1),
                        groundAt(world, corner.getX() + sizeX - 1, corner.getZ() + sizeZ - 1)));
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        int wallHeight = 4;
        // Solid ground first: every footprint column packed flush down to
        // real terrain, so no wall ever stands on air or a see-through gap.
        packUnder(world, origin, sizeX, sizeZ, y, log);
        // Timber-framed cottage: stone footing course, log corner posts and
        // studs, plank infill, framed windows on every face.
        for (int h = 0; h <= wallHeight; h++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    boolean edge = x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1;
                    BlockPos pos = origin.add(x, h, z);
                    if (h == 0) {
                        set(world, pos, roofAccent);
                        continue;
                    }
                    if (!edge) {
                        set(world, pos, Blocks.AIR);
                        continue;
                    }
                    boolean cornerPost = (x == 0 || x == sizeX - 1) && (z == 0 || z == sizeZ - 1);
                    boolean stud = ((z == 0 || z == sizeZ - 1) && x % 3 == 0)
                            || ((x == 0 || x == sizeX - 1) && z % 3 == 0);
                    boolean windowCol = ((x == 0 || x == sizeX - 1) ? z == sizeZ / 2
                            : x == sizeX / 2) && !(z == sizeZ - 1 && x == sizeX / 2);
                    boolean doorway = z == sizeZ - 1 && x == sizeX / 2;
                    if (doorway && (h == 1 || h == 2)) {
                        set(world, pos, Blocks.AIR);
                    } else if (h == wallHeight || cornerPost || stud) {
                        set(world, pos, log);
                    } else if (h == 3 && windowCol) {
                        set(world, pos, log);
                    } else if (h == 2 && windowCol) {
                        set(world, pos, Blocks.GLASS_PANE);
                    } else {
                        set(world, pos, wall);
                    }
                }
            }
        }
        // A proper entry: doorstep, posts and a shingled hood over the door.
        set(world, origin.add(sizeX / 2, 0, sizeZ), stair);
        set(world, origin.add(sizeX / 2, 1, sizeZ + 1), Blocks.OAK_FENCE);
        set(world, origin.add(sizeX / 2, 2, sizeZ + 1), Blocks.OAK_FENCE);
        set(world, origin.add(sizeX / 2, 3, sizeZ + 1), stair);
        set(world, origin.add(sizeX / 2, 3, sizeZ - 1), log);

        // Pitched roof: one clean gable along the long axis - stair rows
        // climbing both long walls to a flat ridge cap.
        int ridge = (sizeZ - 1) / 2;
        for (int r = 0; r < ridge; r++) {
            int h = wallHeight + 1 + r;
            for (int x = -1; x <= sizeX; x++) {
                set(world, origin.add(x, h, r), stair);
                set(world, origin.add(x, h, sizeZ - 1 - r), stair);
            }
        }
        int cap = sizeZ - 2 * ridge;
        if (cap > 0) {
            int h = wallHeight + ridge;
            for (int x = -1; x <= sizeX; x++) {
                for (int z = ridge; z <= sizeZ - 1 - ridge; z++) {
                    set(world, origin.add(x, h, z), roofAccent);
                }
            }
        }
        // Closed gable ends: no see-through triangles under the roof.
        for (int r = 1; r < ridge; r++) {
            int h = wallHeight + 1 + r;
            for (int z = r + 1; z <= sizeZ - 2 - r; z++) {
                set(world, origin.add(0, h, z), roofAccent);
                set(world, origin.add(sizeX - 1, h, z), roofAccent);
            }
        }
        // A brick chimney climbs the gable wall past the roofline.
        for (int h = 1; h <= wallHeight + ridge + 2; h++) {
            set(world, origin.add(0, h, 2), Blocks.BRICKS);
        }
        set(world, origin.add(sizeX / 2, wallHeight + ridge + 1, sizeZ / 2), Blocks.LANTERN);

        // Furnish the inside.
        int floor = y + 1;
        set(world, origin.add(1, floor, 1), Blocks.CRAFTING_TABLE);
        set(world, origin.add(1, 0, 2), Blocks.COBBLESTONE);
        set(world, origin.add(1, floor, 2), Blocks.CAMPFIRE);
        set(world, origin.add(2, floor, 2), Blocks.BARREL);
        set(world, origin.add(sizeX - 2, floor, 1), Blocks.BOOKSHELF);
        set(world, origin.add(sizeX / 2, floor + 2, sizeZ / 2), Blocks.LANTERN);
        set(world, origin.add(1, floor, sizeZ - 2), Blocks.WHITE_CARPET);
        set(world, origin.add(sizeX - 1, floor, 0), Blocks.OAK_WOOD);
        set(world, origin.add(sizeX - 1, floor + 1, 0), Blocks.OAK_WOOD);
        stockChest(world, origin.add(sizeX - 2, floor, sizeZ - 2), new ItemStack(Items.BREAD, 3),
                new ItemStack(Items.STICK, 4), new ItemStack(ModItems.ROYAL_COIN, 1));
    }

    private static void palace(ServerWorld world, BlockPos corner, int sizeX, int height, int sizeZ) {
        keep(world, corner, sizeX, height, sizeZ, ModBlocks.CASTLE_STONE, ModBlocks.CROWN_BRICK);
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        // Front colonnade + gold-accented entry.
        for (int x = 1; x < sizeX - 1; x += 3) {
            for (int h = 1; h <= 4; h++) {
                set(world, corner.add(x, y + h, sizeZ), Blocks.POLISHED_ANDESITE);
            }
            set(world, corner.add(x, y + 5, sizeZ), ModBlocks.CROWN_BRICK);
        }
        set(world, corner.add(sizeX / 2, y + 6, sizeZ), ModBlocks.REALM_BANNER);
        stockChest(world, corner.add(sizeX / 2, y + 1, sizeZ / 2), new ItemStack(ModItems.ROYAL_JEWELRY, 3),
                new ItemStack(ModItems.ROYAL_COIN, 8), new ItemStack(Items.GOLD_BLOCK));
    }

    private static void tavern(ServerWorld world, BlockPos base) {
        house(world, base, 8, 7, ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG,
                Blocks.DARK_OAK_STAIRS, ModBlocks.SHIP_PLANKS);
        int y = groundAt(world, base.getX() + 4, base.getZ() + 3);
        // Sign post and barrels out front.
        set(world, base.add(4, y + 1, 8), Blocks.OAK_FENCE);
        set(world, base.add(4, y + 2, 8), Blocks.OAK_FENCE);
        set(world, base.add(3, y + 1, 8), Blocks.BARREL);
        set(world, base.add(5, y + 1, 8), Blocks.BARREL);
        set(world, base.add(4, y + 2, 7), Blocks.LANTERN);
    }

    private static void saloon(ServerWorld world, BlockPos base, int sizeX, int sizeZ, Block wood, Block log) {
        house(world, base, sizeX, sizeZ, wood, log, Blocks.ACACIA_STAIRS, wood);
        int y = groundAt(world, base.getX() + sizeX / 2, base.getZ() + sizeZ / 2);
        set(world, base.add(sizeX / 2, y + 1, sizeZ), Blocks.OAK_FENCE);
        set(world, base.add(sizeX / 2, y + 2, sizeZ), Blocks.OAK_FENCE);
        set(world, base.add(sizeX / 2 - 1, y + 1, sizeZ), Blocks.BARREL);
        set(world, base.add(sizeX / 2 + 1, y + 1, sizeZ), Blocks.BARREL);
    }

    private static void warehouse(ServerWorld world, BlockPos corner, int sizeX, int sizeZ,
                                  Block wall, Block log) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, sizeX, sizeZ, y, wall);
        int height = 5;
        for (int h = 0; h <= height; h++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    boolean shell = x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1 || h == 0 || h == height;
                    BlockPos pos = origin.add(x, h, z);
                    if (h == 0) {
                        set(world, pos, log);
                        foundation(world, pos.getX(), pos.getZ(), y - 1, log);
                    } else {
                        set(world, pos, shell ? wall : Blocks.AIR);
                    }
                }
            }
        }
        // Loading doors + cargo racks inside.
        set(world, origin.add(sizeX / 2, 1, sizeZ - 1), Blocks.AIR);
        set(world, origin.add(sizeX / 2, 2, sizeZ - 1), Blocks.AIR);
        int floor = y + 1;
        for (int x = 2; x < sizeX - 2; x += 3) {
            set(world, origin.add(x, floor, 2), Blocks.BARREL);
            set(world, origin.add(x, floor + 1, 2), Blocks.BARREL);
            set(world, origin.add(x, floor, 3), Blocks.BARREL);
        }
        stockChest(world, origin.add(sizeX / 2, floor, sizeZ / 2), new ItemStack(Items.IRON_INGOT, 5),
                new ItemStack(Items.COAL, 6), new ItemStack(ModItems.ROYAL_COIN, 2));
        set(world, origin.add(sizeX / 2, floor + 3, sizeZ / 2), Blocks.LANTERN);
    }

    private static void workshop(ServerWorld world, BlockPos corner, int sizeX, int sizeZ,
                                 Block wall, Block log) {
        house(world, corner, sizeX, sizeZ, wall, log,
                wall == ModBlocks.AIRSHIP_METAL ? Blocks.SPRUCE_STAIRS : Blocks.OAK_STAIRS, wall);
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        set(world, corner.add(1, y + 1, sizeZ - 2), Blocks.ANVIL);
        set(world, corner.add(2, y + 1, sizeZ - 2), Blocks.FURNACE);
        set(world, corner.add(3, y + 1, sizeZ - 2), Blocks.CAULDRON);
        set(world, corner.add(1, y + 1, sizeZ - 3), Blocks.GRINDSTONE);
    }

    private static void stable(ServerWorld world, BlockPos corner, int sizeX, int sizeZ) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        for (int x = 0; x < sizeX; x++) {
            set(world, corner.add(x, y, sizeZ / 2), Blocks.OAK_FENCE);
            set(world, corner.add(x, y + 1, sizeZ / 2), Blocks.OAK_FENCE);
        }
        for (int z = 0; z < sizeZ; z++) {
            set(world, corner.add(sizeX / 2, y, z), Blocks.OAK_FENCE);
            set(world, corner.add(sizeX / 2, y + 1, z), Blocks.OAK_FENCE);
        }
        roofFlat(world, corner, sizeX, sizeZ, y + 2, Blocks.SPRUCE_PLANKS);
        set(world, corner.add(sizeX / 2, y + 1, sizeZ / 2), Blocks.HAY_BLOCK);
        set(world, corner.add(1, y, 1), Blocks.WATER);
    }

    /** A fire basket on a stone post - courtyard and gate light. */
    private static void brazier(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        set(world, base.add(0, y + 1, 0), Blocks.COBBLESTONE_WALL);
        set(world, base.add(0, y + 2, 0), Blocks.COBBLESTONE_WALL);
        set(world, base.add(0, y + 3, 0), Blocks.CAMPFIRE);
    }

    /** A dressed-stone fountain: the heart of any market square. */
    private static void fountain(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean rim = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                set(world, base.add(dx, y + 1, dz), rim ? Blocks.STONE_BRICKS : Blocks.WATER);
            }
        }
        set(world, base.add(0, y, 0), Blocks.STONE_BRICKS);
        set(world, base.add(0, y + 2, 0), Blocks.STONE_BRICK_SLAB);
        foundation(world, base.getX(), base.getZ(), y - 1, Blocks.STONE_BRICKS);
    }

    /** A chapel: stone walls, bell spire, pews and a carpet aisle. */
    private static void chapel(ServerWorld world, BlockPos corner) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), 7, 9);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, 7, 9, y, Blocks.STONE_BRICKS);
        for (int h = 0; h <= 5; h++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 9; z++) {
                    boolean edge = x == 0 || z == 0 || x == 6 || z == 8;
                    BlockPos pos = origin.add(x, h, z);
                    if (h == 0) {
                        set(world, pos, Blocks.STONE_BRICKS);
                    } else if (edge) {
                        boolean pillar = (x % 3 == 0) && (z % 4 == 0);
                        set(world, pos, pillar ? ModBlocks.CROWN_BRICK : Blocks.STONE_BRICKS);
                    } else {
                        set(world, pos, Blocks.AIR);
                    }
                }
            }
            if (h == 3) {
                set(world, origin.add(3, h, 8), Blocks.AIR);
                set(world, origin.add(0, h, 2), Blocks.GLASS_PANE);
                set(world, origin.add(6, h, 2), Blocks.GLASS_PANE);
                set(world, origin.add(0, h, 5), Blocks.GLASS_PANE);
                set(world, origin.add(6, h, 5), Blocks.GLASS_PANE);
            }
        }
        // Bell spire over the entry.
        for (int h = 5; h <= 8; h++) {
            for (int x = 2; x <= 4; x++) {
                for (int z = 7; z <= 8; z++) {
                    boolean edge = x == 2 || x == 4 || z == 7;
                    set(world, origin.add(x, h, z), h == 8 || !edge
                            ? Blocks.STONE_BRICK_SLAB : Blocks.STONE_BRICKS);
                }
            }
        }
        set(world, origin.add(3, 6, 7), Blocks.BELL);
        set(world, origin.add(3, 9, 8), ModBlocks.REALM_BANNER);
        // Pews and the aisle.
        for (int z = 2; z <= 6; z += 2) {
            for (int x = 1; x <= 5; x++) {
                if (x != 3) {
                    set(world, origin.add(x, 1, z), Blocks.SPRUCE_STAIRS);
                }
            }
        }
        for (int z = 1; z <= 7; z++) {
            set(world, origin.add(3, 1, z), Blocks.WHITE_CARPET);
        }
        set(world, origin.add(1, 1, 1), Blocks.LANTERN);
        set(world, origin.add(5, 1, 1), Blocks.LANTERN);
    }

    /** A proper two-storey townhouse: shop below, home above, balcony out front. */
    private static void townhouse(ServerWorld world, BlockPos corner, int sizeX, int sizeZ) {
        house(world, corner, sizeX, sizeZ, Blocks.OAK_PLANKS, Blocks.OAK_LOG,
                Blocks.OAK_STAIRS, Blocks.OAK_PLANKS);
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        // Upper floor: log band, framed windows, its own little gable.
        for (int h = 5; h <= 8; h++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    boolean edge = x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1;
                    BlockPos pos = origin.add(x, h, z);
                    if (!edge) {
                        set(world, pos, Blocks.AIR);
                        continue;
                    }
                    boolean cornerPost = (x == 0 || x == sizeX - 1) && (z == 0 || z == sizeZ - 1);
                    if (h == 5 || h == 8 || cornerPost) {
                        set(world, pos, Blocks.OAK_LOG);
                    } else if (h == 7 && (x == 1 || x == sizeX - 2) && z == sizeZ / 2) {
                        set(world, pos, Blocks.GLASS_PANE);
                    } else {
                        set(world, pos, Blocks.SPRUCE_PLANKS);
                    }
                }
            }
        }
        for (int r = 0; r < (sizeZ - 1) / 2; r++) {
            int h = 9 + r;
            for (int x = -1; x <= sizeX; x++) {
                set(world, origin.add(x, h, r), Blocks.SPRUCE_STAIRS);
                set(world, origin.add(x, h, sizeZ - 1 - r), Blocks.SPRUCE_STAIRS);
            }
        }
        // Balcony over the door with a rail and a hanging lantern.
        int doorX = sizeX / 2;
        for (int dx = -1; dx <= 1; dx++) {
            set(world, origin.add(doorX + dx, 5, sizeZ), Blocks.SPRUCE_SLAB);
        }
        set(world, origin.add(doorX - 1, 6, sizeZ), Blocks.OAK_FENCE);
        set(world, origin.add(doorX + 1, 6, sizeZ), Blocks.OAK_FENCE);
        set(world, origin.add(doorX, 6, sizeZ + 1), Blocks.LANTERN);
        // Sign bracket: a shop lives here.
        set(world, origin.add(doorX, 4, sizeZ + 1), Blocks.OAK_FENCE);
    }

    /** A beached, broken ship: ribs, a snapped mast, sand worked in. */
    private static void hulk(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        for (int i = 0; i < 12; i++) {
            int rise = i < 6 ? i / 2 : (11 - i) / 2;
            BlockPos keel = base.add(i, y, 0);
            set(world, keel, ModBlocks.SHIP_PLANKS);
            for (int h = 1; h <= rise; h++) {
                set(world, keel.up(h), h == rise ? Blocks.SPRUCE_LOG : ModBlocks.SHIP_PLANKS);
            }
            set(world, keel.north().up(1), rise >= 2 ? ModBlocks.SHIP_PLANKS : Blocks.AIR);
            set(world, keel.south().up(1), rise >= 2 ? ModBlocks.SHIP_PLANKS : Blocks.AIR);
        }
        // The mast snapped and leans over the side.
        set(world, base.add(4, y + 3, 0), Blocks.AIR);
        for (int h = 1; h <= 2; h++) {
            set(world, base.add(4, y + h, 0), Blocks.SPRUCE_LOG);
        }
        for (int i = 1; i <= 4; i++) {
            set(world, base.add(4 + i, y + 2 - (i / 3), i / 2 + 1), Blocks.SPRUCE_LOG);
        }
        set(world, base.add(2, y + 1, 0), Blocks.BARREL);
        set(world, base.add(7, y + 1, 0), Blocks.BARREL);
    }

    /** A kitchen garden: moss, azalea and a watering barrel. */
    private static void gardenPatch(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                set(world, base.add(dx, y + 1, dz), (dx + dz) % 2 == 0
                        ? Blocks.MOSS_CARPET : Blocks.AZALEA);
            }
        }
        set(world, base.add(3, y + 1, 1), Blocks.BARREL);
    }

    private static void bridge(ServerWorld world, BlockPos start, int length, Block material) {
        int y = start.getY();
        for (int i = 0; i < length; i++) {
            set(world, start.add(0, 0, -i).west(i / 2), material);
            set(world, start.add(0, 0, -i).east(i / 2 + 1), material);
            if (i % 3 == 1) {
                // Piers down to honest ground, so the deck never spans air.
                BlockPos pierPos = start.add(0, 0, -i);
                foundation(world, pierPos.getX(), pierPos.getZ(), y - 1, material);
                BlockPos east = pierPos.east(i / 2 + 1);
                foundation(world, east.getX(), east.getZ(), y - 1, material);
            }
        }
    }

    private static void roofFlat(ServerWorld world, BlockPos corner, int sizeX, int sizeZ, int y, Block block) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                set(world, corner.add(x, y, z), block);
            }
        }
    }

    // ---------------------------------------------------------------- props

    private static void crenellate(ServerWorld world, BlockPos start, int lengthX, int lengthZ, Block trim) {
        for (int x = 0; x < lengthX; x++) {
            for (int z = 0; z < lengthZ; z++) {
                if ((x + z) % 2 == 0) {
                    set(world, start.add(x, 0, z), trim);
                }
            }
        }
    }

    private static void palisade(ServerWorld world, BlockPos corner, int sizeX, int sizeZ,
                                 Block wall, Block log) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        for (int x = 0; x < sizeX; x++) {
            for (int h = 1; h <= 3; h++) {
                if (h < 3 || (x % 2 == 0)) {
                    set(world, corner.add(x, y + h, 0), h == 3 ? log : wall);
                    set(world, corner.add(x, y + h, sizeZ - 1), h == 3 ? log : wall);
                }
            }
            // Gate gap.
            if (x == sizeX / 2) {
                for (int h = 1; h <= 2; h++) {
                    set(world, corner.add(x, y + h, sizeZ - 1), Blocks.AIR);
                }
                set(world, corner.add(x, y + 1, sizeZ - 1),
                        Blocks.OAK_FENCE_GATE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            }
        }
        for (int z = 0; z < sizeZ; z++) {
            for (int h = 1; h <= 3; h++) {
                if (h < 3 || (z % 2 == 0)) {
                    set(world, corner.add(0, y + h, z), h == 3 ? log : wall);
                    set(world, corner.add(sizeX - 1, y + h, z), h == 3 ? log : wall);
                }
            }
        }
    }

    private static void farmPlot(ServerWorld world, BlockPos corner, int sizeX, int sizeZ) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                BlockPos pos = corner.add(x, y, z);
                if (x == sizeX / 2) {
                    set(world, pos, Blocks.WATER);
                    continue;
                }
                set(world, pos, Blocks.FARMLAND);
                set(world, pos.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 4 + world.random.nextInt(4)));
                foundation(world, pos.getX(), pos.getZ(), y - 1, Blocks.DIRT);
            }
        }
        // Fence border with a compost corner.
        for (int x = -1; x <= sizeX; x += sizeX + 1) {
            for (int z = 0; z < sizeZ; z++) {
                set(world, corner.add(x, y + 1, z), Blocks.OAK_FENCE);
            }
        }
        for (int z = -1; z <= sizeZ; z += sizeZ + 1) {
            for (int x = 0; x < sizeX; x++) {
                set(world, corner.add(x, y + 1, z), Blocks.OAK_FENCE);
            }
        }
        set(world, corner.add(sizeX, y + 1, sizeZ), Blocks.COMPOSTER);
    }

    private static void lampPost(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        set(world, base.add(0, y + 1, 0), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 2, 0), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 3, 0), Blocks.LANTERN);
    }

    private static void well(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        set(world, base.add(0, y, 0), Blocks.WATER);
        foundation(world, base.getX(), base.getZ(), y - 1, Blocks.COBBLESTONE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                set(world, base.add(dx, y, dz), Blocks.COBBLESTONE);
                if (Math.abs(dx) + Math.abs(dz) == 1) {
                    set(world, base.add(dx, y + 1, dz), Blocks.COBBLESTONE_WALL);
                }
            }
        }
        set(world, base.add(0, y + 2, 0), Blocks.COBBLESTONE);
        set(world, base.add(0, y + 3, 0), Blocks.LANTERN);
    }

    private static void campfire(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        set(world, base.add(0, y + 1, 0), Blocks.CAMPFIRE);
        set(world, base.add(1, y + 1, 0), Blocks.OAK_LOG);
        set(world, base.add(-1, y + 1, 1), Blocks.BARREL);
    }

    private static void marketStall(ServerWorld world, BlockPos base, Block canopy, Block log) {
        int y = groundAt(world, base.getX() + 1, base.getZ() + 1);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                if (dx == 0 || dx == 1) {
                    set(world, base.add(dx, y + 1, dz * 2), log);
                    set(world, base.add(dx, y + 2, dz * 2), log);
                }
            }
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                set(world, base.add(dx, y + 3, dz), canopy);
            }
        }
        set(world, base.add(0, y + 1, 1), Blocks.OAK_STAIRS.getDefaultState()
                .with(HorizontalFacingBlock.FACING, Direction.EAST));
        set(world, base.add(1, y + 1, 1), Blocks.CHEST);
        stockChest(world, base.add(1, y + 1, 1), new ItemStack(Items.BREAD, 5),
                new ItemStack(ModItems.ROYAL_JEWELRY, 1), new ItemStack(ModItems.ROYAL_COIN, 2));
    }

    private static void storageYard(ServerWorld world, BlockPos base, Block floor) {
        int y = groundAt(world, base.getX() + 2, base.getZ() + 2);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                set(world, base.add(x, y, z), floor);
            }
        }
        set(world, base.add(1, y + 1, 1), Blocks.BARREL);
        set(world, base.add(2, y + 1, 1), Blocks.BARREL);
        set(world, base.add(3, y + 1, 1), Blocks.BARREL);
        set(world, base.add(1, y + 1, 3), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(2, y + 1, 3), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(3, y + 1, 3), Blocks.CHEST);
        stockChest(world, base.add(3, y + 1, 3), new ItemStack(Items.ROTTEN_FLESH, 3),
                new ItemStack(Items.BONE, 2));
        set(world, base.add(4, y + 1, 0), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 1, 4), Blocks.OAK_FENCE);
    }

    private static void pier(ServerWorld world, BlockPos start, int length, Direction direction) {
        int waterY = waterSurfaceAt(world, start);
        int y = waterY > 0 ? waterY : groundAt(world, start.getX(), start.getZ()) + 1;
        Direction cross = direction.rotateYCounterclockwise();
        for (int i = 0; i < length; i++) {
            BlockPos plankPos = start.offset(direction, i).up(y);
            set(world, plankPos, ModBlocks.SHIP_PLANKS);
            if (i % 3 == 0) {
                // Piling legs down to the seabed.
                BlockPos leg = plankPos.down();
                for (int d = 0; d < 10 && d + plankPos.getY() > world.getBottomY() + 1; d++) {
                    BlockState state = world.getBlockState(leg);
                    if (!state.isAir() && world.getFluidState(leg).isEmpty()) {
                        break;
                    }
                    set(world, leg, Blocks.SPRUCE_LOG);
                    leg = leg.down();
                }
            }
            if (i % 4 == 2) {
                set(world, plankPos.offset(cross), Blocks.OAK_FENCE);
                set(world, plankPos.offset(cross.getOpposite()), Blocks.OAK_FENCE);
            }
        }
        // Mooring post with a lantern at the end.
        BlockPos end = start.offset(direction, length - 1).up(y + 1);
        set(world, end, Blocks.OAK_FENCE);
        set(world, end.up(), Blocks.LANTERN);
    }

    private static void pierCrane(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        for (int h = 1; h <= 5; h++) {
            set(world, origin.add(0, h, 0), Blocks.SPRUCE_LOG);
        }
        set(world, origin.add(0, 6, 0), Blocks.SPRUCE_SLAB);
        set(world, origin.add(1, 5, 0), Blocks.CHAIN);
        set(world, origin.add(1, 4, 0), Blocks.CHAIN);
        set(world, origin.add(2, 3, 0), Blocks.CHEST);
        stockChest(world, origin.add(2, 3, 0), new ItemStack(ModItems.ROYAL_JEWELRY, 2),
                new ItemStack(Items.GOLD_NUGGET, 4));
    }

    private static void mastFlag(ServerWorld world, BlockPos base, Block flag) {
        int y = groundAt(world, base.getX(), base.getZ());
        for (int h = 1; h <= 7; h++) {
            set(world, base.add(0, y + h, 0), Blocks.SPRUCE_LOG);
        }
        set(world, base.add(0, y + 8, 0), flag);
    }

    private static void hangar(ServerWorld world, BlockPos corner, int sizeX, int sizeZ) {
        // Open-front ship shelter at ground level: iron sill, arched timber
        // frame, metal roof.
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, sizeX, sizeZ, y, ModBlocks.AIRSHIP_METAL);
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                boolean frame = x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1;
                set(world, origin.add(x, 0, z), ModBlocks.AIRSHIP_METAL);
                if (frame && (x + z) % 2 == 0) {
                    set(world, origin.add(x, 1, z), Blocks.SPRUCE_LOG);
                    set(world, origin.add(x, 2, z), Blocks.SPRUCE_LOG);
                    if ((x + z) % 4 == 0) {
                        set(world, origin.add(x, 3, z), Blocks.SPRUCE_LOG);
                    }
                }
            }
        }
        roofFlat(world, corner, sizeX, sizeZ, y + 4, ModBlocks.AIRSHIP_METAL);
        set(world, origin.add(0, 1, 0), Blocks.LANTERN);
    }

    private static void platform(ServerWorld world, BlockPos corner, int sizeX, int sizeZ, int y, Block surface) {
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                set(world, origin.add(x, 0, z), surface);
                if ((x == 0 || x == sizeX - 1) && (z == 0 || z == sizeZ - 1)) {
                    // Scaffolding legs down to the ground.
                    for (int leg = 1; leg <= Math.max(1, y - groundAt(world, origin.getX() + x, origin.getZ() + z)); leg++) {
                        BlockPos legPos = origin.add(x, -leg, z);
                        if (world.getBlockState(legPos).isAir()
                                || !world.getFluidState(legPos).isEmpty()) {
                            set(world, legPos, Blocks.SCAFFOLDING);
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        // Safety rails + corner lanterns.
        for (int x = 0; x < sizeX; x++) {
            set(world, origin.add(x, 1, 0), Blocks.OAK_FENCE);
            set(world, origin.add(x, 1, sizeZ - 1), Blocks.OAK_FENCE);
        }
        for (int z = 1; z < sizeZ - 1; z++) {
            set(world, origin.add(0, 1, z), Blocks.OAK_FENCE);
            set(world, origin.add(sizeX - 1, 1, z), Blocks.OAK_FENCE);
        }
        set(world, origin.add(0, 2, 0), Blocks.LANTERN);
        set(world, origin.add(sizeX - 1, 2, sizeZ - 1), Blocks.LANTERN);
    }

    private static void mooredAirship(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        // The mast first: iron footing, timber pole, chains aloft.
        set(world, base.add(0, y + 1, 0), ModBlocks.AIRSHIP_METAL);
        for (int h = 2; h <= 9; h++) {
            set(world, base.add(0, y + h, 0), Blocks.SPRUCE_LOG);
        }
        set(world, base.add(0, y + 10, 0), ModBlocks.AIRSHIP_METAL);
        set(world, base.add(0, y + 11, 0), Blocks.CHAIN);
        set(world, base.add(0, y + 12, 0), Blocks.CHAIN);
        // Three mooring points around the foot, each chained to its post.
        int[][] guys = {{3, 0}, {-2, 3}, {-2, -3}};
        for (int[] guy : guys) {
            BlockPos foot = base.add(guy[0], 0, guy[1]);
            int fy = groundAt(world, foot.getX(), foot.getZ());
            set(world, new BlockPos(foot.getX(), fy + 1, foot.getZ()), ModBlocks.AIRSHIP_METAL);
            set(world, new BlockPos(foot.getX(), fy + 2, foot.getZ()), Blocks.CHAIN);
        }
        // The ship rides at the masthead, tugging gently at its lines.
        com.rivalrealms.entity.AirshipEntity ship = ModEntities.AIRSHIP.create(world);
        if (ship == null) {
            return;
        }
        ship.refreshPositionAndAngles(base.getX() + 0.5, y + 13.0, base.getZ() + 0.5,
                world.random.nextFloat() * 360.0f, 0.0f);
        ship.setEnvelopeColor(world.random.nextInt(4));
        world.spawnEntity(ship);
    }

    private static int waterSurfaceAt(ServerWorld world, BlockPos pos) {
        BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, pos);
        for (int y = top.getY(); y >= Math.max(world.getBottomY() + 1, top.getY() - 12); y--) {
            BlockPos probe = new BlockPos(pos.getX(), y, pos.getZ());
            if (world.getFluidState(probe).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
                return y;
            }
        }
        return -1;
    }

    private static void mooredBoat(ServerWorld world, BlockPos base) {
        int waterY = waterSurfaceAt(world, base);
        if (waterY < 0) {
            return;
        }
        BoatEntity boat = EntityType.BOAT.create(world);
        if (boat == null) {
            return;
        }
        boat.refreshPositionAndAngles(base.getX() + 0.5, waterY + 0.4, base.getZ() + 0.5,
                world.random.nextFloat() * 360.0f, 0.0f);
        boat.setVariant(BoatEntity.Type.SPRUCE);
        world.spawnEntity(boat);
    }

    private static void lightYard(ServerWorld world, BlockPos base, int y, int spacing) {
        for (int x = -spacing; x <= spacing; x += spacing) {
            lampPost(world, base.add(x, 0, -spacing / 2));
            lampPost(world, base.add(x, 0, spacing / 2));
        }
    }

    /** A little farmhand cottage: porch, composter, barrel, lantern, loft bed. */
    private static void farmhouse(ServerWorld world, BlockPos corner) {
        Block wall = ModBlocks.FRONTIER_PLANKS;
        Block log = Blocks.SPRUCE_LOG;
        int y = lowestCorner(world, corner.getX(), corner.getZ(), 6, 5);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, 6, 5, y, log);

        foundationRing(world, origin, 6, 5, wall);
        // plank walls with log corner posts, door gap facing the farm
        fill(world, origin, 6, 4, 1, log); fill(world, origin.add(0, 0, 4), 6, 4, 1, log);
        fill(world, origin, 1, 4, 5, log); fill(world, origin.add(5, 0, 0), 1, 4, 5, log);
        for (int x = 1; x <= 4; x++) {
            fill(world, origin.add(x, 0, 0), 1, 4, 1, wall);
            fill(world, origin.add(x, 0, 4), 1, 4, 1, wall);
        }
        for (int z = 1; z <= 3; z++) {
            fill(world, origin.add(0, 0, z), 1, 4, 1, wall);
            fill(world, origin.add(5, 0, z), 1, 4, 1, wall);
        }
        clearColumn(world, origin.getX() + 2, origin.getZ(), y + 1, y + 2);   // doorway
        clearColumn(world, origin.getX() + 4, origin.getZ(), y + 1, y + 2);   // window
        // gabled stair roof
        for (int step = 0; step < 3; step++) {
            fill(world, origin.add(-1 + step, 4 + step, -1), 8 - step * 2, 1, 7,
                    step % 2 == 0 ? Blocks.SPRUCE_STAIRS : wall);
        }
        set(world, origin.add(2, 7, 2), wall);
        // homely interior: composter, barrel, lantern, hay-bed corner
        set(world, origin.add(1, 1, 3), Blocks.COMPOSTER);
        set(world, origin.add(4, 1, 3), Blocks.BARREL);
        set(world, origin.add(4, 1, 1), Blocks.LANTERN);
        set(world, origin.add(1, 1, 1), Blocks.HAY_BLOCK);
        // porch posts
        set(world, origin.add(1, 1, -1), Blocks.OAK_FENCE);
        set(world, origin.add(4, 1, -1), Blocks.OAK_FENCE);
        set(world, origin.add(1, 2, -1), Blocks.SPRUCE_STAIRS);
        set(world, origin.add(4, 2, -1), Blocks.SPRUCE_STAIRS);
    }

    /**
     * Solid ground under a footprint: fills every column between honest
     * terrain and the floor line, unconditionally - no arches of air, no
     * daylit undersides, ever.
     */
    private static void packUnder(ServerWorld world, BlockPos corner, int sizeX,
                                  int sizeZ, int floorY, Block block) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int px = corner.getX() + x;
                int pz = corner.getZ() + z;
                int top = groundAt(world, px, pz);
                int bottom = Math.max(floorY - 24, top);
                for (int y = bottom; y < floorY; y++) {
                    set(world, new BlockPos(px, y, pz), block);
                }
            }
        }
    }

    private static void foundationRing(ServerWorld world, BlockPos corner, int sizeX, int sizeZ, Block fill_) {
        for (int x = 0; x < sizeX; x++) {
            foundation(world, corner.getX() + x, corner.getZ(), corner.getY() - 1, fill_);
            foundation(world, corner.getX() + x, corner.getZ() + sizeZ - 1, corner.getY() - 1, fill_);
        }
        for (int z = 1; z < sizeZ - 1; z++) {
            foundation(world, corner.getX(), corner.getZ() + z, corner.getY() - 1, fill_);
            foundation(world, corner.getX() + sizeX - 1, corner.getZ() + z, corner.getY() - 1, fill_);
        }
    }

    /** Carrot, potato and beetroot rows — the mixed rotation real farms run. */
    private static void vegPlot(ServerWorld world, BlockPos corner, int sizeX, int sizeZ) {
        int y = groundAt(world, corner.getX(), corner.getZ());
        net.minecraft.block.Block[] rotation = {Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        int idx = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                BlockPos soil = corner.add(x, y - corner.getY(), z);
                foundation(world, soil.getX(), soil.getZ(), y - 1, Blocks.DIRT);
                clearColumn(world, soil.getX(), soil.getZ(), y + 1, y + 2);
                set(world, soil, Blocks.FARMLAND);
                if ((x + z) % 2 == 0) {
                    set(world, soil.up(), rotation[idx % rotation.length].getDefaultState());
                }
                idx++;
            }
        }
        // water channel so the crops never dry out
        fill(world, corner.add(-1, 0, 0), 1, 1, sizeZ, Blocks.WATER);
    }

    /** A fence-and-pumpkin scarecrow keeping watch over the rows. */
    private static void scarecrow(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos pos = new BlockPos(base.getX(), y + 1, base.getZ());
        set(world, pos, Blocks.OAK_FENCE);
        set(world, pos.up(), Blocks.OAK_FENCE);
        set(world, pos.up(2), Blocks.CARVED_PUMPKIN);
        set(world, pos.up().west(), Blocks.OAK_FENCE);
        set(world, pos.up().east(), Blocks.OAK_FENCE);
    }

    private static void haystack(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos pos = new BlockPos(base.getX(), y + 1, base.getZ());
        set(world, pos, Blocks.HAY_BLOCK);
        set(world, pos.up(), Blocks.HAY_BLOCK);
    }

    /**
     * A standalone farmstead: two farmhand cottages, a wheat field, a vegetable
     * rotation plot, scarecrows, hay, a small stock pen and a lantern or two.
     * This is where the realm's farmers actually live.
     */
    private static void buildScatteredFarmstead(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 25, 21, Blocks.GRASS_BLOCK);
        Block path = Blocks.COARSE_DIRT;
        fill(world, base.add(-2, y, -10), 5, 1, 21, path);

        farmhouse(world, base.add(-11, 0, -8));
        farmhouse(world, base.add(6, 0, -6));
        farmPlot(world, base.add(-10, 0, 2), 9, 7);
        vegPlot(world, base.add(4, 0, 5), 8, 7);
        scarecrow(world, base.add(-6, 0, 4));
        scarecrow(world, base.add(8, 0, 6));
        haystack(world, base.add(2, 0, -6));
        haystack(world, base.add(-4, 0, -6));
        // fenced stock pen with a water trough
        fill(world, base.add(-2, y + 1, 12), 10, 1, 1, Blocks.OAK_FENCE);
        fill(world, base.add(-2, y + 1, 16), 10, 1, 1, Blocks.OAK_FENCE);
        fill(world, base.add(-2, y + 1, 12), 1, 1, 5, Blocks.OAK_FENCE);
        fill(world, base.add(7, y + 1, 12), 1, 1, 5, Blocks.OAK_FENCE);
        set(world, base.add(-2, y + 1, 14), Blocks.AIR);
        set(world, base.add(2, y + 1, 14), Blocks.WATER);
        lampPost(world, base.add(0, 0, -8));
        set(world, base.add(-3, y + 1, -6), Blocks.COMPOSTER);
        stockChest(world, base.add(-9, groundAt(world, base.getX() - 9, base.getZ() - 8) + 2, -8),
                new ItemStack(Items.BREAD, 5), new ItemStack(Items.WHEAT_SEEDS, 8),
                new ItemStack(ModItems.FARMER_HOE), new ItemStack(ModItems.ROYAL_COIN, 1));
        spawnGuard(world, base, y, BuildStyle.CUSTOM);
    }

    /** A Marauder warcamp: hide tents around a bone-fire, skull totems, loot. */
    private static void buildMarauderCamp(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 19, 17, Blocks.COARSE_DIRT);

        // The bone-fire: campfire ringed with hay and bones of old feasts.
        campfire(world, base.add(-1, 0, -1));
        for (int[] off : new int[][]{{-3, -3}, {3, -3}, {-3, 2}, {3, 2}, {0, -4}}) {
            haystack(world, base.add(off[0], 0, off[1]));
        }

        // Two hide tents: wool A-frames on fence poles.
        for (int[] tent : new int[][]{{-7, -6}, {4, -4}}) {
            BlockPos t = base.add(tent[0], 0, tent[1]);
            int ty = groundAt(world, t.getX(), t.getZ());
            set(world, t.add(0, ty - t.getY() + 1, 0), Blocks.OAK_FENCE);
            set(world, t.add(3, ty - t.getY() + 1, 0), Blocks.OAK_FENCE);
            for (int step = 0; step < 3; step++) {
                fill(world, t.add(0, ty - t.getY() + 1 + step, step), 4, 1, 1,
                        step == 1 ? Blocks.GRAY_WOOL : Blocks.WHITE_WOOL);
                fill(world, t.add(0, ty - t.getY() + 1 + step, 4 - step), 4, 1, 1,
                        step == 1 ? Blocks.GRAY_WOOL : Blocks.WHITE_WOOL);
            }
            set(world, t.add(1, ty - t.getY() + 2, 0), Blocks.WHITE_WOOL);
            set(world, t.add(1, ty - t.getY() + 2, 4), Blocks.WHITE_WOOL);
        }

        // Skull totems announce whose ground this is.
        for (int[] totem : new int[][]{{-8, 3}, {7, 2}}) {
            BlockPos tp = base.add(totem[0], 0, totem[1]);
            int py = groundAt(world, tp.getX(), tp.getZ());
            set(world, tp.add(0, py - tp.getY() + 1, 0), Blocks.OAK_FENCE);
            set(world, tp.add(0, py - tp.getY() + 2, 0), Blocks.OAK_FENCE);
            set(world, tp.add(0, py - tp.getY() + 3, 0), ModBlocks.TROPHY_SKULL);
        }

        // Loot pile and a grim banner.
        stockChest(world, base.add(2, y + 1, 1), new ItemStack(Items.BONE, 6),
                new ItemStack(ModItems.ROYAL_COIN, 2), new ItemStack(Items.GOLD_NUGGET, 8),
                new ItemStack(ModItems.FLINTLOCK));
        set(world, base.add(0, y + 1, 5), ModBlocks.REALM_BANNER);
        set(world, base.add(-3, y + 1, 3), ModBlocks.WAR_TABLE);
        set(world, base.add(3, y + 1, -2), ModBlocks.TROPHY_SKULL);
        set(world, base.add(-5, y + 1, 0), ModBlocks.WEAPON_RACK);
        lightYard(world, base, y, 8);
    }

    /** A Hearthfolk hamlet: three cottages, gardens, a well, warm lanterns. */
    private static void buildHearthHamlet(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 25, 21, Blocks.GRASS_BLOCK);
        fill(world, base.add(-2, y, -10), 5, 1, 21, Blocks.COARSE_DIRT);

        farmhouse(world, base.add(-11, 0, -8));
        farmhouse(world, base.add(6, 0, -8));
        farmhouse(world, base.add(-3, 0, 7));
        farmPlot(world, base.add(-12, 0, 2), 8, 6);
        vegPlot(world, base.add(5, 0, 1), 7, 6);
        well(world, base.add(0, 0, -2));
        set(world, base.add(3, y + 1, -2), ModBlocks.NOTICE_BOARD);
        scarecrow(world, base.add(-8, 0, 5));
        haystack(world, base.add(3, 0, -5));
        lampPost(world, base.add(-6, 0, -1));
        lampPost(world, base.add(6, 0, -1));
        set(world, base.add(0, y + 1, 10), ModBlocks.REALM_BANNER);
        fill(world, base.add(-1, y, -6), 3, 1, 17, ModBlocks.ROAD_STONE);
        set(world, base.add(-2, y + 1, 9), ModBlocks.HEARTH_LANTERN);
        set(world, base.add(2, y + 1, 9), ModBlocks.HEARTH_LANTERN);
        set(world, base.add(3, y + 1, 3), ModBlocks.SUPPLY_CRATE);
        stockChest(world, base.add(-2, y + 1, 2), new ItemStack(Items.BREAD, 8),
                new ItemStack(Items.WHEAT_SEEDS, 10), new ItemStack(ModItems.ROYAL_COIN, 2));
    }

    /** An old treasure shrine: gold circle, four pillars, braziers, one chest. */
    private static void buildScatteredTemple(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 15, 15, Blocks.STONE_BRICKS);
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());

        // Gilded ritual circle inside a stone brick ring.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= 3.2) {
                    set(world, origin.add(dx, 1, dz),
                            dist > 2.2 ? Blocks.STONE_BRICKS : ModBlocks.CASTLE_TILES);
                    clearColumn(world, origin.getX() + dx, origin.getZ() + dz, y + 2, y + 4);
                }
            }
        }
        for (int[] g : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            set(world, origin.add(g[0], 1, g[1]), ModBlocks.GILDED_BRICK);
        }
        set(world, origin.add(0, 1, 0), Blocks.GOLD_BLOCK);
        // four pillars with gold caps
        for (int[] px : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            BlockPos col = origin.add(px[0], 1, px[1]);
            for (int h = 0; h < 4; h++) {
                set(world, col.up(h), ModBlocks.CROWN_PILLAR);
            }
            set(world, col.up(3), ModBlocks.CROWN_BRICK);
            set(world, col.up(4), Blocks.GOLD_BLOCK);
        }
        // braziers at the cardinal points
        for (int[] bx : new int[][]{{0, -4}, {0, 4}, {-4, 0}, {4, 0}}) {
            BlockPos fire = origin.add(bx[0], 1, bx[1]);
            set(world, fire, Blocks.GOLD_BLOCK);
            set(world, fire.up(), Blocks.CAMPFIRE);
        }
        stockChest(world, origin.add(2, 2, 2), new ItemStack(ModItems.ROYAL_COIN, 4),
                new ItemStack(ModItems.ROYAL_JEWELRY, 2), new ItemStack(Items.GOLD_NUGGET, 10),
                new ItemStack(ModItems.RECRUITMENT_CONTRACT));
        spawnGuard(world, base, y, BuildStyle.CUSTOM);
    }

    private static void spawnGuard(ServerWorld world, BlockPos base, int y, BuildStyle style) {
        var guard = ModEntities.SURVIVOR.create(world);
        if (guard == null) {
            return;
        }
        guard.refreshPositionAndAngles(base.getX() + 2.5, y + 1.0, base.getZ() + 2.5,
                world.random.nextFloat() * 360.0f, 0.0f);
        com.rivalrealms.entity.Archetype culture = style == BuildStyle.CUSTOM
                ? com.rivalrealms.entity.Archetype.values()[world.random.nextInt(com.rivalrealms.entity.Archetype.values().length)]
                : com.rivalrealms.entity.Archetype.byFaction(style.faction());
        guard.setArchetype(culture);
        world.spawnEntity(guard);
    }

    // ------------------------------------------------------------ plumbing

    private static void set(ServerWorld world, BlockPos pos, Block block) {
        if (pos.getY() <= world.getBottomY() || pos.getY() >= world.getTopY()) {
            return;
        }
        world.setBlockState(pos, block.getDefaultState(), 3);
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        if (pos.getY() <= world.getBottomY() || pos.getY() >= world.getTopY()) {
            return;
        }
        world.setBlockState(pos, state, 3);
    }

    private static void fill(ServerWorld world, BlockPos corner, int sizeX, int height, int sizeZ, Block block) {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    set(world, corner.add(x, y, z), block);
                }
            }
        }
    }

    private static void wall(ServerWorld world, BlockPos corner, int sizeX, int height, int sizeZ, Block block) {
        fill(world, corner, sizeX, height, sizeZ, block);
    }

    /** Buried loot for treasure maps: a real chest with a real haul. */
    public static void buryTreasure(ServerWorld world, BlockPos pos, ItemStack... stacks) {
        stockChest(world, pos, stacks);
    }

    /**
     * The rebuild reset button: sweeps every block above honest ground in a
     * square, so an old badly-built settlement can be raised anew.
     */
    public static void clearSite(ServerWorld world, BlockPos center, int half, int height) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                int top = groundAt(world, center.getX() + x, center.getZ() + z);
                clearColumn(world, center.getX() + x, center.getZ() + z, top + 1, top + height);
            }
        }
    }

    private static void stockChest(ServerWorld world, BlockPos pos, ItemStack... stacks) {
        set(world, pos, Blocks.CHEST);
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof Inventory inventory) {
            int slot = 0;
            for (ItemStack stack : stacks) {
                if (slot >= inventory.size()) {
                    break;
                }
                if (!stack.isEmpty()) {
                    inventory.setStack(slot, stack);
                }
                slot++;
            }
        }
    }
}

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

    /**
     * Holds every chunk a site spans before a single block is written.
     * Structure blocks placed into unloaded chunks vanish silently - the
     * reason half-built fortresses used to hang in the air. Far probes
     * sync-generate their chunk, exactly like vanilla structure placement.
     */
    public static void forceLoad(ServerWorld world, BlockPos center, int half) {
        int minX = (center.getX() - half) >> 4;
        int maxX = (center.getX() + half) >> 4;
        int minZ = (center.getZ() - half) >> 4;
        int maxZ = (center.getZ() + half) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                world.getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, true);
            }
        }
    }

    public static void build(ServerWorld world, BlockPos origin, BuildStyle style) {
        forceLoad(world, origin, 40);
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
        forceLoad(world, center, 40);
        switch (variant) {
            case FORTRESS -> buildScatteredFortress(world, center);
            case CITADEL -> buildCitadel(world, center);
            case TOWN -> buildScatteredTown(world, center, style);
            case ROYAL_CITY -> buildRoyalCity(world, center);
            case HARBOR -> buildScatteredHarbor(world, center);
            case SHIPYARD -> buildShipyard(world, center);
            case PIRATE_COVE -> buildCove(world, center);
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
            case WATCHTOWER -> buildWatchtower(world, center);
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
        // The gate stands where the road enters the town.
        townGate(world, base.add(0, 0, -14));
        prisonYard(world, base.add(9, 0, -12));
        waysideShrine(world, base.add(4, 0, -9));
        // The richer quarter: a two-storey townhouse and a chapel.
        townhouse(world, base.add(9, 0, -7), 7, 7);
        chapel(world, base.add(-16, 0, 10));
        gardenPatch(world, base.add(9, 0, 2));
        gardenPatch(world, base.add(-16, 0, -8));
        // The smithy and its coal-dark yard.
        forge(world, base.add(-2, 0, 10));
        // The bakery's oven breathes smoke over the square.
        bakeOven(world, base.add(-8, 0, 9));
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
        // The dockhands' grand crane watches the arcade.
        grandQuayCrane(world, base.add(-13, 0, -3));
        // A fishing family's stilt hut, legs in the tide.
        fishingStilt(world, base.add(16, 0, 3));
        // The pride of the harbor rides at anchor.
        galleon(world, base.add(14, 0, 14));
        // The cove pier: bonfire, guns and shade where the crews gather.
        covePier(world, base.add(4, 0, 12));
        // The light that brings the ships home.
        lighthouse(world, base.add(-18, 0, -16));
        // The waterfront arcade: stone arches, timber loft, long dark
        // roof - the building every harbor photograph is of.
        quayArcade(world, base.add(-10, 0, 9), 3);
        // Quay cargo waiting for the tide.
        for (int i = 0; i < 4; i++) {
            int qx = 2 + i * 3;
            int qy = groundAt(world, base.getX() + qx, base.getZ() + 8);
            set(world, base.add(qx, qy + 1, 8), i % 2 == 0
                    ? ModBlocks.SUPPLY_CRATE : Blocks.BARREL);
        }
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
        // The mountain gives up its coal at this adit.
        mineAdit(world, base.add(14, 0, 8));
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
        // The lumber corner: stacks, sawhorse, foreman's shed.
        lumberCamp(world, base.add(-9, 0, 9));
        farmhouse(world, base.add(-7, 0, 13));
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

        // The mill: a tapered stone-and-timber body under a dark cap, a
        // balcony ring at the sail axle, stone apron at its feet.
        BlockPos m0 = base.add(-3, 0, -3);
        int my = lowestCorner(world, m0.getX(), m0.getZ(), 7, 7);
        BlockPos mo = new BlockPos(m0.getX(), my, m0.getZ());
        packUnder(world, mo, 7, 7, my, Blocks.COBBLESTONE);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                for (int h = 1; h <= 8; h++) {
                    BlockPos at = mo.add(dx, h, dz);
                    boolean ring = dist > 2.1 && dist <= 3.2;
                    boolean ring2 = dist > 1.1 && dist <= 2.1;
                    if (h <= 2 && ring) {
                        set(world, at, Blocks.COBBLESTONE);
                    } else if (h <= 4 && (ring || (h == 4 && ring2))) {
                        set(world, at, h <= 3 ? Blocks.COBBLESTONE : Blocks.SPRUCE_LOG);
                    } else if (h <= 6 && ring2) {
                        set(world, at, h == 5 ? Blocks.SPRUCE_PLANKS : Blocks.SPRUCE_LOG);
                    } else if (dist <= 3.2 && h == 7) {
                        set(world, at, Blocks.OAK_FENCE);
                    } else if (dist <= 1.1 && h <= 8) {
                        set(world, at, h <= 6 ? ((dx + dz) % 2 == 0 ? Blocks.SPRUCE_PLANKS
                                : Blocks.SPRUCE_LOG) : Blocks.DARK_OAK_SLAB);
                    }
                }
            }
        }
        // The cap and the balcony rail.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 1.1 && dist <= 2.1) {
                    set(world, mo.add(dx, 9, dz), Blocks.DARK_OAK_STAIRS);
                }
            }
        }
        set(world, mo.add(0, 9, 0), Blocks.DARK_OAK_SLAB);
        set(world, mo.add(0, 1, 3), Blocks.AIR);
        set(world, mo.add(0, 2, 3), Blocks.AIR);
        set(world, mo.add(1, 1, 3), Blocks.STONE_BRICK_SLAB);
        set(world, mo.add(-1, 4, 2), Blocks.LANTERN);
        // Pinwheel of fence arms and canvas panels on the mill's south face.
        BlockPos hub = m0.add(0, my - mo.getY() + 7, 4);
        set(world, hub, Blocks.OAK_FENCE);
        for (int i = 1; i <= 4; i++) {
            set(world, hub.add(i, 0, 0), Blocks.OAK_FENCE);
            set(world, hub.add(-i, 0, 0), Blocks.OAK_FENCE);
            set(world, hub.add(0, i, 0), Blocks.OAK_FENCE);
            set(world, hub.add(0, -i, 0), Blocks.OAK_FENCE);
            if (i <= 3) {
                set(world, hub.add(i, 1, 0), Blocks.WHITE_WOOL);
                set(world, hub.add(-i, -1, 0), Blocks.WHITE_WOOL);
                set(world, hub.add(0, i + 1, 0), Blocks.OAK_FENCE);
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
        // A sister tower fell completely: a low ring of it remains.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 1.6 && dist <= 2.4) {
                    set(world, base.add(8 + dx, y + 1, -8 + dz),
                            world.random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE
                            : Blocks.COBBLESTONE);
                    if (world.random.nextInt(3) == 0) {
                        set(world, base.add(8 + dx, y + 2, -8 + dz), Blocks.COBBLESTONE_WALL);
                    }
                }
            }
        }
        set(world, base.add(8, y + 1, -6), Blocks.BARREL);
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

        // A gothic fence: iron bars between cobble piers, an arched gate
        // facing the road, corners marked with lantern pylons.
        wall(world, base.add(-8, y, -8), 17, 1, 1, Blocks.COBBLESTONE);
        wall(world, base.add(-8, y, 8), 17, 1, 1, Blocks.COBBLESTONE);
        wall(world, base.add(-8, y, -8), 1, 1, 17, Blocks.COBBLESTONE);
        wall(world, base.add(8, y, -8), 1, 1, 17, Blocks.COBBLESTONE);
        for (int i = 0; i < 17; i += 4) {
            set(world, base.add(-8 + i, y + 1, -8), Blocks.COBBLESTONE_WALL);
            set(world, base.add(-8 + i, y + 1, 8), i == 8 ? Blocks.AIR : Blocks.COBBLESTONE_WALL);
            set(world, base.add(-8, y + 1, -8 + i), Blocks.COBBLESTONE_WALL);
            set(world, base.add(8, y + 1, -8 + i), Blocks.COBBLESTONE_WALL);
        }
        for (int x = -7; x <= 7; x++) {
            if (Math.abs(x) > 1) {
                set(world, base.add(x, y + 1, 8), Blocks.IRON_BARS);
            }
        }
        for (int[] pier : new int[][]{{-2, 8}, {2, 8}}) {
            set(world, base.add(pier[0], y + 1, pier[1]), Blocks.COBBLESTONE);
            set(world, base.add(pier[0], y + 2, pier[1]), Blocks.COBBLESTONE);
            set(world, base.add(pier[0], y + 3, pier[1]), Blocks.STONE_BRICK_SLAB);
        }
        set(world, base.add(-2, y + 3, 7), Blocks.SOUL_LANTERN);
        set(world, base.add(2, y + 3, 7), Blocks.SOUL_LANTERN);

        // Three rows of graves: markers, crosses and pedestal tombs.
        for (int gx = -5; gx <= 4; gx += 3) {
            for (int gz = -4; gz <= 2; gz += 6) {
                BlockPos grave = base.add(gx, y + 1, gz);
                set(world, grave, Blocks.COBBLESTONE);
                int kind = world.random.nextInt(3);
                if (kind == 0) {
                    set(world, grave.up(), Blocks.COBBLESTONE_SLAB);
                } else if (kind == 1) {
                    set(world, grave.up(), Blocks.OAK_FENCE);
                    set(world, grave.up(2), Blocks.OAK_FENCE);
                    set(world, grave.up(2).east(), Blocks.OAK_FENCE);
                } else {
                    set(world, grave.up(), Blocks.STONE_BRICKS);
                    set(world, grave.up(2), Blocks.STONE_BRICK_SLAB);
                }
            }
        }
        // Two standing crosses mark the older burials.
        for (int[] cross : new int[][]{{-6, -2}, {6, 0}}) {
            BlockPos c = base.add(cross[0], y + 1, cross[1]);
            set(world, c, Blocks.STONE_BRICKS);
            set(world, c.up(), Blocks.STONE_BRICKS);
            set(world, c.up(2), Blocks.STONE_BRICK_SLAB);
            set(world, c.up(2).east(), Blocks.STONE_BRICK_SLAB);
            set(world, c.up(2).west(), Blocks.STONE_BRICK_SLAB);
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
        // The barbican guards the bridge head.
        barbican(world, base.add(-5, 0, -25));
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
        grandHangar(world, base.add(-16, 0, -13));
        grandHangar(world, base.add(6, 0, -13));
        fill(world, base.add(-15, y, 1), 31, 1, 5, ModBlocks.AIRSHIP_METAL);
        set(world, base.add(0, y + 1, 3), ModBlocks.REALM_BANNER);
        workshop(world, base.add(-15, 0, 9), 7, 6, ModBlocks.AIRSHIP_METAL, Blocks.SPRUCE_LOG);
        grandQuayCrane(world, base.add(-4, 0, 8));
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
        waterTower(world, base.add(12, 0, 8));
        orchard(world, base.add(13, 0, 12));
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
        // A battered footing ring one pace wider, so the tower sits like it
        // was built by masons who knew their business.
        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= radius + 1.5 && d > radius + 0.5) {
                    set(world, origin.add(dx, 0, dz), wall);
                }
            }
        }

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
            // A trim string course runs round the tower at two thirds.
            if (h == (height * 2) / 3) {
                for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                    for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist <= radiusF && dist > radiusF - 1.15) {
                            set(world, origin.add(dx, h, dz), trim);
                        }
                    }
                }
            }
            // The door: an arched opening and a step, facing south.
            if (h == 1 || h == 2) {
                set(world, origin.add(0, h, radius), Blocks.AIR);
            }
            if (h == 3) {
                set(world, origin.add(0, h, radius), trim);
            }
            if (roofed && h == height) {
                set(world, origin.add(0, h, radius - 1), trim);
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
        } else {
            // Machicolations: an overhanging stair ring below the merlons,
            // and the captain's banner over the door.
            for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist <= radiusF + 0.3 && dist > radiusF - 0.6) {
                        set(world, origin.add(dx, height, dz), Blocks.SPRUCE_STAIRS
                                .getDefaultState().with(HorizontalFacingBlock.FACING,
                                        facingFor(dx, dz)));
                    }
                }
            }
            set(world, origin.add(0, height + 1, radius), ModBlocks.REALM_BANNER);
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
            if (x % 4 == 2 && x > 1 && x < sizeX - 2) {
                set(world, corner.add(x, y + height - 2, 0), Blocks.AIR);
                set(world, corner.add(x, y + height - 2, sizeZ - 1), Blocks.AIR);
            }
            if (x % 5 == 0 && x > 0 && x < sizeX - 1) {
                // Pilaster ribs give the wall its vertical rhythm.
                for (int h = 1; h <= height; h++) {
                    set(world, corner.add(x, y + h, -1), wall);
                    set(world, corner.add(x, y + h, sizeZ), wall);
                }
            }
            if (x % 8 == 4) {
                // Working light on the yard face.
                set(world, corner.add(x, y + height - 1, 1), Blocks.LANTERN);
            }
        }
        for (int z = 0; z < sizeZ; z++) {
            wallColumn(world, corner.add(0, 0, z), y, height, wall, trim);
            wallColumn(world, corner.add(sizeX - 1, 0, z), y, height, wall, trim);
            if (z % 4 == 2 && z > 1 && z < sizeZ - 2) {
                // Loopholes: true arrow slits punched clean through the walk.
                set(world, corner.add(0, y + height - 2, z), Blocks.AIR);
                set(world, corner.add(sizeX - 1, y + height - 2, z), Blocks.AIR);
            }
            if (z % 5 == 0 && z > 0 && z < sizeZ - 1) {
                for (int h = 1; h <= height; h++) {
                    set(world, corner.add(-1, y + h, z), wall);
                    set(world, corner.add(sizeX, y + h, z), wall);
                }
            }
            if (z == 0 || z == sizeZ - 1) {
                // Bartizans lean out at the corners.
                for (int h = height + 1; h <= height + 3; h++) {
                    set(world, corner.add(0, y + h, z), h == height + 3 ? trim : wall);
                    set(world, corner.add(sizeX - 1, y + h, z), h == height + 3 ? trim : wall);
                }
            }
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
        // A wandered path leads in; a lean-to shelters the firewood.
        set(world, origin.add(0, 1, 3), Blocks.COBBLESTONE_SLAB);
        set(world, origin.add(1, 1, 4), Blocks.COBBLESTONE_SLAB);
        set(world, origin.add(-1, 1, 4), Blocks.MOSS_CARPET);
        set(world, origin.add(2, 1, 2), Blocks.OAK_FENCE);
        set(world, origin.add(2, 2, 2), Blocks.SPRUCE_STAIRS);
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
            // Tall windows with trim surrounds on the south face.
            if (h >= 3 && h <= 4) {
                for (int x = 2; x < sizeX - 2; x += 3) {
                    set(world, origin.add(x, h, sizeZ - 1), Blocks.GLASS_PANE);
                    set(world, origin.add(x, h, 0), h == 3 ? ModBlocks.ARROW_SLIT : Blocks.GLASS_PANE);
                    if (h == 3) {
                        set(world, origin.add(x, h - 1, sizeZ - 1), trim);
                        set(world, origin.add(x, h + 2, sizeZ - 1), trim);
                    }
                }
            }
            // Door gap on the south face.
            if (h <= 2) {
                set(world, origin.add(sizeX / 2, h, sizeZ - 1), Blocks.AIR);
            }
        }
        // Crenellated parapet with a turret rising at each corner.
        crenellate(world, origin.add(0, height + 1, 0), sizeX, 1, trim);
        crenellate(world, origin.add(0, height + 1, sizeZ - 1), sizeX, 1, trim);
        int[][] turretCorners = {{0, 0}, {sizeX - 1, 0}, {0, sizeZ - 1}, {sizeX - 1, sizeZ - 1}};
        for (int[] c : turretCorners) {
            for (int h = height + 1; h <= height + 3; h++) {
                set(world, origin.add(c[0], h, c[1]), h == height + 3 ? trim : wall);
            }
            set(world, origin.add(c[0], height + 4, c[1]), Blocks.SPRUCE_STAIRS);
        }
        // A grand stair climbs to the great door, banners flanking it.
        for (int step = 1; step <= 2; step++) {
            int stepY = y + step - 1;
            set(world, origin.add(sizeX / 2 - 1, stepY, sizeZ - 1 + (3 - step)),
                    Blocks.STONE_BRICK_STAIRS.getDefaultState()
                            .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            set(world, origin.add(sizeX / 2, stepY, sizeZ - 1 + (3 - step)),
                    Blocks.STONE_BRICK_STAIRS.getDefaultState()
                            .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            set(world, origin.add(sizeX / 2 + 1, stepY, sizeZ - 1 + (3 - step)),
                    Blocks.STONE_BRICK_STAIRS.getDefaultState()
                            .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        }
        set(world, origin.add(sizeX / 2 - 2, y + 3, sizeZ), ModBlocks.REALM_BANNER);
        set(world, origin.add(sizeX / 2 + 2, y + 3, sizeZ), ModBlocks.REALM_BANNER);

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
        // The great hall: a red carpet aisle and flanking banquet benches.
        for (int z = 2; z < sizeZ - 1; z++) {
            set(world, origin.add(sizeX / 2, floor, z), Blocks.RED_CARPET);
        }
        for (int x = 2; x < sizeX - 2; x += 2) {
            if (x != sizeX / 2) {
                set(world, origin.add(x, floor, sizeZ / 2 - 2), Blocks.OAK_SLAB);
                set(world, origin.add(x, floor, sizeZ / 2 + 2), Blocks.OAK_SLAB);
            }
        }
        for (int z = 3; z <= sizeZ - 3; z += 3) {
            set(world, origin.add(1, floor + 1, z), ModBlocks.REALM_BANNER);
            set(world, origin.add(sizeX - 2, floor + 1, z), ModBlocks.REALM_BANNER);
        }
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
        // Flower boxes under the front windows, hoods over the side ones.
        set(world, origin.add(sizeX / 2 - 2, 1, sizeZ), Blocks.AZALEA);
        set(world, origin.add(sizeX / 2 + 2, 1, sizeZ), Blocks.MOSS_CARPET);
        set(world, origin.add(-1, 4, sizeZ / 2), stair);
        set(world, origin.add(sizeX, 4, sizeZ / 2), stair);
        // The lived-in yard: lamp by the door, wood and goods against the
        // wall, a little fenced flower plot out front.
        set(world, origin.add(sizeX / 2 - 2, 1, sizeZ + 1), Blocks.OAK_FENCE);
        set(world, origin.add(sizeX / 2 - 2, 2, sizeZ + 1), Blocks.LANTERN);
        set(world, origin.add(sizeX / 2 + 2, 1, sizeZ + 1), Blocks.BARREL);
        set(world, origin.add(sizeX / 2 + 3, 1, sizeZ + 1), ModBlocks.SUPPLY_CRATE);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 2; dz <= 3; dz++) {
                boolean rim = dx == 1 || dz == 3;
                set(world, origin.add(sizeX / 2 + dx, 1, sizeZ + dz),
                        rim ? Blocks.OAK_FENCE : Blocks.AZALEA);
            }
        }

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
        // The throne approach: a gold inlay in the carpet, banners flanking.
        set(world, corner.add(sizeX / 2, y + 1, sizeZ / 2 - 1), Blocks.GOLD_BLOCK);
        set(world, corner.add(sizeX / 2 - 1, y + 1, sizeZ - 1), ModBlocks.REALM_BANNER);
        set(world, corner.add(sizeX / 2 + 1, y + 1, sizeZ - 1), ModBlocks.REALM_BANNER);
    }

    private static void tavern(ServerWorld world, BlockPos base) {
        house(world, base, 8, 7, ModBlocks.SHIP_PLANKS, Blocks.DARK_OAK_LOG,
                Blocks.DARK_OAK_STAIRS, ModBlocks.SHIP_PLANKS);
        int y = groundAt(world, base.getX() + 4, base.getZ() + 3);
        // Sign post and barrels out front.
        // The pub front: hanging shield sign, striped awning, benches,
        // barrels, and a lamp so the evening crowd can find the door.
        set(world, base.add(4, y + 1, 8), Blocks.OAK_FENCE);
        set(world, base.add(4, y + 2, 8), Blocks.OAK_FENCE);
        set(world, base.add(4, y + 3, 8), ModBlocks.REALM_BANNER);
        set(world, base.add(2, y + 3, 8), Blocks.RED_WOOL);
        set(world, base.add(3, y + 3, 8), Blocks.WHITE_WOOL);
        set(world, base.add(4, y + 3, 8), Blocks.RED_WOOL);
        set(world, base.add(3, y + 1, 8), Blocks.BARREL);
        set(world, base.add(5, y + 1, 8), Blocks.BARREL);
        set(world, base.add(1, y + 1, 8), Blocks.SPRUCE_STAIRS);
        set(world, base.add(1, y + 2, 8), Blocks.SPRUCE_STAIRS);
        set(world, base.add(4, y + 2, 7), Blocks.LANTERN);
        set(world, base.add(6, y + 1, 7), Blocks.AZALEA);
    }

    private static void saloon(ServerWorld world, BlockPos base, int sizeX, int sizeZ, Block wood, Block log) {
        house(world, base, sizeX, sizeZ, wood, log, Blocks.ACACIA_STAIRS, wood);
        int y = groundAt(world, base.getX() + sizeX / 2, base.getZ() + sizeZ / 2);
        // The false front: a tall facade wall capped in dark shingles.
        for (int x = 1; x < sizeX - 1; x++) {
            set(world, base.add(x, y + 4, sizeZ), wood);
            set(world, base.add(x, y + 5, sizeZ), x % 2 == 0 ? wood : log);
        }
        set(world, base.add(0, y + 5, sizeZ), log);
        set(world, base.add(sizeX - 1, y + 5, sizeZ), log);
        for (int x = 0; x < sizeX; x++) {
            set(world, base.add(x, y + 6, sizeZ), Blocks.DARK_OAK_SLAB);
        }
        // The balcony: a railed walk over the porch on posts.
        for (int x = 1; x < sizeX - 1; x++) {
            set(world, base.add(x, y + 3, sizeZ + 1), Blocks.OAK_FENCE);
        }
        set(world, base.add(1, y + 1, sizeZ + 1), Blocks.OAK_FENCE);
        set(world, base.add(1, y + 2, sizeZ + 1), Blocks.OAK_FENCE);
        set(world, base.add(sizeX - 2, y + 1, sizeZ + 1), Blocks.OAK_FENCE);
        set(world, base.add(sizeX - 2, y + 2, sizeZ + 1), Blocks.OAK_FENCE);
        set(world, base.add(sizeX / 2, y + 1, sizeZ + 1),
                Blocks.SPRUCE_STAIRS.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        set(world, base.add(sizeX / 2, y + 2, sizeZ + 1), Blocks.LANTERN);
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
        // The hoist: a ridge beam, a chain, and a cask mid-lift.
        set(world, origin.add(sizeX / 2, floor + 3, sizeZ / 2), Blocks.SPRUCE_LOG);
        set(world, origin.add(sizeX / 2, floor + 2, sizeZ / 2), Blocks.CHAIN);
        set(world, origin.add(sizeX / 2, floor + 1, sizeZ / 2), Blocks.BARREL);
        // Cargo rows along the loading face and lanterns on the eave.
        for (int x = 2; x < sizeX - 2; x += 3) {
            set(world, origin.add(x, floor, sizeZ - 3), ModBlocks.SUPPLY_CRATE);
            set(world, origin.add(x, floor, sizeZ - 4), Blocks.BARREL);
        }
        for (int x = 1; x < sizeX - 1; x += 4) {
            set(world, origin.add(x, floor + height, sizeZ - 1), Blocks.LANTERN);
        }
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
        // A metal flue over the furnace and a stacked rig for the yard.
        set(world, corner.add(2, y + 2, sizeZ - 1), ModBlocks.AIRSHIP_METAL);
        set(world, corner.add(2, y + 3, sizeZ - 1), ModBlocks.AIRSHIP_METAL);
        set(world, corner.add(2, y + 4, sizeZ - 1), Blocks.LANTERN);
        set(world, corner.add(sizeX - 2, y + 1, sizeZ - 2), Blocks.BARREL);
        set(world, corner.add(sizeX - 2, y + 2, sizeZ - 2), ModBlocks.SUPPLY_CRATE);
        set(world, corner.add(sizeX - 3, y + 1, sizeZ - 2), ModBlocks.SUPPLY_CRATE);
    }

    private static void stable(ServerWorld world, BlockPos corner, int sizeX, int sizeZ) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        packUnder(world, corner, sizeX, sizeZ, y, Blocks.COBBLESTONE);
        // The open shelter: log posts on stone bases, plank half-walls.
        int shelterZ = Math.max(3, sizeZ - 3);
        for (int x : new int[]{0, sizeX - 1}) {
            for (int z : new int[]{0, shelterZ - 1}) {
                set(world, corner.add(x, y, z), Blocks.COBBLESTONE);
                set(world, corner.add(x, y + 1, z), Blocks.OAK_LOG);
                set(world, corner.add(x, y + 2, z), Blocks.OAK_LOG);
            }
        }
        for (int x = 0; x < sizeX; x++) {
            set(world, corner.add(x, y + 1, 0), Blocks.SPRUCE_PLANKS);
        }
        for (int z = 1; z < shelterZ - 1; z++) {
            set(world, corner.add(0, y + 1, z), Blocks.SPRUCE_PLANKS);
            set(world, corner.add(sizeX - 1, y + 1, z), Blocks.SPRUCE_PLANKS);
        }
        // Deep shingled roof with an eave over the open front.
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < shelterZ; z++) {
                set(world, corner.add(x, y + 3, z), z == 0
                        ? Blocks.SPRUCE_PLANKS : Blocks.SPRUCE_SLAB);
            }
            set(world, corner.add(x, y + 2, 0), Blocks.SPRUCE_PLANKS);
        }
        // Stalls: hay bedding, a water trough that stays in its cauldron.
        set(world, corner.add(2, y + 1, 1), Blocks.HAY_BLOCK);
        set(world, corner.add(3, y + 1, 1), Blocks.CAULDRON);
        set(world, corner.add(4, y + 2, 1), Blocks.LANTERN);
        // The paddock strip: fenced with a gate gap, hay and a trough.
        for (int z = shelterZ; z < sizeZ; z++) {
            set(world, corner.add(0, y + 1, z), Blocks.OAK_FENCE);
            set(world, corner.add(sizeX - 1, y + 1, z), Blocks.OAK_FENCE);
        }
        for (int x = 0; x < sizeX; x++) {
            if (x != sizeX / 2) {
                set(world, corner.add(x, y + 1, sizeZ - 1), Blocks.OAK_FENCE);
            }
        }
        set(world, corner.add(1, y + 1, sizeZ - 2), Blocks.HAY_BLOCK);
        set(world, corner.add(sizeX - 2, y + 1, sizeZ - 2), Blocks.CAULDRON);
        // A lamp by the yard gate.
        set(world, corner.add(sizeX - 1, y + 2, sizeZ - 1), Blocks.OAK_FENCE);
        set(world, corner.add(sizeX - 1, y + 3, sizeZ - 1), Blocks.LANTERN);
    }

    /** A striped lighthouse: banded tower, gallery, a light that never sleeps. */
    private static void lighthouse(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        packUnder(world, origin.add(-3, 0, -3), 7, 7, y, Blocks.COBBLESTONE);
        // Banded drum: red and white courses, tapering by rings.
        for (int h = 1; h <= 12; h++) {
            int reach = h <= 3 ? 3 : (h <= 8 ? 2 : 1);
            boolean red = (h / 2) % 2 == 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    boolean shell = dist > reach - 1.1 && dist <= reach + 0.1;
                    boolean solid = dist <= reach + 0.1;
                    if (shell) {
                        set(world, origin.add(dx, h, dz), red
                                ? Blocks.RED_WOOL : Blocks.SPRUCE_PLANKS);
                    } else if (solid && h == 1) {
                        set(world, origin.add(dx, h, dz), Blocks.COBBLESTONE);
                    } else if (solid) {
                        set(world, origin.add(dx, h, dz), Blocks.AIR);
                    }
                }
            }
            if (h == 6) {
                set(world, origin.add(0, h, reach), Blocks.AIR);
                set(world, origin.add(0, h - 1, reach), Blocks.AIR);
            }
        }
        // The gallery: rail, the great light, a dark cap.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                set(world, origin.add(dx, 13, dz), rim ? Blocks.STONE_BRICKS : Blocks.GLOWSTONE);
                if (rim) {
                    set(world, origin.add(dx, 14, dz), Blocks.OAK_FENCE);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(world, origin.add(dx, 15, dz), Blocks.DARK_OAK_SLAB);
            }
        }
        set(world, origin.add(0, 12, 3), Blocks.STONE_BRICK_SLAB);
        set(world, origin.add(-2, 12, 3), Blocks.BARREL);
    }

    /** The smithy: brick walls under a towering chimney, and a working yard. */
    private static void forge(ServerWorld world, BlockPos corner) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), 8, 7);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, 8, 7, y, Blocks.COBBLESTONE);
        for (int h = 1; h <= 4; h++) {
            for (int x = 0; x <= 7; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean edge = x == 0 || z == 0 || x == 7 || z == 6;
                    BlockPos pos = origin.add(x, h, z);
                    if (h <= 4 && edge) {
                        boolean post = (x < 2 || x > 5) && (z < 2 || z > 4);
                        set(world, pos, post ? Blocks.SPRUCE_LOG
                                : (h == 1 ? Blocks.COBBLESTONE : Blocks.BRICKS));
                    } else if (h == 4 && !edge) {
                        set(world, pos, Blocks.SPRUCE_PLANKS);
                    } else {
                        set(world, pos, Blocks.AIR);
                    }
                }
            }
            // Open work bays to the south.
            if (h <= 3) {
                set(world, origin.add(2, h, 6), Blocks.AIR);
                set(world, origin.add(4, h, 6), Blocks.AIR);
            }
            if (h == 3) {
                set(world, origin.add(2, h, 6), Blocks.SPRUCE_SLAB);
                set(world, origin.add(4, h, 6), Blocks.SPRUCE_SLAB);
            }
        }
        // The dark shingled roof falls to a valley over the forge hall.
        for (int x = 0; x <= 7; x++) {
            set(world, origin.add(x, 5, 0), Blocks.SPRUCE_LOG);
            for (int z = 1; z <= 3; z++) {
                set(world, origin.add(x, 5, z), Blocks.DARK_OAK_STAIRS);
            }
            for (int z = 4; z <= 6; z++) {
                set(world, origin.add(x, 5, z), Blocks.DARK_OAK_STAIRS);
            }
        }
        set(world, origin.add(3, 5, 4), Blocks.DARK_OAK_SLAB);
        set(world, origin.add(4, 5, 4), Blocks.DARK_OAK_SLAB);
        // The chimney: full stone, smoking at the top.
        for (int h = 1; h <= 9; h++) {
            set(world, origin.add(5, h, 2), Blocks.STONE_BRICKS);
            set(world, origin.add(6, h, 2), Blocks.STONE_BRICKS);
            set(world, origin.add(5, h, 1), Blocks.STONE_BRICKS);
            set(world, origin.add(6, h, 1), Blocks.STONE_BRICKS);
        }
        set(world, origin.add(5, 10, 1), Blocks.STONE_BRICK_SLAB);
        set(world, origin.add(6, 10, 2), Blocks.STONE_BRICK_SLAB);
        set(world, origin.add(5, 9, 2), Blocks.CAMPFIRE);
        // The forge floor and the working yard.
        set(world, origin.add(6, 1, 3), Blocks.ANVIL);
        stockChest(world, origin.add(1, 1, 1), new ItemStack(Items.IRON_INGOT, 5),
                new ItemStack(Items.COAL, 8), new ItemStack(ModItems.ROYAL_COIN, 2));
        set(world, origin.add(1, 1, 5), Blocks.CAULDRON);
        set(world, origin.add(8, y + 1, 5), Blocks.COAL_BLOCK);
        set(world, origin.add(8, y + 1, 4), Blocks.COAL_BLOCK);
        set(world, origin.add(9, y + 1, 5), Blocks.BARREL);
        set(world, origin.add(8, y + 2, 4), ModBlocks.WEAPON_RACK);
    }

    /** A stilt fishing hut: rugged legs in the water, a ladder to the deck. */
    private static void fishingStilt(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX() + 2, base.getZ() + 2);
        BlockPos deck = new BlockPos(base.getX(), y + 2, base.getZ());
        // Legs: fences down into whatever is under the deck - water or air.
        for (int[] leg : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
            for (int down = 1; down <= 8; down--) {
                // (loop bounds kept simple below)
                break;
            }
            for (int down = 0; down < 8; down++) {
                BlockPos at = deck.add(leg[0], -down, leg[1]);
                if (at.getY() <= y - 3) {
                    break;
                }
                set(world, at, Blocks.OAK_FENCE);
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                set(world, deck.add(x, 0, z), ModBlocks.SHIP_PLANKS);
            }
        }
        // The hut: stripped corners, plaster infill, a lamp in the window.
        for (int h = 1; h <= 3; h++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
                    BlockPos pos = deck.add(x, h, z);
                    if (h == 3 && edge) {
                        set(world, pos, Blocks.SPRUCE_LOG);
                    } else if (edge) {
                        boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                        boolean door = z == 4 && x == 2 && h <= 2;
                        set(world, pos, corner ? Blocks.STRIPPED_OAK_LOG
                                : (door ? Blocks.AIR : Blocks.SPRUCE_PLANKS));
                    } else {
                        set(world, pos, Blocks.AIR);
                    }
                }
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                set(world, deck.add(x, 4, z), x % 2 == z % 2
                        ? Blocks.SPRUCE_STAIRS : Blocks.SPRUCE_SLAB);
            }
        }
        set(world, deck.add(2, 2, 1), Blocks.GLASS_PANE);
        set(world, deck.add(0, 1, 2), Blocks.LANTERN);
        set(world, deck.add(3, 1, 3), Blocks.BARREL);
        stockChest(world, deck.add(3, 1, 0), new ItemStack(Items.COOKED_COD, 4),
                new ItemStack(Items.STRING, 3));
        // The landing ladder.
        set(world, deck.add(4, 1, 2), net.minecraft.block.Blocks.LADDER
                .getDefaultState().with(net.minecraft.block.HorizontalFacingBlock.FACING,
                        net.minecraft.util.math.Direction.EAST));
    }

    /** A captured battering ram on its frame, waiting in the war yard. */
    private static void siegeRam(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX() + 3, base.getZ() + 1);
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        // The frame: four stripped posts with a chain-hung roof beam.
        for (int[] post : new int[][]{{0, 0}, {0, 2}, {7, 0}, {7, 2}}) {
            for (int h = 1; h <= 4; h++) {
                set(world, origin.add(post[0], h, post[1]), Blocks.STRIPPED_OAK_LOG);
            }
        }
        for (int x = 0; x <= 7; x++) {
            set(world, origin.add(x, 5, 0), x % 3 == 0 ? Blocks.STRIPPED_OAK_LOG
                    : Blocks.OAK_FENCE);
            set(world, origin.add(x, 5, 2), x % 3 == 0 ? Blocks.STRIPPED_OAK_LOG
                    : Blocks.OAK_FENCE);
        }
        // The ram: a banded trunk with an iron-shod head.
        for (int x = 0; x <= 6; x++) {
            set(world, origin.add(x + 1, 3, 1), x % 2 == 0 ? Blocks.OAK_WOOD
                    : Blocks.SPRUCE_LOG);
        }
        set(world, origin.add(1, 2, 1), Blocks.CHAIN);
        set(world, origin.add(1, 3, 1), Blocks.AIR);
        set(world, origin.add(7, 3, 1), Blocks.ANVIL);
        set(world, origin.add(0, 1, 1), Blocks.BARREL);
    }

    /** The grand quay crane: timber tower, railed jib, chain-hung cargo. */
    private static void grandQuayCrane(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX() + 2, base.getZ() + 2);
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        packUnder(world, origin, 5, 5, y, Blocks.STONE_BRICKS);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean pier = (x == 0 || x == 4) && (z == 0 || z == 4);
                set(world, origin.add(x, 1, z), pier ? Blocks.STONE_BRICKS
                        : Blocks.STONE_BRICK_SLAB);
            }
        }
        // The mast: paired posts with cross braces, a cap platform, a light.
        for (int[] post : new int[][]{{1, 1}, {3, 1}}) {
            for (int h = 2; h <= 9; h++) {
                set(world, origin.add(post[0], h, post[1]), Blocks.SPRUCE_LOG);
            }
        }
        for (int h = 3; h <= 8; h += 2) {
            set(world, origin.add(2, h, 1), Blocks.STRIPPED_OAK_LOG);
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 2; z++) {
                boolean rim = x == 0 || x == 4 || z == 0 || z == 2;
                set(world, origin.add(x, 10, z), rim ? Blocks.DARK_OAK_SLAB
                        : Blocks.SPRUCE_PLANKS);
            }
        }
        set(world, origin.add(2, 11, 0), Blocks.OAK_FENCE);
        set(world, origin.add(2, 11, 2), Blocks.OAK_FENCE);
        set(world, origin.add(0, 11, 1), Blocks.LANTERN);
        // The jib reaches over the water, railed, with the load on chains.
        for (int arm = 1; arm <= 8; arm++) {
            set(world, origin.add(2 + arm, 10, 1), Blocks.SPRUCE_LOG);
            set(world, origin.add(2 + arm, 11, 1), Blocks.OAK_FENCE);
            set(world, origin.add(2 + arm, 9, 1), Blocks.OAK_FENCE);
        }
        for (int h = 6; h <= 8; h++) {
            set(world, origin.add(10, h, 1), Blocks.CHAIN);
        }
        set(world, origin.add(10, 5, 1), ModBlocks.SUPPLY_CRATE);
        // The capstan the dockhands lean on, and cargo at the foot.
        set(world, origin.add(2, 2, 3), Blocks.HAY_BLOCK);
        set(world, origin.add(2, 3, 3), Blocks.SPRUCE_LOG);
        set(world, origin.add(4, 2, 4), Blocks.BARREL);
        set(world, origin.add(0, 2, 4), Blocks.BARREL);
        set(world, origin.add(5, 2, 0), ModBlocks.SUPPLY_CRATE);
    }

    /** A grand hangar: an arched metal vault with cradles, rigging, gearwork. */
    private static void grandHangar(ServerWorld world, BlockPos corner) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), 11, 10);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, 11, 10, y, ModBlocks.AIRSHIP_METAL);
        for (int h = 1; h <= 6; h++) {
            for (int x = 0; x <= 10; x++) {
                for (int z = 0; z <= 9; z++) {
                    boolean side = x == 0 || x == 10 || z == 0 || z == 9;
                    BlockPos pos = origin.add(x, h, z);
                    if (side) {
                        boolean frame = x % 5 == 0 || z % 9 == 0;
                        set(world, pos, frame ? ModBlocks.AIRSHIP_METAL : Blocks.SPRUCE_LOG);
                    } else {
                        set(world, pos, Blocks.AIR);
                    }
                }
            }
            if (h >= 4) {
                set(world, origin.add(1, h, 0), ModBlocks.AIRSHIP_METAL);
                set(world, origin.add(9, h, 0), ModBlocks.AIRSHIP_METAL);
                set(world, origin.add(1, h, 9), ModBlocks.AIRSHIP_METAL);
                set(world, origin.add(9, h, 9), ModBlocks.AIRSHIP_METAL);
            }
            if (h >= 5) {
                set(world, origin.add(2, h, 0), ModBlocks.AIRSHIP_METAL);
                set(world, origin.add(8, h, 0), ModBlocks.AIRSHIP_METAL);
                set(world, origin.add(2, h, 9), ModBlocks.AIRSHIP_METAL);
                set(world, origin.add(8, h, 9), ModBlocks.AIRSHIP_METAL);
            }
        }
        // The vault roof falls away from the crown.
        for (int x = 0; x <= 10; x++) {
            set(world, origin.add(x, 7, 0), Blocks.DARK_OAK_STAIRS);
            set(world, origin.add(x, 7, 9), Blocks.DARK_OAK_STAIRS);
            for (int z = 1; z <= 8; z++) {
                set(world, origin.add(x, 7, z), Blocks.DARK_OAK_SLAB);
            }
        }
        // The great door faces the apron.
        for (int h = 1; h <= 5; h++) {
            for (int x = 3; x <= 7; x++) {
                set(world, origin.add(x, h, 9), Blocks.AIR);
            }
        }
        set(world, origin.add(4, 1, 9), Blocks.SPRUCE_SLAB);
        set(world, origin.add(6, 1, 9), Blocks.SPRUCE_SLAB);
        // Inside: the ship cradle, rigging chains, the workbench row.
        for (int x = 3; x <= 7; x++) {
            set(world, origin.add(x, 1, 3), Blocks.STRIPPED_OAK_LOG);
            set(world, origin.add(x, 1, 5), Blocks.STRIPPED_OAK_LOG);
        }
        for (int z = 3; z <= 5; z++) {
            set(world, origin.add(3, 1, z), Blocks.STRIPPED_OAK_LOG);
            set(world, origin.add(7, 1, z), Blocks.STRIPPED_OAK_LOG);
        }
        for (int h = 4; h <= 5; h++) {
            set(world, origin.add(3, h, 4), Blocks.CHAIN);
            set(world, origin.add(7, h, 4), Blocks.CHAIN);
        }
        set(world, origin.add(1, 1, 2), Blocks.CRAFTING_TABLE);
        set(world, origin.add(1, 1, 3), Blocks.ANVIL);
        set(world, origin.add(9, 1, 2), Blocks.BARREL);
        set(world, origin.add(9, 1, 3), Blocks.BARREL);
        stockChest(world, origin.add(9, 1, 6), new ItemStack(ModItems.AIRSHIP_KIT),
                new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.COPPER_INGOT, 5));
        // The gearwork emblem over the door, and the banner above it.
        set(world, origin.add(4, 6, 8), Blocks.OAK_FENCE);
        set(world, origin.add(6, 6, 8), Blocks.OAK_FENCE);
        set(world, origin.add(5, 6, 8), Blocks.SPRUCE_LOG);
        set(world, origin.add(4, 7, 8), Blocks.OAK_FENCE);
        set(world, origin.add(6, 7, 8), Blocks.OAK_FENCE);
        set(world, origin.add(5, 7, 8), ModBlocks.REALM_BANNER);
        set(world, origin.add(1, 5, 8), Blocks.LANTERN);
        set(world, origin.add(9, 5, 8), Blocks.LANTERN);
    }

    /** The barbican: twin drum towers, a wide arch, a portcullis, a gate room. */
    private static void barbican(ServerWorld world, BlockPos corner) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), 11, 7);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, 11, 7, y, ModBlocks.CROWN_BRICK);
        // Twin drums with arrow slits, capped in trim with a banner each.
        for (int[] tower : new int[][]{{0, 0}, {8, 0}}) {
            BlockPos drum = origin.add(tower[0] + 1, 0, tower[1] + 1);
            for (int h = 1; h <= 8; h++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist > 0.8 && dist <= 1.6) {
                            set(world, drum.add(dx, h, dz), h == 8 ? ModBlocks.CROWN_BRICK
                                    : Blocks.STONE_BRICKS);
                        } else if (dist <= 0.8) {
                            set(world, drum.add(dx, h, dz), Blocks.AIR);
                        }
                    }
                }
                if (h == 3 || h == 6) {
                    set(world, drum.add(0, h, 1), Blocks.AIR);
                }
            }
            set(world, drum.add(0, 9, 0), ModBlocks.REALM_BANNER);
            set(world, drum.add(0, 8, 2), Blocks.LANTERN);
        }
        // The passage: a wide arch with a raised portcullis in the throat.
        for (int h = 1; h <= 5; h++) {
            for (int x = 3; x <= 7; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean vault = (x == 3 || x == 7) || h == 5;
                    set(world, origin.add(x, h, z), vault && h >= 2 ? Blocks.STONE_BRICKS
                            : Blocks.AIR);
                }
            }
        }
        for (int h = 1; h <= 4; h++) {
            set(world, origin.add(5, h, 3), Blocks.OAK_FENCE);
        }
        set(world, origin.add(4, 4, 3), Blocks.OAK_FENCE);
        set(world, origin.add(6, 4, 3), Blocks.OAK_FENCE);
        // The gate room over the passage, half timbered, glassy, warmed.
        for (int x = 3; x <= 7; x++) {
            for (int z = 0; z <= 6; z++) {
                boolean wall = x == 3 || x == 7 || z == 0 || z == 6;
                set(world, origin.add(x, 6, z), wall ? Blocks.SPRUCE_PLANKS : Blocks.AIR);
                set(world, origin.add(x, 7, z), wall ? Blocks.SPRUCE_LOG
                        : (z == 3 ? Blocks.GLASS_PANE : Blocks.AIR));
            }
        }
        for (int x = 3; x <= 7; x++) {
            set(world, origin.add(x, 8, 0), Blocks.DARK_OAK_STAIRS);
            set(world, origin.add(x, 8, 6), Blocks.DARK_OAK_STAIRS);
            for (int z = 1; z <= 5; z++) {
                set(world, origin.add(x, 8, z), Blocks.DARK_OAK_SLAB);
            }
        }
        set(world, origin.add(4, 7, 1), Blocks.LANTERN);
        stockChest(world, origin.add(6, 7, 1), new ItemStack(Items.ARROW, 12),
                new ItemStack(ModItems.ROYAL_COIN, 3));
    }

    /** A trading galleon at anchor: striped hull, two masts, full sails. */
    private static void galleon(ServerWorld world, BlockPos base) {
        int waterY = waterSurfaceAt(world, base);
        int y = waterY > 0 ? waterY : groundAt(world, base.getX(), base.getZ());
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        // The hull: 18 rows, tapering to the bow and stern, both rising.
        for (int i = 0; i < 18; i++) {
            int half;
            if (i == 0 || i == 17) {
                half = 0;
            } else if (i == 1 || i == 16) {
                half = 1;
            } else if (i == 2 || i == 3 || i == 14 || i == 15) {
                half = 1;
            } else {
                half = 2;
            }
            int shear = i < 3 ? 3 - i : (i > 14 ? i - 14 : 0);
            for (int z = -half; z <= half; z++) {
                // Keel and striped wales: dark, then light, then planks.
                set(world, origin.add(i, 0, z), ModBlocks.SHIP_PLANKS);
                set(world, origin.add(i, 1, z), Blocks.SPRUCE_LOG);
                set(world, origin.add(i, 2, z), ModBlocks.SHIP_PLANKS);
                set(world, origin.add(i, 3 + shear, z), Blocks.SPRUCE_PLANKS);
                if (half < 2) {
                    // Filled-in bow and stern faces.
                    set(world, origin.add(i, 3 + shear + 1, z), Blocks.SPRUCE_PLANKS);
                }
            }
        }
        // Bulwarks and the deck furniture.
        for (int i = 2; i <= 15; i++) {
            set(world, origin.add(i, 4, -2), Blocks.SPRUCE_LOG);
            set(world, origin.add(i, 4, 2), Blocks.SPRUCE_LOG);
            if (i % 6 == 2) {
                set(world, origin.add(i, 5, -2), Blocks.LANTERN);
            }
        }
        // The stern castle: raised deck, rail, the officer's lantern.
        for (int i = 15; i <= 17; i++) {
            for (int z = -1; z <= 1; z++) {
                set(world, origin.add(i, 5 + (i > 15 ? 1 : 0), z), Blocks.SPRUCE_PLANKS);
            }
        }
        set(world, origin.add(16, 7, -1), Blocks.OAK_FENCE);
        set(world, origin.add(16, 7, 1), Blocks.OAK_FENCE);
        set(world, origin.add(16, 8, 0), ModBlocks.REALM_BANNER);
        // Two masts with yard arms, white canvas, dark caps and pennants.
        int[][] masts = {{5, 10}, {12, 8}};
        for (int[] mast : masts) {
            int x = mast[0];
            int top = mast[1];
            for (int h = 1; h <= top; h++) {
                set(world, origin.add(x, 4 + h, 0), h >= top - 1
                        ? Blocks.SPRUCE_LOG : Blocks.OAK_FENCE);
            }
            for (int yard = top - 2; yard >= 4; yard -= 3) {
                for (int z = -2; z <= 2; z++) {
                    set(world, origin.add(x, 4 + yard, z), Blocks.OAK_FENCE);
                    if (Math.abs(z) < 2) {
                        set(world, origin.add(x, 4 + yard - 1, z), Blocks.WHITE_WOOL);
                    }
                }
            }
            set(world, origin.add(x, 4 + top + 1, 0), Blocks.SPRUCE_SLAB);
            set(world, origin.add(x, 4 + top + 2, 0), Blocks.WHITE_WOOL);
        }
        // Rigging: glass panes slanting from the mastheads to the rails.
        for (int k = 0; k < 4; k++) {
            set(world, origin.add(5 - k, 11 - k, -1), Blocks.WHITE_STAINED_GLASS_PANE);
            set(world, origin.add(12 - k, 9 - k, 1), Blocks.WHITE_STAINED_GLASS_PANE);
        }
        // The bowsprit reaches forward and up from the bow.
        set(world, origin.add(18, 5, 0), Blocks.SPRUCE_LOG);
        set(world, origin.add(19, 6, 0), Blocks.SPRUCE_LOG);
        set(world, origin.add(20, 7, 0), Blocks.SPRUCE_SLAB);
        set(world, origin.add(18, 7, 0), Blocks.WHITE_WOOL);
        // Cargo on deck: water for the crossing, powder for the rails.
        set(world, origin.add(8, 5, 1), Blocks.BARREL);
        set(world, origin.add(9, 5, 1), Blocks.BARREL);
        set(world, origin.add(8, 5, -1), ModBlocks.SUPPLY_CRATE);
        set(world, origin.add(14, 5, 0), Blocks.CAULDRON);
        // The anchor chain runs over the bow.
        set(world, origin.add(1, 3, 0), Blocks.CHAIN);
        set(world, origin.add(0, 2, 0), Blocks.CHAIN);
    }

    /** The village bakery's outdoor bread oven: brick dome, smoke, wood pile. */
    private static void bakeOven(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX() + 1, base.getZ() + 1);
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        // The dome: brick ring, a mouth with a fire glowing inside, a cap.
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                boolean rim = dx == 0 || dz == 0 || dx == 2 || dz == 2;
                set(world, origin.add(dx, 1, dz), rim ? Blocks.BRICKS : Blocks.AIR);
            }
        }
        set(world, origin.add(1, 1, 2), Blocks.AIR);
        set(world, origin.add(1, 1, 1), Blocks.CAMPFIRE);
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                set(world, origin.add(dx, 2, dz), Blocks.BRICKS);
            }
        }
        set(world, origin.add(1, 3, 0), Blocks.BRICKS);
        set(world, origin.add(1, 3, 1), Blocks.BRICKS);
        set(world, origin.add(1, 3, 2), Blocks.BRICKS);
        set(world, origin.add(1, 4, 1), Blocks.STONE_BRICK_SLAB);
        // The wood pile and the cooling shelf by the mouth.
        set(world, origin.add(3, 1, 1), Blocks.OAK_WOOD);
        set(world, origin.add(3, 2, 1), Blocks.OAK_WOOD);
        set(world, origin.add(3, 1, 2), Blocks.SPRUCE_SLAB);
        set(world, origin.add(4, 1, 2), Blocks.BARREL);
        // Fresh bread under the awning pole.
        set(world, origin.add(0, 1, 3), Blocks.OAK_FENCE);
        set(world, origin.add(0, 2, 3), Blocks.OAK_FENCE);
        set(world, origin.add(0, 3, 3), ModBlocks.REALM_BANNER);
    }

    /** The watchtower: a stone drum, a timber belfry, and a keeper's annex. */
    private static void buildWatchtower(ServerWorld world, BlockPos base) {
        int y = plateau(world, base, 15, 15, Blocks.GRASS_BLOCK);
        BlockPos drum = base.add(-3, 0, -3);
        packUnder(world, drum, 7, 7, y, Blocks.COBBLESTONE);
        // Mixed stone drum with moss working in, quoins at the corners.
        for (int h = 1; h <= 7; h++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean edge = x == 0 || z == 0 || x == 6 || z == 6;
                    BlockPos at = drum.add(x, h, z);
                    if (!edge) {
                        set(world, at, h == 7 ? Blocks.SPRUCE_PLANKS : Blocks.AIR);
                        continue;
                    }
                    boolean quoin = (x < 2 || x > 4) && (z < 2 || z > 4);
                    boolean mossy = (x + z + h) % 5 == 0;
                    set(world, at, quoin ? Blocks.STONE_BRICKS
                            : mossy ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                }
            }
            if (h == 1 || h == 2) {
                set(world, drum.add(3, h, 6), Blocks.AIR);
            }
            if (h == 3) {
                set(world, drum.add(3, h, 6), Blocks.SPRUCE_SLAB);
            }
            if (h == 5 || h == 6) {
                set(world, drum.add(3, h, 0), ModBlocks.ARROW_SLIT);
            }
        }
        // Door, torches, and the belfry: timber-framed with arched openings.
        set(world, drum.add(3, 1, 7), Blocks.SPRUCE_SLAB);
        set(world, drum.add(2, 1, 7), Blocks.OAK_FENCE);
        set(world, drum.add(4, 1, 7), Blocks.OAK_FENCE);
        set(world, drum.add(2, 2, 7), Blocks.TORCH);
        set(world, drum.add(4, 2, 7), Blocks.TORCH);
        for (int h = 8; h <= 10; h++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean edge = x == 0 || z == 0 || x == 6 || z == 6;
                    BlockPos at = drum.add(x, h, z);
                    boolean post = (x % 3 == 0) && (z % 3 == 0);
                    boolean arch = (x == 3 || z == 3) && h >= 9;
                    if (!edge) {
                        set(world, at, h == 10 ? Blocks.SPRUCE_PLANKS : Blocks.AIR);
                    } else if (arch && !post) {
                        set(world, at, h == 10 ? Blocks.SPRUCE_LOG : Blocks.AIR);
                    } else if (h == 8 || post) {
                        set(world, at, Blocks.SPRUCE_LOG);
                    } else {
                        set(world, at, Blocks.SPRUCE_PLANKS);
                    }
                }
            }
        }
        // Corbels, then the dark pyramid and its finial.
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                boolean edge = x == 0 || z == 0 || x == 6 || z == 6;
                if (edge) {
                    set(world, drum.add(x, 11, z), Blocks.SPRUCE_SLAB);
                }
            }
        }
        for (int ring = 0; ring < 3; ring++) {
            int lo = ring;
            int hi = 6 - ring;
            for (int x = lo; x <= hi; x++) {
                for (int z = lo; z <= hi; z++) {
                    boolean edge = x == lo || z == lo || x == hi || z == hi;
                    if (edge) {
                        set(world, drum.add(x, 12 + ring, z), Blocks.DARK_OAK_STAIRS);
                    }
                }
            }
        }
        set(world, drum.add(3, 15, 3), Blocks.SPRUCE_SLAB);
        set(world, drum.add(3, 16, 3), Blocks.OAK_FENCE);
        set(world, drum.add(3, 17, 3), ModBlocks.REALM_BANNER);
        // The keeper's annex: a little cottage with a smoking chimney.
        BlockPos annex = drum.add(8, 0, 2);
        house(world, annex, 5, 5, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
        int chimneyBase = groundAt(world, annex.getX() + 1, annex.getZ() + 1);
        for (int h = 1; h <= 8; h++) {
            set(world, annex.add(1, h, 1), Blocks.BRICKS);
        }
        set(world, annex.add(1, 7, 1), Blocks.CAMPFIRE);
        // The warning lantern hangs from a jetty out the tower's side.
        set(world, drum.add(-1, 5, 3), Blocks.SPRUCE_LOG);
        set(world, drum.add(-2, 5, 3), Blocks.CHAIN);
        set(world, drum.add(-2, 4, 3), Blocks.LANTERN);
        // Watch gear: firewood, signal horn crate, water barrel.
        set(world, base.add(4, y + 1, 4), Blocks.OAK_WOOD);
        set(world, base.add(4, y + 2, 4), Blocks.OAK_WOOD);
        stockChest(world, base.add(-4, y + 1, 5), new ItemStack(Items.BREAD, 3),
                new ItemStack(Items.ARROW, 10), new ItemStack(ModItems.ROYAL_COIN, 1));
        set(world, base.add(5, y + 1, 0), Blocks.BARREL);
        spawnGuard(world, base, y, BuildStyle.KNIGHT);
    }

    /** A timber-framed mine adit bored into the hillside beside it. */
    private static void mineAdit(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos mouth = new BlockPos(base.getX(), y, base.getZ());
        // Face the dig toward whichever horizon rises highest.
        int[] best = {4, 0};
        int bestTop = groundAt(world, mouth.getX() + 4, mouth.getZ());
        for (int[] dir : new int[][]{{-4, 0}, {0, 4}, {0, -4}}) {
            int top = groundAt(world, mouth.getX() + dir[0], mouth.getZ() + dir[1]);
            if (top > bestTop) {
                bestTop = top;
                best = dir;
            }
        }
        int dx = Integer.signum(best[0]);
        int dz = Integer.signum(best[1]);
        // The portal: posts, spreading cap, slab cap-sill, a torch.
        for (int z = -1; z <= 1; z++) {
            if (Math.abs(z) == 1) {
                for (int h = 1; h <= 3; h++) {
                    set(world, mouth.add(0, h, z), Blocks.OAK_LOG);
                }
            }
        }
        for (int z = -2; z <= 2; z++) {
            set(world, mouth.add(0, 4, z), z == 0 ? Blocks.OAK_WOOD : Blocks.OAK_LOG);
        }
        set(world, mouth.add(0, 5, -2), Blocks.SPRUCE_SLAB);
        set(world, mouth.add(0, 5, 2), Blocks.SPRUCE_SLAB);
        set(world, mouth.add(0, 2, 1), Blocks.TORCH);
        // The dig itself: carve only where the hill stands above the mouth.
        for (int step = 1; step <= 5; step++) {
            for (int z = -1; z <= 1; z++) {
                for (int h = 1; h <= 2; h++) {
                    BlockPos at = mouth.add(dx * step, h, dz * step);
                    if (groundAt(world, at.getX(), at.getZ()) >= y + h) {
                        set(world, at, Blocks.AIR);
                        if (step == 2 && z == 0 && h == 1) {
                            set(world, at, Blocks.TORCH);
                        }
                        if (step == 4 && z == 0 && h == 1) {
                            set(world, at, Blocks.COAL_ORE);
                        }
                    }
                }
            }
        }
        // The pit yard: cart, ore sacks, spare timber.
        stockChest(world, mouth.add(-dx * 2, 1, -dz * 2 + (dz == 0 ? 2 : 0)),
                new ItemStack(Items.COAL, 9), new ItemStack(Items.IRON_INGOT, 4),
                new ItemStack(ModItems.ROYAL_COIN, 1));
        set(world, mouth.add(dx * -2, 1, dz * -2), Blocks.BARREL);
        set(world, mouth.add(dx * -3, 1, dz * -3), Blocks.OAK_WOOD);
        set(world, mouth.add(dx * -3, 2, dz * -3), Blocks.OAK_WOOD);
        set(world, mouth.add(dx * -3, 1, dz * -3 + 1), Blocks.OAK_FENCE);
    }

    /** The lumber camp: bark-log stacks, a sawhorse, the foreman's shed. */
    private static void lumberCamp(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX() + 2, base.getZ() + 2);
        // The great stack: rising bark rows with a ladder up the face.
        for (int row = 0; row < 6; row++) {
            for (int h = 0; h <= row / 2; h++) {
                set(world, base.add(row, y + 1 + h, 0), Blocks.OAK_WOOD);
            }
        }
        set(world, base.add(1, y + 1, 1), Blocks.LADDER);
        set(world, base.add(1, y + 2, 1), Blocks.LADDER);
        // The sawhorse: fence legs, a slab top, the log being worked.
        for (int[] leg : new int[][]{{0, 0}, {3, 0}}) {
            set(world, base.add(leg[0], y + 1, leg[1] + 3), Blocks.OAK_FENCE);
        }
        set(world, base.add(0, y + 2, 3), Blocks.SPRUCE_SLAB);
        set(world, base.add(1, y + 2, 3), Blocks.SPRUCE_SLAB);
        set(world, base.add(2, y + 2, 3), Blocks.SPRUCE_SLAB);
        set(world, base.add(1, y + 3, 3), Blocks.OAK_WOOD);
        // The foreman's shed and the axe yard.
        house(world, base.add(-6, 0, -3), 5, 5, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
        campfire(world, base.add(-3, 0, 3));
        stockChest(world, base.add(-6, groundAt(world, base.getX() - 6, base.getZ() - 3) + 2, -3),
                new ItemStack(Items.STICK, 12), new ItemStack(Items.BREAD, 3),
                new ItemStack(ModItems.ROYAL_COIN, 1));
        set(world, base.add(6, y + 1, 3), ModBlocks.SUPPLY_CRATE);
        // A fresh stump with the ring of chips.
        set(world, base.add(3, y + 1, -3), Blocks.OAK_LOG);
        set(world, base.add(2, y + 1, -4), Blocks.MOSS_CARPET);
        set(world, base.add(4, y + 1, -4), Blocks.MOSS_CARPET);
    }

    /** The cove pier: a timber deck on piling legs, bonfire and guns at the end. */
    private static void covePier(ServerWorld world, BlockPos base) {
        int waterY = waterSurfaceAt(world, base);
        int y = waterY > 0 ? waterY : groundAt(world, base.getX() + 2, base.getZ() + 2);
        // The walk: 4 wide, 12 long, decked on log legs into the tide.
        for (int i = 0; i < 12; i++) {
            for (int z = 0; z <= 3; z++) {
                BlockPos plank = base.add(i, y, z);
                set(world, plank, ModBlocks.SHIP_PLANKS);
                if (i % 3 == 0 && (z == 0 || z == 3)) {
                    BlockPos leg = plank;
                    for (int down = 0; down < 6; down++) {
                        BlockPos below = leg.down();
                        if (!world.getFluidState(below).isEmpty()
                                || world.getBlockState(below).isAir()) {
                            set(world, below, Blocks.SPRUCE_LOG);
                        } else {
                            break;
                        }
                    }
                }
            }
            if (i % 4 == 2) {
                set(world, base.add(i, y + 1, 0), Blocks.OAK_FENCE);
                set(world, base.add(i, y + 1, 3), Blocks.OAK_FENCE);
            }
        }
        // The end platform: 7 by 7, railed, where the crew gathers.
        for (int i = 12; i <= 18; i++) {
            for (int z = -2; z <= 5; z++) {
                set(world, base.add(i, y, z), i == 12 || z == -2 || z == 5
                        ? Blocks.SPRUCE_LOG : ModBlocks.SHIP_PLANKS);
            }
        }
        for (int i = 12; i <= 18; i++) {
            for (int z = -2; z <= 5; z++) {
                if ((i == 12 || z == -2 || z == 5) && (i + z) % 2 == 0) {
                    set(world, base.add(i, y + 1, z), Blocks.OAK_FENCE);
                }
            }
        }
        // The bonfire in its own cask at the far end.
        set(world, base.add(15, y + 1, 1), Blocks.OAK_FENCE);
        set(world, base.add(15, y + 1, 2), Blocks.OAK_FENCE);
        set(world, base.add(16, y + 1, 1), Blocks.OAK_FENCE);
        set(world, base.add(16, y + 1, 2), Blocks.OAK_FENCE);
        set(world, base.add(15, y + 2, 1), Blocks.CAMPFIRE);
        set(world, base.add(14, y + 1, 1), Blocks.BARREL);
        set(world, base.add(14, y + 1, 3), Blocks.BARREL);
        // The guns: the realm's own cannons along the seaward rail.
        for (int gun = 0; gun < 3; gun++) {
            set(world, base.add(13 + gun * 2, y + 1, 5),
                    com.rivalrealms.block.ModBlocks.CANNON);
        }
        // A furled sail slung between two posts gives the crew shade.
        set(world, base.add(13, y + 3, -2), Blocks.OAK_FENCE);
        set(world, base.add(17, y + 3, -2), Blocks.OAK_FENCE);
        set(world, base.add(14, y + 4, -2), Blocks.WHITE_WOOL);
        set(world, base.add(15, y + 4, -2), Blocks.WHITE_WOOL);
        set(world, base.add(16, y + 4, -2), Blocks.WHITE_WOOL);
        set(world, base.add(17, y + 4, -2), Blocks.WHITE_WOOL);
        stockChest(world, base.add(12, y + 1, 1), new ItemStack(Items.GOLD_NUGGET, 7),
                new ItemStack(ModItems.ROYAL_COIN, 3), new ItemStack(Items.COOKED_COD, 5));
        set(world, base.add(18, y + 1, 0), Blocks.LANTERN);
    }

    /** The town gate: a white drum with brick inlay, terracotta pyramid, belfry. */
    private static void townGate(ServerWorld world, BlockPos center) {
        int y = lowestCorner(world, center.getX() - 3, center.getZ() - 3, 7, 7);
        BlockPos drum = new BlockPos(center.getX() - 3, y, center.getZ() - 3);
        packUnder(world, drum, 7, 7, y, Blocks.COBBLESTONE);
        for (int h = 1; h <= 7; h++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean edge = x == 0 || z == 0 || x == 6 || z == 6;
                    BlockPos at = drum.add(x, h, z);
                    boolean inArch = z >= 2 && z <= 4 && x >= 2 && x <= 4;
                    if (!edge && !inArch) {
                        set(world, at, Blocks.AIR);
                        continue;
                    }
                    if (inArch && h <= 4) {
                        set(world, at, Blocks.AIR);
                        continue;
                    }
                    boolean quoin = (x < 2 || x > 4) && (z < 2 || z > 4);
                    boolean brickPatch = (x * 7 + z * 3 + h * 5) % 11 == 0;
                    set(world, at, h == 7 ? Blocks.SPRUCE_PLANKS
                            : quoin ? Blocks.STONE_BRICKS
                            : brickPatch ? Blocks.BRICKS : Blocks.POLISHED_ANDESITE);
                }
            }
            // The passage: an arch up to a timber lintel, doors open.
            if (h == 4) {
                set(world, drum.add(3, h, 2), Blocks.SPRUCE_SLAB);
                set(world, drum.add(3, h, 4), Blocks.SPRUCE_SLAB);
            }
            if (h == 2 || h == 3) {
                set(world, drum.add(1, h, 3), Blocks.OAK_FENCE);
                set(world, drum.add(5, h, 3), Blocks.OAK_FENCE);
            }
            if (h == 5 || h == 6) {
                set(world, drum.add(3, h, 0), Blocks.GLASS_PANE);
                set(world, drum.add(3, h, 6), Blocks.GLASS_PANE);
            }
        }
        // The corbelled walk and the belfry: timber frame, white infill.
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                boolean edge = x == 0 || z == 0 || x == 6 || z == 6;
                if (edge) {
                    set(world, drum.add(x, 8, z), Blocks.SPRUCE_SLAB);
                }
            }
        }
        for (int h = 9; h <= 10; h++) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    boolean edge = x == 0 || z == 0 || x == 6 || z == 6;
                    BlockPos at = drum.add(x, h, z);
                    boolean post = x % 3 == 0 && z % 3 == 0;
                    boolean arch = (x == 3 || z == 3) && h == 10;
                    if (!edge) {
                        set(world, at, h == 10 ? Blocks.SPRUCE_PLANKS : Blocks.AIR);
                    } else if (post || !arch) {
                        set(world, at, post ? Blocks.SPRUCE_LOG : Blocks.POLISHED_ANDESITE);
                    } else {
                        set(world, at, Blocks.AIR);
                    }
                }
            }
        }
        // The great terracotta pyramid, four falling rings.
        for (int ring = 0; ring < 4; ring++) {
            int lo = ring;
            int hi = 6 - ring;
            for (int x = lo; x <= hi; x++) {
                for (int z = lo; z <= hi; z++) {
                    boolean edge = x == lo || z == lo || x == hi || z == hi;
                    if (edge) {
                        set(world, drum.add(x, 11 + ring, z), Blocks.ORANGE_TERRACOTTA);
                    }
                }
            }
        }
        set(world, drum.add(3, 15, 3), Blocks.ORANGE_TERRACOTTA);
        set(world, drum.add(3, 16, 3), Blocks.OAK_FENCE);
        set(world, drum.add(3, 17, 3), ModBlocks.REALM_BANNER);
        // The gate keeper's nook and the welcome light.
        set(world, drum.add(1, 1, 1), Blocks.BARREL);
        stockChest(world, drum.add(5, 1, 1), new ItemStack(ModItems.ROYAL_COIN, 2),
                new ItemStack(Items.BREAD, 2));
        set(world, drum.add(3, 2, 1), Blocks.LANTERN);
        set(world, drum.add(0, 2, 6), Blocks.LANTERN);
    }

    /** A wayside shrine: a hooded niche with flowers, where the road passes. */
    private static void waysideShrine(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
        // The pillar and its hooded top.
        set(world, pos.add(0, 1, 0), Blocks.STONE_BRICKS);
        set(world, pos.add(0, 2, 0), Blocks.STONE_BRICKS);
        set(world, pos.add(-1, 3, 0), Blocks.STONE_BRICK_SLAB);
        set(world, pos.add(0, 3, 0), Blocks.STONE_BRICK_SLAB);
        set(world, pos.add(1, 3, 0), Blocks.STONE_BRICK_SLAB);
        set(world, pos.add(0, 4, 0), Blocks.STONE_BRICK_SLAB);
        // The icon, the offering flowers, the traveller's candle.
        set(world, pos.add(0, 2, -1), ModBlocks.REALM_BANNER);
        set(world, pos.add(0, 1, -1), Blocks.AZALEA);
        set(world, pos.add(0, 1, 1), Blocks.CANDLE);
        set(world, pos.add(1, 1, 0), Blocks.MOSS_CARPET);
    }

    /** The pirate cove: a beached longboat, tents, treasure and a gibbet. */
    private static void buildCove(ServerWorld world, BlockPos center) {
        int y = groundAt(world, center.getX(), center.getZ());
        BlockPos origin = new BlockPos(center.getX(), y, center.getZ());
        plateau(world, origin.add(-8, 0, -8), 17, 17, Blocks.SAND);
        // The longboat, beached and stripped: keel in the sand, benches out.
        BlockPos boat = origin.add(4, 0, 5);
        for (int r = 0; r <= 8; r++) {
            set(world, boat.add(r, 0, 0), Blocks.SPRUCE_LOG);
            if (r == 0 || r == 8) {
                set(world, boat.add(r, 1, 0), Blocks.OAK_LOG);
                set(world, boat.add(r, 2, 0), Blocks.OAK_LOG);
                continue;
            }
            for (int z = -1; z <= 1; z++) {
                set(world, boat.add(r, 1, z), ModBlocks.SHIP_PLANKS);
                if (z != 0 || r == 1 || r == 7) {
                    set(world, boat.add(r, 2, z), r == 4 ? Blocks.SPRUCE_PLANKS
                            : Blocks.SPRUCE_LOG);
                }
            }
            if (r == 3 || r == 5) {
                set(world, boat.add(r, 2, -1), Blocks.SPRUCE_SLAB);
                set(world, boat.add(r, 2, 0), Blocks.SPRUCE_SLAB);
                set(world, boat.add(r, 2, 1), Blocks.SPRUCE_SLAB);
            }
        }
        set(world, boat.add(2, 2, 0), Blocks.BARREL);
        set(world, boat.add(6, 2, 0), ModBlocks.SUPPLY_CRATE);
        // A furled sail slung between stem and stern.
        set(world, boat.add(3, 3, 0), Blocks.WHITE_WOOL);
        set(world, boat.add(4, 3, 0), Blocks.WHITE_WOOL);
        set(world, boat.add(5, 3, 0), Blocks.WHITE_WOOL);
        // The campfire and its log seats.
        campfire(world, origin.add(-3, 0, -2));
        set(world, origin.add(-3, 1, -1), Blocks.SPRUCE_SLAB);
        set(world, origin.add(-2, 1, -3), Blocks.SPRUCE_SLAB);
        set(world, origin.add(-4, 1, -3), Blocks.SPRUCE_SLAB);
        // Two canvas tents: red and white, bedrolls at the mouth.
        tent(world, origin.add(-7, 0, -6), Blocks.RED_WOOL);
        tent(world, origin.add(-8, 0, 2), Blocks.WHITE_WOOL);
        // The treasure: a chest, a gold course, a candle stub.
        stockChest(world, origin.add(-2, 0, 3), new ItemStack(Items.GOLD_NUGGET, 12),
                new ItemStack(ModItems.ROYAL_COIN, 6), new ItemStack(Items.GOLD_INGOT, 3));
        set(world, origin.add(-1, 0, 3), Blocks.GOLD_BLOCK);
        set(world, origin.add(-1, 1, 3), Blocks.CANDLE);
        // The gibbet: an iron cage on posts, a skull inside, a chain above.
        BlockPos cage = origin.add(3, 0, -5);
        for (int h = 1; h <= 3; h++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    boolean edge = x == 0 || z == 0 || x == 2 || z == 2;
                    if (edge) {
                        set(world, cage.add(x, h, z), Blocks.IRON_BARS);
                    }
                }
            }
        }
        set(world, cage.add(1, 1, 1), ModBlocks.TROPHY_SKULL);
        set(world, cage.add(1, 4, 1), Blocks.CHAIN);
        // The captain's map table and rum store.
        set(world, origin.add(-6, 1, 6), ModBlocks.WAR_TABLE);
        set(world, origin.add(-4, 1, 6), Blocks.SPRUCE_SLAB);
        set(world, origin.add(-7, 1, 6), Blocks.SPRUCE_SLAB);
        set(world, origin.add(6, 0, -2), Blocks.BARREL);
        set(world, origin.add(7, 0, -1), Blocks.BARREL);
        set(world, origin.add(6, 0, -1), Blocks.BARREL);
        set(world, origin.add(7, 1, -2), Blocks.LANTERN);
        // Ragged red flags on poles around the camp.
        int[][] poles = {{-7, 7}, {7, 7}, {7, -7}};
        for (int[] pole : poles) {
            for (int h = 1; h <= 3; h++) {
                set(world, origin.add(pole[0], h, pole[1]), Blocks.OAK_FENCE);
            }
            set(world, origin.add(pole[0], 4, pole[1]), Blocks.RED_WOOL);
        }
    }

    /** A canvas tent: wool walls, a slab ridge, a bedroll at the mouth. */
    private static void tent(ServerWorld world, BlockPos corner, net.minecraft.block.Block wool) {
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                boolean edge = x == 0 || x == 2 || z == 0;
                if (edge) {
                    set(world, corner.add(x, 1, z), wool);
                    set(world, corner.add(x, 2, z), z == 0 ? wool : Blocks.AIR);
                }
            }
        }
        for (int x = 0; x <= 2; x++) {
            set(world, corner.add(x, 3, 0), wool);
            set(world, corner.add(x, 3, 1), Blocks.SPRUCE_SLAB);
        }
        set(world, corner.add(1, 1, 1), Blocks.SPRUCE_SLAB);
        set(world, corner.add(1, 1, 2), Blocks.WHITE_WOOL);
    }

    /** The lockup: a barred cell, the stocks, and the wanted board. */
    private static void prisonYard(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos corner = new BlockPos(base.getX() - 2, y, base.getZ() - 2);
        // The cell: five by five, mossy cobble, log quoins, barred door.
        for (int h = 1; h <= 3; h++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
                    if (!edge) {
                        set(world, corner.add(x, h, z), Blocks.AIR);
                        continue;
                    }
                    boolean quoin = (x == 0 || x == 4) && (z == 0 || z == 4);
                    boolean moss = (x * 3 + z * 5 + h) % 7 == 0;
                    set(world, corner.add(x, h, z), quoin ? Blocks.OAK_LOG
                            : moss ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                }
            }
        }
        set(world, corner.add(2, 1, 4), Blocks.IRON_BARS);
        set(world, corner.add(2, 2, 4), Blocks.IRON_BARS);
        set(world, corner.add(0, 2, 2), Blocks.IRON_BARS);
        set(world, corner.add(4, 2, 2), Blocks.IRON_BARS);
        roofFlat(world, corner, 5, 5, 4, Blocks.SPRUCE_SLAB);
        // A bracket lantern over the door; a cot and rations inside.
        set(world, corner.add(2, 4, 5), Blocks.OAK_FENCE);
        set(world, corner.add(2, 3, 5), Blocks.LANTERN);
        set(world, corner.add(1, 1, 1), Blocks.SPRUCE_SLAB);
        set(world, corner.add(1, 1, 2), Blocks.WHITE_WOOL);
        set(world, corner.add(2, 3, 2), Blocks.LANTERN);
        stockChest(world, corner.add(3, 1, 1), new ItemStack(Items.BREAD, 2),
                new ItemStack(Items.STRING, 1));
        // The stocks before the cell: posts, a seat, the restraint board.
        BlockPos stocks = corner.add(-3, 0, 2);
        set(world, stocks.add(0, 1, 0), Blocks.OAK_FENCE);
        set(world, stocks.add(2, 1, 0), Blocks.OAK_FENCE);
        set(world, stocks.add(1, 1, 0), Blocks.SPRUCE_SLAB);
        set(world, stocks.add(0, 2, 0), Blocks.SPRUCE_SLAB);
        set(world, stocks.add(2, 2, 0), Blocks.SPRUCE_SLAB);
        // The wanted bills, nailed up where the accused will see them.
        set(world, corner.add(-2, 1, -1), ModBlocks.NOTICE_BOARD);
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

    /** A waterfront arcade: stone arches at the quay, a timber loft above. */
    private static void quayArcade(ServerWorld world, BlockPos corner, int bays) {
        int spanX = bays * 3 + 1;
        int y = lowestCorner(world, corner.getX(), corner.getZ(), spanX, 6);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, spanX, 6, y, Blocks.STONE_BRICKS);
        for (int bay = 0; bay < bays; bay++) {
            int x0 = bay * 3;
            // Stone piers flanking an arched, open bay at the quay level.
            for (int h = 1; h <= 3; h++) {
                set(world, origin.add(x0, h, 0), Blocks.STONE_BRICKS);
                set(world, origin.add(x0 + 2, h, 0), Blocks.STONE_BRICKS);
            }
            set(world, origin.add(x0 + 1, 3, 0), Blocks.STONE_BRICK_SLAB);
            for (int z = 1; z <= 4; z++) {
                set(world, origin.add(x0 + 1, 1, z), Blocks.AIR);
                set(world, origin.add(x0 + 1, 2, z), Blocks.AIR);
                set(world, origin.add(x0, 1, z), z == 2 || z == 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
                set(world, origin.add(x0, 2, z), z == 2 || z == 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
            }
            // The loft: timber band, shuttered windows, storage inside.
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 5; z++) {
                    boolean wall = x == 0 || z == 0 || x == 2 || z == 5;
                    set(world, origin.add(x0 + x, 4, z), wall && (x == 0 || z == 0 || z == 5)
                            ? Blocks.SPRUCE_LOG : Blocks.SPRUCE_PLANKS);
                    set(world, origin.add(x0 + x, 5, z), wall ? Blocks.SPRUCE_PLANKS : Blocks.AIR);
                }
                set(world, origin.add(x0 + x, 4, 0), Blocks.SPRUCE_LOG);
            }
            set(world, origin.add(x0 + 1, 5, 5), Blocks.GLASS_PANE);
            set(world, origin.add(x0 + 1, 5, 2), Blocks.BARREL);
            set(world, origin.add(x0 + 1, 4, 1), Blocks.LANTERN);
        }
        // The long roof: dark shingles falling toward the water.
        for (int x = 0; x < spanX; x++) {
            set(world, origin.add(x, 4, 0), x % 3 == 0 ? Blocks.SPRUCE_LOG
                    : Blocks.SPRUCE_PLANKS);
            set(world, origin.add(x, 5, 0), Blocks.SPRUCE_PLANKS);
            set(world, origin.add(x, 6, 0), Blocks.DARK_OAK_STAIRS);
            set(world, origin.add(x, 5, 5), Blocks.SPRUCE_PLANKS);
            set(world, origin.add(x, 6, 5), Blocks.DARK_OAK_STAIRS);
            for (int z = 1; z <= 4; z++) {
                set(world, origin.add(x, 6, z), Blocks.DARK_OAK_SLAB);
            }
        }
        // End walls.
        for (int z = 1; z <= 4; z++) {
            set(world, origin.add(spanX - 1, 4, z), Blocks.SPRUCE_PLANKS);
            set(world, origin.add(spanX - 1, 5, z), Blocks.SPRUCE_PLANKS);
        }
    }

    /** A western water tower: legs, tank, shingled cap. */
    private static void waterTower(ServerWorld world, BlockPos base) {
        int y = lowestCorner(world, base.getX(), base.getZ(), 4, 4);
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        packUnder(world, origin, 4, 4, y, Blocks.COARSE_DIRT);
        for (int[] leg : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
            for (int h = 1; h <= 4; h++) {
                set(world, origin.add(leg[0], h, leg[1]), Blocks.OAK_FENCE);
            }
        }
        set(world, origin.add(0, 3, 0), Blocks.LADDER);
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                boolean rim = x == 0 || z == 0 || x == 3 || z == 3;
                set(world, origin.add(x, 5, z), rim ? Blocks.SPRUCE_PLANKS : Blocks.WATER);
                set(world, origin.add(x, 6, z), rim ? Blocks.SPRUCE_PLANKS : Blocks.AIR);
                set(world, origin.add(x, 7, z), Blocks.DARK_OAK_SLAB);
            }
        }
    }

    /** The village maypole: ribbons and all, on the green. */
    private static void maypole(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        for (int h = 1; h <= 6; h++) {
            set(world, base.add(0, y + h, 0), Blocks.OAK_FENCE);
        }
        set(world, base.add(0, y + 7, 0), Blocks.WHITE_WOOL);
        set(world, base.add(1, y + 6, 0), Blocks.RED_WOOL);
        set(world, base.add(-1, y + 5, 0), Blocks.WHITE_WOOL);
        set(world, base.add(0, y + 5, 1), Blocks.RED_WOOL);
        set(world, base.add(0, y + 6, -1), Blocks.WHITE_WOOL);
    }

    /** An open hay barn: posts, wool roof, wagon room. */
    private static void hayBarn(ServerWorld world, BlockPos corner) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), 7, 5);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, 7, 5, y, Blocks.COARSE_DIRT);
        for (int[] post : new int[][]{{0, 0}, {0, 4}, {6, 0}, {6, 4}, {3, 0}, {3, 4}}) {
            for (int h = 1; h <= 3; h++) {
                set(world, origin.add(post[0], h, post[1]), Blocks.SPRUCE_LOG);
            }
        }
        for (int x = 0; x <= 6; x++) {
            set(world, origin.add(x, 4, 0), x % 2 == 0 ? Blocks.SPRUCE_LOG : Blocks.WHITE_WOOL);
            set(world, origin.add(x, 4, 4), x % 2 == 0 ? Blocks.SPRUCE_LOG : Blocks.WHITE_WOOL);
            for (int z = 0; z <= 4; z++) {
                set(world, origin.add(x, 5, z), Blocks.WHITE_WOOL);
            }
        }
        set(world, origin.add(1, 1, 2), Blocks.HAY_BLOCK);
        set(world, origin.add(2, 1, 2), Blocks.HAY_BLOCK);
        set(world, origin.add(5, 1, 2), Blocks.BARREL);
        set(world, origin.add(1, 1, 3), Blocks.OAK_FENCE);
    }

    /** A farm pond: the watering hole every livestock yard deserves. */
    private static void pond(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX() + 1, base.getZ() + 1);
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                boolean rim = dx == 0 || dz == 0 || dx == 2 || dz == 2;
                set(world, base.add(dx, y, dz), rim ? Blocks.GRASS_BLOCK : Blocks.WATER);
                if (rim && (dx + dz) % 2 == 0) {
                    set(world, base.add(dx, y + 1, dz), Blocks.MOSS_CARPET);
                }
            }
        }
    }

    /** An orchard row: young trees staked and tied. */
    private static void orchard(ServerWorld world, BlockPos base) {
        for (int i = 0; i < 3; i++) {
            BlockPos tree = base.add(i * 3, 0, 0);
            int y = groundAt(world, tree.getX(), tree.getZ());
            set(world, tree.add(0, y + 1, 0), Blocks.OAK_LOG);
            set(world, tree.add(0, y + 2, 0), Blocks.OAK_LOG);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!(dx == 0 && dz == 0)) {
                        set(world, tree.add(dx, y + 3, dz), Blocks.OAK_LEAVES);
                    }
                }
            }
            set(world, tree.add(0, y + 4, 0), Blocks.OAK_LEAVES);
            set(world, tree.add(1, y + 1, 0), Blocks.OAK_FENCE);
        }
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
        for (int i = 0; i < length; i++) {
            // The arch: the deck rises toward mid-span and settles again.
            int rise = Math.round(2.2f * (float) Math.sin(Math.PI * i / Math.max(1, length - 1)));
            for (int w = -1; w <= 1; w++) {
                set(world, start.add(w, rise, -i), material);
            }
            // Lantern-capped parapets along both edges.
            if (i % 2 == 0) {
                set(world, start.add(-1, rise + 1, -i), Blocks.OAK_FENCE);
                set(world, start.add(1, rise + 1, -i), Blocks.OAK_FENCE);
                if (i % 4 == 0) {
                    set(world, start.add(-1, rise + 2, -i), Blocks.LANTERN);
                    set(world, start.add(1, rise + 2, -i), Blocks.LANTERN);
                }
            }
            // Piers down to honest ground, so the arch never spans air.
            if (i % 3 == 1) {
                BlockPos pierPos = start.add(0, rise, -i);
                foundation(world, pierPos.getX(), pierPos.getZ(), pierPos.getY() - 1, material);
                BlockPos east = pierPos.east(2);
                foundation(world, east.getX(), east.getZ(), east.getY() - 1, material);
                BlockPos west = pierPos.west(2);
                foundation(world, west.getX(), west.getZ(), west.getY() - 1, material);
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
        // Deep crenellations: tall merlons in pairs with slab merlons
        // between, the rhythm real fortifications are read by.
        for (int x = 0; x < lengthX; x++) {
            for (int z = 0; z < lengthZ; z++) {
                if ((x + z) % 4 < 2) {
                    set(world, start.add(x, 0, z), trim);
                } else if ((x + z) % 4 == 2) {
                    set(world, start.add(x, 0, z), net.minecraft.block.Blocks.STONE_BRICK_SLAB);
                }
            }
        }
    }

    private static void palisade(ServerWorld world, BlockPos corner, int sizeX, int sizeZ,
                                 Block wall, Block log) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        for (int x = 0; x < sizeX; x++) {
            for (int h = 1; h <= 3; h++) {
                set(world, corner.add(x, y + h, 0), h == 3 ? log : wall);
                set(world, corner.add(x, y + h, sizeZ - 1), h == 3 ? log : wall);
            }
            // Sharpened tips along the walk line.
            if (x % 2 == 0) {
                set(world, corner.add(x, y + 4, 0), Blocks.SPRUCE_SLAB);
                set(world, corner.add(x, y + 4, sizeZ - 1), Blocks.SPRUCE_SLAB);
            }
            // The gate: an open gap under a flanked lintel.
            if (x == sizeX / 2) {
                for (int h = 1; h <= 2; h++) {
                    set(world, corner.add(x, y + h, sizeZ - 1), Blocks.AIR);
                }
                set(world, corner.add(x, y + 3, sizeZ - 1), Blocks.SPRUCE_SLAB);
                set(world, corner.add(x - 1, y + 3, sizeZ - 1), log);
                set(world, corner.add(x + 1, y + 3, sizeZ - 1), log);
                set(world, corner.add(x - 1, y + 1, sizeZ - 2), Blocks.TORCH);
            }
        }
        for (int z = 0; z < sizeZ; z++) {
            for (int h = 1; h <= 3; h++) {
                set(world, corner.add(0, y + h, z), h == 3 ? log : wall);
                set(world, corner.add(sizeX - 1, y + h, z), h == 3 ? log : wall);
            }
            if (z % 2 == 0) {
                set(world, corner.add(0, y + 4, z), Blocks.SPRUCE_SLAB);
                set(world, corner.add(sizeX - 1, y + 4, z), Blocks.SPRUCE_SLAB);
            }
        }
    }

    private static void farmPlot(ServerWorld world, BlockPos corner, int sizeX, int sizeZ) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                BlockPos pos = corner.add(x, y, z);
                if (x == sizeX / 2) {
                    // A dry footpath between the rows - no open water to run.
                    set(world, pos, Blocks.DIRT_PATH);
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
        set(world, corner.add(-1, y + 1, sizeZ - 1), Blocks.HAY_BLOCK);
    }

    /** A street lantern on a stone footing - the settlement's night light. */
    private static void lampPost(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        set(world, base.add(0, y + 1, 0), Blocks.COBBLESTONE);
        set(world, base.add(0, y + 2, 0), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 3, 0), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 4, 0), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 5, 0), Blocks.LANTERN);
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
        // The draw-roof: corner posts, a shingled cap, the windlass chain.
        set(world, base.add(-1, y + 3, -1), Blocks.OAK_FENCE);
        set(world, base.add(1, y + 3, -1), Blocks.OAK_FENCE);
        set(world, base.add(-1, y + 3, 1), Blocks.OAK_FENCE);
        set(world, base.add(1, y + 3, 1), Blocks.OAK_FENCE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(world, base.add(dx, y + 4, dz), Blocks.SPRUCE_SLAB);
            }
        }
        set(world, base.add(0, y + 5, 0), Blocks.SPRUCE_SLAB);
        set(world, base.add(0, y + 2, 0), Blocks.CHAIN);
        set(world, base.add(0, y + 3, 0), Blocks.CHAIN);
    }

    private static void campfire(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        set(world, base.add(0, y + 1, 0), Blocks.CAMPFIRE);
        set(world, base.add(1, y + 1, 0), Blocks.OAK_LOG);
        set(world, base.add(-1, y + 1, 1), Blocks.BARREL);
    }

    private static void marketStall(ServerWorld world, BlockPos base, Block canopy, Block log) {
        int y = groundAt(world, base.getX() + 1, base.getZ() + 1);
        // The counter: a solid plank table on fence legs, goods laid out.
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                boolean rim = dx == 0 || dz == 0 || dx == 2 || dz == 3;
                set(world, base.add(dx, y + 1, dz), rim ? Blocks.SPRUCE_PLANKS
                        : Blocks.SPRUCE_SLAB);
            }
        }
        set(world, base.add(0, y + 2, 1), Blocks.HAY_BLOCK);
        set(world, base.add(2, y + 2, 1), ModBlocks.SUPPLY_CRATE);
        set(world, base.add(0, y + 2, 3), Blocks.BARREL);
        stockChest(world, base.add(2, y + 2, 3), new ItemStack(Items.BREAD, 5),
                new ItemStack(ModItems.ROYAL_JEWELRY, 1), new ItemStack(ModItems.ROYAL_COIN, 2));
        // Tall corner posts under a thick striped canopy.
        for (int[] post : new int[][]{{0, 0}, {2, 0}, {0, 3}, {2, 3}}) {
            set(world, base.add(post[0], y + 2, post[1]), log);
            set(world, base.add(post[0], y + 3, post[1]), log);
        }
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                set(world, base.add(dx, y + 4, dz), (dx + dz) % 2 == 0 ? canopy
                        : Blocks.WHITE_WOOL);
            }
        }
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
        // A post-and-slab canopy shades the crate row; a lamp for night work.
        int[][] posts = {{0, 2}, {0, 4}, {4, 2}, {4, 4}};
        for (int[] post : posts) {
            set(world, base.add(post[0], y + 1, post[1]), Blocks.OAK_FENCE);
            set(world, base.add(post[0], y + 2, post[1]), Blocks.OAK_FENCE);
        }
        for (int x = 0; x <= 4; x++) {
            set(world, base.add(x, y + 3, 2), Blocks.SPRUCE_SLAB);
            set(world, base.add(x, y + 3, 3), Blocks.SPRUCE_SLAB);
            set(world, base.add(x, y + 3, 4), Blocks.SPRUCE_SLAB);
        }
        set(world, base.add(2, y + 4, 3), Blocks.LANTERN);
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

    /** The quay crane: A-frame legs, a jib over the water, the load mid-lift. */
    private static void pierCrane(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
        // The mast and its A-frame legs.
        for (int h = 1; h <= 5; h++) {
            set(world, origin.add(0, h, 0), Blocks.SPRUCE_LOG);
        }
        set(world, origin.add(-1, 1, 0), Blocks.SPRUCE_LOG);
        set(world, origin.add(1, 1, 0), Blocks.SPRUCE_LOG);
        set(world, origin.add(-1, 2, 0), Blocks.SPRUCE_LOG);
        set(world, origin.add(1, 2, 0), Blocks.SPRUCE_LOG);
        set(world, origin.add(-1, 5, 0), Blocks.SPRUCE_SLAB);
        set(world, origin.add(1, 5, 0), Blocks.SPRUCE_SLAB);
        // The jib reaches over the quay; the load hangs from its tip.
        for (int jib = 1; jib <= 3; jib++) {
            set(world, origin.add(0, 5, jib), Blocks.SPRUCE_LOG);
        }
        set(world, origin.add(0, 6, 0), Blocks.SPRUCE_SLAB);
        set(world, origin.add(0, 6, 1), Blocks.LANTERN);
        set(world, origin.add(0, 4, 3), Blocks.CHAIN);
        set(world, origin.add(0, 3, 3), Blocks.CHAIN);
        set(world, origin.add(0, 2, 3), Blocks.BARREL);
        // The windlass barrel and the tally chest at the foot.
        set(world, origin.add(0, 1, 1), Blocks.BARREL);
        stockChest(world, origin.add(-1, 1, 1), new ItemStack(ModItems.ROYAL_JEWELRY, 2),
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
        // Open-front arched shed: iron sill, timber arch frames, vault roof.
        int y = lowestCorner(world, corner.getX(), corner.getZ(), sizeX, sizeZ);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, sizeX, sizeZ, y, ModBlocks.AIRSHIP_METAL);
        int crown = Math.min(5, sizeX / 2 + 2);
        for (int x = 0; x < sizeX; x++) {
            int dist = Math.min(x, sizeX - 1 - x);
            int arch = Math.max(1, crown - dist);
            // Arched timber frames at each gable.
            for (int z : new int[]{0, sizeZ - 1}) {
                for (int h = 1; h <= arch; h++) {
                    set(world, origin.add(x, h, z), dist == 0 || dist >= crown - 1
                            ? Blocks.SPRUCE_LOG : Blocks.SPRUCE_SLAB);
                }
            }
            // The vaulted metal roof follows the arch.
            for (int z = 0; z < sizeZ; z++) {
                set(world, origin.add(x, arch + 1, z), ModBlocks.AIRSHIP_METAL);
            }
        }
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

    /** The war chief's longhouse-tent: log ribs, hide roof, war-fire inside. */
    private static void warTent(ServerWorld world, BlockPos corner) {
        int y = lowestCorner(world, corner.getX(), corner.getZ(), 11, 7);
        BlockPos origin = new BlockPos(corner.getX(), y, corner.getZ());
        packUnder(world, origin, 11, 7, y, Blocks.COARSE_DIRT);
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 6; z++) {
                int ridge = 4 - Math.abs(x - 5) / 3 - (z == 0 || z == 6 ? 1 : 0);
                for (int h = 1; h <= Math.max(1, ridge); h++) {
                    boolean rib = x % 5 == 0;
                    if (h == ridge) {
                        set(world, origin.add(x, h, z), Blocks.GRAY_WOOL);
                    } else if (rib || z == 0 || z == 6) {
                        set(world, origin.add(x, h, z), rib ? Blocks.SPRUCE_LOG
                                : Blocks.WHITE_WOOL);
                    } else {
                        set(world, origin.add(x, h, z), Blocks.AIR);
                    }
                }
            }
        }
        // The door faces the fire, banner over the lintel.
        set(world, origin.add(5, 1, 6), Blocks.AIR);
        set(world, origin.add(5, 2, 6), Blocks.AIR);
        set(world, origin.add(5, 3, 6), ModBlocks.REALM_BANNER);
        set(world, origin.add(5, 1, 3), Blocks.CAMPFIRE);
        set(world, origin.add(3, 1, 2), ModBlocks.TROPHY_SKULL);
        set(world, origin.add(7, 1, 2), ModBlocks.WEAPON_RACK);
        set(world, origin.add(8, 1, 4), Blocks.BARREL);
    }

    /** A sharpened stake of the camp palisade, sometimes crowned with a skull. */
    private static void spike(ServerWorld world, BlockPos base, boolean skull) {
        int y = groundAt(world, base.getX(), base.getZ());
        set(world, base.add(0, y + 1, 0), Blocks.OAK_FENCE);
        set(world, base.add(0, y + 2, 0), Blocks.OAK_FENCE);
        if (skull) {
            set(world, base.add(0, y + 3, 0), ModBlocks.TROPHY_SKULL);
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
        // The tenders' path and a compost corner - no open water to run.
        for (int z = 0; z < sizeZ; z++) {
            set(world, corner.add(-1, 0, z), Blocks.DIRT_PATH);
        }
        set(world, corner.add(-1, 1, sizeZ - 1), Blocks.COMPOSTER);
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

    /** A hay stack: two broad at the shoulder, a slab for the thatch. */
    private static void haystack(ServerWorld world, BlockPos base) {
        int y = groundAt(world, base.getX(), base.getZ());
        BlockPos pos = new BlockPos(base.getX(), y + 1, base.getZ());
        set(world, pos, Blocks.HAY_BLOCK);
        set(world, pos.up(), Blocks.HAY_BLOCK);
        set(world, pos.east(), Blocks.HAY_BLOCK);
        set(world, pos.east().up(), Blocks.HAY_BLOCK);
        set(world, pos.up(2), Blocks.HAY_BLOCK);
        set(world, pos.up(3), Blocks.SPRUCE_SLAB);
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
        // The working yard: open hay barn, watering pond, young orchard.
        hayBarn(world, base.add(9, 0, -8));
        pond(world, base.add(1, 0, 1));
        orchard(world, base.add(-8, 0, -14));
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

        // The war chief's longhouse-tent: log ribs under a hide roof.
        warTent(world, base.add(-9, 0, -8));
        // One smaller hide tent for the raiding crew.
        BlockPos t = base.add(5, 0, -6);
        int ty = groundAt(world, t.getX(), t.getZ());
        set(world, t.add(0, ty + 1, 0), Blocks.OAK_FENCE);
        set(world, t.add(3, ty + 1, 0), Blocks.OAK_FENCE);
        for (int step = 0; step < 3; step++) {
            fill(world, t.add(0, ty + 1 + step, step), 4, 1, 1,
                    step == 1 ? Blocks.GRAY_WOOL : Blocks.WHITE_WOOL);
            fill(world, t.add(0, ty + 1 + step, 4 - step), 4, 1, 1,
                    step == 1 ? Blocks.GRAY_WOOL : Blocks.WHITE_WOOL);
        }
        set(world, t.add(1, ty + 2, 0), Blocks.WHITE_WOOL);
        set(world, t.add(1, ty + 2, 4), Blocks.WHITE_WOOL);
        // A spiked palisade rings the camp, gate to the south.
        for (int x = -9; x <= 9; x++) {
            spike(world, base.add(x, 0, -9), x % 5 == 0);
            if (Math.abs(x) > 1) {
                spike(world, base.add(x, 0, 9), x % 5 == 2);
            }
        }
        for (int z = -8; z <= 8; z++) {
            spike(world, base.add(-9, 0, z), z % 5 == 0);
            spike(world, base.add(9, 0, z), z % 5 == 3);
        }
        set(world, base.add(-2, groundAt(world, base.getX() - 2, base.getZ() + 9) + 3, 9),
                ModBlocks.REALM_BANNER);
        // A captured ram waits in the yard for the next soft gate.
        siegeRam(world, base.add(1, 0, 4));
        // Bench logs around the bone-fire, the war council's seats.
        for (int[] seat : new int[][]{{-3, -1}, {-3, 0}, {1, -1}, {1, 0}}) {
            set(world, base.add(seat[0], y + 1, seat[1]), Blocks.SPRUCE_STAIRS);
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
        waysideShrine(world, base.add(-4, 0, -3));
        scarecrow(world, base.add(-8, 0, 5));
        haystack(world, base.add(3, 0, -5));
        // The village green: a maypole, benches, and room to dance.
        maypole(world, base.add(0, 0, 4));
        set(world, base.add(-2, y + 1, 5), Blocks.SPRUCE_STAIRS);
        set(world, base.add(2, y + 1, 5), Blocks.SPRUCE_STAIRS);
        set(world, base.add(0, y + 1, 6), Blocks.AZALEA);

        lampPost(world, base.add(-6, 0, -1));
        lampPost(world, base.add(6, 0, -1));
        // The hamlet's bread comes out of this little dome oven.
        bakeOven(world, base.add(-6, 0, -5));
        waysideShrine(world, base.add(4, 0, 1));
        // The hamlet's timber comes from this little camp.
        lumberCamp(world, base.add(-13, 0, 3));
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
        // The outer processional ring, weathered but still walked.
        for (int[] step : new int[][]{{0, -6}, {1, -6}, {-1, -6}}) {
            set(world, origin.add(step[0], 1, step[1]), Blocks.STONE_BRICK_STAIRS
                    .getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        }
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 5.4 && dist <= 6.2) {
                    set(world, origin.add(dx, 1, dz), (dx + dz) % 7 == 0
                            ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
            }
        }
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

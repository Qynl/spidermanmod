package com.rivalrealms.item;

import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.world.RealmState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * The Camp Kit: place it and a life takes root. A tent, a fire, a bed, a
 * chest of basics, a map table and a weapon rack — claimed in the world as
 * a real settlement that grows into a landmark as you return to it.
 */
public class CampKitItem extends Item {
    public CampKitItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        if (world instanceof ServerWorld serverWorld) {
            BlockPos center = BlockPos.ofFloored(user.getX(), user.getY(), user.getZ())
                    .offset(user.getHorizontalFacing(), 4);
            if (!serverWorld.getChunkManager().isChunkLoaded(center.getX() >> 4, center.getZ() >> 4)) {
                user.sendMessage(Text.literal("Too close to the edge of the world. Move on first.")
                        .formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            RealmState state = RealmState.get(serverWorld);
            if (!state.canClaimBase(center)) {
                user.sendMessage(Text.literal("Another camp or settlement stands too close.")
                        .formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            build(serverWorld, center, user);
            RealmState.BaseRecord camp = state.claimCustomBase(center, user.getUuid(), "Independent",
                    user.getName().getString() + "'s Camp");
            if (camp != null) {
                camp.setFamineCycles(0);
                state.chronicle(serverWorld.getTime(), user.getName().getString()
                        + " raised a camp by the road. Every traveler needs one true place.", false);
                user.sendMessage(Text.literal("Your camp stands. Return to it over the days and it will grow.")
                        .formatted(Formatting.GOLD), false);
            }
            serverWorld.playSound(null, center.getX(), center.getY(), center.getZ(),
                    SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 1.0f, 0.8f);
            user.getItemCooldownManager().set(this, 100);
            if (!user.isCreative()) {
                stack.decrement(1);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    private void build(ServerWorld world, BlockPos center, PlayerEntity owner) {
        Direction facing = owner.getHorizontalFacing();
        // Ground cloth of coarse dirt so the camp reads as a place.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos at = surface(world, center.add(dx, 0, dz));
                world.setBlockState(at, Blocks.COARSE_DIRT.getDefaultState());
            }
        }
        // The fire, the pot, the warmth.
        set(world, center, Blocks.CAMPFIRE);
        set(world, center.west(2).north(2), ModBlocks.HEARTH_LANTERN);
        set(world, center.east(2).south(2), ModBlocks.HEARTH_LANTERN);
        // Bed roll beside the fire, foot to the flames.
        BlockPos bedFoot = surface(world, center.south(2));
        BlockState foot = Blocks.RED_BED.getDefaultState()
                .with(BedBlock.FACING, Direction.SOUTH)
                .with(BedBlock.PART, BedPart.FOOT);
        BlockState head = Blocks.RED_BED.getDefaultState()
                .with(BedBlock.FACING, Direction.SOUTH)
                .with(BedBlock.PART, BedPart.HEAD);
        world.setBlockState(bedFoot, foot);
        world.setBlockState(bedFoot.south(), head);
        // Storage, map table, weapon rack.
        set(world, center.west(2), Blocks.CHEST);
        set(world, center.north(2), ModBlocks.WAR_TABLE);
        set(world, center.east(2), ModBlocks.WEAPON_RACK);
        set(world, center.north(3), ModBlocks.SUPPLY_CRATE);
        // One tent: fence poles with a wool ridge — home enough for now.
        buildTent(world, surface(world, center.east(3).north(3)));
        buildTent(world, surface(world, center.west(3).south(3)));
    }

    private void buildTent(ServerWorld world, BlockPos t) {
        set(world, t, Blocks.OAK_FENCE);
        set(world, t.east(3), Blocks.OAK_FENCE);
        for (int step = 0; step < 3; step++) {
            fillRow(world, t.up(1 + step).south(step), 4,
                    step == 1 ? Blocks.GRAY_WOOL : Blocks.WHITE_WOOL);
            fillRow(world, t.up(1 + step).north(step), 4,
                    step == 1 ? Blocks.GRAY_WOOL : Blocks.WHITE_WOOL);
        }
        set(world, t.up(2), Blocks.WHITE_WOOL);
        set(world, t.east(3).up(2), Blocks.WHITE_WOOL);
    }

    private void fillRow(ServerWorld world, BlockPos start, int length, net.minecraft.block.Block block) {
        for (int i = 0; i < length; i++) {
            set(world, start.east(i), block);
        }
    }

    private void set(ServerWorld world, BlockPos pos, net.minecraft.block.Block block) {
        BlockPos at = surface(world, pos);
        world.setBlockState(at, block.getDefaultState());
    }

    private BlockPos surface(ServerWorld world, BlockPos requested) {
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, requested);
        return top.getY() >= world.getBottomY() && top.getY() < world.getTopY() ? top : requested;
    }
}

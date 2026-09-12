package com.rivalrealms.block;

import com.rivalrealms.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A placeable field cannon. Load it with a cannonball, then use it again to
 * fire a real physics ball down its facing direction — with recoil smoke, a
 * thunderclap, and no aim assist. The siege engine for player fortresses and
 * the answer to raider sails on the horizon.
 */
public class CannonBlock extends HorizontalFacingBlock {
    public static final BooleanProperty LOADED = BooleanProperty.of("loaded");

    private static final VoxelShape CARRIAGE = Block.createCuboidShape(3.0, 0.0, 5.0, 13.0, 5.0, 12.0);
    private static final VoxelShape BARREL = Block.createCuboidShape(4.5, 4.0, 1.0, 11.5, 11.0, 14.0);
    private static final VoxelShape SHAPE = VoxelShapes.union(CARRIAGE, BARREL);

    public CannonBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH).with(LOADED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return this.getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        if (!state.get(LOADED) && held.isOf(ModItems.CANNONBALL)) {
            if (!world.isClient) {
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                world.setBlockState(pos, state.with(LOADED, true), Block.NOTIFY_ALL);
                world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.8f, 0.7f);
            }
            return ActionResult.SUCCESS;
        }
        if (state.get(LOADED)) {
            if (!world.isClient) {
                fire(world, pos, state);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** Muzzle flash, thunder, recoil shake, and a live cannonball downrange. */
    private void fire(World world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state.with(LOADED, false), Block.NOTIFY_ALL);
        Direction facing = state.get(FACING);
        Vec3d muzzle = new Vec3d(
                pos.getX() + 0.5 + facing.getOffsetX() * 1.1,
                pos.getY() + 0.7,
                pos.getZ() + 0.5 + facing.getOffsetZ() * 1.1);

        com.rivalrealms.entity.CannonballEntity ball = new com.rivalrealms.entity.CannonballEntity(
                com.rivalrealms.entity.ModEntities.CANNONBALL, world, null);
        ball.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z, 0.0f, 0.0f);
        ball.setVelocity(facing.getOffsetX() * 1.15, 0.16, facing.getOffsetZ() * 1.15);
        world.spawnEntity(ball);

        world.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.BLOCKS, 1.2f, 0.65f);
        world.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 0.5f, 1.5f);
        ServerWorld serverWorld = (ServerWorld) world;
        serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                muzzle.x, muzzle.y, muzzle.z, 12, 0.2, 0.1, 0.2, 0.03);
        serverWorld.spawnParticles(ParticleTypes.FLAME,
                muzzle.x, muzzle.y, muzzle.z, 6, 0.15, 0.05, 0.15, 0.06);
        serverWorld.spawnParticles(ParticleTypes.POOF,
                pos.getX() + 0.5 - facing.getOffsetX() * 0.6, pos.getY() + 0.6,
                pos.getZ() + 0.5 - facing.getOffsetZ() * 0.6, 4, 0.2, 0.1, 0.2, 0.02);
    }

}

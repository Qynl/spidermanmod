package com.rivalrealms.item;

import com.rivalrealms.block.ModBlocks;
import com.rivalrealms.world.LivingRealm;
import com.rivalrealms.world.RealmState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * The Treasure Map: never "go to X". Reading it gives you a cryptic line —
 * a landmark, a bearing, a pace count — and somewhere out there a mound of
 * dark earth covers a chest. Green motes gather when the X is under your
 * boots. You still have to be the one who finds it.
 */
public class TreasureMapItem extends Item {
    private static final String[] DIRECTIONS = {"north", "north-east", "east", "south-east",
            "south", "south-west", "west", "north-west"};

    public TreasureMapItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        if (world instanceof ServerWorld serverWorld) {
            // Somewhere between half and two hundred paces, in a random bearing.
            double angle = serverWorld.random.nextDouble() * Math.PI * 2.0;
            int distance = 56 + serverWorld.random.nextInt(120);
            BlockPos target = surface(serverWorld, user.getBlockPos().add(
                    (int) Math.round(Math.cos(angle) * distance), 0,
                    (int) Math.round(Math.sin(angle) * distance)));
            if (!serverWorld.getChunkManager().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) {
                user.sendMessage(Text.literal("The map's ink has not dried where you stand. Try again.")
                        .formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }

            // Bury it: a chest under a mound of dark earth.
            BlockPos hole = target.down();
            serverWorld.setBlockState(target, Blocks.COARSE_DIRT.getDefaultState());
            com.rivalrealms.world.StructureBuilder.buryTreasure(serverWorld, hole,
                    new ItemStack(ModItems.ROYAL_COIN, 2 + serverWorld.random.nextInt(4)),
                    new ItemStack(Items.GOLD_NUGGET, 6 + serverWorld.random.nextInt(8)),
                    new ItemStack(ModItems.ROYAL_JEWELRY, 1),
                    serverWorld.random.nextInt(4) == 0 ? new ItemStack(Items.DIAMOND) : ItemStack.EMPTY);

            // The cryptic line: nearest settlement, a bearing, a pace count.
            RealmState state = RealmState.get(serverWorld);
            String origin = "the open wilds";
            String direction = DIRECTIONS[serverWorld.random.nextInt(DIRECTIONS.length)];
            for (RealmState.BaseRecord base : state.bases()) {
                if (!base.abandoned()) {
                    origin = base.name();
                    direction = bearing(base.center(), target);
                    break;
                }
            }
            String pace = distance < 90 ? "half a morning's walk" : "a long day's walk";
            String riddle = switch (serverWorld.random.nextInt(3)) {
                case 0 -> "where the ground was struck long ago";
                case 1 -> "beneath a mound of dark earth the old road-builders left";
                default -> "where no shepherd grazes his flock";
            };
            user.sendMessage(Text.literal("You study the worn map... \"From " + origin
                    + ", bear " + direction + ". " + pace + ", " + riddle + ". Dig where the green motes gather.\"")
                    .formatted(Formatting.GOLD), false);
            serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 0.7f);
            LivingRealm.registerTreasure(user.getUuid(), hole);
            state.chronicle(serverWorld.getTime(), user.getName().getString()
                    + " set out following an old treasure map.", false);
            user.getItemCooldownManager().set(this, 100);
            if (!user.isCreative()) {
                stack.decrement(1);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /** Compass bearing in words: 0° = north, 90° = east, clockwise. */
    private String bearing(BlockPos from, BlockPos to) {
        double deg = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        int index = (int) Math.round(deg / 45.0);
        return DIRECTIONS[((index % 8) + 8) % 8];
    }

    private BlockPos surface(ServerWorld world, BlockPos requested) {
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, requested);
        return top.getY() >= world.getBottomY() && top.getY() < world.getTopY() ? top : requested;
    }
}

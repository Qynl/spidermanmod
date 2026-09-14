package com.manhunt.entity;

import com.manhunt.Manhunt;
import com.manhunt.ManhuntSounds;
import com.manhunt.world.HunterVoice;
import com.manhunt.world.ManhuntState;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The resident: a {@link PlayerEntity} in every mechanical sense - player
 * inventory, hunger manager, attack cooldowns, armor slots, bow, bed - driven
 * by a haunting state machine instead of a network connection.
 *
 * <p>He lives first: gathers, crafts, smelts, eats, equips. Then, when you
 * make the mistake of looking at him for three full seconds - or on the third
 * dawn, if you never look - the hunt ignites and escalates through WATCH,
 * STALK and HUNT. He walks; he never sprints. He does not need to.
 */
public class HunterEntity extends PlayerEntity {

    public enum Phase { HAUNT, WATCH, STALK, HUNT, TRAP, CAMP }

    /** What he works towards, in order. Tags keep him compatible with mods. */
    private static final List<Object> GEAR_PLAN = List.of(
            net.minecraft.item.Items.OAK_PLANKS,
            net.minecraft.item.Items.CRAFTING_TABLE,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "wooden_pickaxes")),
            net.minecraft.item.Items.FURNACE,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "stone_pickaxes")),
            net.minecraft.item.Items.STONE_SWORD,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "iron_pickaxes")),
            net.minecraft.item.Items.IRON_SWORD,
            net.minecraft.item.Items.IRON_HELMET,
            net.minecraft.item.Items.IRON_CHESTPLATE,
            net.minecraft.item.Items.IRON_LEGGINGS,
            net.minecraft.item.Items.IRON_BOOTS,
            net.minecraft.item.Items.BOW,
            net.minecraft.item.Items.ARROW,
            net.minecraft.item.Items.SHIELD,
            net.minecraft.item.Items.RED_BED,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "diamond_pickaxes")),
            net.minecraft.item.Items.DIAMOND_SWORD);

    private Phase phase = Phase.HAUNT;
    private Identifier lastDimension;
    private BlockPos mineTarget;
    private float mineProgress;
    private int scanCooldown;
    private int craftCooldown;
    private int smeltCounter;
    private int eatTimer;
    private int bowCooldown;
    private int stareTimer;
    private Vec3d wanderPoint;
    private int wanderTimer;
    private boolean ringBuilt;
    private boolean saidSight;
    private int nightMark = -1;
    private int mirrorTicks;
    private BlockPos vigilTarget;
    private List<Recipe<?>> craftingRecipes;
    private List<Recipe<?>> smeltingRecipes;

    public HunterEntity(EntityType<? extends PlayerEntity> type, World world) {
        super(world, BlockPos.ORIGIN, 0.0f, new GameProfile(UUID.randomUUID(), "TheHunter"));
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("The Hunter").formatted(Formatting.DARK_RED);
    }

    public Phase phase() {
        return phase;
    }

    // ------------------------------------------------------------ lifecycle --

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }
        ServerWorld world = (ServerWorld) getWorld();
        getHungerManager().update(this);
        ManhuntState state = ManhuntState.get(world);
        PlayerEntity player = nearestPlayer();
        trackNight(world, state);
        syncPhase(world, state, player);
        tickMirror(world, state, player);
        tickSigns(world, state, player);
        scareAnimals();
        tickEating(world);
        pickupNearby();
        switch (phase) {
            case HAUNT -> liveLikeAPlayer(world, state, player);
            case WATCH -> watchPlayer(world, state, player);
            case STALK -> stalkPlayer(world, state, player);
            case HUNT -> huntPlayer(world, state, player);
            case TRAP -> guardPortal(state);
            case CAMP -> campSite(state);
        }
        if (--craftCooldown <= 0) {
            craftCooldown = 40;
            tryCraftNext();
        }
        if (age % 200 == 0) {
            smeltOnce();
        }
        equipBest();
    }

    private void trackNight(ServerWorld world, ManhuntState state) {
        int night = (int) (world.getTimeOfDay() / 24000L);
        if (night != nightMark) {
            nightMark = night;
            state.night = night;
            saidSight = false;
            if (state.ignited && state.act == ManhuntState.ACT_WATCH) {
                HunterVoice.speak(world, this, "dawn");
            }
            state.markDirty();
        }
    }

    /** Mirror hours, 3:00 to 3:30: at the edge of sight, facing you, once a night. */
    private void tickMirror(ServerWorld world, ManhuntState state, PlayerEntity player) {
        long clock = world.getTimeOfDay() % 24000L;
        boolean window = clock >= 21000L && clock <= 21500L;
        if (mirrorTicks > 0) {
            mirrorTicks--;
            if (player != null) {
                faceEntity(player);
                if (player.squaredDistanceTo(this) < 144) {
                    mirrorTicks = 0;
                    vanish(world, state);
                }
            }
            if (mirrorTicks == 0 && player != null) {
                vanish(world, state);
            }
            return;
        }
        if (window && player != null && state.mirrorNight != state.night
                && player.squaredDistanceTo(this) > 900) {
            state.mirrorNight = state.night;
            state.markDirty();
            Vec3d away = getPos().subtract(player.getPos()).multiply(1, 0, 1).normalize();
            BlockPos spot = player.getBlockPos().add((int) (away.x * 44), 0, (int) (away.z * 44));
            spot = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spot);
            refreshPositionAndAngles(spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5,
                    getYaw(), 0);
            mirrorTicks = 300;
            HunterVoice.ghost(world, this, ManhuntSounds.GHOST_LOOK);
        }
    }

    private void vanish(ServerWorld world, ManhuntState state) {
        PlayerEntity player = nearestPlayer();
        if (player == null) {
            return;
        }
        Vec3d away = getPos().subtract(player.getPos()).multiply(1, 0, 1).normalize();
        BlockPos spot = player.getBlockPos().add((int) (away.x * 96), 0, (int) (away.z * 96));
        spot = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spot);
        refreshPositionAndAngles(spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5,
                getYaw(), 0);
    }

    /** Signs: one subtle edit near you, every several minutes, with a whisper. */
    private void tickSigns(ServerWorld world, ManhuntState state, PlayerEntity player) {
        if (--state.signsTick > 0 || player == null) {
            return;
        }
        state.signsTick = 4800 + random.nextInt(6000);
        state.markDirty();
        if (player.squaredDistanceTo(this) > 6400) {
            return;
        }
        BlockPos spot = findSignSpot(world, player);
        if (spot == null) {
            return;
        }
        applySign(world, spot);
        HunterVoice.whisper(world, (ServerPlayerEntity) player, random.nextBoolean()
                ? "I moved something small. You'll notice tonight."
                : "Your door was open. It isn't.");
    }

    private BlockPos findSignSpot(World world, PlayerEntity player) {
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos pos = player.getBlockPos().add(random.nextInt(13) - 6, random.nextInt(5) - 2,
                    random.nextInt(13) - 6);
            if (!world.isChunkLoaded(pos)) {
                continue;
            }
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isOf(Blocks.OAK_DOOR) || blockState.isOf(Blocks.SPRUCE_DOOR)
                    || blockState.isOf(Blocks.BIRCH_DOOR) || blockState.isOf(Blocks.TORCH)) {
                return pos;
            }
            if (blockState.isAir() && world.getBlockState(pos.down()).isSolid()) {
                return pos;
            }
        }
        return null;
    }

    private void applySign(World world, BlockPos spot) {
        BlockState blockState = world.getBlockState(spot);
        if (blockState.getBlock() instanceof net.minecraft.block.DoorBlock door) {
            world.setBlockState(spot, blockState.with(net.minecraft.block.DoorBlock.OPEN,
                    !blockState.get(net.minecraft.block.DoorBlock.OPEN)));
        } else if (blockState.isOf(Blocks.TORCH)) {
            world.setBlockState(spot, Blocks.SOUL_TORCH.getDefaultState());
        } else if (blockState.isAir()) {
            world.setBlockState(spot, Blocks.COBBLESTONE.getDefaultState());
        }
        world.playSound(null, spot, ManhuntSounds.CREAK, SoundCategory.BLOCKS, 0.35f, 0.8f);
    }

    /** Livestock know what he is. They flee, and they will not eat near him. */
    private void scareAnimals() {
        for (AnimalEntity animal : getWorld().getEntitiesByClass(AnimalEntity.class,
                getBoundingBox().expand(8), AnimalEntity::isAlive)) {
            Vec3d away = animal.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() < 0.01) {
                away = new Vec3d(1, 0, 0);
            }
            animal.setVelocity(away.normalize().multiply(0.35));
            animal.velocityDirty = true;
        }
    }

    /** Ignition, acts, truce: the shape of the hunt at this moment. */
    private void syncPhase(ServerWorld world, ManhuntState state, PlayerEntity player) {
        Identifier here = world.getRegistryKey().getValue();
        if (player != null) {
            eyeContact(world, state, player);
        }
        if (!state.ignited) {
            phase = Phase.HAUNT;
            return;
        }
        if (player == null) {
            if (state.deathSite != null && here.equals(state.deathSiteDim)
                    && world.getTime() - state.deathTick < 6000L) {
                vigilTarget = state.deathSite;
                phase = Phase.CAMP;
                return;
            }
            vigilTarget = null;
            if (state.act != ManhuntState.ACT_HUNT) {
                phase = Phase.HAUNT;
                return;
            }
            if (!here.equals(lastDimension)) {
                lastDimension = here;
                ringBuilt = false;
                boolean endGame = ManhuntState.end().equals(state.playerDimension);
                phase = endGame && random.nextBoolean() ? Phase.CAMP
                        : random.nextBoolean() ? Phase.TRAP : Phase.HUNT;
            }
            if (state.portalMarks.get(here) == null && state.lastSeen.get(here) == null) {
                phase = Phase.HUNT;
            }
            return;
        }
        lastDimension = here;
        long since = world.getTime() - state.ignitedTick;
        double distance = Math.sqrt(player.squaredDistanceTo(this));
        if (state.act == ManhuntState.ACT_WATCH && since > 2400L) {
            state.act = ManhuntState.ACT_STALK;
            state.markDirty();
        }
        if (state.act == ManhuntState.ACT_STALK
                && (state.contactTicks >= 60 || state.playerHits >= 2 || since > 7200L)) {
            state.act = ManhuntState.ACT_HUNT;
            state.markDirty();
            HunterVoice.speak(world, this, "hunt_open");
        }
        if (distance > 400) {
            state.farTicks++;
        } else {
            state.farTicks = 0;
        }
        if (state.farTicks > 1200 && state.act > ManhuntState.ACT_WATCH) {
            state.act = ManhuntState.ACT_WATCH;
            state.farTicks = 0;
            state.markDirty();
            if (state.truceNight != state.night && player instanceof ServerPlayerEntity serverPlayer) {
                state.truceNight = state.night;
                HunterVoice.whisper(world, serverPlayer,
                        "dawn truce, " + serverPlayer.getName().getString()
                                + ". I'll be where I always am.");
            }
        }
        phase = switch (state.act) {
            case ManhuntState.ACT_STALK -> Phase.STALK;
            case ManhuntState.ACT_HUNT -> Phase.HUNT;
            default -> Phase.WATCH;
        };
    }

    /** Three seconds of being looked at is consent. He counts every tick. */
    private void eyeContact(ServerWorld world, ManhuntState state, PlayerEntity player) {
        double distance = Math.sqrt(player.squaredDistanceTo(this));
        if (distance > 48) {
            state.contactTicks = Math.max(0, state.contactTicks - 4);
            return;
        }
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d towards = getPos().add(0, 1.4, 0).subtract(player.getEyePos()).normalize();
        if (look.dotProduct(towards) > 0.93 && hasLineOfSight(player)) {
            state.contactTicks++;
        } else {
            state.contactTicks = Math.max(0, state.contactTicks - 2);
        }
        if (!state.ignited && state.contactTicks >= 60) {
            ignite(world, state, player);
        }
    }

    /** The Acknowledgement: the hunt begins because you looked. */
    public void ignite(ServerWorld world, ManhuntState state, PlayerEntity player) {
        state.ignited = true;
        state.act = ManhuntState.ACT_WATCH;
        state.ignitedTick = world.getTime();
        state.contactTicks = 0;
        state.markDirty();
        HunterVoice.speak(world, this, "ignition");
        world.setWeather(0, 2400, true, false);
        world.playSound(null, getBlockPos(), ManhuntSounds.THUNDER, SoundCategory.WEATHER, 2.0f, 0.6f);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            HunterVoice.whisper(world, serverPlayer, "there it is. you looked.");
        }
    }

    private PlayerEntity nearestPlayer() {
        return getWorld().getClosestPlayer(getX(), getY(), getZ(), -1.0,
                candidate -> candidate.isAlive() && !candidate.isSpectator());
    }

    private boolean playerLookingAt(PlayerEntity player, double threshold, double range) {
        double distance = Math.sqrt(player.squaredDistanceTo(this));
        if (distance > range) {
            return false;
        }
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d towards = getPos().add(0, 1.4, 0).subtract(player.getEyePos()).normalize();
        return look.dotProduct(towards) > threshold;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && source.getAttacker() instanceof ServerPlayerEntity
                && !getWorld().isClient) {
            ManhuntState state = ManhuntState.get((ServerWorld) getWorld());
            state.playerHits++;
            state.markDirty();
            HunterVoice.speak((ServerWorld) getWorld(), this,
                    random.nextBoolean() ? "hurt_a" : "hurt_b");
        }
        return hurt;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        if (!getWorld().isClient) {
            HunterVoice.speak((ServerWorld) getWorld(), this,
                    random.nextBoolean() ? "death_a" : "death_b");
        }
        super.onDeath(damageSource);
        Manhunt.onHunterDeath(this);
    }

    // ------------------------------------------------------------- haunting --

    /** Pre-ignition: he lives, and sometimes lets you catch him looking. */
    private Identifier here(World world) {
        return world.getRegistryKey().getValue();
    }

    private void liveLikeAPlayer(ServerWorld world, ManhuntState state, PlayerEntity player) {
        if (player != null && playerLookingAt(player, 0.9, 40) && stareTimer-- <= 0) {
            stareTimer = 80;
            faceEntity(player);
            HunterVoice.speak(world, this, switch (state.night % 3) {
                case 0 -> "watch_a";
                case 1 -> "watch_b";
                default -> "watch_c";
            });
        }
        if (getHungerManager().getFoodLevel() < 10 && huntAnimal()) {
            return;
        }
        if (mineTarget != null && mineTick(mineTarget)) {
            mineTarget = null;
            return;
        }
        if (--scanCooldown <= 0) {
            scanCooldown = 40;
            mineTarget = findWantedBlock();
        }
        if (mineTarget != null) {
            steerTowards(Vec3d.ofCenter(mineTarget), 0.55);
            return;
        }
        if (state.deathSite != null && here(world).equals(state.deathSiteDim)
                && world.isNight() && player != null
                && player.squaredDistanceTo(Vec3d.ofCenter(state.deathSite)) > 1024) {
            if (Vec3d.ofCenter(state.deathSite).squaredDistanceTo(getPos()) > 4) {
                steerTowards(Vec3d.ofCenter(state.deathSite), 0.45);
            } else {
                faceBlock(state.deathSite);
            }
            return;
        }
        if (--wanderTimer <= 0 || wanderPoint == null) {
            wanderTimer = 120 + random.nextInt(120);
            double angle = random.nextDouble() * Math.PI * 2;
            wanderPoint = new Vec3d(getX() + Math.cos(angle) * 24, getY(),
                    getZ() + Math.sin(angle) * 24);
        }
        steerTowards(wanderPoint, 0.4);
    }

    /** WATCH: keep the band, mirror the strafe, never blink first. */
    private void watchPlayer(ServerWorld world, ManhuntState state, PlayerEntity player) {
        double distance = Math.sqrt(player.squaredDistanceTo(this));
        faceEntity(player);
        if (!saidSight && distance < 40) {
            saidSight = true;
            HunterVoice.speak(world, this, random.nextBoolean() ? "sight_a" : "sight_b");
        }
        if (distance < 24) {
            steerTowards(getPos().add(getPos().subtract(player.getPos()).multiply(1, 0, 1)
                    .normalize()), 0.5);
        } else if (distance > 40) {
            steerTowards(player.getPos(), 0.5);
        } else {
            double sway = Math.sin(age * 0.02) * 0.6;
            steerTowards(player.getPos().add(sway, 0, sway), 0.35);
        }
    }

    /** STALK: close only while unseen; freeze the instant you look. */
    private void stalkPlayer(ServerWorld world, ManhuntState state, PlayerEntity player) {
        double distance = Math.sqrt(player.squaredDistanceTo(this));
        boolean observed = playerLookingAt(player, 0.8, 32) && hasLineOfSight(player);
        if (observed) {
            state.unseenTicks = 0;
            faceEntity(player);
            if (distance < 20 && random.nextInt(200) == 0) {
                HunterVoice.ghost(world, this, ManhuntSounds.GHOST_TURN);
            }
            return;
        }
        state.unseenTicks++;
        if (state.unseenTicks > 900 && distance > 24 && distance < 96) {
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d behind = player.getPos().subtract(look.multiply(18, 0, 18));
            setPos(behind.x, player.getY(), behind.z);
            state.unseenTicks = 0;
            state.markDirty();
        }
        if (distance < 60 && player instanceof ServerPlayerEntity serverPlayer) {
            int value = Math.clamp((int) (distance / 3), 1, 20);
            if (value != state.lastCount) {
                state.lastCount = value;
                state.markDirty();
                HunterVoice.count(world, serverPlayer, value);
            }
        }
        if (distance > 3.5) {
            steerTowards(player.getPos(), 0.7);
        }
        faceEntity(player);
    }

    // ------------------------------------------------------------- survival --

    private boolean huntAnimal() {
        if (eatTimer > 0) {
            return true;
        }
        AnimalEntity prey = getWorld().getEntitiesByClass(AnimalEntity.class,
                        getBoundingBox().expand(20), AnimalEntity::isAlive).stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(this), b.squaredDistanceTo(this)))
                .orElse(null);
        if (prey == null) {
            return false;
        }
        if (prey.squaredDistanceTo(this) < 9) {
            if (getAttackCooldownProgress(0.5f) >= 1.0f) {
                attack(prey);
            }
            return true;
        }
        steerTowards(prey.getPos(), 0.9);
        return true;
    }

    private BlockPos findWantedBlock() {
        World world = getWorld();
        BlockPos self = getBlockPos();
        boolean wantLogs = countItem(net.minecraft.item.Items.OAK_PLANKS) < 24;
        List<Block> wants = new ArrayList<>();
        if (countItem(net.minecraft.item.Items.COBBLESTONE) < 24) {
            wants.add(Blocks.STONE);
            wants.add(Blocks.DEEPSLATE);
        }
        if (countItem(net.minecraft.item.Items.COAL) < 6) {
            wants.add(Blocks.COAL_ORE);
            wants.add(Blocks.DEEPSLATE_COAL_ORE);
        }
        if (pickTier() >= 1 && countItem(net.minecraft.item.Items.RAW_IRON) < 6) {
            wants.add(Blocks.IRON_ORE);
            wants.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        }
        if (pickTier() >= 2 && countItem(net.minecraft.item.Items.DIAMOND) < 3) {
            wants.add(Blocks.DIAMOND_ORE);
            wants.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        }
        if (wantLogs || !wants.isEmpty()) {
            int radius = 20;
            for (BlockPos pos : BlockPos.iterateRandomly(random, 900,
                    self.getX() - radius, self.getY() - 8, self.getZ() - radius,
                    self.getX() + radius, self.getY() + 12, self.getZ() + radius)) {
                if (!world.isChunkLoaded(pos)) {
                    continue;
                }
                BlockState blockState = world.getBlockState(pos);
                if (wantLogs && blockState.isIn(BlockTags.LOGS)) {
                    return pos.toImmutable();
                }
                for (Block block : wants) {
                    if (blockState.isOf(block)) {
                        return pos.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private int pickTier() {
        int tier = 0;
        for (ItemStack stack : carried()) {
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            if (!path.endsWith("_pickaxe")) {
                continue;
            }
            tier = Math.max(tier, path.startsWith("diamond") || path.startsWith("netherite") ? 3
                    : path.startsWith("iron") ? 2 : path.startsWith("stone") ? 1 : 0);
        }
        return tier;
    }

    private boolean mineTick(BlockPos pos) {
        World world = getWorld();
        if (!world.isChunkLoaded(pos)) {
            return true;
        }
        BlockState blockState = world.getBlockState(pos);
        if (blockState.isAir() || Vec3d.ofCenter(pos).squaredDistanceTo(getPos()) > 36) {
            return true;
        }
        if (Vec3d.ofCenter(pos).squaredDistanceTo(getPos()) > 6.5) {
            steerTowards(Vec3d.ofCenter(pos), 0.6);
            return false;
        }
        faceBlock(pos);
        swingHand(Hand.MAIN_HAND);
        float speed = getMainHandStack().getMiningSpeedMultiplier(blockState);
        float hardness = blockState.getHardness(world, pos);
        if (hardness <= 0) {
            world.breakBlock(pos, true, this);
            return true;
        }
        mineProgress += speed / hardness / (speed > 1.0f ? 30.0f : 100.0f);
        if (mineProgress >= 1.0f) {
            mineProgress = 0.0f;
            world.breakBlock(pos, true, this);
            world.playSound(null, pos, blockState.getSoundGroup().getBreakSound(),
                    SoundCategory.BLOCKS, 0.6f, 0.9f + random.nextFloat() * 0.2f);
            return true;
        }
        return false;
    }

    private void pickupNearby() {
        for (ItemEntity itemEntity : getWorld().getEntitiesByClass(ItemEntity.class,
                getBoundingBox().expand(2.5), ItemEntity::isAlive)) {
            ItemStack stack = itemEntity.getStack();
            getInventory().insertStack(stack);
            if (stack.isEmpty()) {
                itemEntity.discard();
                getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                        SoundCategory.PLAYERS, 0.4f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
    }

    // ---------------------------------------------------------------- eating --

    private void tickEating(ServerWorld world) {
        if (eatTimer > 0) {
            swingHand(Hand.MAIN_HAND);
            if (--eatTimer == 0) {
                ItemStack food = getMainHandStack();
                FoodComponent meal = foodOf(food);
                if (meal != null) {
                    getHungerManager().add(meal.nutrition(), meal.saturation() * 2.0f);
                    food.decrement(1);
                    getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_BURP,
                            SoundCategory.PLAYERS, 0.5f, 0.9f + random.nextFloat() * 0.2f);
                    HunterVoice.speak(world, this, "eat");
                }
            }
            return;
        }
        if (getHungerManager().getFoodLevel() >= 15) {
            return;
        }
        for (int slot = 0; slot < getInventory().size(); slot++) {
            if (foodOf(getInventory().getStack(slot)) != null) {
                moveToHand(slot);
                eatTimer = 32;
                return;
            }
        }
    }

    // -------------------------------------------------------------- crafting --

    private void tryCraftNext() {
        for (Object want : GEAR_PLAN) {
            int needed = want == net.minecraft.item.Items.ARROW ? 16 : 1;
            if (have(want, needed)) {
                continue;
            }
            Recipe<?> recipe = findRecipe(want);
            if (recipe == null || !canConsume(recipe)) {
                continue;
            }
            consume(recipe);
            getInventory().insertStack(recipe.getResult(getRegistryManager()).copy());
            getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_USE,
                    SoundCategory.PLAYERS, 0.3f, 1.1f);
            return;
        }
    }

    private boolean have(Object want, int count) {
        if (want instanceof Item item) {
            return countItem(item) >= count;
        }
        @SuppressWarnings("unchecked")
        TagKey<Item> tag = (TagKey<Item>) want;
        int total = 0;
        for (ItemStack stack : carried()) {
            if (stack.getRegistryEntry().isIn(tag)) {
                total += stack.getCount();
            }
        }
        return total >= count;
    }

    private Recipe<?> findRecipe(Object want) {
        if (craftingRecipes == null) {
            craftingRecipes = new ArrayList<>();
            for (Recipe<?> recipe : getRegistryManager().get(RegistryKeys.RECIPE)) {
                if (recipe instanceof CraftingRecipe) {
                    craftingRecipes.add(recipe);
                }
            }
        }
        for (Recipe<?> recipe : craftingRecipes) {
            ItemStack result = recipe.getResult(getRegistryManager());
            if (result.isEmpty()) {
                continue;
            }
            if (want instanceof Item item && result.isOf(item)) {
                return recipe;
            }
            if (want instanceof TagKey<?> tag && result.getRegistryEntry().isIn((TagKey<Item>) tag)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canConsume(Recipe<?> recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            boolean has = false;
            for (ItemStack option : ingredient.getMatchingStacks()) {
                if (countItem(option.getItem()) >= 1) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                return false;
            }
        }
        return true;
    }

    private void consume(Recipe<?> recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            outer:
            for (ItemStack option : ingredient.getMatchingStacks()) {
                for (int slot = 0; slot < getInventory().size(); slot++) {
                    ItemStack stack = getInventory().getStack(slot);
                    if (stack.isOf(option.getItem())) {
                        stack.decrement(1);
                        break outer;
                    }
                }
            }
        }
    }

    private void smeltOnce() {
        if (countItem(net.minecraft.item.Items.COAL) < 1) {
            return;
        }
        if (smeltingRecipes == null) {
            smeltingRecipes = new ArrayList<>();
            for (Recipe<?> recipe : getRegistryManager().get(RegistryKeys.RECIPE)) {
                if (recipe instanceof SmeltingRecipe) {
                    smeltingRecipes.add(recipe);
                }
            }
        }
        for (Recipe<?> recipe : smeltingRecipes) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack option : ingredient.getMatchingStacks()) {
                    int slot = slotOf(option.getItem());
                    if (slot < 0) {
                        continue;
                    }
                    getInventory().getStack(slot).decrement(1);
                    getInventory().insertStack(recipe.getResult(getRegistryManager()).copy());
                    if (smeltCounter++ % 4 == 0) {
                        int coal = slotOf(net.minecraft.item.Items.COAL);
                        if (coal >= 0) {
                            getInventory().getStack(coal).decrement(1);
                        }
                    }
                    return;
                }
            }
        }
    }

    private int slotOf(Item item) {
        for (int slot = 0; slot < getInventory().size(); slot++) {
            if (getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private void equipBest() {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            int bestSlot = -1;
            int bestRank = armorRank(getEquippedStack(slot));
            for (int i = 0; i < getInventory().size(); i++) {
                ItemStack stack = getInventory().getStack(i);
                if (slotForArmor(stack) == slot && armorRank(stack) > bestRank) {
                    bestRank = armorRank(stack);
                    bestSlot = i;
                }
            }
            if (bestSlot >= 0) {
                ItemStack armor = getInventory().getStack(bestSlot).split(1);
                ItemStack old = getEquippedStack(slot);
                equipStack(slot, armor);
                if (!old.isEmpty()) {
                    getInventory().insertStack(old);
                }
            }
        }
        int bestSlot = -1;
        int bestRank = weaponRank(getMainHandStack());
        for (int i = 0; i < 9; i++) {
            if (weaponRank(getInventory().getStack(i)) > bestRank) {
                bestRank = weaponRank(getInventory().getStack(i));
                bestSlot = i;
            }
        }
        if (bestSlot >= 0 && bowCooldown <= 0) {
            getInventory().selectedSlot = bestSlot;
        }
    }

    private EquipmentSlot slotForArmor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (path.endsWith("_helmet")) return EquipmentSlot.HEAD;
        if (path.endsWith("_chestplate")) return EquipmentSlot.CHEST;
        if (path.endsWith("_leggings")) return EquipmentSlot.LEGS;
        if (path.endsWith("_boots")) return EquipmentSlot.FEET;
        return null;
    }

    private int armorRank(ItemStack stack) {
        if (slotForArmor(stack) == null) {
            return 0;
        }
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (path.startsWith("netherite")) return 6;
        if (path.startsWith("diamond")) return 5;
        if (path.startsWith("iron")) return 4;
        if (path.startsWith("chainmail")) return 3;
        if (path.startsWith("golden")) return 2;
        if (path.startsWith("leather")) return 1;
        return 0;
    }

    private int weaponRank(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (!path.endsWith("_sword")) {
            return 0;
        }
        return path.startsWith("diamond") || path.startsWith("netherite") ? 5
                : path.startsWith("iron") ? 4 : path.startsWith("stone") ? 3
                : path.startsWith("golden") ? 2 : 1;
    }

    private int countItem(Item item) {
        return getInventory().count(item);
    }

    private List<ItemStack> carried() {
        List<ItemStack> list = new ArrayList<>();
        for (int slot = 0; slot < getInventory().size(); slot++) {
            list.add(getInventory().getStack(slot));
        }
        return list;
    }

    private void moveToHand(int slot) {
        if (slot < 9) {
            getInventory().selectedSlot = slot;
            return;
        }
        ItemStack held = getInventory().getStack(getInventory().selectedSlot);
        getInventory().setStack(getInventory().selectedSlot, getInventory().getStack(slot));
        getInventory().setStack(slot, held);
    }

    private FoodComponent foodOf(ItemStack stack) {
        return stack.getItem().getComponents().get(DataComponentTypes.FOOD);
    }

    // ------------------------------------------------------------------ hunt --

    /** He walks. He never sprints. You can outrun him; it changes nothing. */
    private void huntPlayer(ServerWorld world, ManhuntState state, PlayerEntity player) {
        double distance = Math.sqrt(player.squaredDistanceTo(this));
        if (!saidSight && distance < 48) {
            saidSight = true;
            HunterVoice.speak(world, this, random.nextBoolean() ? "sight_a" : "search_a");
        }
        if (distance > 3.2) {
            if (tryShootBow(world, player, distance)) {
                return;
            }
            steerTowards(player.getPos(), 1.0);
            return;
        }
        if (getAttackCooldownProgress(0.5f) >= 1.0f) {
            attack(player);
            if (!player.isAlive()) {
                ManhuntState hunterState = ManhuntState.get(world);
                hunterState.deathTally++;
                hunterState.markDirty();
                HunterVoice.speak(world, this, "kill_a");
            }
        }
    }

    private boolean tryShootBow(ServerWorld world, PlayerEntity target, double distance) {
        if (bowCooldown > 0) {
            bowCooldown--;
            return false;
        }
        int bowSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (getInventory().getStack(i).getItem() instanceof BowItem) {
                bowSlot = i;
                break;
            }
        }
        if (bowSlot < 0 || countItem(net.minecraft.item.Items.ARROW) < 1
                || distance < 5 || distance > 26 || !hasLineOfSight(target)) {
            return false;
        }
        moveToHand(bowSlot);
        faceEntity(target);
        Vec3d lead = target.getPos().add(target.getVelocity().multiply(distance / 3.0, 0, distance / 3.0));
        Vec3d delta = lead.add(0, 1.2, 0).subtract(getCameraPosVec(1.0f));
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float aimYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float aimPitch = (float) -Math.toDegrees(Math.atan2(delta.y, flat));
        setYaw(aimYaw);
        setPitch(aimPitch);
        net.minecraft.entity.projectile.PersistentProjectileEntity arrow =
                ((ArrowItem) net.minecraft.item.Items.ARROW).createArrow(
                        getWorld(), new ItemStack(net.minecraft.item.Items.ARROW), this,
                        getMainHandStack());
        arrow.setVelocity(this, aimPitch, aimYaw, 0.0f, 3.0f, 1.0f);
        getWorld().spawnEntity(arrow);
        getInventory().getStack(slotOf(net.minecraft.item.Items.ARROW)).decrement(1);
        swingHand(Hand.MAIN_HAND);
        getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS, 0.8f, 0.9f + random.nextFloat() * 0.2f);
        HunterVoice.speak(world, this, "bow");
        bowCooldown = 30;
        return true;
    }

    private boolean hasLineOfSight(LivingEntity target) {
        HitResult hit = getWorld().raycast(new RaycastContext(
                getCameraPosVec(1.0f), target.getCameraPosVec(1.0f),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void huntThroughPortals(ManhuntState state) {
        Identifier here = getWorld().getRegistryKey().getValue();
        BlockPos mark = state.portalMarks.get(here);
        if (mark == null) {
            BlockPos seen = state.lastSeen.get(here);
            if (seen != null) {
                steerTowards(Vec3d.ofCenter(seen), 0.8);
            }
            return;
        }
        steerTowards(Vec3d.ofCenter(mark), 1.0);
        if (Vec3d.ofCenter(mark).squaredDistanceTo(getPos()) < 2.5
                && getWorld().getBlockState(mark).isOf(Blocks.NETHER_PORTAL)) {
            HunterVoice.speak((ServerWorld) getWorld(), this, "portal");
            setPos(mark.getX() + 0.5, mark.getY(), mark.getZ() + 0.5);
        }
    }

    private void guardPortal(ManhuntState state) {
        Identifier here = getWorld().getRegistryKey().getValue();
        BlockPos mark = state.portalMarks.get(here);
        if (mark == null) {
            phase = Phase.HUNT;
            return;
        }
        if (!ringBuilt) {
            if (Vec3d.ofCenter(mark).squaredDistanceTo(getPos()) > 49) {
                steerTowards(Vec3d.ofCenter(mark), 0.9);
                return;
            }
            ringBuilt = buildRing(mark);
            if (ringBuilt) {
                HunterVoice.speak((ServerWorld) getWorld(), this, "trap_done");
            }
            return;
        }
        double angle = age * 0.02;
        Vec3d patrol = Vec3d.ofCenter(mark).add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
        if (patrol.squaredDistanceTo(getPos()) > 4) {
            steerTowards(patrol, 0.6);
        }
        faceBlock(mark);
    }

    private boolean buildRing(BlockPos mark) {
        int placed = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            for (int lift = 0; lift < 2; lift++) {
                BlockPos pos = mark.offset(direction).up(lift);
                if (getWorld().getBlockState(pos).isAir() && slotOfBlockItem() >= 0) {
                    moveToHand(slotOfBlockItem());
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos),
                            direction.getOpposite(), pos, false);
                    getMainHandStack().useOnBlock(new ItemUsageContext(this, Hand.MAIN_HAND, hit));
                    placed++;
                }
            }
        }
        getWorld().playSound(null, mark, SoundEvents.BLOCK_STONE_PLACE,
                SoundCategory.BLOCKS, 0.5f, 0.8f);
        return placed > 0 || slotOfBlockItem() < 0;
    }

    private int slotOfBlockItem() {
        for (int slot = 0; slot < getInventory().size(); slot++) {
            ItemStack stack = getInventory().getStack(slot);
            if (stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock().getHardness() > 0
                    && !blockItem.getBlock().equals(Blocks.BEDROCK)) {
                return slot;
            }
        }
        return -1;
    }

    private void campSite(ManhuntState state) {
        if (nearestPlayer() != null) {
            phase = Phase.HUNT;
            return;
        }
        Identifier here = getWorld().getRegistryKey().getValue();
        BlockPos mark = vigilTarget != null ? vigilTarget : state.portalMarks.get(here);
        if (mark != null && Vec3d.ofCenter(mark).squaredDistanceTo(getPos()) > 36) {
            steerTowards(Vec3d.ofCenter(mark), 0.8);
            return;
        }
        if (state.respawnPos == null && age % 100 == 0) {
            tryPlaceBed();
        }
        if (age % 240 < 100) {
            steerTowards(getPos().add(0.8, 0, 0.8), 0.3);
        } else {
            faceBlock(mark != null ? mark : getBlockPos());
        }
    }

    private void tryPlaceBed() {
        for (int slot = 0; slot < getInventory().size(); slot++) {
            ItemStack stack = getInventory().getStack(slot);
            if (!Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_bed")) {
                continue;
            }
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos pos = getBlockPos().offset(direction);
                if (getWorld().getBlockState(pos).isAir()
                        && getWorld().getBlockState(pos.up()).isAir()
                        && getWorld().getBlockState(pos.down()).isSolid()) {
                    moveToHand(slot);
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos),
                            direction.getOpposite(), pos, false);
                    getMainHandStack().useOnBlock(new ItemUsageContext(this, Hand.MAIN_HAND, hit));
                    if (getWorld().getBlockState(pos).getBlock() instanceof net.minecraft.block.BedBlock) {
                        ManhuntState state = ManhuntState.get((ServerWorld) getWorld());
                        state.respawnPos = pos;
                        state.respawnDimension = getWorld().getRegistryKey().getValue();
                        state.markDirty();
                        HunterVoice.speak((ServerWorld) getWorld(), this, "end_bed");
                    }
                    return;
                }
            }
        }
    }

    // --------------------------------------------------------------- movement --

    private void steerTowards(Vec3d target, double effort) {
        double dx = target.x - getX();
        double dz = target.z - getZ();
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        setYaw(lerpAngle(getYaw(), want, 24));
        this.headYaw = getYaw();
        this.bodyYaw = getYaw();
        travel(new Vec3d(0, 0, effort));
        if (horizontalCollision && isOnGround()) {
            setVelocity(getVelocity().add(0, 0.42, 0));
            velocityDirty = true;
        }
        if (isTouchingWater()) {
            setVelocity(getVelocity().add(0, 0.045, 0));
            velocityDirty = true;
        }
    }

    private static float lerpAngle(float from, float to, float maxDelta) {
        float delta = MathHelper.wrapDegrees(to - from);
        return from + Math.clamp(delta, -maxDelta, maxDelta);
    }

    private void faceBlock(BlockPos pos) {
        Vec3d delta = Vec3d.ofCenter(pos).subtract(getCameraPosVec(1.0f));
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        this.headYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        setPitch((float) -Math.toDegrees(Math.atan2(delta.y, flat)));
    }

    private void faceEntity(net.minecraft.entity.Entity entity) {
        faceBlock(entity.getBlockPos().up());
    }
}

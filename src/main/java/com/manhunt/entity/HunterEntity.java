package com.manhunt.entity;

import com.manhunt.Manhunt;
import com.manhunt.world.ManhuntState;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The resident hunter: a {@link PlayerEntity} in every mechanical sense -
 * player inventory, hunger manager, attack cooldowns, bows, armor slots, beds
 * - driven by a survival state machine instead of a network connection.
 *
 * <p>Before {@code /manhunt start} he lives: punches trees, mines with real
 * tool speeds, smelts in his pack, crafts his way up a gear plan, kills stock
 * when the larder is empty, and stares at you from across valleys. After it he
 * always knows where you are. Change dimension and he walks to the portal you
 * used - then either walls it in behind a ring of carried cobble and waits for
 * you to step through, or steps through after you.
 */
public class HunterEntity extends PlayerEntity {

    public enum Phase { WANDER, HUNT, TRAP, CAMP }

    /** What he works towards, in order. Tags keep him compatible with mods. */
    private static final List<Object> GEAR_PLAN = List.of(
            Items.OAK_PLANKS,
            Items.CRAFTING_TABLE,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "wooden_pickaxes")),
            Items.FURNACE,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "stone_pickaxes")),
            Items.STONE_SWORD,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "iron_pickaxes")),
            Items.IRON_SWORD,
            Items.IRON_HELMET,
            Items.IRON_CHESTPLATE,
            Items.IRON_LEGGINGS,
            Items.IRON_BOOTS,
            Items.BOW,
            Items.ARROW,
            Items.SHIELD,
            Items.RED_BED,
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "diamond_pickaxes")),
            Items.DIAMOND_SWORD);

    /** Local alias so the gear plan reads like a shopping list. */
    private interface Items {
        Item OAK_PLANKS = net.minecraft.item.Items.OAK_PLANKS;
        Item CRAFTING_TABLE = net.minecraft.item.Items.CRAFTING_TABLE;
        Item FURNACE = net.minecraft.item.Items.FURNACE;
        Item STONE_SWORD = net.minecraft.item.Items.STONE_SWORD;
        Item IRON_SWORD = net.minecraft.item.Items.IRON_SWORD;
        Item IRON_HELMET = net.minecraft.item.Items.IRON_HELMET;
        Item IRON_CHESTPLATE = net.minecraft.item.Items.IRON_CHESTPLATE;
        Item IRON_LEGGINGS = net.minecraft.item.Items.IRON_LEGGINGS;
        Item IRON_BOOTS = net.minecraft.item.Items.IRON_BOOTS;
        Item BOW = net.minecraft.item.Items.BOW;
        Item ARROW = net.minecraft.item.Items.ARROW;
        Item SHIELD = net.minecraft.item.Items.SHIELD;
        Item RED_BED = net.minecraft.item.Items.RED_BED;
        Item DIAMOND_SWORD = net.minecraft.item.Items.DIAMOND_SWORD;
    }

    private Phase phase = Phase.WANDER;
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
    private List<RecipeEntry<?>> craftingRecipes;
    private List<RecipeEntry<?>> smeltingRecipes;

    public HunterEntity(EntityType<? extends PlayerEntity> type, World world) {
        super(world, BlockPos.ORIGIN, 0.0f, new GameProfile(UUID.randomUUID(), "TheHunter"));
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
        getHungerManager().update(this);
        ManhuntState state = ManhuntState.get((ServerWorld) getWorld());
        syncPhase(state);
        tickEating();
        pickupNearby();
        switch (phase) {
            case WANDER -> liveLikeAPlayer();
            case HUNT -> huntPlayer(state);
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

    /** He always knows where you are; the phase decides what he does about it. */
    private void syncPhase(ManhuntState state) {
        Identifier here = getWorld().getRegistryKey().getValue();
        PlayerEntity player = nearestPlayer();
        if (!state.started) {
            if (phase == Phase.HUNT || phase == Phase.TRAP) {
                phase = Phase.WANDER;
            }
            if (player != null && player.squaredDistanceTo(this) < 400 && stareTimer-- <= 0) {
                faceEntity(player);
                stareTimer = 60;
            }
            return;
        }
        if (player != null) {
            phase = Phase.HUNT;
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
    }

    private PlayerEntity nearestPlayer() {
        return getWorld().getClosestPlayer(getX(), getY(), getZ(), -1.0,
                candidate -> candidate.isAlive() && !candidate.isSpectator());
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        Manhunt.onHunterDeath(this);
    }

    // ------------------------------------------------------------- survival --

    private void liveLikeAPlayer() {
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
        if (--wanderTimer <= 0 || wanderPoint == null) {
            wanderTimer = 120 + random.nextInt(120);
            double angle = random.nextDouble() * Math.PI * 2;
            wanderPoint = new Vec3d(getX() + Math.cos(angle) * 24, getY(),
                    getZ() + Math.sin(angle) * 24);
        }
        steerTowards(wanderPoint, 0.4);
    }

    /** Chase and kill the nearest stock when the larder is empty. */
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

    /** What a player would mine next, given what his inventory is missing. */
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
            wants.add(Blocks.DEEPSLATE_IRON_ORE);
        }
        if (pickTier() >= 2 && countItem(net.minecraft.item.Items.DIAMOND) < 3) {
            wants.add(Blocks.DIAMOND_ORE);
            wants.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        }
        if (wantLogs || !wants.isEmpty()) {
            int radius = 20;
            for (BlockPos pos : BlockPos.iterateRandomly(random, 900,
                    self.add(-radius, -8, -radius), self.add(radius, 12, radius))) {
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
        for (ItemStack stack : getInventory().getMain()) {
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            if (!path.endsWith("_pickaxe")) {
                continue;
            }
            tier = Math.max(tier, path.startsWith("diamond") || path.startsWith("netherite") ? 3
                    : path.startsWith("iron") ? 2 : path.startsWith("stone") ? 1 : 0);
        }
        return tier;
    }

    /** Swing at a block with real tool speeds until it breaks and drops. */
    private boolean mineTick(BlockPos pos) {
        World world = getWorld();
        if (!world.isChunkLoaded(pos)) {
            return true;
        }
        BlockState blockState = world.getBlockState(pos);
        if (blockState.isAir() || pos.getSquaredDistance(getX(), getY(), getZ(), true) > 36) {
            return true;
        }
        if (pos.getSquaredDistance(getX(), getY(), getZ(), true) > 6.5) {
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
            ItemStack leftover = getInventory().insertStack(stack);
            if (leftover.isEmpty()) {
                itemEntity.discard();
                getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                        SoundCategory.PLAYERS, 0.4f, 0.9f + random.nextFloat() * 0.2f);
            } else {
                itemEntity.setStack(leftover);
            }
        }
    }

    // ---------------------------------------------------------------- eating --

    private void tickEating() {
        if (eatTimer > 0) {
            swingHand(Hand.MAIN_HAND);
            if (--eatTimer == 0) {
                ItemStack food = getMainHandStack();
                if (food.getItem().getFoodComponent() != null) {
                    getHungerManager().add(food.getItem().getFoodComponent().getHunger(),
                            food.getItem().getFoodComponent().getSaturationModifier() * 2.0f);
                    food.decrement(1);
                    getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_BURP,
                            SoundCategory.PLAYERS, 0.5f, 0.9f + random.nextFloat() * 0.2f);
                }
            }
            return;
        }
        if (getHungerManager().getFoodLevel() >= 15) {
            return;
        }
        for (int slot = 0; slot < getInventory().size(); slot++) {
            if (getInventory().getStack(slot).getItem().getFoodComponent() != null) {
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
            RecipeEntry<?> recipe = findRecipe(want);
            if (recipe == null || !canConsume(recipe)) {
                continue;
            }
            consume(recipe);
            getInventory().insertStack(recipe.value().getResult(getRegistryManager()).copy());
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
        for (ItemStack stack : getInventory().getMain()) {
            if (stack.getRegistryEntry().isIn(tag)) {
                total += stack.getCount();
            }
        }
        return total >= count;
    }

    private RecipeEntry<?> findRecipe(Object want) {
        if (craftingRecipes == null) {
            craftingRecipes = new ArrayList<>();
            for (RecipeEntry<?> entry : getRegistryManager().get(RegistryKeys.RECIPE)) {
                if (entry.value() instanceof CraftingRecipe) {
                    craftingRecipes.add(entry);
                }
            }
        }
        for (RecipeEntry<?> entry : craftingRecipes) {
            ItemStack result = entry.value().getResult(getRegistryManager());
            if (result.isEmpty()) {
                continue;
            }
            if (want instanceof Item item && result.isOf(item)) {
                return entry;
            }
            if (want instanceof TagKey<?> tag && result.getRegistryEntry().isIn((TagKey<Item>) tag)) {
                return entry;
            }
        }
        return null;
    }

    private boolean canConsume(RecipeEntry<?> recipe) {
        for (Ingredient ingredient : recipe.value().getIngredients()) {
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

    private void consume(RecipeEntry<?> recipe) {
        for (Ingredient ingredient : recipe.value().getIngredients()) {
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

    /** Pack smelting with real smelting recipes: one input, one result, coal every fourth. */
    private void smeltOnce() {
        if (countItem(net.minecraft.item.Items.COAL) < 1) {
            return;
        }
        if (smeltingRecipes == null) {
            smeltingRecipes = new ArrayList<>();
            for (RecipeEntry<?> entry : getRegistryManager().get(RegistryKeys.RECIPE)) {
                if (entry.value() instanceof SmeltingRecipe) {
                    smeltingRecipes.add(entry);
                }
            }
        }
        for (RecipeEntry<?> entry : smeltingRecipes) {
            for (Ingredient ingredient : entry.value().getIngredients()) {
                for (ItemStack option : ingredient.getMatchingStacks()) {
                    int slot = slotOf(option.getItem());
                    if (slot < 0) {
                        continue;
                    }
                    getInventory().getStack(slot).decrement(1);
                    getInventory().insertStack(
                            entry.value().getResult(getRegistryManager()).copy());
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

    /** Wear the best of what he carries, exactly where a player would. */
    private void equipBest() {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            int bestSlot = -1;
            int bestRank = armorRank(getEquippedStack(slot));
            for (int i = 0; i < getInventory().size(); i++) {
                ItemStack stack = getInventory().getStack(i);
                if (stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == slot
                        && armorRank(stack) > bestRank) {
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

    private int armorRank(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
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

    private void moveToHand(int slot) {
        if (slot < 9) {
            getInventory().selectedSlot = slot;
            return;
        }
        ItemStack held = getInventory().getStack(getInventory().selectedSlot);
        getInventory().setStack(getInventory().selectedSlot, getInventory().getStack(slot));
        getInventory().setStack(slot, held);
    }

    // ------------------------------------------------------------------ hunt --

    private void huntPlayer(ManhuntState state) {
        PlayerEntity target = nearestPlayer();
        if (target == null) {
            huntThroughPortals(state);
            return;
        }
        double distance = Math.sqrt(target.squaredDistanceTo(this));
        if (distance > 3.2) {
            if (tryShootBow(target, distance)) {
                return;
            }
            steerTowards(target.getPos(), 1.0);
            return;
        }
        if (getAttackCooldownProgress(0.5f) >= 1.0f) {
            attack(target);
        }
    }

    /** A drawn bow, a loosed arrow: vanilla projectile, hunter aim with lead. */
    private boolean tryShootBow(PlayerEntity target, double distance) {
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
        Vec3d delta = lead.add(0, 1.2, 0).subtract(getCameraPosVec());
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        this.yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        this.pitch = (float) -Math.toDegrees(Math.atan2(delta.y, flat));
        ArrowEntity arrow = new ArrowEntity(getWorld(), this,
                new ItemStack(net.minecraft.item.Items.ARROW));
        arrow.setVelocity(this, this.pitch, this.yaw, 0.0f, 3.0f, 1.0f);
        getWorld().spawnEntity(arrow);
        getInventory().getStack(slotOf(net.minecraft.item.Items.ARROW)).decrement(1);
        swingHand(Hand.MAIN_HAND);
        getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS, 0.8f, 0.9f + random.nextFloat() * 0.2f);
        bowCooldown = 30;
        return true;
    }

    private boolean hasLineOfSight(net.minecraft.entity.LivingEntity target) {
        net.minecraft.util.hit.HitResult hit = getWorld().raycast(new net.minecraft.world.RaycastContext(
                getCameraPosVec(), target.getCameraPosVec(),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    /** Player is in another dimension: walk to the portal they slipped through. */
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
        if (mark.getSquaredDistance(getX(), getY(), getZ(), true) < 2.5
                && getWorld().getBlockState(mark).isOf(Blocks.NETHER_PORTAL)) {
            // Vanilla portal mechanics carry players; he is one, mechanically.
            setPos(mark.getX() + 0.5, mark.getY(), mark.getZ() + 0.5);
        }
    }

    /** Camp the player's portal behind a ring of carried cobble, and wait. */
    private void guardPortal(ManhuntState state) {
        Identifier here = getWorld().getRegistryKey().getValue();
        BlockPos mark = state.portalMarks.get(here);
        if (mark == null) {
            phase = Phase.HUNT;
            return;
        }
        if (!ringBuilt) {
            if (mark.getSquaredDistance(getX(), getY(), getZ(), true) > 49) {
                steerTowards(Vec3d.ofCenter(mark), 0.9);
                return;
            }
            ringBuilt = buildRing(mark);
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

    /** Pitch his bed by the End frame or your portal and hold the ground. */
    private void campSite(ManhuntState state) {
        if (nearestPlayer() != null) {
            phase = Phase.HUNT;
            return;
        }
        Identifier here = getWorld().getRegistryKey().getValue();
        BlockPos mark = state.portalMarks.get(here);
        if (mark != null && mark.getSquaredDistance(getX(), getY(), getZ(), true) > 36) {
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
                    }
                    return;
                }
            }
        }
    }

    // --------------------------------------------------------------- movement --

    /**
     * Player-like locomotion without a navigator: face the target, feed the
     * travel input, jump when shin-high geometry gets in the way, swim when
     * the world puts water in the way. Works in any structure any mod adds.
     */
    private void steerTowards(Vec3d target, double effort) {
        double dx = target.x - getX();
        double dz = target.z - getZ();
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        this.yaw = lerpAngle(yaw, want, 24);
        this.headYaw = yaw;
        this.bodyYaw = yaw;
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
        Vec3d delta = Vec3d.ofCenter(pos).subtract(getCameraPosVec());
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        this.headYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        this.pitch = (float) -Math.toDegrees(Math.atan2(delta.y, flat));
    }

    private void faceEntity(net.minecraft.entity.Entity entity) {
        faceBlock(entity.getBlockPos().up());
    }
}

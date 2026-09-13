package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
import com.rivalrealms.world.RealmState;
import com.rivalrealms.world.SettlementRole;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.village.SimpleMerchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player-like survivor with a real inventory, equipment, combat goals,
 * persistent relationships, and a culture that changes the way it looks and
 * fights. The same server-authoritative entity works in singleplayer and on a
 * dedicated server.
 *
 * <h2>Improvements in this rework</h2>
 * <ul>
 *   <li>Culture is chosen exactly once (the old code re-rolled knights 75% of
 *       the time at random); explicit spawns keep their archetype.</li>
 *   <li>Culture-specific name pools with epithets instead of 16 shared names.</li>
 *   <li>Gun cultures fire with muzzle smoke and a gunshot crack; sky captains
 *       loose bolts with crossbow sounds.</li>
 *   <li>Recruited companions actively defend their owner against their last
 *       attacker, can be fed more foods, and can be released (sneak + empty
 *       hand) without betraying the crew.</li>
 *   <li>Crew riding ships stay seated instead of fighting their own vehicle's
 *       navigation.</li>
 * </ul>
 */
public class SurvivorEntity extends PathAwareEntity implements RangedAttackMob {
    private static final TrackedData<String> ARCHETYPE = DataTracker.registerData(
            SurvivorEntity.class, TrackedDataHandlerRegistry.STRING);

    private UUID ownerUuid;
    private int trust;
    private boolean recruited;
    private boolean guarding;
    private boolean loadoutApplied;
    private boolean archetypeLocked;
    private Temperament temperament = Temperament.GUARDED;
    private boolean temperamentChosen;
    private UUID guardOwnerUuid;
    private BlockPos guardCenter;
    private String guardFaction;
    private SettlementRole settlementRole = SettlementRole.NONE;
    private long nextTargetScan;
    private long nextWorkMove;
    private long nextOwnerDefenseScan;
    private long nextQuip;
    private long nextEat;
    private long nextGift;
    private long fleeUntil;
    private Vec3d fleeFrom;
    private int eatTimer;
    private ItemStack savedHand = ItemStack.EMPTY;
    private BlockPos farmTarget;
    private long nextFarmAction;
    private UUID grudgeUuid;
    private long grudgeUntil;
    private int suspicion;
    private int forgeTimer;
    private long nextPatrol;
    private String familyName = "";
    private boolean child;
    private int childAge;
    private SettlementRole inheritedRole = SettlementRole.NONE;
    private final Map<UUID, Byte> bonds = new HashMap<>();
    private UUID rumorAbout;
    private int rumorKind;
    private int rumorStrength;
    private int aimTicks;
    private long nextRumorShare;

    public SurvivorEntity(EntityType<? extends SurvivorEntity> entityType, World world) {
        super(entityType, world);
        setPersistent();
        setCanPickUpLoot(true);
        this.experiencePoints = 10;
    }

    public static Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.15);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new ConditionalProjectileGoal(this, 1.0, 38, 20.0f));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.add(3, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        goalSelector.add(5, new LookAroundGoal(this));
        targetSelector.add(1, new PersonalityRevengeGoal(this));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ARCHETYPE, Archetype.KNIGHT.id());
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }

        // Crew seated on a sailing ship are cargo, not pathfinders; their ship
        // owns the movement until they are ejected.
        if (this.hasVehicle()) {
            return;
        }

        ensureLoadout();
        PlayerEntity owner = ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);

        // A survivor that was just hit by a chill stranger plays it safe and
        // puts distance between them instead of trading blows.
        if (fleeing()) {
            fleeTick();
            return;
        }

        if (child) {
            childTick();
            return;
        }

        eatTick();
        farmTick();
        suspicionTick();
        aimTick();
        if (getWorld().getTime() % 100L == 0L) {
            rumorTick();
        }

        if (recruited) {
            if (owner == null) {
                // A companion whose owner is offline must not become an
                // unowned hostile NPC. Keep it dormant until the owner returns.
                setTarget(null);
                getNavigation().stop();
            } else {
                if (getTarget() == owner) {
                    setTarget(null);
                }
                defendOwner(owner);
                if (guarding) {
                    getNavigation().stop();
                } else if (squaredDistanceTo(owner.getX(), owner.getY(), owner.getZ()) > 7.0 * 7.0) {
                    getNavigation().startMovingTo(owner, 1.1);
                }

                // Trust is not a static menu value: leaving a companion alone
                // for days or repeatedly hurting them can turn an alliance sour.
                if (getWorld().getTime() % 2400L == 0 && trust > 0) {
                    trust--;
                }
                if (trust < 25 && random.nextInt(1200) == 0) {
                    betray(owner);
                }
            }
        } else if (guardCenter != null) {
            guardHomeTick();
        } else {
            acquireRivalTarget();
        }

        // Trusting companions share their wealth; survivors people-watch and
        // mutter one-liners at passers-by.
        giftTick(owner);
        quipTick();

        // Ranged cultures keep their offhand weapon stocked so their pose reads
        // correctly at a glance.
        if (getWorld().getTime() % 80L == 0 && isRanged() && getOffHandStack().isEmpty()) {
            setStackInHand(Hand.OFF_HAND, getArchetype().rangedStack());
        }
    }

    /** Wounded survivors eat like players do: bread in hand, nibble sounds, a chunk of health back. */
    private void eatTick() {
        long time = getWorld().getTime();
        if (eatTimer > 0) {
            getNavigation().stop();
            eatTimer--;
            ServerWorld serverWorld = (ServerWorld) getWorld();
            if (eatTimer % 14 == 0 && eatTimer > 0) {
                serverWorld.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EAT,
                        SoundCategory.NEUTRAL, 0.7f, 0.9f + random.nextFloat() * 0.3f);
                serverWorld.spawnParticles(ParticleTypes.POOF,
                        getX(), getY() + getStandingEyeHeight() * 0.8, getZ(), 4, 0.2, 0.1, 0.2, 0.02);
            }
            if (eatTimer == 0) {
                this.heal(8.0f);
                serverWorld.spawnParticles(ParticleTypes.HEART,
                        getX(), getY() + getStandingEyeHeight() + 0.4, getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                setStackInHand(Hand.MAIN_HAND, savedHand);
                savedHand = ItemStack.EMPTY;
                nextEat = time + 1800L + random.nextInt(1200);
            }
            return;
        }
        boolean hungryAndSafe = getHealth() < getMaxHealth() * 0.55f && time > nextEat
                && getTarget() == null && isOnGround() && !hasVehicle();
        boolean canFreeHands = getMainHandStack().isEmpty() || !isRanged();
        if (hungryAndSafe && canFreeHands) {
            savedHand = getMainHandStack().copy();
            setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BREAD));
            eatTimer = 50;
            ((ServerWorld) getWorld()).playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EAT,
                    SoundCategory.NEUTRAL, 0.7f, 1.0f);
        }
    }

    /** Chatter lines keep crowds feeling like people instead of mobs. */
    private void quipTick() {
        long time = getWorld().getTime();
        if (time < nextQuip) {
            return;
        }
        nextQuip = time + 300L + random.nextInt(600);
        if (random.nextInt(20) != 0 || eatTimer > 0) {
            return;
        }
        PlayerEntity listener = getWorld().getClosestPlayer(this, 7.0);
        if (listener == null) {
            return;
        }
        listener.sendMessage(Text.literal("<" + getName().getString() + "> " + quip())
                .formatted(Formatting.GRAY), true);
        nextQuip = time + 3600L + random.nextInt(5400);
    }

    private String quip() {
        return switch (temperament) {
            case HOSTILE -> switch (random.nextInt(6)) {
                case 0 -> "Draw, stranger.";
                case 1 -> "Yer purse or yer teeth.";
                case 2 -> "Wrong frontier, friend.";
                case 3 -> "Keep walkin'.";
                case 4 -> "You lost? Dead men don't ask directions.";
                default -> "One more step. I dare ye.";
            };
            case GUARDED -> switch (random.nextInt(6)) {
                case 0 -> "Keep yer steel where I can see it.";
                case 1 -> "Trouble finds folk fast out here.";
                case 2 -> "Start something and we finish it.";
                case 3 -> "We're square. Stay that way.";
                case 4 -> "I watch everyone. No offence.";
                default -> "Roads get rowdy after dark.";
            };
            case CHILL -> switch (random.nextInt(7)) {
                case 0 -> "Fine weather for a walk.";
                case 1 -> "Got any bread to spare?";
                case 2 -> "The road's been quiet lately.";
                case 3 -> "Careful past the ridge, friend.";
                case 4 -> "Need supplies? I trade fair.";
                case 5 -> "Seen any good sunrises lately?";
                default -> "May the road stay soft for ye.";
            };
        };
    }

    /** Trusted companions press small gifts on their owner now and then. */
    private void giftTick(PlayerEntity owner) {
        long time = getWorld().getTime();
        if (!recruited || trust < 95 || owner == null || time < nextGift) {
            return;
        }
        nextGift = time + 4800L;
        if (random.nextInt(5) != 0) {
            return;
        }
        ItemStack gift = switch (random.nextInt(6)) {
            case 0 -> new ItemStack(Items.EMERALD, 1 + random.nextInt(2));
            case 1 -> new ItemStack(Items.GOLDEN_CARROT, 2);
            case 2 -> new ItemStack(Items.ARROW, 6 + random.nextInt(6));
            case 3 -> new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(2));
            case 4 -> new ItemStack(ModItems.FRONTIER_STEW, 1);
            default -> new ItemStack(Items.BREAD, 3);
        };
        this.dropStack(gift);
        owner.sendMessage(Text.literal(getName().getString()
                + " presses a small gift into your hand."), true);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_VILLAGER_YES,
                SoundCategory.NEUTRAL, 0.8f, 1.2f);
    }

    private boolean fleeing() {
        return getWorld().getTime() < fleeUntil;
    }

    /** Chill folk break off fights and sprint for the horizon. */
    private void startFleeing(LivingEntity threat) {
        fleeUntil = getWorld().getTime() + 260L;
        fleeFrom = threat.getPos();
        setTarget(null);
        getNavigation().stop();
        if (threat instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal(getName().getString()
                    + " flees from you! (" + temperament.title() + ")"), true);
        }
    }

    private void fleeTick() {
        if (fleeFrom == null) {
            fleeUntil = 0L;
            return;
        }
        if (getWorld().getTime() % 20L == 0) {
            Vec3d away = getPos().subtract(fleeFrom).normalize().multiply(14.0);
            BlockPos fleeTarget = BlockPos.ofFloored(getX() + away.x, getY(), getZ() + away.z);
            getNavigation().startMovingTo(fleeTarget.getX(), getY(), fleeTarget.getZ(), 1.25);
        }
        if (!fleeing()) {
            fleeFrom = null;
        }
    }

    /** Companions actively avenge their owner's last attacker. */
    private void defendOwner(PlayerEntity owner) {
        long now = getWorld().getTime();
        if (now < nextOwnerDefenseScan) {
            return;
        }
        nextOwnerDefenseScan = now + 20L;
        LivingEntity attacker = owner.getAttacker();
        if (attacker != null && attacker.isAlive() && attacker != this && attacker != owner
                && !(attacker instanceof SurvivorEntity ally && ally.isRecruited())) {
            setTarget(attacker);
        }
    }

    private void ensureLoadout() {
        if (loadoutApplied) {
            return;
        }
        if (!archetypeLocked) {
            // No one picked a culture for this survivor: roll one, once.
            Archetype[] cultures = Archetype.values();
            setArchetype(cultures[random.nextInt(cultures.length)]);
            archetypeLocked = true;
        } else {
            setArchetype(getArchetype());
        }
        if (!temperamentChosen) {
            // Some raid on sight, some are chill until you attack, and a rare
            // few are good-natured traders. Rolled once, remembered forever.
            temperament = Temperament.roll(getArchetype(), random);
            temperamentChosen = true;
        }
        if (!hasCustomName()) {
            // Family folk carry surnames; drifters keep their culture epithets.
            String display = familyName.isEmpty()
                    ? getArchetype().randomName(random)
                    : getArchetype().firstName(random) + " " + familyName;
            setCustomName(Text.literal(display).formatted(temperament.color()));
        }
    }

    public Temperament getTemperament() {
        return temperament;
    }

    private void acquireRivalTarget() {
        LivingEntity current = getTarget();
        if (current != null && current.isAlive()) {
            return;
        }
        long now = getWorld().getTime();
        if (now < nextTargetScan) {
            return;
        }
        nextTargetScan = now + 20L;

        // Free survivors behave per temperament: Bloodthirsty raid on sight,
        // Wary folk only fight back, and Good-natured wanderers never draw on
        // a stranger. Settlement guards are not hostile to players. Guards
        // and wary survivors only select rival NPCs when diplomacy says the
        // factions are at war; chill souls sit the wars out.
        if ((guardCenter == null || "Marauders".equals(effectiveFaction()))
                && temperament == Temperament.HOSTILE) {
            PlayerEntity player = getWorld().getClosestPlayer(this, 16.0);
            if (player != null && !player.isCreative() && !player.isSpectator()) {
                setTarget(player);
                return;
            }
        }

        // Grudges: someone who wronged me (attacked me, aimed at me, stole
        // from the fields) is hunted on sight until the memory fades. Even
        // settlement folk will pick a fight over an old score.
        if (grudgeUuid != null) {
            if (getWorld().getTime() >= grudgeUntil) {
                expireGrudge();
            } else {
                for (PlayerEntity candidate : getWorld().getPlayers()) {
                    if (candidate.getUuid().equals(grudgeUuid) && candidate.isAlive()
                            && !candidate.isCreative() && !candidate.isSpectator()
                            && this.squaredDistanceTo(candidate) < 20.0 * 20.0) {
                        if (temperament != Temperament.CHILL) {
                            setTarget(candidate);
                            return;
                        }
                    }
                }
            }
        }

        // Reputation has teeth: a faction's people hunt players it considers
        // enemies (rep -40 or worse) - even the patient ones. Good-natured
        // folk still refuse to fight; they just stop trading with you.
        if (temperament != Temperament.CHILL
                && getWorld() instanceof ServerWorld repWorld && now % 40L == 0L) {
            for (PlayerEntity candidate : repWorld.getPlayers()) {
                if (candidate.isCreative() || candidate.isSpectator() || !candidate.isAlive()
                        || this.squaredDistanceTo(candidate) > 24.0 * 24.0) {
                    continue;
                }
                RealmState realms = com.rivalrealms.world.RealmState.get(repWorld);
                if (realms.getReputation(candidate.getUuid(), effectiveFaction()) <= -40) {
                    setTarget(candidate);
                    return;
                }
                // A single settlement can hate you even when the faction tolerates you.
                if (guardCenter != null) {
                    var home = realms.findByCenter(guardCenter);
                    if (home != null && home.localReputation(candidate.getUuid()) <= -35) {
                        setTarget(candidate);
                        return;
                    }
                }
            }
        }

        if (temperament == Temperament.CHILL) {
            return;
        }
        List<net.minecraft.entity.Entity> nearby = getWorld().getOtherEntities(
                this, getBoundingBox().expand(20.0), entity -> entity instanceof SurvivorEntity survivor
                        && survivor.isAlive()
                        && !survivor.isRecruited()
                        && RealmState.get((ServerWorld) getWorld()).isHostile(effectiveFaction(), survivor.effectiveFaction()));
        if (!nearby.isEmpty()) {
            setTarget((LivingEntity) nearby.get(random.nextInt(nearby.size())));
        }
    }

    private void guardHomeTick() {
        if (guardCenter == null) {
            return;
        }
        double distance = squaredDistanceTo(guardCenter.getX() + 0.5, getY(), guardCenter.getZ() + 0.5);
        if (distance > 32.0 * 32.0) {
            getNavigation().startMovingTo(guardCenter.getX() + 0.5, guardCenter.getY(), guardCenter.getZ() + 0.5, 0.9);
            return;
        }

        long now = getWorld().getTime();
        if (settlementRole.isWorkRole() && now >= nextWorkMove) {
            nextWorkMove = now + 20L;
            BlockPos worksite = worksite();
            if (squaredDistanceTo(worksite.getX() + 0.5, worksite.getY(), worksite.getZ() + 0.5) > 8.0 * 8.0) {
                getNavigation().startMovingTo(worksite.getX() + 0.5, worksite.getY(), worksite.getZ() + 0.5, 0.85);
            } else if (random.nextInt(80) == 0) {
                getNavigation().startMovingTo(guardCenter.getX() + random.nextInt(13) - 6,
                        guardCenter.getY(), guardCenter.getZ() + random.nextInt(13) - 6, 0.75);
            }
        }
        if (settlementRole == SettlementRole.GUARD && now >= nextPatrol) {
            // Guards walk their rounds instead of standing like statues.
            nextPatrol = now + 200L + random.nextInt(200);
            getNavigation().startMovingTo(guardCenter.getX() + random.nextInt(17) - 8,
                    guardCenter.getY(), guardCenter.getZ() + random.nextInt(17) - 8, 0.8);
        }
        if (settlementRole.isCombatant()) {
            acquireRivalTarget();
        }
        if (settlementRole == SettlementRole.BLACKSMITH && now % 20L == 0L) {
            forgeTick();
        }
    }

    /**
     * The village smith actually makes weapons: hammer sparks at the forge,
     * anvil rings, and every few sessions a finished blade clatters onto the
     * ground - occasionally a good one. The settlement arms itself.
     */
    private void forgeTick() {
        BlockPos forge = worksite();
        if (squaredDistanceTo(forge.getX() + 0.5, forge.getY(), forge.getZ() + 0.5) > 7.0 * 7.0
                || !getMainHandStack().isEmpty()) {
            return;
        }
        forgeTimer++;
        swingHand(Hand.MAIN_HAND);
        ServerWorld serverWorld = (ServerWorld) getWorld();
        serverWorld.playSound(null, forge.getX() + 0.5, forge.getY() + 1.0, forge.getZ() + 0.5,
                SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.6f, 1.1f + random.nextFloat() * 0.2f);
        serverWorld.spawnParticles(ParticleTypes.LAVA,
                forge.getX() + 0.5, forge.getY() + 1.1, forge.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
        if (forgeTimer >= 6) {
            forgeTimer = 0;
            ItemStack crafted = random.nextInt(10) == 0
                    ? new ItemStack(Items.IRON_SWORD)
                    : random.nextBoolean() ? new ItemStack(Items.IRON_SWORD) : new ItemStack(Items.IRON_AXE);
            if (random.nextInt(10) == 0) {
                var enchantments = getWorld().getRegistryManager()
                        .get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
                crafted.addEnchantment(enchantments.entryOf(net.minecraft.enchantment.Enchantments.SHARPNESS),
                        1 + random.nextInt(2));
            }
            this.dropStack(crafted);
            serverWorld.playSound(null, forge.getX() + 0.5, forge.getY() + 1.0, forge.getZ() + 0.5,
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8f, 0.8f);
        }
    }

    private BlockPos worksite() {
        int offset = switch (settlementRole) {
            case BUILDER -> 8;
            case FARMER -> -8;
            case BAKER -> 2;
            case HERBALIST -> -3;
            case TRADER, MERCHANT, JEWELER -> 0;
            case BLACKSMITH -> 5;
            case MASON -> 10;
            case MINER -> -12;
            case SAILOR, QUARTERMASTER -> 4;
            case GUNNER -> -4;
            case NAVIGATOR -> 0;
            default -> 0;
        };
        int zOffset = switch (settlementRole) {
            case FARMER, HERBALIST, MINER -> 7;
            case SAILOR, QUARTERMASTER, NAVIGATOR -> 0;
            default -> -5;
        };
        return guardCenter.add(offset, 0, zOffset);
    }

    public void setArchetype(Archetype archetype) {
        dataTracker.set(ARCHETYPE, archetype.id());
        loadoutApplied = true;
        archetypeLocked = true;
        if (!getWorld().isClient) {
            if (getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
                getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(archetype.health());
            }
            if (getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null) {
                getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(archetype.speed());
            }
            if (getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) != null) {
                getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(archetype == Archetype.KNIGHT ? 5.0 : 3.5);
            }
            setHealth((float) archetype.health());
            archetype.equip(this, random, getRegistryManager().get(RegistryKeys.ENCHANTMENT));
        }
    }

    public Archetype getArchetype() {
        return Archetype.byId(dataTracker.get(ARCHETYPE));
    }

    public boolean isRanged() {
        return getArchetype().isRanged();
    }

    public String effectiveFaction() {
        return guardFaction == null || guardFaction.isBlank() ? getArchetype().faction() : guardFaction;
    }

    public boolean isBaseGuard() {
        return guardCenter != null && settlementRole == SettlementRole.GUARD;
    }

    public boolean isSettlementWorker() {
        return guardCenter != null && settlementRole.isWorkRole();
    }

    public SettlementRole settlementRole() {
        return settlementRole;
    }

    public void assignGuard(BlockPos center, UUID owner, String faction) {
        assignSettlement(center, owner, faction, SettlementRole.GUARD);
    }

    public void assignWorker(BlockPos center, UUID owner, String faction, SettlementRole role) {
        if (!role.isWorkRole()) {
            return;
        }
        assignSettlement(center, owner, faction, role);
    }

    private void assignSettlement(BlockPos center, UUID owner, String faction, SettlementRole role) {
        guardCenter = center.toImmutable();
        guardOwnerUuid = owner;
        guardFaction = faction;
        settlementRole = role;
        recruited = false;
        ownerUuid = null;
        guarding = false;
        setTarget(null);
        // Assigned crew and guards serve a banner: the easy-going trader roll
        // is reserved for free wanderers, so ships and settlements stay sharp.
        if (temperament == Temperament.CHILL) {
            temperament = Temperament.GUARDED;
            temperamentChosen = true;
        }
        String nameBase = guardFaction != null && guardFaction.equals("Freebooters")
                ? Archetype.pirateShipTitle(random)
                : role.displayName();
        setCustomName(Text.literal(nameBase + " · " + getArchetype().title()));
        if (role == SettlementRole.FARMER && !getWorld().isClient) {
            // Farmhands carry the farmhand's hoe; harvesting is their trade.
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModItems.FARMER_HOE));
        }
    }

    public UUID guardOwnerUuid() {
        return guardOwnerUuid;
    }

    public BlockPos guardCenter() {
        return guardCenter;
    }

    // ------------------------------------------------------- family & bonds

    /** Joins a family line; {@code parent} (if present) becomes kin. */
    public void setFamily(String family, SurvivorEntity parent) {
        this.familyName = family == null ? "" : family;
        if (parent != null) {
            setBond(parent.getUuid(), (byte) 2);
            parent.setBond(getUuid(), (byte) 2);
        }
    }

    public String familyName() {
        return familyName;
    }

    public String firstName(net.minecraft.util.math.random.Random rng) {
        return getArchetype().firstName(rng);
    }

    public boolean isChild() {
        return child;
    }

    public void setChild(boolean child) {
        this.child = child;
        var speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(child ? 0.22 : 0.33);
        }
    }

    public void setInheritedRole(SettlementRole role) {
        this.inheritedRole = role == null ? SettlementRole.NONE : role;
    }

    public void setBond(UUID other, byte strength) {
        if (other != null && !other.equals(getUuid())) {
            bonds.put(other, strength);
        }
    }

    public int bondStrength(UUID other) {
        return other == null ? 0 : bonds.getOrDefault(other, (byte) 0);
    }

    /**
     * The child grows up: takes its parent's trade at the home settlement,
     * drops the "Young" prefix and starts pulling full weight.
     */
    private void growUp() {
        child = false;
        setChild(false);
        String oldName = hasCustomName() ? getName().getString() : "Young";
        String grown = oldName.startsWith("Young ") ? oldName.substring("Young ".length()) : oldName;
        setCustomName(Text.literal(grown + (familyName.isEmpty() ? "" : " " + familyName)
                + (inheritedRole == SettlementRole.NONE ? "" : " · " + inheritedRole.displayName())));
        if (guardCenter != null) {
            SettlementRole role = inheritedRole == SettlementRole.NONE
                    ? (random.nextBoolean() ? SettlementRole.FARMER : SettlementRole.BUILDER)
                    : inheritedRole;
            assignWorker(guardCenter, guardOwnerUuid, effectiveFaction(), role);
            if (getWorld() instanceof ServerWorld serverWorld) {
                RealmState realms = RealmState.get(serverWorld);
                var base = realms.findByCenter(guardCenter);
                if (base != null) {
                    realms.chronicle(getWorld().getTime(), grown + " " + familyName
                            + " came of age in " + base.name() + " and took up the "
                            + role.displayName().toLowerCase(java.util.Locale.ROOT) + "'s trade.", false);
                }
            }
        }
        inheritedRole = SettlementRole.NONE;
    }

    /** Children shadow the adults, never fight, and grow up after ~30 minutes. */
    private void childTick() {
        setTarget(null);
        childAge++;
        if (childAge >= 36000) {
            growUp();
            return;
        }
        if (childAge % 40 == 0) {
            for (net.minecraft.entity.Entity adult : getWorld().getOtherEntities(this,
                    getBoundingBox().expand(12.0), e -> e instanceof SurvivorEntity other
                            && other.isAlive() && !other.isChild())) {
                getNavigation().startMovingTo(adult, 1.0);
                break;
            }
        }
    }

    // ------------------------------------------------------- rumors

    /**
     * A witness carries news. Rumours travel mouth to mouth and only become
     * reputation when they reach someone tied to a settlement.
     */
    public void seedRumor(UUID about, int kind, int strength) {
        this.rumorAbout = about;
        this.rumorKind = kind;
        this.rumorStrength = strength;
        this.nextRumorShare = getWorld().getTime() + 60L;
    }

    private void rumorTick() {
        long now = getWorld().getTime();
        if (rumorStrength <= 0 || rumorAbout == null || now < nextRumorShare) {
            return;
        }
        nextRumorShare = now + 100L;
        for (net.minecraft.entity.Entity listener : getWorld().getOtherEntities(this,
                getBoundingBox().expand(6.0), e -> e instanceof SurvivorEntity other
                        && other.isAlive() && other.rumorAbout != rumorAbout)) {
            SurvivorEntity other = (SurvivorEntity) listener;
            other.rumorAbout = rumorAbout;
            other.rumorKind = rumorKind;
            other.rumorStrength = rumorStrength - 1;
            if (other.guardCenter != null) {
                // The news reached a settlement: it becomes local record.
                if (getWorld() instanceof ServerWorld serverWorld) {
                    RealmState realms = RealmState.get(serverWorld);
                    var base = realms.findByCenter(other.guardCenter);
                    if (base != null) {
                        base.adjustLocalReputation(rumorAbout, -(rumorStrength + 2));
                        PlayerEntity subject = getWorld().getPlayerByUuid(rumorAbout);
                        if (subject != null && subject.squaredDistanceTo(this) < 32.0 * 32.0) {
                            String crime = switch (rumorKind) {
                                case 1 -> "theft";
                                case 2 -> "murder";
                                default -> "threats";
                            };
                            subject.sendMessage(Text.literal("Word of your " + crime
                                    + " reaches " + base.name() + ".").formatted(Formatting.GOLD), true);
                        }
                    }
                }
                rumorStrength = 0;
                rumorAbout = null;
            } else {
                rumorStrength = Math.max(0, rumorStrength - 1);
                rumorAbout = null;
            }
            break;
        }
        if (getWorld().getTime() % 1200L == 0L && rumorStrength > 0) {
            rumorStrength--;
        }
    }

    // ------------------------------------------------------- player-like aim

    /**
     * Ranged survivors aim like players: they track the target, hold fire
     * without a clear line of sight, and their accuracy tightens the longer
     * they keep the target in their sights.
     */
    private void aimTick() {
        LivingEntity target = getTarget();
        if (!isRanged() || target == null || !target.isAlive()
                || squaredDistanceTo(target) > 24.0 * 24.0) {
            aimTicks = Math.max(0, aimTicks - 2);
            return;
        }
        if (age % 2 == 0 && canSee(target)) {
            aimTicks++;
        } else if (age % 2 == 0) {
            aimTicks = Math.max(0, aimTicks - 3);
        }
    }

    private int aimRequirement() {
        return getArchetype() == Archetype.OUTLAW || getArchetype() == Archetype.PIRATE ? 12 : 8;
    }

    public boolean isRecruited() {
        return recruited && ownerUuid != null;
    }

    public boolean isOwner(PlayerEntity player) {
        return player != null && ownerUuid != null && ownerUuid.equals(player.getUuid()) && recruited;
    }

    public int getTrust() {
        return trust;
    }

    public boolean isGuarding() {
        return guarding;
    }

    public void betray(PlayerEntity owner) {
        recruited = false;
        guarding = false;
        ownerUuid = null;
        trust = 0;
        setTarget(owner);
        setCustomName(Text.literal("Betrayer · " + getArchetype().title()).formatted(Formatting.RED));
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal(getName().getString() + " has betrayed you!"), false);
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);

        // Grudges close hearts: someone you wronged wants nothing from you.
        if (grudgeAgainst(player) && !isRecruited() && !isOwner(player) && !held.isOf(ModItems.RECRUITMENT_CONTRACT)) {
            if (!getWorld().isClient) {
                player.sendMessage(Text.literal(getName().getString()
                        + " turns away from you coldly."), true);
            }
            return ActionResult.PASS;
        }

        // Good-natured survivors open their trade satchel for a friendly face.
        if (temperament == Temperament.CHILL && !isRecruited() && held.isEmpty()
                && !player.shouldCancelInteraction()) {
            if (!getWorld().isClient) {
                openTrades(player);
            }
            return ActionResult.SUCCESS;
        }

        // Anyone can share food with a non-hostile stranger; it builds rapport
        // and stops them from feeling like vending-machine mobs.
        if (!isRecruited() && temperament != Temperament.HOSTILE && isPreferredFood(held)) {
            if (!getWorld().isClient) {
                String foodName = held.getName().getString();
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EAT,
                        SoundCategory.NEUTRAL, 0.8f, 1.0f);
                if (temperament == Temperament.CHILL) {
                    ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HEART,
                            getX(), getY() + getStandingEyeHeight() + 0.4, getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                }
                player.sendMessage(Text.literal(getName().getString() + " accepts your "
                        + foodName + "."), true);
            }
            return ActionResult.SUCCESS;
        }

        if (held.isOf(ModItems.RECRUITMENT_CONTRACT)) {
            if (!isRecruited() || isOwner(player)) {
                ownerUuid = player.getUuid();
                recruited = true;
                guarding = false;
                guardCenter = null;
                guardOwnerUuid = null;
                guardFaction = null;
                settlementRole = SettlementRole.NONE;
                trust = Math.max(trust, 80);
                setTarget(null);
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_VILLAGER_YES,
                        SoundCategory.NEUTRAL, 1.0f, 1.0f);
                player.sendMessage(Text.literal(getName().getString() + " joined your crew. Trust: "
                        + trust + "/100"), false);
                return ActionResult.SUCCESS;
            }
            player.sendMessage(Text.literal("This survivor already belongs to another crew."), true);
            return ActionResult.FAIL;
        }

        if (isOwner(player)) {
            if (isPreferredFood(held)) {
                trust = Math.min(100, trust + 8);
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EAT,
                        SoundCategory.NEUTRAL, 0.8f, 1.0f);
                player.sendMessage(Text.literal("Trust increased to " + trust + "/100."), true);
                return ActionResult.SUCCESS;
            }
            if (held.isEmpty()) {
                if (player.shouldCancelInteraction()) {
                    // Sneak + empty hand releases the companion honourably.
                    released(player);
                    return ActionResult.SUCCESS;
                }
                guarding = !guarding;
                player.sendMessage(Text.literal(guarding ? "Companion is holding this position."
                        : "Companion is following you."), true);
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    private static boolean isPreferredFood(ItemStack stack) {
        return stack.isOf(Items.GOLDEN_CARROT) || stack.isOf(Items.COOKED_BEEF)
                || stack.isOf(Items.BREAD) || stack.isOf(Items.COOKED_SALMON)
                || stack.isOf(Items.APPLE);
    }

    /**
     * Opens a villager-style trade screen with offers rolled from this
     * survivor's culture. Uses the vanilla merchant protocol, so it works on
     * dedicated servers exactly like trading with a wanderer.
     */
    private void openTrades(PlayerEntity player) {
        // Local reputation decides how the door opens: hostile towns refuse,
        // friendly ones sweeten the deal.
        RealmState realms = guardCenter == null ? null : RealmState.get((ServerWorld) getWorld());
        var base = realms == null ? null : realms.findByCenter(guardCenter);
        if (base != null) {
            int local = base.localReputation(player.getUuid());
            if (local <= -25) {
                getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_VILLAGER_NO,
                        SoundCategory.NEUTRAL, 0.9f, 1.0f);
                player.sendMessage(Text.literal(getName().getString()
                        + ": \"We don't trade with your kind. Not after what you did.\""), true);
                return;
            }
            SimpleMerchant merchant = new SimpleMerchant(player);
            merchant.setCustomer(player);
            rollTrades(merchant.getOffers());
            if (local >= 15) {
                merchant.getOffers().add(new TradeOffer(new TradedItem(Items.EMERALD, 3), Optional.empty(),
                        new ItemStack(com.rivalrealms.item.ModItems.ROYAL_COIN, 1), 6, 4, 0.05f));
            }
            merchant.sendOffers(player, getDisplayName(), 0);
            getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_VILLAGER_YES,
                    SoundCategory.NEUTRAL, 0.9f, 1.0f);
            return;
        }
        SimpleMerchant merchant = new SimpleMerchant(player);
        merchant.setCustomer(player);
        rollTrades(merchant.getOffers());
        merchant.sendOffers(player, getDisplayName(), 0);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_VILLAGER_YES,
                SoundCategory.NEUTRAL, 0.9f, 1.0f);
    }

    /** Staples for everyone, culture goods on top, one rare diamond deal. */
    private void rollTrades(TradeOfferList offers) {
        int emeralds = 1 + random.nextInt(2);
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, emeralds), Optional.empty(),
                new ItemStack(Items.BREAD, 3 + random.nextInt(4)), 8, 2, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 1), Optional.empty(),
                new ItemStack(Items.ARROW, 8 + random.nextInt(9)), 8, 2, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 2), Optional.empty(),
                new ItemStack(Items.TORCH, 10 + random.nextInt(7)), 8, 2, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 3), Optional.empty(),
                new ItemStack(Items.COOKED_SALMON, 3 + random.nextInt(3)), 6, 3, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 4 + random.nextInt(3)), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(2)), 6, 3, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 7), Optional.of(new TradedItem(Items.IRON_INGOT, 2)),
                new ItemStack(Items.DIAMOND, 1), 3, 6, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.COAL, 12), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 8, 2, 0.05f));
        offers.add(new TradeOffer(new TradedItem(Items.LEATHER, 5), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 8, 2, 0.05f));
        switch (getArchetype()) {
            case KNIGHT -> offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 5), Optional.empty(),
                    new ItemStack(Items.GOLDEN_CARROT, 3), 6, 3, 0.05f));
            case PIRATE -> offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 3), Optional.empty(),
                    new ItemStack(Items.COMPASS, 1), 6, 3, 0.05f));
            case OUTLAW -> offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 2), Optional.empty(),
                    new ItemStack(Items.LEATHER, 4), 8, 2, 0.05f));
            case SKY_CAPTAIN -> offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 4), Optional.empty(),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 3), 6, 4, 0.05f));
            case HEARTHFOLK -> {
                offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 1), Optional.empty(),
                        new ItemStack(Items.CARROT, 5), 12, 2, 0.05f));
                offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 2), Optional.empty(),
                        new ItemStack(Items.APPLE, 4), 12, 2, 0.05f));
                offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 1), Optional.empty(),
                        new ItemStack(Items.WHEAT_SEEDS, 9), 12, 2, 0.05f));
                offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 3), Optional.empty(),
                        new ItemStack(com.rivalrealms.item.ModItems.FRONTIER_STEW, 1), 8, 3, 0.05f));
                offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 2), Optional.empty(),
                        new ItemStack(com.rivalrealms.item.ModItems.MEAD, 1), 10, 2, 0.05f));
            }
            case MARAUDER -> offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 6), Optional.empty(),
                    new ItemStack(Items.BONE, 4), 4, 2, 0.05f));
        }
    }

    private void released(PlayerEntity player) {
        recruited = false;
        guarding = false;
        ownerUuid = null;
        trust = 0;
        setTarget(null);
        setCustomName(Text.literal(getArchetype().randomName(random)));
        if (!player.isCreative()) {
            this.dropStack(new ItemStack(ModItems.RECRUITMENT_CONTRACT, 1));
        }
        player.sendMessage(Text.literal(getName().getString() + " waves goodbye and returns to the frontier."), true);
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld deathWorld) {
            String killerName = source.getAttacker() == null
                    ? "misfortune" : source.getAttacker().getName().getString();
            // Named settlers are written into the world chronicle when they fall.
            if (guardCenter != null && hasCustomName()) {
                RealmState realms = RealmState.get(deathWorld);
                var base = realms.findByCenter(guardCenter);
                if (base != null) {
                    boolean major = settlementRole == SettlementRole.GUARD
                            || settlementRole == SettlementRole.WARLORD
                            || settlementRole == SettlementRole.CAPTAIN;
                    realms.chronicle(getWorld().getTime(), getName().getString() + " of " + base.name()
                            + " was slain by " + killerName + ".", major);
                }
            }
            // Kin and friends mourn - and remember. Family grudges outlive the
            // moment, and the news spreads mouth to mouth from the body.
            for (net.minecraft.entity.Entity candidate : deathWorld.getOtherEntities(this,
                    getBoundingBox().expand(20.0), e -> e instanceof SurvivorEntity mourner
                            && mourner.isAlive() && mourner.bondStrength(getUuid()) > 0)) {
                SurvivorEntity mourner = (SurvivorEntity) candidate;
                if (source.getAttacker() instanceof ServerPlayerEntity killer) {
                    mourner.holdGrudge(killer, mourner.bondStrength(getUuid()) >= 2 ? 144000L : 72000L);
                    mourner.seedRumor(killer.getUuid(), 2, mourner.bondStrength(getUuid()) >= 2 ? 4 : 2);
                    if (mourner.temperament != Temperament.CHILL && mourner.bondStrength(getUuid()) >= 2) {
                        mourner.setTarget(killer);
                    }
                } else if (source.getAttacker() instanceof LivingEntity killerNpc
                        && mourner.temperament != Temperament.CHILL) {
                    mourner.setTarget(killerNpc);
                }
            }
        }
        if (!getWorld().isClient && source.getAttacker() instanceof ServerPlayerEntity killer) {
            boolean wasDefending = getTarget() == killer;
            int delta = wasDefending ? -5 : -15;
            String faction = effectiveFaction();
            com.rivalrealms.world.RealmState realm = com.rivalrealms.world.RealmState.get((ServerWorld) getWorld());
            int before = realm.getReputation(killer.getUuid(), faction);
            realm.adjustReputation(killer.getUuid(), faction, delta);
            int after = realm.getReputation(killer.getUuid(), faction);
            if (before > -40 && after <= -40) {
                killer.sendMessage(Text.literal(faction + " will no longer tolerate you. "
                        + "Their people attack on sight now.").formatted(Formatting.RED), false);
            } else if (delta == -15) {
                killer.sendMessage(Text.literal("The " + faction + " frown upon this murder. ("
                        + faction + " reputation " + after + ")").formatted(Formatting.GRAY), true);
            }
            // The enemy of your enemy: the Crownlands respect every fallen
            // Freebooter, so raiders killed in their name earn goodwill.
            if ("Freebooters".equals(faction) && wasDefending) {
                realm.adjustReputation(killer.getUuid(), "Crownlands", 2);
            }
        }
        super.onDeath(source);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient) {
            // Getting hit ruins an appetite mid-bite.
            if (eatTimer > 0) {
                eatTimer = 0;
                setStackInHand(Hand.MAIN_HAND, savedHand);
                savedHand = ItemStack.EMPTY;
            }
            if (source.getAttacker() instanceof LivingEntity attacker && attacker != this) {
                if (temperament == Temperament.CHILL && !isRecruited() && !isSettlementWorker()) {
                    // Good-natured folk never fight back: they run.
                    startFleeing(attacker);
                    return super.damage(source, amount);
                }
            }
            if (source.getAttacker() instanceof PlayerEntity player) {
                witnessAttack(player);
                if (guardCenter != null && guardOwnerUuid != null && guardOwnerUuid.equals(player.getUuid())) {
                    // Settlement staff are protected from accidental friendly fire;
                    // RevengeGoal must never turn an owner's mistake into a revolt.
                    setTarget(null);
                    return false;
                }
                if (isOwner(player)) {
                    trust = Math.max(0, trust - 35);
                    if (trust < 25 && random.nextInt(4) == 0) {
                        betray(player);
                    }
                }
            }
        }
        return super.damage(source, amount);
    }

    @Override
    public void shootAt(LivingEntity target, float pullProgress) {
        if (!isRanged() || !target.isAlive() || getWorld().isClient) {
            return;
        }
        // Real shooters do not snap-fire: they track, settle, then squeeze.
        if (aimTicks < aimRequirement()) {
            return;
        }
        Archetype archetype = getArchetype();
        if (archetype == Archetype.OUTLAW || archetype == Archetype.PIRATE) {
            fireGunshot(target, archetype);
        } else {
            fireBolt(target, archetype);
        }
        // A shot partly resets the aim, like recoil settling.
        aimTicks = Math.max(aimRequirement() / 2, aimTicks - 6);
    }

    /**
     * Leads a moving target the way a player would: aim where the target
     * WILL be when the shot lands, with error that shrinks as aim settles.
     */
    private Vec3d leadTarget(LivingEntity target, double projectileSpeed) {
        double dist = Math.sqrt(squaredDistanceTo(target));
        int flight = (int) Math.max(1, dist / projectileSpeed);
        Vec3d v = target.getVelocity();
        double err = (1.0 - Math.min(1.0, aimTicks / 24.0)) * 1.1 + 0.12;
        return new Vec3d(
                target.getX() + v.x * flight * 0.9 + (random.nextDouble() - 0.5) * err,
                target.getY() + target.getHeight() * 0.5 + v.y * flight * 0.9 + (random.nextDouble() - 0.5) * err * 0.6,
                target.getZ() + v.z * flight * 0.9 + (random.nextDouble() - 0.5) * err);
    }

    /**
     * Outlaws and pirates shoot loud, smoky gunshots instead of silent arrows.
     * Shots and muzzle smoke come from the middle of the body, so the tracer
     * never reads as firing out of the face or the held weapon.
     */
    private void fireGunshot(LivingEntity target, Archetype archetype) {
        ServerWorld serverWorld = (ServerWorld) getWorld();
        ArrowEntity bullet = new ArrowEntity(getWorld(), this, new ItemStack(Items.ARROW), null);
        double muzzleY = getY() + getHeight() * 0.55;
        bullet.setPosition(getX(), muzzleY, getZ());
        Vec3d aim = leadTarget(target, 3.0);
        double dx = aim.x - getX();
        double dy = aim.y - muzzleY;
        double dz = aim.z - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        bullet.setVelocity(dx, dy + horizontal * 0.04, dz, 3.0f, 1.2f);
        bullet.setDamage((float) archetype.rangedDamage());
        getWorld().spawnEntity(bullet);

        serverWorld.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.NEUTRAL, 0.5f, 1.7f);
        serverWorld.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITHER_SHOOT,
                SoundCategory.NEUTRAL, 0.35f, 1.8f);
        serverWorld.spawnParticles(ParticleTypes.POOF,
                getX() + dx * 0.08, muzzleY, getZ() + dz * 0.08, 5, 0.12, 0.08, 0.12, 0.01);
        serverWorld.spawnParticles(ParticleTypes.FLAME,
                getX() + dx * 0.08, muzzleY, getZ() + dz * 0.08, 2, 0.05, 0.03, 0.05, 0.01);
        // A short tracer so the shot reads across a street.
        for (double d = 1.0; d < Math.min(horizontal, 8.0); d += 1.6) {
            serverWorld.spawnParticles(ParticleTypes.CRIT,
                    getX() + dx / horizontal * d, muzzleY + dy / Math.max(1.0, horizontal) * d,
                    getZ() + dz / horizontal * d, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Sky captains keep the classic crossbow bolt, loosed from chest height. */
    private void fireBolt(LivingEntity target, Archetype archetype) {
        ServerWorld serverWorld = (ServerWorld) getWorld();
        ArrowEntity bolt = new ArrowEntity(getWorld(), this, new ItemStack(Items.ARROW), null);
        double muzzleY = getY() + getHeight() * 0.55;
        bolt.setPosition(getX(), muzzleY, getZ());
        Vec3d aim = leadTarget(target, 1.9);
        double dx = aim.x - getX();
        double dy = aim.y - muzzleY;
        double dz = aim.z - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        bolt.setVelocity(dx, dy + horizontal * 0.07, dz, 1.9f, 4.0f);
        bolt.setDamage((float) archetype.rangedDamage());
        getWorld().spawnEntity(bolt);
        serverWorld.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT,
                SoundCategory.NEUTRAL, 0.9f, 1.0f);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Archetype", getArchetype().id());
        nbt.putBoolean("ArchetypeLocked", archetypeLocked);
        nbt.putString("Temperament", temperament.id());
        nbt.putBoolean("TemperamentChosen", temperamentChosen);
        nbt.putBoolean("LoadoutApplied", loadoutApplied);
        if (grudgeUuid != null) {
            nbt.putUuid("Grudge", grudgeUuid);
            nbt.putLong("GrudgeUntil", grudgeUntil);
        }
        nbt.putBoolean("Recruited", recruited);
        nbt.putBoolean("Guarding", guarding);
        nbt.putInt("Trust", trust);
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
        nbt.putBoolean("BaseGuard", guardCenter != null);
        nbt.putString("SettlementRole", settlementRole.id());
        if (guardCenter != null) {
            nbt.putLong("GuardCenter", guardCenter.asLong());
        }
        if (guardOwnerUuid != null) {
            nbt.putUuid("GuardOwner", guardOwnerUuid);
        }
        if (guardFaction != null) {
            nbt.putString("GuardFaction", guardFaction);
        }
        nbt.putString("Family", familyName);
        nbt.putBoolean("Child", child);
        nbt.putInt("ChildAge", childAge);
        nbt.putString("InheritedRole", inheritedRole.id());
        nbt.putBoolean("AimWarm", aimTicks > 0);
        nbt.putInt("AimTicks", aimTicks);
        if (rumorAbout != null) {
            nbt.putUuid("RumorAbout", rumorAbout);
            nbt.putInt("RumorKind", rumorKind);
            nbt.putInt("RumorStrength", rumorStrength);
        }
        if (!bonds.isEmpty()) {
            NbtCompound savedBonds = new NbtCompound();
            for (Map.Entry<UUID, Byte> bond : bonds.entrySet()) {
                savedBonds.putByte(bond.getKey().toString(), bond.getValue());
            }
            nbt.put("Bonds", savedBonds);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(ARCHETYPE, nbt.getString("Archetype"));
        // A persisted archetype always wins; only brand-new survivors may roll one.
        archetypeLocked = nbt.contains("Archetype", NbtElement.STRING_TYPE);
        temperament = Temperament.byId(nbt.getString("Temperament"));
        temperamentChosen = nbt.getBoolean("TemperamentChosen");
        grudgeUuid = nbt.containsUuid("Grudge") ? nbt.getUuid("Grudge") : null;
        grudgeUntil = nbt.getLong("GrudgeUntil");
        recruited = nbt.getBoolean("Recruited");
        guarding = nbt.getBoolean("Guarding");
        trust = Math.max(0, Math.min(100, nbt.getInt("Trust")));
        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        guardCenter = nbt.getBoolean("BaseGuard") ? BlockPos.fromLong(nbt.getLong("GuardCenter")) : null;
        guardOwnerUuid = nbt.containsUuid("GuardOwner") ? nbt.getUuid("GuardOwner") : null;
        guardFaction = nbt.contains("GuardFaction", NbtElement.STRING_TYPE) ? nbt.getString("GuardFaction") : null;
        settlementRole = SettlementRole.byId(nbt.getString("SettlementRole"));
        if (nbt.getBoolean("BaseGuard") && settlementRole == SettlementRole.NONE) {
            settlementRole = SettlementRole.GUARD;
        }
        // Equipment itself is saved by the vanilla Mob NBT (HandItems/ArmorItems);
        // only re-roll the loadout for survivors that never had one.
        loadoutApplied = nbt.getBoolean("LoadoutApplied");
        familyName = nbt.getString("Family");
        child = nbt.getBoolean("Child");
        childAge = Math.max(0, nbt.getInt("ChildAge"));
        inheritedRole = SettlementRole.byId(nbt.getString("InheritedRole"));
        aimTicks = Math.max(0, nbt.getInt("AimTicks"));
        rumorAbout = nbt.containsUuid("RumorAbout") ? nbt.getUuid("RumorAbout") : null;
        rumorKind = Math.max(0, nbt.getInt("RumorKind"));
        rumorStrength = Math.max(0, nbt.getInt("RumorStrength"));
        bonds.clear();
        NbtCompound savedBonds = nbt.getCompound("Bonds");
        for (String key : savedBonds.getKeys()) {
            try {
                bonds.put(UUID.fromString(key), savedBonds.getByte(key, (byte) 0));
            } catch (IllegalArgumentException ignored) {
                // One corrupt bond must not sink the social graph.
            }
        }
        if (child) {
            setChild(true);
        }
    }

    // ------------------------------------------------------------------ farming

    /**
     * Real farmer labour, not villager wandering: walk the fields, harvest
     * crops the moment they reach full growth, replant the same crop on the
     * spot, till fresh soil when the plot runs out of farmland, and sow a
     * varied rotation. Works the worksite by day, knocks off at night.
     */
    private void farmTick() {
        if (settlementRole != SettlementRole.FARMER || guardCenter == null || recruited
                || eatTimer > 0 || getTarget() != null || this.hasVehicle()) {
            return;
        }
        World world = getWorld();
        if (world.isClient || !world.isDay() || !isOnGround()) {
            return;
        }
        long now = world.getTime();
        if (now < nextFarmAction) {
            return;
        }
        BlockPos plot = worksite();

        // 1) A mature crop within the plot: walk to it, then cut it down and
        //    replant the same crop in the same breath, like a player does.
        if (this.farmTarget == null) {
            if (now % 20L == 0L) {
                this.farmTarget = findBlock(plot, 8, state -> {
                    if (state.getBlock() instanceof CropBlock crop) {
                        return crop.isMature(state);
                    }
                    return false;
                });
            }
            if (this.farmTarget == null) {
                nextFarmAction = now + 10L;
                return;
            }
        }

        double reach = this.squaredDistanceTo(farmTarget.getX() + 0.5, farmTarget.getY(), farmTarget.getZ() + 0.5);
        if (reach > 3.2 * 3.2) {
            getNavigation().startMovingTo(farmTarget.getX() + 0.5, farmTarget.getY(), farmTarget.getZ() + 0.5, 0.85);
            // Abandon unreachable spots so the farmer never grinds against a wall.
            if (getNavigation().isIdle()) {
                this.farmTarget = null;
                nextFarmAction = now + 40L;
            }
            return;
        }
        getNavigation().stop();
        swingHand(Hand.MAIN_HAND);

        if (world.getBlockState(farmTarget).getBlock() instanceof CropBlock crop
                && crop.isMature(world.getBlockState(farmTarget))) {
            world.breakBlock(farmTarget, true, this);
            world.setBlockState(farmTarget, crop.getDefaultState());
            world.playSound(null, farmTarget.getX(), farmTarget.getY(), farmTarget.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.7f, 1.1f);
        } else if (world.getBlockState(farmTarget).isOf(Blocks.FARMLAND)
                && world.getBlockState(farmTarget.up()).isAir()) {
            // Sow a rotation: mostly wheat, sometimes roots, like mixed farms.
            int roll = random.nextInt(10);
            net.minecraft.block.Block seed = roll < 6 ? Blocks.WHEAT
                    : roll < 8 ? Blocks.CARROTS : Blocks.POTATOES;
            world.setBlockState(farmTarget.up(), seed.getDefaultState());
            world.playSound(null, farmTarget.getX(), farmTarget.getY(), farmTarget.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.7f, 1.2f);
        } else if (world.getBlockState(farmTarget).isOf(Blocks.GRASS_BLOCK)
                || world.getBlockState(farmTarget).isOf(Blocks.DIRT)) {
            // Fresh soil to till first.
            world.setBlockState(farmTarget, Blocks.FARMLAND.getDefaultState());
            world.playSound(null, farmTarget.getX(), farmTarget.getY(), farmTarget.getZ(),
                    SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 0.9f, 1.0f);
            ((ServerWorld) world).spawnParticles(ParticleTypes.POOF,
                    farmTarget.getX() + 0.5, farmTarget.getY() + 1.1, farmTarget.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.01);
        }
        this.farmTarget = null;
        nextFarmAction = now + 20L;
    }

    /** Finds a block matching {@code filter} within {@code radius} of {@code center}. */
    private BlockPos findBlock(BlockPos center, int radius, java.util.function.Predicate<net.minecraft.block.BlockState> filter) {
        int found = 0;
        BlockPos best = null;
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -1, -radius), center.add(radius, 1, radius))) {
            if (filter.test(getWorld().getBlockState(pos))) {
                found++;
                // Random-ish pick: every tenth match wins, keeps rows untidy.
                if (best == null || random.nextInt(found) == 0) {
                    best = pos.toImmutable();
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ grudges

    /** Remembers a player for {@code ticks}. Grudges survive saves. */
    public void holdGrudge(PlayerEntity player, long ticks) {
        this.grudgeUuid = player.getUuid();
        this.grudgeUntil = getWorld().getTime() + ticks;
    }

    public boolean grudgeAgainst(PlayerEntity player) {
        return player != null && grudgeUuid != null && grudgeUuid.equals(player.getUuid())
                && getWorld().getTime() < grudgeUntil;
    }

    private void expireGrudge() {
        grudgeUuid = null;
        suspicion = 0;
    }

    /**
     * The social brain, not just revenge: NPCs who SAW you attack one of
     * their own remember your face and drag their friends into it. Chill
     * souls never fight, but they remember too.
     */
    private void witnessAttack(PlayerEntity attacker) {
        holdGrudge(attacker, 72000L); // an hour of in-game time
        seedRumor(attacker.getUuid(), 3, 2);
        for (net.minecraft.entity.Entity witness : getWorld().getOtherEntities(this,
                getBoundingBox().expand(14.0), e -> e instanceof SurvivorEntity ally
                        && ally.isAlive() && !ally.isRecruited()
                        && ally.effectiveFaction().equals(effectiveFaction()))) {
            SurvivorEntity ally = (SurvivorEntity) witness;
            ally.holdGrudge(attacker, 72000L);
            ally.seedRumor(attacker.getUuid(), 3, 2);
            if (ally.temperament != Temperament.CHILL) {
                ally.setTarget(attacker);
            }
        }
    }

    /**
     * Static hook fired by the block-break listener when a player harvests a
     * mature crop inside a settlement's fields: farm folk call that theft.
     */
    public static void onCropTheft(ServerWorld world, PlayerEntity thief, String faction, BlockPos pos) {
        com.rivalrealms.world.RealmState realms = com.rivalrealms.world.RealmState.get(world);
        realms.adjustReputation(thief.getUuid(), faction, -4);
        for (net.minecraft.entity.Entity witness : world.getOtherEntities(null,
                new net.minecraft.util.math.Box(pos).expand(16.0), e -> e instanceof SurvivorEntity folk
                        && !folk.isRecruited()
                        && folk.effectiveFaction().equals(faction))) {
            SurvivorEntity folk = (SurvivorEntity) witness;
            folk.holdGrudge(thief, 108000L);
            if (folk.temperament != Temperament.CHILL && folk.isSettlementWorker()) {
                folk.setTarget(thief);
            }
        }
    }

    /**
     * Aiming a drawn crossbow straight at someone is as good as attacking
     * them. Aim long enough and even the patient ones decide you meant it.
     */
    private void suspicionTick() {
        long now = getWorld().getTime();
        if (now % 30L != 0L || !(getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (grudgeUuid == null) {
            suspicion = Math.max(0, suspicion - 1);
        }
        PlayerEntity player = getWorld().getClosestPlayer(this, 5.0);
        if (player == null || player.isCreative() || player.isSpectator() || isOwner(player)) {
            return;
        }
        ItemStack held = player.getMainHandStack();
        if (!held.isOf(net.minecraft.item.Items.CROSSBOW)
                || !net.minecraft.item.CrossbowItem.isCharged(held)) {
            return;
        }
        Vec3d toMe = getPos().add(0.0, getStandingEyeHeight(), 0.0)
                .subtract(player.getPos().add(0.0, player.getStandingEyeHeight(), 0.0)).normalize();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        if (look.dotProduct(toMe) < 0.93) {
            return;
        }
        suspicion++;
        if (suspicion >= 2 && !grudgeAgainst(player)) {
            holdGrudge(player, 48000L);
            player.sendMessage(Text.literal(getName().getString()
                    + " does not like the way you are aiming that.").formatted(Formatting.GOLD), true);
            if (temperament != Temperament.CHILL) {
                setTarget(player);
            }
        }
    }

    /** Wary and hostile survivors repay attacks; good-natured ones run instead. */
    private static final class PersonalityRevengeGoal extends RevengeGoal {
        private final SurvivorEntity survivor;

        private PersonalityRevengeGoal(SurvivorEntity survivor) {
            super(survivor);
            this.survivor = survivor;
        }

        @Override
        public boolean canStart() {
            return survivor.temperament != Temperament.CHILL && super.canStart();
        }
    }

    private static final class ConditionalProjectileGoal extends ProjectileAttackGoal {
        private final SurvivorEntity survivor;

        private ConditionalProjectileGoal(SurvivorEntity survivor, double mobSpeed, int intervalTicks, float maxShootRange) {
            super(survivor, mobSpeed, intervalTicks, maxShootRange);
            this.survivor = survivor;
        }

        @Override
        public boolean canStart() {
            return survivor.isRanged() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return survivor.isRanged() && super.shouldContinue();
        }
    }
}

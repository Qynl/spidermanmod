package com.rivalrealms.entity;

import com.rivalrealms.item.ModItems;
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
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import com.rivalrealms.world.RealmState;
import com.rivalrealms.world.SettlementRole;

import java.util.List;
import java.util.UUID;

/**
 * A player-like survivor with a real inventory, equipment, combat goals,
 * persistent relationships, and a culture that changes the way it looks and
 * fights. The same server-authoritative entity works in singleplayer and on a
 * dedicated server.
 */
public class SurvivorEntity extends PathAwareEntity implements RangedAttackMob {
    private static final TrackedData<String> ARCHETYPE = DataTracker.registerData(
            SurvivorEntity.class, TrackedDataHandlerRegistry.STRING);

    private static final String[] NAMES = {
            "Mara", "Rowan", "Vera", "Jules", "Iris", "Tomas", "Nell", "Corin",
            "Sable", "Hugo", "Mae", "Bram", "Rook", "Lena", "Otto", "Ash"
    };

    private UUID ownerUuid;
    private int trust;
    private boolean recruited;
    private boolean guarding;
    private boolean loadoutApplied;
    private UUID guardOwnerUuid;
    private BlockPos guardCenter;
    private String guardFaction;
    private SettlementRole settlementRole = SettlementRole.NONE;
    private long nextTargetScan;
    private long nextWorkMove;

    public SurvivorEntity(EntityType<? extends SurvivorEntity> entityType, World world) {
        super(entityType, world);
        setPersistent();
        setCanPickUpLoot(true);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
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
        goalSelector.add(1, new ConditionalProjectileGoal(this, 1.0, 20, 18.0f));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.add(3, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        goalSelector.add(5, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
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

        ensureLoadout();
        PlayerEntity owner = ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);

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

        // Outlaws occasionally reload a damaged weapon by swapping back to
        // their culture's ranged weapon. It also makes their inventories feel
        // alive instead of being a cosmetic skin only.
        if (getWorld().getTime() % 80L == 0 && isRanged() && getOffHandStack().isEmpty()) {
            setStackInHand(Hand.OFF_HAND, getArchetype().rangedStack());
        }
    }

    private void ensureLoadout() {
        if (loadoutApplied) {
            return;
        }
        if (getArchetype() == Archetype.KNIGHT && random.nextInt(4) != 0) {
            Archetype[] cultures = Archetype.values();
            setArchetype(cultures[random.nextInt(cultures.length)]);
        } else {
            setArchetype(getArchetype());
        }
        if (!hasCustomName()) {
            setCustomName(Text.literal(NAMES[random.nextInt(NAMES.length)] + " · " + getArchetype().title()));
        }
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

        // Free survivors are dangerous to players; settlement guards are not.
        // Guards only select rival NPCs when diplomacy says the factions are at war.
        if (guardCenter == null) {
            PlayerEntity player = getWorld().getClosestPlayer(this, 18.0);
            if (player != null && !player.isCreative() && !player.isSpectator()) {
                setTarget(player);
                return;
            }
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
        if (settlementRole.isCombatant()) {
            acquireRivalTarget();
        }
    }

    private BlockPos worksite() {
        int offset = switch (settlementRole) {
            case BUILDER -> 8;
            case FARMER -> -8;
            case TRADER -> 0;
            case BLACKSMITH -> 5;
            default -> 0;
        };
        return guardCenter.add(offset, 0, settlementRole == SettlementRole.FARMER ? 7 : -5);
    }

    public void setArchetype(Archetype archetype) {
        dataTracker.set(ARCHETYPE, archetype.id());
        loadoutApplied = true;
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
            archetype.equip(this);
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
        setCustomName(Text.literal(role.displayName() + " · " + getArchetype().title()));
    }

    public UUID guardOwnerUuid() {
        return guardOwnerUuid;
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
        setCustomName(Text.literal("Betrayer · " + getArchetype().title()));
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal(getName().getString() + " has betrayed you!"), false);
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);

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
                player.sendMessage(Text.literal(getName().getString() + " joined your crew. Trust: " + trust + "/100"), false);
                return ActionResult.SUCCESS;
            }
            player.sendMessage(Text.literal("This survivor already belongs to another crew."), true);
            return ActionResult.FAIL;
        }

        if (isOwner(player)) {
            if (held.isOf(Items.GOLDEN_CARROT) || held.isOf(Items.COOKED_BEEF)) {
                trust = Math.min(100, trust + 8);
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                player.sendMessage(Text.literal("Trust increased to " + trust + "/100."), true);
                return ActionResult.SUCCESS;
            }
            if (held.isEmpty()) {
                guarding = !guarding;
                player.sendMessage(Text.literal(guarding ? "Companion is holding this position." : "Companion is following you."), true);
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity player) {
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
        return super.damage(source, amount);
    }

    @Override
    public void shootAt(LivingEntity target, float pullProgress) {
        if (!isRanged() || !target.isAlive()) {
            return;
        }
        ArrowEntity arrow = new ArrowEntity(getWorld(), this, new ItemStack(Items.ARROW), null);
        double dx = target.getX() - getX();
        double dy = target.getY() + target.getStandingEyeHeight() * 0.55 - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.setVelocity(dx, dy + horizontal * 0.16, dz, 1.6f, 10.0f);
        arrow.setDamage(getArchetype().rangedDamage());
        getWorld().spawnEntity(arrow);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Archetype", getArchetype().id());
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
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(ARCHETYPE, nbt.getString("Archetype"));
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
        loadoutApplied = false;
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

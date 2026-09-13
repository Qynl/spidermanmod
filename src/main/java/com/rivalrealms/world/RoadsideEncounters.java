package com.rivalrealms.world;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.entity.Archetype;
import com.rivalrealms.entity.ModEntities;
import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The road is not a battlefield. Lost children, stranded merchants, horse
 * disputes, festivals, gossips and travelers who know your name — the
 * encounters that make walking somewhere worth it. Each open encounter is
 * tracked with a deadline: help, ignore, or watch it resolve on its own.
 */
public final class RoadsideEncounters {
    private RoadsideEncounters() {
    }

    /** Kinds. */
    public static final int LOST_CHILD = 0;
    public static final int STRANDED_MERCHANT = 1;
    public static final int HORSE_DISPUTE = 2;
    public static final int FESTIVAL = 3;
    public static final int GOSSIP = 4;
    public static final int RECOGNIZER = 5;

    private static final class Open {
        final int kind;
        final UUID subject;
        final BlockPos pos;
        final long resolveBy;
        boolean helped;

        Open(int kind, UUID subject, BlockPos pos, long resolveBy) {
            this.kind = kind;
            this.subject = subject;
            this.pos = pos.toImmutable();
            this.resolveBy = resolveBy;
        }
    }

    private static final Map<UUID, Open> OPEN = new HashMap<>();

    // ------------------------------------------------------------- spawning

    /** Rolls a non-combat encounter near a player. Returns the kind or -1. */
    public static int spawn(ServerWorld world, BlockPos near) {
        int roll = world.random.nextInt(6);
        BlockPos at = surface(world, near.add(world.random.nextInt(17) - 8, 0, world.random.nextInt(17) - 8));
        return switch (roll) {
            case LOST_CHILD -> spawnLostChild(world, at);
            case STRANDED_MERCHANT -> spawnStrandedMerchant(world, at);
            case HORSE_DISPUTE -> spawnHorseDispute(world, at);
            case FESTIVAL -> spawnFestival(world, at);
            case GOSSIP -> spawnGossip(world, at);
            default -> spawnRecognizer(world, at);
        };
    }

    private static int spawnLostChild(ServerWorld world, BlockPos at) {
        SurvivorEntity child = spawnOne(world, at, Archetype.HEARTHFOLK);
        if (child == null) {
            return -1;
        }
        child.setChild(true);
        child.setCustomName(Text.literal("Lost Child").formatted(Formatting.YELLOW));
        child.setFamily(LivingRealm.surname("Hearthfolk", world.random), null);
        track(child, LOST_CHILD, world);
        return LOST_CHILD;
    }

    private static int spawnStrandedMerchant(ServerWorld world, BlockPos at) {
        SurvivorEntity merchant = spawnOne(world, at, Archetype.KNIGHT);
        if (merchant == null) {
            return -1;
        }
        merchant.setCustomName(Text.literal("Stranded Merchant").formatted(Formatting.AQUA));
        merchant.setFamily(LivingRealm.surname("Crownlands", world.random), null);
        // A cart that lost its wheel: a stripped log on its side.
        set(world, at.east(2), net.minecraft.block.Blocks.STRIPPED_OAK_LOG);
        track(merchant, STRANDED_MERCHANT, world);
        return STRANDED_MERCHANT;
    }

    private static int spawnHorseDispute(ServerWorld world, BlockPos at) {
        SurvivorEntity first = spawnOne(world, at, Archetype.OUTLAW);
        SurvivorEntity second = spawnOne(world, at.east(3), Archetype.OUTLAW);
        if (first == null || second == null) {
            return -1;
        }
        first.setCustomName(Text.literal("Horse Trader").formatted(Formatting.WHITE));
        second.setCustomName(Text.literal("Drifter").formatted(Formatting.WHITE));
        // The bone of contention, standing between them.
        track(first, HORSE_DISPUTE, world);
        track(second, HORSE_DISPUTE, world);
        return HORSE_DISPUTE;
    }

    private static int spawnFestival(ServerWorld world, BlockPos at) {
        int spawned = 0;
        for (int i = 0; i < 4; i++) {
            SurvivorEntity dancer = spawnOne(world, at.add(i * 2 - 3, 0, i % 2 * 2), Archetype.HEARTHFOLK);
            if (dancer == null) {
                continue;
            }
            dancer.setCustomName(Text.literal("Festivalgoer").formatted(Formatting.LIGHT_PURPLE));
            spawned++;
        }
        if (spawned == 0) {
            return -1;
        }
        // A little celebration in the grass: fire, music, dancing lights.
        world.setBlockState(at, net.minecraft.block.Blocks.CAMPFIRE.getDefaultState());
        RealmMusic.play(world, at.up(), RealmMusic.HARVEST_REEL);
        return FESTIVAL;
    }

    private static int spawnGossip(ServerWorld world, BlockPos at) {
        SurvivorEntity gossip = spawnOne(world, at, Archetype.HEARTHFOLK);
        if (gossip == null) {
            return -1;
        }
        gossip.setCustomName(Text.literal("Wayfarer").formatted(Formatting.GRAY));
        track(gossip, GOSSIP, world);
        return GOSSIP;
    }

    private static int spawnRecognizer(ServerWorld world, BlockPos at) {
        SurvivorEntity traveler = spawnOne(world, at, Archetype.values()[world.random.nextInt(Archetype.values().length)]);
        if (traveler == null) {
            return -1;
        }
        traveler.setCustomName(Text.literal("Traveler").formatted(Formatting.WHITE));
        track(traveler, RECOGNIZER, world);
        return RECOGNIZER;
    }

    private static void track(SurvivorEntity entity, int kind, ServerWorld world) {
        OPEN.put(entity.getUuid(), new Open(kind, entity.getUuid(),
                entity.getBlockPos(), world.getTime() + 6000L));
    }

    // ------------------------------------------------------------- resolution

    /** Called every 100 ticks: resolves open encounters and thanks helpers. */
    public static void tick(ServerWorld world) {
        if (OPEN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Open>> iterator = OPEN.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Open> entry = iterator.next();
            Open open = entry.getValue();
            Entity subject = world.getEntity(open.subject);
            if (subject == null || !subject.isAlive()) {
                iterator.remove();
                continue;
            }
            if (world.getTime() > open.resolveBy) {
                // Nobody came. The moment passes like most moments do.
                if (open.kind == LOST_CHILD) {
                    chronicleQuietly(world, "A lost child wandered home alone.");
                }
                subject.discard();
                iterator.remove();
                continue;
            }
            PlayerEntity near = world.getClosestPlayer(subject.getX(), subject.getY(), subject.getZ(), 5.0, false);
            if (near == null) {
                continue;
            }
            switch (open.kind) {
                case LOST_CHILD -> resolveChild(world, (SurvivorEntity) subject, near, open, iterator);
                case STRANDED_MERCHANT -> merchantHint(world, (SurvivorEntity) subject, near);
                case HORSE_DISPUTE -> disputeBark(world, (SurvivorEntity) subject, near);
                case GOSSIP -> gossipBark(world, (SurvivorEntity) subject, near);
                case RECOGNIZER -> recognizeBark(world, (SurvivorEntity) subject, near);
                default -> {
                }
            }
        }
    }

    private static void resolveChild(ServerWorld world, SurvivorEntity child, PlayerEntity near,
                                     Open open, Iterator<Map.Entry<UUID, Open>> iterator) {
        RealmState state = RealmState.get(world);
        int before = state.getReputation(near.getUuid(), "Hearthfolk");
        state.adjustReputation(near.getUuid(), "Hearthfolk", 6);
        world.playSound(null, child.getX(), child.getY(), child.getZ(),
                SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                child.getX(), child.getY() + 2, child.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
        near.sendMessage(Text.literal("The child lights up: \"You found my road home! "
                + "The Hearthfolk will hear of you.\" (Hearthfolk reputation "
                + before + " → " + state.getReputation(near.getUuid(), "Hearthfolk") + ")")
                .formatted(Formatting.GREEN), false);
        state.chronicle(world.getTime(), "A traveler reunited a lost child with the road home.", false);
        LivingRealm.seedPlayerDeed(world, near, "found a lost child");
        child.discard();
        iterator.remove();
    }

    private static void merchantHint(ServerWorld world, SurvivorEntity merchant, PlayerEntity near) {
        if (world.getTime() % 200L != 0L) {
            return;
        }
        near.sendMessage(Text.literal(merchant.getName().getString()
                + ": \"Axle snapped a mile back. Bring me an iron ingot and half this load is yours.\"")
                .formatted(Formatting.AQUA), true);
    }

    /** Called from interactMob: an iron ingot repairs the cart. */
    public static boolean tryHelpMerchant(SurvivorEntity merchant, PlayerEntity player) {
        if (!merchant.hasCustomName()
                || !"Stranded Merchant".equals(merchant.getName().getString())) {
            return false;
        }
        Open open = OPEN.get(merchant.getUuid());
        if (open == null || open.helped) {
            return true; // already helped; eat the interaction
        }
        if (!(merchant.getWorld() instanceof ServerWorld world)) {
            return true;
        }
        if (!player.getMainHandStack().isOf(net.minecraft.item.Items.IRON_INGOT)) {
            player.sendMessage(Text.literal(merchant.getName().getString()
                    + ": \"An iron ingot for the axle, that's all I ask.\"").formatted(Formatting.AQUA), true);
            return true;
        }
        if (!player.isCreative()) {
            player.getMainHandStack().decrement(1);
        }
        open.helped = true;
        RealmState state = RealmState.get(world);
        state.adjustReputation(player.getUuid(), "Crownlands", 4);
        state.adjustReputation(player.getUuid(), "Hearthfolk", 2);
        var drops = new net.minecraft.item.ItemStack[]{
                new net.minecraft.item.ItemStack(net.minecraft.item.Items.EMERALD, 4 + world.random.nextInt(4)),
                new net.minecraft.item.ItemStack(com.rivalrealms.item.ModItems.ROYAL_COIN, 1 + world.random.nextInt(2)),
                new net.minecraft.item.ItemStack(net.minecraft.item.Items.BREAD, 4)};
        for (var drop : drops) {
            merchant.dropStack(drop);
        }
        player.sendMessage(Text.literal("\"Bless your hands! Take your pick of the load.\"")
                .formatted(Formatting.GREEN), false);
        state.chronicle(world.getTime(), "A traveler repaired a stranded merchant's cart on the open road.", false);
        LivingRealm.seedPlayerDeed(world, player, "saved a merchant's caravan");
        merchant.discard();
        OPEN.remove(merchant.getUuid());
        return true;
    }

    private static void disputeBark(ServerWorld world, SurvivorEntity speaker, PlayerEntity near) {
        if (world.getTime() % 260L != 0L) {
            return;
        }
        String line = world.random.nextBoolean()
                ? "\"That sorrel was MY father's, and I have the brand to prove it!\""
                : "\"Brands fade. Deeds don't. That horse carried me from Dustveil itself!\"";
        near.sendMessage(Text.literal(speaker.getName().getString() + " " + line)
                .formatted(Formatting.GRAY), true);
    }

    private static void gossipBark(ServerWorld world, SurvivorEntity gossip, PlayerEntity near) {
        if (world.getTime() % 300L != 0L) {
            return;
        }
        var entries = RealmState.get(world).chronicle();
        String line;
        if (!entries.isEmpty()) {
            var latest = entries.get(entries.size() - 1);
            line = "Heard the news? Day " + latest.day() + ": " + latest.text() + " Is that how it truly happened?";
        } else {
            line = "Quiet roads lately. Too quiet, my mother would say.";
        }
        near.sendMessage(Text.literal(gossip.getName().getString() + ": " + line)
                .formatted(Formatting.GRAY), true);
        // Gossip has a voice, pitched like the speaker.
        if (world instanceof ServerWorld serverWorld) {
            com.rivalrealms.sound.ModSounds.playProfiled(serverWorld, gossip.getBlockPos(),
                    "rumor_player", gossip.getUuid(), 1.0f, 0.9f);
        }
    }

    private static void recognizeBark(ServerWorld world, SurvivorEntity traveler, PlayerEntity near) {
        if (world.getTime() % 300L != 0L) {
            return;
        }
        RealmState state = RealmState.get(world);
        String faction = traveler.effectiveFaction();
        int rep = state.getReputation(near.getUuid(), faction);
        String title = LivingRealm.titleFor(rep);
        String line;
        if (rep <= -40) {
            line = "\"Keep walking, " + title + ". The " + faction + " want your head.\"";
        } else if (rep >= 30) {
            line = "\"Wait — you're " + title + " of the " + faction + "! An honor, truly.\"";
        } else if (rep <= -15) {
            line = "\"The " + faction + " speak poorly of you, " + title + ".\"";
        } else {
            line = "\"Safe roads, traveler. The " + faction + " mention your name now and then.\"";
        }
        near.sendMessage(Text.literal(traveler.getName().getString() + " " + line)
                .formatted(Formatting.WHITE), true);
    }

    // ------------------------------------------------------------- helpers

    private static SurvivorEntity spawnOne(ServerWorld world, BlockPos at, Archetype culture) {
        SurvivorEntity survivor = ModEntities.SURVIVOR.create(world);
        if (survivor == null) {
            return null;
        }
        BlockPos spot = surface(world, at);
        survivor.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                world.random.nextFloat() * 360.0f, 0.0f);
        survivor.setArchetype(culture);
        survivor.setFamily(LivingRealm.surname(culture.faction(), world.random), null);
        if (!world.spawnEntity(survivor)) {
            return null;
        }
        return survivor;
    }

    private static BlockPos surface(ServerWorld world, BlockPos requested) {
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, requested);
        return top.getY() >= world.getBottomY() && top.getY() < world.getTopY() ? top : requested;
    }

    private static void set(ServerWorld world, BlockPos pos, net.minecraft.block.Block block) {
        if (world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            world.setBlockState(pos, block.getDefaultState());
        }
    }

    private static void chronicleQuietly(ServerWorld world, String text) {
        RealmState.get(world).chronicle(world.getTime(), text, false);
    }

    /** Test hook for tooling. */
    public static int openCount() {
        return OPEN.size();
    }
}

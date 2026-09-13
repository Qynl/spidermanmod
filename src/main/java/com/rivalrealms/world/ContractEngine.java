package com.rivalrealms.world;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.sound.ModSounds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * The contract board: work born from what actually happened. A settlement
 * that bled posts a bounty on the kind of raider that bled it; a hungry
 * one posts gather orders for grain; a curious one pays scouts to look at
 * the strange place over the hill. Contracts persist in the realm save,
 * expire if ignored, and pay coin plus standing where they belong.
 */
public final class ContractEngine {
    private ContractEngine() {
    }

    public static final String BOUNTY = "bounty";     // kill a champion of a hostile culture
    public static final String HUNT = "hunt";         // kill N of a hostile culture
    public static final String DELIVER = "deliver";   // carry word to a sister settlement
    public static final String GATHER = "gather";     // bring N of an item
    public static final String EXPLORE = "explore";   // visit a strange place

    private static long lastBoardVoice;

    // ------------------------------------------------------------- issuing

    /** Slow heartbeat: fed, loaded settlements post work. Called from RealmEvents. */
    public static void tick(ServerWorld world) {
        RealmState state = RealmState.get(world);
        List<RealmState.BaseRecord> loaded = new ArrayList<>();
        for (RealmState.BaseRecord base : state.bases()) {
            if (!base.abandoned() && base.food() >= 16
                    && areaLoaded(world, base.center(), base.radius() + 8)) {
                loaded.add(base);
            }
        }
        if (loaded.isEmpty()) {
            return;
        }
        // Expire stale work first.
        state.expireContracts(world.getTime());
        // Then post: at most two open contracts per settlement, ~1 in 4 cycles.
        if (world.getTime() % 1200L != 0L || world.random.nextFloat() >= 0.35f) {
            return;
        }
        RealmState.BaseRecord base = loaded.get(world.random.nextInt(loaded.size()));
        if (state.openContractsFor(base.center().asLong()) >= 2) {
            return;
        }
        issueOne(world, state, base);
    }

    private static void issueOne(ServerWorld world, RealmState state, RealmState.BaseRecord base) {
        int roll = world.random.nextInt(10);
        long now = world.getTime();
        switch (roll) {
            case 0, 1 -> {
                // A champion's head is worth a champion's price.
                state.addContract(BOUNTY, base.center().asLong(), "champion", 1, 0,
                        4 + world.random.nextInt(4), 10, now + 72000L);
            }
            case 2, 3, 4 -> {
                // The marauders prey on everyone; everyone pays to hunt them.
                state.addContract(HUNT, base.center().asLong(), "marauders",
                        2 + world.random.nextInt(2), 0,
                        3 + world.random.nextInt(3), 8, now + 96000L);
            }
            case 5 -> {
                // Deliver word to a nearby friendly town.
                RealmState.BaseRecord target = pickNeighbor(world, state, base);
                if (target != null) {
                    state.addContract(DELIVER, base.center().asLong(),
                            String.valueOf(target.center().asLong()), 1, 0,
                            3 + world.random.nextInt(2), 8, now + 96000L);
                }
            }
            case 6, 7 -> {
                // Hungry towns buy grain; poor towns buy iron.
                String item = base.food() < 30 ? "minecraft:wheat" : "minecraft:iron_ingot";
                int count = 6 + world.random.nextInt(10);
                state.addContract(GATHER, base.center().asLong(), item, count, 0,
                        2 + count / 4, 6, now + 96000L);
            }
            default -> {
                // Scouts wanted: strange places were generated for a reason.
                BlockPos target = strangePlace(state, world);
                if (target != null) {
                    state.addContract(EXPLORE, base.center().asLong(),
                            target.getX() + "," + target.getZ(), 1, 0,
                            3 + world.random.nextInt(3), 7, now + 120000L);
                }
            }
        }
    }

    private static RealmState.BaseRecord pickNeighbor(ServerWorld world, RealmState state,
                                                      RealmState.BaseRecord from) {
        for (RealmState.BaseRecord other : state.bases()) {
            if (other != from && !other.abandoned()
                    && !state.isHostile(other.faction(), from.faction())
                    && other.center().getSquaredDistance(from.center()) <= 220.0 * 220.0) {
                return other;
            }
        }
        return null;
    }

    private static BlockPos strangePlace(RealmState state, ServerWorld world) {
        for (RealmState.BaseRecord base : state.bases()) {
            if (base.style() == BuildStyle.CUSTOM && !base.abandoned()) {
                return base.center();
            }
        }
        return null;
    }

    // --------------------------------------------------------- progression

    /** Kill hooks: bounties and hunts progress when the player lands the blow. */
    public static void onPlayerKill(ServerWorld world, ServerPlayerEntity killer,
                                    boolean champion, String faction) {
        RealmState state = RealmState.get(world);
        for (RealmState.ContractRecord contract : state.contracts()) {
            if (!"active".equals(contract.state())
                    || !killer.getUuid().equals(contract.taker())
                    || contract.deadline() <= world.getTime()) {
                continue;
            }
            boolean hit = false;
            if (BOUNTY.equals(contract.type()) && champion) {
                RealmState.BaseRecord giver = state.findByCenter(contract.giver());
                hit = giver != null && state.isHostile(giver.faction(), faction);
            }
            if (hit) {
                complete(world, killer, contract.id());
                break;
            }
            if (HUNT.equals(contract.type()) && "marauders".equalsIgnoreCase(faction)) {
                state.progressContract(contract.id(), 1);
                if (contract.progress() + 1 >= contract.count()) {
                    complete(world, killer, contract.id());
                } else {
                    ModSounds.playVoiceFor(world, killer.getBlockPos(), "hunt_progress", killer);
                    killer.sendMessage(Text.literal("Hunt progress: " + (contract.progress() + 1)
                            + "/" + contract.count() + " raiders.").formatted(Formatting.YELLOW), true);
                }
                continue;
            }
        }
    }

    /** Explore checks: walk within sight of the strange place. */
    public static void tickExplore(ServerWorld world) {
        RealmState state = RealmState.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            for (RealmState.ContractRecord contract : state.contracts()) {
                if (!"active".equals(contract.state()) || !EXPLORE.equals(contract.type())
                        || !player.getUuid().equals(contract.taker())) {
                    continue;
                }
                String[] parts = contract.target().split(",");
                if (parts.length != 2) {
                    continue;
                }
                try {
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[1]);
                    if (player.squaredDistanceTo(x, player.getY(), z) <= 28.0 * 28.0) {
                        complete(world, player, contract.id());
                    }
                } catch (NumberFormatException ignored) {
                    // A malformed coordinate simply never completes.
                }
            }
        }
    }

    /**
     * Turning work in at a notice board: gathers hand over items, delivers
     * land at the right town, everything else pays at the home board.
     */
    public static boolean tryTurnIn(ServerWorld world, ServerPlayerEntity player,
                                    BlockPos boardPos, ItemStack held) {
        RealmState state = RealmState.get(world);
        RealmState.BaseRecord board = nearestBase(state, boardPos);
        if (board == null) {
            return false;
        }
        // Board pitch the first time each visit: there is work here.
        if (world.getTime() - lastBoardVoice > 2400L) {
            lastBoardVoice = world.getTime();
            ModSounds.playVoiceFor(world, boardPos, "board_pitch", player);
        }
        printBoard(world, player, board);

        // Complete an active deliver at its destination board.
        for (RealmState.ContractRecord contract : state.contracts()) {
            if ("active".equals(contract.state()) && DELIVER.equals(contract.type())
                    && player.getUuid().equals(contract.taker())
                    && String.valueOf(board.center().asLong()).equals(contract.target())) {
                complete(world, player, contract.id());
                return true;
            }
        }
        // Gather: hand the goods over right here.
        for (RealmState.ContractRecord contract : state.contracts()) {
            if ("active".equals(contract.state()) && GATHER.equals(contract.type())
                    && player.getUuid().equals(contract.taker())
                    && board.center().asLong() == contract.giver()
                    && net.minecraft.registry.Registries.ITEM.getId(held.getItem()).getPath()
                            .equals(contract.target().replace("minecraft:", ""))) {
                if (held.getCount() >= contract.count()) {
                    held.decrement(contract.count());
                    complete(world, player, contract.id());
                } else {
                    player.sendMessage(Text.literal("The board wants " + contract.count()
                            + " - you hold " + held.getCount() + ".").formatted(Formatting.GRAY), true);
                }
                return true;
            }
        }
        // Nothing handed in; maybe there is a gather order worth hearing about.
        for (RealmState.ContractRecord contract : state.contracts()) {
            if ("offer".equals(contract.state()) && GATHER.equals(contract.type())
                    && board.center().asLong() == contract.giver()) {
                ModSounds.playVoiceFor(world, boardPos, "gather_request", player);
                return true;
            }
        }
        return true;
    }

    /** Accepting an offer: the giver speaks, the work is yours. */
    public static void accept(ServerWorld world, ServerPlayerEntity player, String id) {
        RealmState state = RealmState.get(world);
        RealmState.ContractRecord contract = state.findContract(id);
        if (contract == null || !"offer".equals(contract.state())) {
            player.sendMessage(Text.literal("That work is already taken or gone.")
                    .formatted(Formatting.RED), true);
            return;
        }
        if (state.activateContract(id, player.getUuid())) {
            ModSounds.playVoiceFor(world, player.getBlockPos(), "contract_offer", player);
            String where = nameOf(state, contract.giver());
            player.sendMessage(Text.literal("Taken: " + describe(state, contract)
                    + " (" + where + ")").formatted(Formatting.GOLD), false);
            if (DELIVER.equals(contract.type())) {
                ModSounds.playVoiceFor(world, player.getBlockPos(), "deliver_given", player);
            } else if (EXPLORE.equals(contract.type())) {
                ModSounds.playVoiceFor(world, player.getBlockPos(), "explore_hint", player);
            }
        }
    }

    private static void complete(ServerWorld world, ServerPlayerEntity player, String id) {
        RealmState state = RealmState.get(world);
        RealmState.ContractRecord contract = state.findContract(id);
        if (contract == null || "done".equals(contract.state())) {
            return;
        }
        state.finishContract(id);
        RealmState.BaseRecord giver = state.findByCenter(contract.giver());
        String faction = giver != null ? giver.faction() : "Independent";
        state.adjustReputation(player.getUuid(), faction, contract.rewardRep());
        if (giver != null) {
            giver.adjustLocalReputation(player.getUuid(), contract.rewardRep() / 2);
        }
        // The purse drops at your feet, real and weighty.
        player.dropStack(new ItemStack(com.rivalrealms.item.ModItems.ROYAL_COIN, contract.rewardCoins()));
        player.dropStack(new ItemStack(Items.EMERALD, 2 + world.random.nextInt(4)));
        ModSounds.playVoiceFor(world, player.getBlockPos(), "contract_done", player);
        player.sendMessage(Text.literal("CONTRACT COMPLETE - " + contract.rewardCoins()
                + " royal coin, +" + contract.rewardRep() + " " + faction + " standing.")
                .formatted(Formatting.GREEN), false);
        state.chronicle(world.getTime(), player.getName().getString() + " fulfilled a contract for "
                + (giver != null ? giver.name() : "the roads") + ".", false);
        LivingRealm.seedPlayerDeed(world, player, "kept a settlement's bargain");
    }

    /** Ignored work expires with a shrug, not a grudge. */
    public static void onExpired(ServerWorld world, ServerPlayerEntity player) {
        ModSounds.playVoiceFor(world, player.getBlockPos(), "contract_expired", player);
    }

    // ------------------------------------------------------------- reading

    public static void printBoard(ServerWorld world, ServerPlayerEntity player,
                                  RealmState.BaseRecord board) {
        RealmState state = RealmState.get(world);
        player.sendMessage(Text.literal("==== NOTICE BOARD · " + board.name() + " ====")
                .formatted(Formatting.GOLD), false);
        int shown = 0;
        for (RealmState.ContractRecord contract : state.contracts()) {
            if (contract.giver() != board.center().asLong() || !"offer".equals(contract.state())) {
                continue;
            }
            String line = "[" + contract.id() + "] " + describe(state, contract)
                    + " - " + contract.rewardCoins() + " coin";
            player.sendMessage(Text.literal(line).formatted(Formatting.YELLOW), false);
            if (++shown >= 4) {
                break;
            }
        }
        if (shown == 0) {
            player.sendMessage(Text.literal("The board is bare. Work comes and goes with the days.")
                    .formatted(Formatting.GRAY), false);
        }
        player.sendMessage(Text.literal("Accept with /rivalrealms contracts accept <id>")
                .formatted(Formatting.DARK_GRAY), false);
    }

    public static String describe(RealmState state, RealmState.ContractRecord contract) {
        return switch (contract.type()) {
            case BOUNTY -> "BOUNTY: a champion raider's head";
            case HUNT -> "HUNT: slay " + contract.count() + " marauders ("
                    + contract.progress() + " done)";
            case DELIVER -> "DELIVER: carry word to " + nameOf(state, Long.parseLong(contract.target()));
            case GATHER -> "GATHER: " + contract.count() + " "
                    + contract.target().replace("minecraft:", "").replace('_', ' ');
            case EXPLORE -> "SCOUT: find what lies at " + contract.target();
            default -> "WORK";
        };
    }

    private static String nameOf(RealmState state, long center) {
        RealmState.BaseRecord base = state.findByCenter(center);
        return base != null ? base.name() : "the roads";
    }

    private static String factionName(long center) {
        return "any";
    }

    private static RealmState.BaseRecord nearestBase(RealmState state, BlockPos pos) {
        RealmState.BaseRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RealmState.BaseRecord base : state.bases()) {
            if (!base.abandoned()) {
                double distance = base.center().getSquaredDistance(pos);
                if (distance < bestDistance && distance <= (base.radius() + 24.0) * (base.radius() + 24.0)) {
                    bestDistance = distance;
                    best = base;
                }
            }
        }
        return best;
    }

    private static boolean areaLoaded(ServerWorld world, BlockPos center, int radius) {
        int[] offsets = {-radius, radius};
        for (int x : offsets) {
            for (int z : offsets) {
                if (!world.getChunkManager().isChunkLoaded((center.getX() + x) >> 4,
                        (center.getZ() + z) >> 4)) {
                    return false;
                }
            }
        }
        return world.getChunkManager().isChunkLoaded(center.getX() >> 4, center.getZ() >> 4);
    }

}

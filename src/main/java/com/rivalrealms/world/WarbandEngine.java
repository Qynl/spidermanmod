package com.rivalrealms.world;

import com.rivalrealms.entity.SurvivorEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real wars between neighbours: a settlement that hates a banner within
 * reach musters a visible warband under a captain - a scout with a spyglass
 * studying the horizon first - and marches it out to take the enemy walls
 * by ladder. The assault itself runs through the settlers' heads: see
 * SurvivorEntity, where a soldier at hostile walls plants ladders and calls
 * the siege down on the town.
 */
public final class WarbandEngine {
    private WarbandEngine() {
    }

    /** Last tick each settlement mustered; wars are not weekly affairs. */
    private static final Map<Long, Long> LAST_MUSTER = new HashMap<>();

    public static void tick(ServerWorld world) {
        if (world.getTime() % 1400L != 700L || world.random.nextFloat() >= 0.20f) {
            return;
        }
        RealmState state = RealmState.get(world);
        for (RealmState.BaseRecord home : state.bases()) {
            if (home.abandoned()
                    || !world.getChunkManager().isChunkLoaded(
                            home.center().getX() >> 4, home.center().getZ() >> 4)) {
                continue;
            }
            long now = world.getTime();
            Long last = LAST_MUSTER.get(home.center().asLong());
            if (last != null && now - last < 72000L) {
                continue;
            }
            // The muster: armed folk under a captain, a scout with the glass.
            List<SurvivorEntity> folk = LivingRealm.population(world, home);
            List<SurvivorEntity> band = new ArrayList<>();
            SurvivorEntity captain = null;
            SurvivorEntity scout = null;
            for (SurvivorEntity f : folk) {
                SettlementRole role = f.settlementRole();
                boolean fighter = role == SettlementRole.CAPTAIN
                        || role == SettlementRole.GUARD
                        || role == SettlementRole.RAIDER
                        || role == SettlementRole.WARLORD;
                if (f.getTarget() != null) {
                    continue;
                }
                if (role == SettlementRole.SCOUT && scout == null) {
                    scout = f;
                } else if (fighter) {
                    band.add(f);
                    if (role == SettlementRole.CAPTAIN || role == SettlementRole.WARLORD) {
                        captain = captain == null ? f : captain;
                    }
                }
            }
            if (band.size() < 2) {
                continue;
            }
            // The rival worth marching against: the nearest hated banner.
            RealmState.BaseRecord enemy = null;
            double best = 260.0 * 260.0;
            for (RealmState.BaseRecord other : state.bases()) {
                if (other.abandoned() || other.center().equals(home.center())
                        || !state.isHostile(home.faction(), other.faction())) {
                    continue;
                }
                double distance = home.center().getSquaredDistance(other.center());
                if (distance > 40.0 * 40.0 && distance < best) {
                    best = distance;
                    enemy = other;
                }
            }
            if (enemy == null) {
                continue;
            }
            LAST_MUSTER.put(home.center().asLong(), now);
            // The scout takes the glass; the whole band re-posts to the enemy
            // flag and starts walking. Their commute discipline keeps them on
            // the road; the assault logic takes over at the walls.
            if (scout != null) {
                scout.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SPYGLASS));
                band.add(scout);
            }
            for (SurvivorEntity member : band) {
                member.setPost(enemy.center());
            }
            SurvivorEntity leader = captain != null ? captain : band.get(0);
            com.rivalrealms.sound.ModSounds.playVoice(world, home.center(), "war_muster");
            com.rivalrealms.sound.ModSounds.playProfiled(world, leader.getBlockPos(),
                    world.random.nextBoolean() ? "oath_sworn" : "scout_report",
                    leader.getUuid(), 1.0f, 1.2f);
            com.rivalrealms.sound.ModSounds.playVoice(world, home.center(), "march_out");
            state.chronicle(now, "War horns at " + home.name() + ": a band marches on "
                    + enemy.name() + ".", true);
            DialogueEngine.noteEvent(world, enemy.center(),
                    "War horns in the hills. The " + home.faction()
                            + " are coming for these walls.");
        }
    }
}

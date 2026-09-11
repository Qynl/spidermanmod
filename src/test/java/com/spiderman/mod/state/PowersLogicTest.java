package com.spiderman.mod.state;

/**
 * Unit tests for the pure-logic ability registry and client powers mirror.
 * Zero dependencies: run with {@code ./build-local/test.sh}.
 */
public final class PowersLogicTest {
    private static int passed;

    private PowersLogicTest() {
    }

    public static void main(String[] args) {
        abilityIds();
        clientPowersReset();
        clientPowersTick();
        clientPowersPings();
        playerPowersGating();
        playerPowersReset();
        System.out.println("PowersLogicTest: " + passed + " assertions passed");
    }

    private static void abilityIds() {
        check(AbilityIds.COUNT == 10, "COUNT == 10");
        check(AbilityIds.NAMES.length == AbilityIds.COUNT, "NAMES length matches COUNT");
        check(AbilityIds.MIN_STAGE.length == AbilityIds.COUNT, "MIN_STAGE length matches COUNT");
        for (int i = 0; i < AbilityIds.COUNT; i++) {
            check(AbilityIds.valid(i), "valid(" + i + ")");
            check(AbilityIds.NAMES[i] != null && !AbilityIds.NAMES[i].isEmpty(),
                    "NAMES[" + i + "] non-empty");
            check(AbilityIds.MIN_STAGE[i] >= 1 && AbilityIds.MIN_STAGE[i] <= 4,
                    "MIN_STAGE[" + i + "] in 1..4");
        }
        check(!AbilityIds.valid(-1), "valid(-1) is false");
        check(!AbilityIds.valid(AbilityIds.COUNT), "valid(COUNT) is false");
        check(!AbilityIds.valid(999), "valid(999) is false");
        // First web shot unlocks at stage 2 (Spider-Sense + first web).
        check(AbilityIds.MIN_STAGE[AbilityIds.SHOT] == 2, "SHOT unlocks at stage 2");
        // Swing/zip movement comes online at stage 3.
        check(AbilityIds.MIN_STAGE[AbilityIds.SWING] == 3, "SWING unlocks at stage 3");
        check(AbilityIds.MIN_STAGE[AbilityIds.ZIP] == 3, "ZIP unlocks at stage 3");
    }

    private static void clientPowersReset() {
        ClientPowers.has = true;
        ClientPowers.stage = 4;
        ClientPowers.mastery = 10;
        ClientPowers.selected = 5;
        ClientPowers.combo = 3;
        ClientPowers.swingActive = true;
        ClientPowers.swingLife = 12;
        ClientPowers.cinematicTicks = 30;
        ClientPowers.clientTick = 777;
        ClientPowers.lastShotHand = 1;
        ClientPowers.addPing(1.0, 2.0, 3.0, 0);
        ClientPowers.reset();
        check(!ClientPowers.has, "reset clears has");
        check(ClientPowers.stage == 0, "reset clears stage");
        check(ClientPowers.mastery == 0, "reset clears mastery");
        check(ClientPowers.selected == 0, "reset clears selected");
        check(ClientPowers.combo == 0, "reset clears combo");
        check(!ClientPowers.swingActive, "reset clears swingActive");
        check(ClientPowers.swingLife == 0, "reset clears swingLife");
        check(ClientPowers.cinematicTicks == 0, "reset clears cinematicTicks");
        check(ClientPowers.clientTick == 0, "reset clears clientTick");
        check(ClientPowers.lastShotHand == -1, "reset clears lastShotHand");
        check(ClientPowers.pings.isEmpty(), "reset clears pings");
    }

    private static void clientPowersTick() {
        ClientPowers.reset();
        ClientPowers.cinematicTicks = 3;
        ClientPowers.swingActive = true;
        ClientPowers.swingLife = 2;
        ClientPowers.tick();
        check(ClientPowers.cinematicTicks == 2, "tick decrements cinematic");
        check(ClientPowers.swingActive && ClientPowers.swingLife == 1,
                "tick decrements swing life");
        ClientPowers.tick();
        check(!ClientPowers.swingActive && ClientPowers.swingLife == 0,
                "swing deactivates at end of life");
        ClientPowers.tick();
        check(ClientPowers.cinematicTicks == 0, "cinematic clamps at zero");
    }

    private static void clientPowersPings() {
        ClientPowers.reset();
        ClientPowers.addPing(1.0, 2.0, 3.0, 1);
        check(ClientPowers.pings.size() == 1, "addPing stores ping");
        double[] ping = ClientPowers.pings.get(0);
        check(ping[0] == 1.0 && ping[1] == 2.0 && ping[2] == 3.0 && ping[3] == 1.0,
                "ping keeps coordinates and kind");
        check(ping[4] == 40.0, "ping lives 40 ticks");
        for (int i = 0; i < 40; i++) {
            ClientPowers.tick();
        }
        check(ClientPowers.pings.isEmpty(), "ping expires after 40 ticks");
        // Flood: the list must stay bounded (oldest evicted first).
        for (int i = 0; i < 25; i++) {
            ClientPowers.addPing(i, 0.0, 0.0, 0);
        }
        check(ClientPowers.pings.size() <= 10, "ping list stays bounded");
        check(ClientPowers.pings.get(0)[0] > 0.0, "oldest pings evicted first");
    }

    private static void playerPowersGating() {
        PlayerPowers powers = new PlayerPowers();
        check(!powers.canUse(AbilityIds.SHOT, 1000), "powerless player cannot use abilities");
        powers.hasPowers = true;
        powers.stage = 1;
        check(!powers.canUse(AbilityIds.SHOT, 1000), "stage gate blocks early shot");
        check(!powers.canUse(99, 1000), "invalid id rejected");
        powers.stage = 2;
        check(powers.canUse(AbilityIds.SHOT, 1000), "unlocked ability usable");
        powers.cooldowns.put(AbilityIds.SHOT, 1500L);
        check(!powers.canUse(AbilityIds.SHOT, 1000), "cooldown blocks reuse");
        check(powers.canUse(AbilityIds.SHOT, 1500), "cooldown expires on time");
        check(powers.canUse(AbilityIds.TRAP, 1000), "cooldowns are per-ability");
    }

    private static void playerPowersReset() {
        PlayerPowers powers = new PlayerPowers();
        powers.hasPowers = true;
        powers.stage = 3;
        powers.mastery = 500;
        powers.selected = 4;
        powers.cooldowns.put(AbilityIds.SHOT, 9999L);
        powers.combo = 5;
        powers.comboUntil = 9999L;
        powers.swinging = true;
        powers.zipTicks = 12;
        powers.doubleJumpUsed = true;
        powers.climbing = true;
        powers.wallRunUntil = 9999L;
        powers.focusTicks = 7;
        powers.resetTransient();
        check(powers.hasPowers && powers.stage == 3, "reset keeps persistent powers");
        check(powers.mastery == 500 && powers.selected == 4, "reset keeps mastery/selection");
        check(powers.cooldowns.isEmpty(), "reset clears cooldowns");
        check(powers.combo == 0 && powers.comboUntil == 0, "reset clears combo");
        check(!powers.swinging, "reset clears swing");
        check(powers.zipTicks == 0, "reset clears zip");
        check(!powers.doubleJumpUsed && !powers.climbing, "reset clears climb/double-jump");
        check(powers.wallRunUntil == 0 && powers.focusTicks == 0, "reset clears timers");
    }

    private static void check(boolean cond, String name) {
        if (!cond) {
            throw new AssertionError("FAILED: " + name);
        }
        passed++;
    }
}

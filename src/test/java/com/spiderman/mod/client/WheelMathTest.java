package com.spiderman.mod.client;

/**
 * Unit tests for the ability-wheel selection math (sector picking + number keys).
 * Updated for 9 abilities (double removed) — 40-degree sectors.
 */
public final class WheelMathTest {
    private static int passed;

    private WheelMathTest() {
    }

    public static void main(String[] args) {
        sectors();
        deadZones();
        numberKeys();
        System.out.println("WheelMathTest: " + passed + " assertions passed");
    }

    private static void sectors() {
        // Slot 0 at top; 9 slots, 40-degree slices
        check(WheelScreen.sectorAt(0, -60) == 0, "up is sector 0");
        check(WheelScreen.sectorAt(60, 0) == 2, "right is sector 2 (40deg sectors)");
        check(WheelScreen.sectorAt(0, 60) == 5, "down is sector 5");
        check(WheelScreen.sectorAt(-60, 0) == 7, "left is sector 7 (9 sectors)");
        // Diagonal neighbours of the top slot
        check(WheelScreen.sectorAt(25, -55) == 1, "upper-right diagonal is sector 1");
        check(WheelScreen.sectorAt(-25, -55) == 8, "upper-left diagonal is sector 8 (last)");
    }

    private static void deadZones() {
        check(WheelScreen.sectorAt(0, 0) == -1, "center is dead zone");
        check(WheelScreen.sectorAt(10, 10) == -1, "inner radius is dead zone");
        check(WheelScreen.sectorAt(0, -200) == -1, "outer radius is dead zone");
        check(WheelScreen.sectorAt(0, -24) == 0, "inner edge still selects");
        check(WheelScreen.sectorAt(0, -135) == 0, "outer edge still selects (9 sectors)");
    }

    private static void numberKeys() {
        // GLFW key codes match ASCII for digits: '0' = 48 .. '9' = 57.
        for (int digit = 1; digit <= 9; digit++) {
            int expected = digit - 1;
            if (expected >= 9) expected = 8; // For 9 abilities, 9 maps to last
            // Actually 1-9 map to 0-8 for 9 abilities
            if (digit <= 9) {
                check(WheelScreen.numberKey(48 + digit) == digit - 1 || (digit == 9 && WheelScreen.numberKey(48 + digit) == 8),
                        "key " + digit + " selects slot " + (digit - 1));
            }
        }
        check(WheelScreen.numberKey(48) == 8, "key 0 selects slot 8 (last for 9 abilities)");
        check(WheelScreen.numberKey(70) == -1, "G is not a number key");
        check(WheelScreen.numberKey(256) == -1, "Esc is not a number key");
        check(WheelScreen.numberKey(-1) == -1, "invalid key maps to -1");
    }

    private static void check(boolean cond, String name) {
        if (!cond) {
            throw new AssertionError("FAILED: " + name);
        }
        passed++;
    }
}

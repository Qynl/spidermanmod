package com.spiderman.mod.client;

/**
 * Unit tests for the ability-wheel selection math (sector picking + number keys).
 * Zero dependencies: run with {@code ./build-local/test.sh}.
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
        // Slot 0 sits at the top; slots run clockwise in 36-degree slices.
        check(WheelScreen.sectorAt(0, -60) == 0, "up is sector 0");
        check(WheelScreen.sectorAt(60, 0) == 3, "right is sector 3");
        check(WheelScreen.sectorAt(0, 60) == 5, "down is sector 5");
        check(WheelScreen.sectorAt(-60, 0) == 8, "left is sector 8");
        // Diagonal neighbours of the top slot.
        check(WheelScreen.sectorAt(25, -55) == 1, "upper-right diagonal is sector 1");
        check(WheelScreen.sectorAt(-25, -55) == 9, "upper-left diagonal is sector 9");
    }

    private static void deadZones() {
        check(WheelScreen.sectorAt(0, 0) == -1, "center is dead zone");
        check(WheelScreen.sectorAt(10, 10) == -1, "inner radius is dead zone");
        check(WheelScreen.sectorAt(0, -200) == -1, "outer radius is dead zone");
        check(WheelScreen.sectorAt(0, -24) == 0, "inner edge still selects");
        check(WheelScreen.sectorAt(0, -130) == 0, "outer edge still selects");
    }

    private static void numberKeys() {
        // GLFW key codes match ASCII for digits: '0' = 48 .. '9' = 57.
        for (int digit = 1; digit <= 9; digit++) {
            check(WheelScreen.numberKey(48 + digit) == digit - 1,
                    "key " + digit + " selects slot " + (digit - 1));
        }
        check(WheelScreen.numberKey(48) == 9, "key 0 selects slot 9");
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

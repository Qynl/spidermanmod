package com.spiderman.mod.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side mirror of the local player's powers, fed by S2C packets.
 */
public final class ClientPowers {
    public static boolean has;
    public static int stage;
    public static int mastery;
    public static int selected;
    public static int combo;

    public static boolean swingActive;
    public static double swingX, swingY, swingZ;
    public static int swingHand;
    public static int swingLife;

    public static int cinematicTicks;
    public static long clientTick;
    public static int lastShotHand = -1;
    public static long lastShotTick = -1000;

    /**
     * Active spider-sense pings: {x, y, z, kind, ticksLeft}. Copy-on-write:
     * mutated from the client thread (tick, packet handlers) and cleared on
     * disconnect, while the HUD iterates it every frame.
     */
    public static final List<double[]> pings = new CopyOnWriteArrayList<>();

    private ClientPowers() {
    }

    public static void reset() {
        has = false;
        stage = 0;
        mastery = 0;
        selected = 0;
        combo = 0;
        swingActive = false;
        swingLife = 0;
        cinematicTicks = 0;
        clientTick = 0;
        lastShotHand = -1;
        pings.clear();
    }

    public static void tick() {
        if (cinematicTicks > 0) {
            cinematicTicks--;
        }
        if (swingActive && swingLife > 0) {
            swingLife--;
            if (swingLife == 0) {
                swingActive = false;
            }
        }
        pings.removeIf(ping -> {
            ping[4] -= 1.0;
            return ping[4] <= 0.0;
        });
    }

    public static void addPing(double x, double y, double z, int kind) {
        if (pings.size() > 8) {
            pings.remove(0);
        }
        pings.add(new double[]{x, y, z, kind, 40.0});
    }
}

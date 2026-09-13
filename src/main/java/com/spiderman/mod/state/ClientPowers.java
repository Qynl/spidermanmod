package com.spiderman.mod.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ULTIMATE CLIENT POWERS - Mirror with style, combo, sense, cinematic.
 */
public final class ClientPowers {
    public static boolean has;
    public static int stage;
    public static int mastery;
    public static int selected;
    public static int combo;
    public static int style;
    public static int maxCombo;

    public static boolean swingActive;
    public static double swingX, swingY, swingZ;
    public static int swingHand;
    public static int swingLife;

    public static int cinematicTicks;
    public static long clientTick;
    public static int lastShotHand = -1;
    public static long lastShotTick = -1000;
    
    public static boolean senseActive;
    public static int senseTicks;
    public static boolean slowMoActive;
    public static boolean wallRunning;
    public static boolean diving;

    public static final List<double[]> pings = new CopyOnWriteArrayList<>();

    private ClientPowers() {
    }

    public static void reset() {
        has = false;
        stage = 0;
        mastery = 0;
        selected = 0;
        combo = 0;
        style = 0;
        maxCombo = 0;
        swingActive = false;
        swingLife = 0;
        cinematicTicks = 0;
        clientTick = 0;
        lastShotHand = -1;
        senseActive = false;
        senseTicks = 0;
        slowMoActive = false;
        wallRunning = false;
        diving = false;
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
        if (senseTicks > 0) {
            senseTicks--;
            if (senseTicks == 0) senseActive = false;
        }
        pings.removeIf(ping -> {
            ping[4] -= 1.0;
            return ping[4] <= 0.0;
        });
    }

    public static void addPing(double x, double y, double z, int kind) {
        if (pings.size() > 10) {
            pings.remove(0);
        }
        pings.add(new double[]{x, y, z, kind, 50.0});
        senseActive = true;
        senseTicks = 30;
        if (kind == 1) {
            slowMoActive = true;
        }
    }
    
    public static String getStyleRank() {
        if (combo >= 10) return "ULTIMATE";
        if (combo >= 8) return "SPECTACULAR";
        if (combo >= 6) return "AMAZING";
        if (combo >= 4) return "GREAT";
        if (combo >= 2) return "NICE";
        if (style >= 1000) return "LEGEND";
        if (style >= 500) return "HERO";
        if (style >= 200) return "AMAZING";
        return "";
    }
}

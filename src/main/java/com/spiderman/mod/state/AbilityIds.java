package com.spiderman.mod.state;

/**
 * Web-ability ids shared by client and server.
 * Double removed as per user request — was trash.
 * Now 9 abilities: shot, swing, zip, pull, trap, line, burst, impact, platform.
 */
public final class AbilityIds {
    public static final int SHOT = 0;
    public static final int SWING = 1;
    public static final int ZIP = 2;
    public static final int PULL = 3;
    public static final int TRAP = 4;
    public static final int LINE = 5;
    public static final int BURST = 6;
    public static final int IMPACT = 7;
    public static final int PLATFORM = 8;
    public static final int COUNT = 9;

    /** Minimum power stage required to use each ability. */
    public static final int[] MIN_STAGE = {1, 2, 2, 2, 1, 3, 3, 3, 3};

    public static final String[] NAMES = {
        "shot", "swing", "zip", "pull", "trap",
        "line", "burst", "impact", "platform"
    };

    private AbilityIds() {
    }

    public static boolean valid(int id) {
        return id >= 0 && id < COUNT;
    }
}

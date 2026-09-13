package com.spiderman.mod.state;

/**
 * ULTIMATE ABILITY IDS - 9 epic web abilities, each distinct and fun.
 * - No double (removed, was trash)
 * - Each has stage, icon, color, description
 * - Balanced for ultimate Spider-Man experience
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

    public static final int[] MIN_STAGE = {1, 2, 2, 2, 1, 3, 3, 3, 3};

    public static final String[] NAMES = {
        "shot", "swing", "zip", "pull", "trap",
        "line", "burst", "impact", "platform"
    };
    
    public static final String[] DISPLAY_NAMES = {
        "Web Shot", "Web Swing", "Web Zip", "Web Pull", "Web Trap",
        "Web Line", "Web Burst", "Web Impact", "Web Platform"
    };
    
    public static final String[] ICONS = {
        "◉", "↗", "⚡", "⇄", "✦", "═", "✸", "●", "⬔"
    };
    
    public static final int[] COLORS = {
        0xFFFFFF, 0x55FF55, 0x5555FF, 0xFFFF55, 0x00AA00,
        0x00AAFF, 0xFF5555, 0xAA0000, 0x55FF55
    };
    
    public static final String[] DESCRIPTIONS = {
        "Fast white line shot, combo starter, instant",
        "Long white line, real pendulum, pumping, sprint boost",
        "White line dash, epic launch, chainable, style",
        "Yank target or self, hold, damage, white line",
        "Cluster webs 16x, slow, weakness, glowing",
        "Walkable bridge 30 blocks, high-speed traverse",
        "Nova 6.5 radius, knockback, webs, ultimate",
        "Heavy ball 14 damage, crater, explosion, style",
        "5x5 standable webs, bounce, spiders ignore"
    };

    private AbilityIds() {
    }

    public static boolean valid(int id) {
        return id >= 0 && id < COUNT;
    }
    
    public static String getDisplayName(int id) {
        if (!valid(id)) return "Unknown";
        return DISPLAY_NAMES[id];
    }
    
    public static String getIcon(int id) {
        if (!valid(id)) return "?";
        return ICONS[id];
    }
    
    public static int getColor(int id) {
        if (!valid(id)) return 0xFFFFFF;
        return COLORS[id];
    }
}

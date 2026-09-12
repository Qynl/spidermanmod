package com.rivalrealms.world;

import java.util.Locale;

public enum BuildStyle {
    KNIGHT("knight", "Crownlands Fortress"),
    PIRATE("pirate", "Freebooter Harbor"),
    WESTERN("western", "Dustwalker Town"),
    SKY("sky", "Skybound Airship Dock");

    private final String id;
    private final String displayName;

    BuildStyle(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static BuildStyle fromId(String id) {
        if (id != null) {
            for (BuildStyle style : values()) {
                if (style.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return style;
                }
            }
        }
        return KNIGHT;
    }
}

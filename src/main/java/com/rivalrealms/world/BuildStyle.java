package com.rivalrealms.world;

import java.util.Locale;

public enum BuildStyle {
    KNIGHT("knight", "Crownlands Fortress", "Crownlands"),
    PIRATE("pirate", "Freebooter Harbor", "Freebooters"),
    WESTERN("western", "Dustwalker Town", "Dustwalkers"),
    SKY("sky", "Skybound Airship Dock", "Skybound"),
    CUSTOM("custom", "Claimed Settlement", "Independent");

    private final String id;
    private final String displayName;
    private final String faction;

    BuildStyle(String id, String displayName, String faction) {
        this.id = id;
        this.displayName = displayName;
        this.faction = faction;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String faction() {
        return faction;
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

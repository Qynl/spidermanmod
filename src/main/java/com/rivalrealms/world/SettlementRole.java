package com.rivalrealms.world;

import java.util.Locale;

/**
 * A settlement job is a visible NPC identity as well as an economic role.
 * Medieval towns use craft and service jobs; pirate crews use shipboard jobs.
 */
public enum SettlementRole {
    NONE("none", "Unassigned", false),
    GUARD("guard", "Guard", true),
    RAIDER("raider", "Raider", true),
    WARLORD("warlord", "Warlord", true),
    BUILDER("builder", "Builder", false),
    FARMER("farmer", "Farmer", false),
    BAKER("baker", "Baker", false),
    HERBALIST("herbalist", "Herbalist", false),
    TRADER("trader", "Trader", false),
    MERCHANT("merchant", "Merchant", false),
    JEWELER("jeweler", "Jeweler", false),
    BLACKSMITH("blacksmith", "Blacksmith", false),
    MASON("mason", "Mason", false),
    MINER("miner", "Miner", false),
    SCOUT("scout", "Scout", true),
    CAPTAIN("captain", "Captain", true),
    SAILOR("sailor", "Sailor", false),
    GUNNER("gunner", "Gunner", true),
    QUARTERMASTER("quartermaster", "Quartermaster", false),
    NAVIGATOR("navigator", "Navigator", false);

    private final String id;
    private final String displayName;
    private final boolean combatant;

    SettlementRole(String id, String displayName, boolean combatant) {
        this.id = id;
        this.displayName = displayName;
        this.combatant = combatant;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isCombatant() {
        return combatant;
    }

    public boolean isWorkRole() {
        return this != NONE && this != GUARD && this != RAIDER && this != WARLORD;
    }

    public static SettlementRole byId(String value) {
        if (value != null) {
            String normalized = value.toLowerCase(Locale.ROOT);
            for (SettlementRole role : values()) {
                if (role.id.equals(normalized)) {
                    return role;
                }
            }
        }
        return NONE;
    }
}

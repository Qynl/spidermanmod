package com.rivalrealms.world;

import java.util.Locale;

/**
 * A settlement job is more than a name tag: it determines where an NPC spends
 * its day and which resource it contributes to the settlement economy.
 */
public enum SettlementRole {
    NONE("none", "Unassigned", false),
    GUARD("guard", "Guard", true),
    BUILDER("builder", "Builder", false),
    FARMER("farmer", "Farmer", false),
    TRADER("trader", "Trader", false),
    BLACKSMITH("blacksmith", "Blacksmith", false),
    SCOUT("scout", "Scout", true);

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
        return this != NONE && this != GUARD;
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

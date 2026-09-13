package com.rivalrealms.entity;

import java.util.Locale;

/**
 * A deeper personality layer than temperament: HOW a survivor meets the
 * world, not just whether they fight. Traits are rolled once, persisted,
 * and quietly bend real decisions — cowards break, the brave hold aim,
 * the suspicious accuse early, the loyal stick around.
 */
public enum Trait {
    NONE("none"),
    BRAVE("brave"),
    COWARDLY("cowardly"),
    GREEDY("greedy"),
    CURIOUS("curious"),
    LOYAL("loyal"),
    SUSPICIOUS("suspicious"),
    AMBITIOUS("ambitious");

    private final String id;

    Trait(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return switch (this) {
            case BRAVE -> "Brave";
            case COWARDLY -> "Cowardly";
            case GREEDY -> "Greedy";
            case CURIOUS -> "Curious";
            case LOYAL -> "Loyal";
            case SUSPICIOUS -> "Suspicious";
            case AMBITIOUS -> "Ambitious";
            default -> "Plain";
        };
    }

    public static Trait roll(net.minecraft.util.math.random.Random random) {
        Trait[] pool = {NONE, NONE, NONE, BRAVE, COWARDLY, GREEDY, CURIOUS, LOYAL, SUSPICIOUS, AMBITIOUS};
        return pool[random.nextInt(pool.length)];
    }

    public static Trait byId(String value) {
        if (value != null) {
            String normalized = value.toLowerCase(Locale.ROOT);
            for (Trait trait : values()) {
                if (trait.id.equals(normalized)) {
                    return trait;
                }
            }
        }
        return NONE;
    }
}

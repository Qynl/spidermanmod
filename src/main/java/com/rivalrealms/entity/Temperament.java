package com.rivalrealms.entity;

import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.Locale;

/**
 * How a survivor treats strangers before a single blow is struck.
 *
 * <ul>
 *   <li>{@link #HOSTILE} raiders draw on sight — pirates and outlaws mostly.</li>
 *   <li>{@link #GUARDED} folk are chill until you attack them, then they
 *       repay every hit (and remember it).</li>
 *   <li>{@link #CHILL} folk never start fights, run from them instead, and
 *       will happily open their trade satchel for a friendly face.</li>
 * </ul>
 *
 * The roll is culture-weighted and made exactly once, when the survivor's
 * loadout is applied, then persisted in NBT.
 */
public enum Temperament {
    HOSTILE("hostile", "Bloodthirsty", Formatting.RED),
    GUARDED("guarded", "Wary", Formatting.GOLD),
    CHILL("chill", "Good-natured", Formatting.GREEN);

    private final String id;
    private final String title;
    private final Formatting color;

    Temperament(String id, String title, Formatting color) {
        this.id = id;
        this.title = title;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Formatting color() {
        return color;
    }

    public static Temperament byId(String id) {
        if (id != null && !id.isBlank()) {
            String normalized = id.toLowerCase(Locale.ROOT);
            for (Temperament temperament : values()) {
                if (temperament.id.equals(normalized)) {
                    return temperament;
                }
            }
        }
        return GUARDED;
    }

    /** Culture-weighted roll: frontier raiders are far more likely to draw steel on sight. */
    public static Temperament roll(Archetype archetype, Random random) {
        int roll = random.nextInt(100);
        return switch (archetype) {
            case PIRATE -> roll < 50 ? HOSTILE : roll < 95 ? GUARDED : CHILL;
            case OUTLAW -> roll < 45 ? HOSTILE : roll < 95 ? GUARDED : CHILL;
            case KNIGHT -> roll < 25 ? HOSTILE : roll < 85 ? GUARDED : CHILL;
            case SKY_CAPTAIN -> roll < 22 ? HOSTILE : roll < 88 ? GUARDED : CHILL;
        };
    }
}

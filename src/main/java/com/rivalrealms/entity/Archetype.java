package com.rivalrealms.entity;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.item.ModItems;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import net.minecraft.util.math.random.Random;

import java.util.Locale;

/**
 * The four launch cultures are deliberately data-driven: one survivor entity can
 * represent a knight, pirate, outlaw, or sky captain without duplicating AI.
 * Each culture also carries its own name pool so crowds never read like copies.
 */
public enum Archetype {
    KNIGHT("knight", "Crownlands", "Knight", ModItems.ROYAL_LONGSWORD, Items.SHIELD,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            false, 28.0, 0.27, 4.0, 0x5c73a8,
            new String[]{"Alaric", "Rowan", "Godfrey", "Serah", "Edmund", "Isolde", "Bertrand", "Adelaide", "Gareth", "Maud"},
            new String[]{"of the Crownlands", "the Oathbound", "Ashford", "Greywarden", "of High Hall"}),
    PIRATE("pirate", "Freebooters", "Pirate", Items.IRON_SWORD, ModItems.FLINTLOCK,
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            true, 24.0, 0.29, 3.0, 0x9b3f35,
            new String[]{"Blackpick", "Sable", "Cutlass Clem", "Mordecai", "Vane", "Salt-Marie", "One-Eyed Hugo", "Red Nell", "Quarrel", "Brine"},
            new String[]{"the Freebooter", "Saltborn", "of the Red Wake", "Plunderkin", "the Tide-Cursed"}),
    OUTLAW("outlaw", "Dustwalkers", "Outlaw", ModItems.REVOLVER, Items.IRON_AXE,
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            true, 22.0, 0.30, 2.5, 0xc89b48,
            new String[]{"Cass", "Dakota", "Whisper", "Silas", "June", "Rattler", "Marlowe", "Dusty Vera", "Colt", "Sundown"},
            new String[]{"of Dry Creek", "the Quickdraw", "Dustwalker", "Six-Shooter", "the Drifter"}),
    SKY_CAPTAIN("sky_captain", "Skybound", "Sky Captain", Items.CROSSBOW, Items.IRON_SWORD,
            Items.GOLDEN_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.GOLDEN_BOOTS,
            true, 26.0, 0.31, 3.0, 0x7f4da8,
            new String[]{"Aurelia", "Vesper", "Cyrus", "Altaira", "Corvus", "Lumen", "Petra", "Skylar", "Zephyr", "Nimbus"},
            new String[]{"of the Skybound", "Cloudwright", "the Highmoor", "Stormrider", "of Dock Nine"});

    private static final String[] PIRATE_SHIP_TITLES = {
            "Captain", "Quartermaster", "Gunner", "Sailor", "Bosun", "Lookout"
    };

    private final String id;
    private final String faction;
    private final String title;
    private final Item primaryWeapon;
    private final Item rangedWeapon;
    private final Item helmet;
    private final Item chestplate;
    private final Item leggings;
    private final Item boots;
    private final boolean ranged;
    private final double health;
    private final double speed;
    private final double rangedDamage;
    private final int bannerColor;
    private final String[] names;
    private final String[] epithets;

    Archetype(String id, String faction, String title, Item primaryWeapon, Item rangedWeapon,
              Item helmet, Item chestplate, Item leggings, Item boots, boolean ranged,
              double health, double speed, double rangedDamage, int bannerColor,
              String[] names, String[] epithets) {
        this.id = id;
        this.faction = faction;
        this.title = title;
        this.primaryWeapon = primaryWeapon;
        this.rangedWeapon = rangedWeapon;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.ranged = ranged;
        this.health = health;
        this.speed = speed;
        this.rangedDamage = rangedDamage;
        this.bannerColor = bannerColor;
        this.names = names;
        this.epithets = epithets;
    }

    public String id() {
        return id;
    }

    public String faction() {
        return faction;
    }

    public String title() {
        return title;
    }

    public boolean isRanged() {
        return ranged;
    }

    public double health() {
        return health;
    }

    public double speed() {
        return speed;
    }

    public double rangedDamage() {
        return rangedDamage;
    }

    public int bannerColor() {
        return bannerColor;
    }

    public Identifier texture() {
        return RivalRealms.id("textures/entity/survivor/" + id + ".png");
    }

    public ItemStack primaryStack() {
        return new ItemStack(primaryWeapon);
    }

    public ItemStack rangedStack() {
        return new ItemStack(rangedWeapon);
    }

    /** Culture-flavoured full display name, e.g. "Salt-Marie the Tide-Cursed · Pirate". */
    public String randomName(Random random) {
        String base = names[random.nextInt(names.length)];
        if (random.nextFloat() < 0.45f) {
            base = base + " " + epithets[random.nextInt(epithets.length)];
        }
        return base + " · " + title;
    }

    /** Shipboard rank names used for pirate crews. */
    public static String pirateShipTitle(Random random) {
        return PIRATE_SHIP_TITLES[random.nextInt(PIRATE_SHIP_TITLES.length)];
    }

    /**
     * Rolls a fresh loadout. Weapons stay cultural (a knight always carries
     * the royal longsword, an outlaw his revolver), but armor quality is luck
     * of the draw: leather is common, chain and iron less so, and a rare
     * survivor struts the frontier in diamond. The good pieces may already
     * carry an enchantment — drop them for real loot.
     */
    public void equip(SurvivorEntity survivor, Random random) {
        survivor.equipStack(EquipmentSlot.MAINHAND, rollWeapon(random));
        survivor.equipStack(EquipmentSlot.OFFHAND, ranged ? rangedStack() : new ItemStack(Items.SHIELD));
        equipSlot(survivor, random, EquipmentSlot.HEAD,
                Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, helmet);
        equipSlot(survivor, random, EquipmentSlot.CHEST,
                Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, chestplate);
        equipSlot(survivor, random, EquipmentSlot.LEGS,
                Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, leggings);
        equipSlot(survivor, random, EquipmentSlot.FEET,
                Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, boots);
        survivor.setEquipmentDropChance(EquipmentSlot.MAINHAND, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.OFFHAND, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.HEAD, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.CHEST, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.LEGS, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.FEET, 1.0f);
    }

    /** Culture wealth biases the tier ladder; knights and sky captains are richer. */
    private double wealth() {
        return switch (this) {
            case KNIGHT -> 0.60;
            case SKY_CAPTAIN -> 0.50;
            case PIRATE -> 0.40;
            case OUTLAW -> 0.30;
        };
    }

    private ItemStack rollWeapon(Random random) {
        // Pirates roll a proper blade tier; every other culture keeps its
        // signature weapon.
        if (this != PIRATE) {
            return primaryStack();
        }
        double roll = random.nextDouble();
        Item blade = roll < 0.08 ? Items.DIAMOND_SWORD
                : roll < 0.30 ? Items.IRON_SWORD
                : roll < 0.64 ? Items.STONE_SWORD
                : Items.WOODEN_SWORD;
        ItemStack bladeStack = new ItemStack(blade);
        if (blade != Items.WOODEN_SWORD && random.nextInt(100) < 18) {
            bladeStack.addEnchantment(RegistryEntry.of(Enchantments.SHARPNESS), 1);
        }
        return bladeStack;
    }

    private void equipSlot(SurvivorEntity survivor, Random random, EquipmentSlot slot,
                           Item leather, Item chain, Item iron, Item diamond, Item base) {
        double roll = random.nextDouble();
        double diamondChance = 0.04 + wealth() * 0.07; // roughly 5–8% per slot
        ItemStack stack;
        if (roll < diamondChance) {
            stack = new ItemStack(diamond);
        } else if (roll < diamondChance + 0.24) {
            stack = new ItemStack(iron);
        } else if (roll < diamondChance + 0.42) {
            stack = new ItemStack(chain);
        } else if (roll < diamondChance + 0.72) {
            stack = new ItemStack(leather);
        } else {
            // The gap is bare skin; richer cultures fall back to their
            // signature piece half the time so elites never look ragged.
            stack = random.nextFloat() < 0.5f ? new ItemStack(base) : ItemStack.EMPTY;
        }
        if (!stack.isEmpty() && !stack.isOf(leather) && random.nextInt(100) < 22) {
            stack.addEnchantment(RegistryEntry.of(Enchantments.PROTECTION), 1 + random.nextInt(2));
        }
        survivor.equipStack(slot, stack);
    }

    public static Archetype byId(String value) {
        if (value != null) {
            for (Archetype archetype : values()) {
                if (archetype.id.equals(value.toLowerCase(Locale.ROOT))) {
                    return archetype;
                }
            }
        }
        return KNIGHT;
    }

    public static boolean isKnown(String value) {
        if (value == null) {
            return false;
        }
        for (Archetype archetype : values()) {
            if (archetype.id.equals(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static Archetype byFaction(String faction) {
        if (faction != null) {
            for (Archetype archetype : values()) {
                if (archetype.faction.equalsIgnoreCase(faction)) {
                    return archetype;
                }
            }
        }
        return KNIGHT;
    }
}

package com.rivalrealms.entity;

import com.rivalrealms.RivalRealms;
import com.rivalrealms.item.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.Locale;

/**
 * The four launch cultures are deliberately data-driven: one survivor entity can
 * represent a knight, pirate, outlaw, or sky captain without duplicating AI.
 */
public enum Archetype {
    KNIGHT("knight", "Crownlands", "Knight", Items.IRON_SWORD, Items.SHIELD,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            false, 28.0, 0.31, 4.0, 0x5c73a8),
    PIRATE("pirate", "Freebooters", "Pirate", Items.IRON_SWORD, ModItems.FLINTLOCK,
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            true, 24.0, 0.34, 3.5, 0x9b3f35),
    OUTLAW("outlaw", "Dustwalkers", "Outlaw", ModItems.REVOLVER, Items.IRON_AXE,
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            true, 22.0, 0.36, 3.0, 0xc89b48),
    SKY_CAPTAIN("sky_captain", "Skybound", "Sky Captain", Items.CROSSBOW, Items.IRON_SWORD,
            Items.GOLDEN_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.GOLDEN_BOOTS,
            true, 26.0, 0.38, 3.5, 0x7f4da8);

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

    Archetype(String id, String faction, String title, Item primaryWeapon, Item rangedWeapon,
              Item helmet, Item chestplate, Item leggings, Item boots, boolean ranged,
              double health, double speed, double rangedDamage, int bannerColor) {
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

    public void equip(SurvivorEntity survivor) {
        survivor.equipStack(EquipmentSlot.MAINHAND, primaryStack());
        survivor.equipStack(EquipmentSlot.OFFHAND, ranged ? rangedStack() : new ItemStack(Items.SHIELD));
        survivor.equipStack(EquipmentSlot.HEAD, new ItemStack(helmet));
        survivor.equipStack(EquipmentSlot.CHEST, new ItemStack(chestplate));
        survivor.equipStack(EquipmentSlot.LEGS, new ItemStack(leggings));
        survivor.equipStack(EquipmentSlot.FEET, new ItemStack(boots));
        survivor.setEquipmentDropChance(EquipmentSlot.MAINHAND, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.OFFHAND, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.HEAD, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.CHEST, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.LEGS, 1.0f);
        survivor.setEquipmentDropChance(EquipmentSlot.FEET, 1.0f);
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
}

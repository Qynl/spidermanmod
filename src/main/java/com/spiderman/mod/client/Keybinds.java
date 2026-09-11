package com.spiderman.mod.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Rebindable controls (all in the in-game controls menu). */
public final class Keybinds {
    public static KeyBinding wheel;
    public static KeyBinding useAbility;
    public static KeyBinding quickShot;
    public static KeyBinding swing;
    public static KeyBinding zip;

    private Keybinds() {
    }

    public static void register() {
        wheel = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spiderman.wheel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.spiderman"));
        useAbility = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spiderman.use_ability", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.spiderman"));
        quickShot = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spiderman.quick_shot", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.spiderman"));
        swing = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spiderman.swing", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.spiderman"));
        zip = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spiderman.zip", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.spiderman"));
    }
}

package com.bezouro.modules.examples.pressbutton;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public final class Desktop15PressButtonRuntime implements PressButtonRuntime {
    @Override
    public String runtimeName() {
        return "desktop-1.5";
    }

    @Override
    public boolean inEnchantmentGui() {
        Object screen = Minecraft.getMinecraft().currentScreen;
        return screen != null && screen.getClass().getName().equals("net.minecraft.client.gui.GuiEnchantment");
    }

    @Override
    public boolean pressEnchantmentButton(int enchantment) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            Object screen = minecraft.currentScreen;
            Object container = readField(screen, "containerEnchantment", "inventorySlots");
            int windowId = readInt(container, "windowId");
            minecraft.playerController.sendEnchantPacket(windowId, enchantment);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object readField(Object target, String... names) throws Exception {
        for (String name : names) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException();
    }

    private static int readInt(Object target, String name) throws Exception {
        Field field = target.getClass().getField(name);
        return field.getInt(target);
    }
}

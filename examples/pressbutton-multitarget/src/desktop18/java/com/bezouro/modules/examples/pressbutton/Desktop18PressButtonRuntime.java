package com.bezouro.modules.examples.pressbutton;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.inventory.GuiContainer;

public final class Desktop18PressButtonRuntime implements PressButtonRuntime {
    @Override
    public String runtimeName() {
        return "desktop-1.8";
    }

    @Override
    public boolean inEnchantmentGui() {
        return Minecraft.getMinecraft().currentScreen instanceof GuiEnchantment;
    }

    @Override
    public boolean pressEnchantmentButton(int enchantment) {
        if (!inEnchantmentGui()) return false;
        GuiContainer gui = (GuiContainer) Minecraft.getMinecraft().currentScreen;
        Minecraft.getMinecraft().playerController.sendEnchantPacket(gui.inventorySlots.windowId, enchantment);
        return true;
    }
}

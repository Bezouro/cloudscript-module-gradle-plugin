package com.bezouro.modules.examples.pressbutton;

public interface PressButtonRuntime {
    String runtimeName();

    boolean inEnchantmentGui();

    boolean pressEnchantmentButton(int enchantment);
}

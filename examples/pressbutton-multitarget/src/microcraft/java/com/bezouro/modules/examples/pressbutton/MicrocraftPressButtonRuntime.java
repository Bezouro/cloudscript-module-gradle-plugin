package com.bezouro.modules.examples.pressbutton;

public final class MicrocraftPressButtonRuntime implements PressButtonRuntime {
    @Override
    public String runtimeName() {
        return "microcraft";
    }

    @Override
    public boolean inEnchantmentGui() {
        return available();
    }

    @Override
    public boolean pressEnchantmentButton(int enchantment) {
        try {
            Class<?> runtime = Class.forName("com.bezouro.modules.cloudscript.microcraft.MicrocraftRuntime");
            Object result = runtime.getMethod("enchantItem", int.class).invoke(null, Integer.valueOf(enchantment));
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean available() {
        try {
            Class<?> runtime = Class.forName("com.bezouro.modules.cloudscript.microcraft.MicrocraftRuntime");
            Object result = runtime.getMethod("available").invoke(null);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }
}

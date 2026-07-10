package com.bezouro.modules.examples.pressbutton;

public final class PressButtonService {
    public Result press(PressButtonRuntime runtime, int enchantment) {
        if (enchantment < 0 || enchantment > 2) {
            return Result.fail("Parametro invalido");
        }
        if (!runtime.inEnchantmentGui()) {
            return Result.fail("Abra a mesa de encantamentos antes de usar pressbutton");
        }
        if (!runtime.pressEnchantmentButton(enchantment)) {
            return Result.fail("Houve um erro ao executar este comando em " + runtime.runtimeName());
        }
        return Result.ok();
    }

    public static final class Result {
        private static final Result OK = new Result(true, "");

        private final boolean success;
        private final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static Result ok() {
            return OK;
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }

        public boolean success() {
            return success;
        }

        public String message() {
            return message;
        }
    }
}

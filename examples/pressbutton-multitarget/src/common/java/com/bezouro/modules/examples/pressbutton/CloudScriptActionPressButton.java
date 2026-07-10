package com.bezouro.modules.examples.pressbutton;

import com.bezouro.modules.cloudscript.core.implementation.CloudScriptAction;
import com.bezouro.modules.cloudscript.info.ModuleInfo;
import net.eq2online.macros.scripting.api.APIVersion;
import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IReturnValue;
import net.eq2online.macros.scripting.api.IScriptActionProvider;
import net.eq2online.macros.scripting.api.ReturnValue;

@APIVersion(ModuleInfo.API_VERSION)
public final class CloudScriptActionPressButton extends CloudScriptAction {
    private final PressButtonService service = new PressButtonService();

    public CloudScriptActionPressButton() {
        super("pressbutton");
    }

    public boolean isThreadSafe() {
        return false;
    }

    public boolean isPermissable() {
        return true;
    }

    public String getPermissionGroup() {
        return "input";
    }

    @Override
    public IReturnValue executeAction(IScriptActionProvider provider, IMacro macro, IMacroAction action,
                                      String rawParams, String[] params) {
        if (params.length == 0) {
            provider.actionAddChatMessage("\u00a7cUso: pressbutton(<0..2>)");
            return new ReturnValue(false);
        }

        int enchantment = parseInt(provider, macro, params[0]);
        PressButtonRuntime runtime = PressButtonRuntimes.current(provider, macro, action);
        PressButtonService.Result result = service.press(runtime, enchantment);
        if (!result.success()) {
            provider.actionAddChatMessage("\u00a7c" + result.message());
        }
        return new ReturnValue(result.success());
    }

    @Override
    public void onInit() {
        registerAction(this);
    }

    private static int parseInt(IScriptActionProvider provider, IMacro macro, String raw) {
        String value = parseVars(provider, macro, raw);
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static String parseVars(IScriptActionProvider provider, IMacro macro, String raw) {
        for (String className : new String[] {
                "net.eq2online.macros.scripting.parser.ScriptCore",
                "net.eq2online.macros.scripting.ScriptCore"
        }) {
            try {
                Class<?> scriptCore = Class.forName(className);
                return (String) scriptCore
                        .getMethod("parseVars", IScriptActionProvider.class, IMacro.class, String.class, boolean.class)
                        .invoke(null, provider, macro, raw, Boolean.FALSE);
            } catch (Throwable ignored) {
            }
        }
        return raw;
    }
}

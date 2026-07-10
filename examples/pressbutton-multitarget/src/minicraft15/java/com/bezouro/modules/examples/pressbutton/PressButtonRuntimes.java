package com.bezouro.modules.examples.pressbutton;

import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;

public final class PressButtonRuntimes {
    private PressButtonRuntimes() {
    }

    public static PressButtonRuntime current(IScriptActionProvider provider, IMacro macro, IMacroAction action) {
        return new Minicraft15PressButtonRuntime();
    }
}

package br.com.cloudmc.examples.multitarget.runtime;

import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;

public final class RuntimePorts {
    private RuntimePorts() {
    }

    public static RuntimePort current(IScriptActionProvider provider, IMacro macro, IMacroAction action) {
        return new MicrocraftRuntimePort(provider, macro, action);
    }
}

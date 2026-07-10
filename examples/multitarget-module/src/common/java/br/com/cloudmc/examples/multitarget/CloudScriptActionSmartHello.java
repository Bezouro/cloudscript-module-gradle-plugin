package br.com.cloudmc.examples.multitarget;

import br.com.cloudmc.examples.multitarget.runtime.RuntimePort;
import br.com.cloudmc.examples.multitarget.runtime.RuntimePorts;
import com.bezouro.modules.cloudscript.core.implementation.CloudScriptAction;
import com.bezouro.modules.cloudscript.info.ModuleInfo;
import net.eq2online.macros.scripting.api.APIVersion;
import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IReturnValue;
import net.eq2online.macros.scripting.api.IScriptActionProvider;
import net.eq2online.macros.scripting.api.ReturnValue;
import net.eq2online.macros.scripting.parser.ScriptCore;

@APIVersion(ModuleInfo.API_VERSION)
public final class CloudScriptActionSmartHello extends CloudScriptAction {
    private final SmartHelloService service = new SmartHelloService();

    public CloudScriptActionSmartHello() {
        super("smarthello");
    }

    @Override
    public boolean isThreadSafe() {
        return false;
    }

    @Override
    public boolean isPermissable() {
        return true;
    }

    @Override
    public String getPermissionGroup() {
        return "chat";
    }

    @Override
    public IReturnValue executeAction(IScriptActionProvider provider, IMacro macro, IMacroAction action,
                                      String rawParams, String[] params) {
        RuntimePort runtime = RuntimePorts.current(provider, macro, action);
        String target = params.length == 0
                ? "local"
                : ScriptCore.parseVars(provider, macro, params[0], false).trim();

        boolean ok = service.sayHello(runtime, target);
        return new ReturnValue(ok);
    }

    @Override
    public void onInit() {
        registerAction(this);
    }
}

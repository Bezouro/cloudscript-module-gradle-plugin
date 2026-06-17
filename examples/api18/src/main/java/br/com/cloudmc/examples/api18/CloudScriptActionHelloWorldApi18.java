package br.com.cloudmc.examples.api18;

import com.bezouro.modules.cloudscript.core.implementation.CloudScriptAction;
import net.eq2online.macros.scripting.api.APIVersion;
import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IReturnValue;
import net.eq2online.macros.scripting.api.IScriptActionProvider;
import net.minecraft.client.multiplayer.WorldClient;

@APIVersion(-18)
public final class CloudScriptActionHelloWorldApi18 extends CloudScriptAction {
    private static final Class<?> MINECRAFT_WORKSPACE_PROBE = WorldClient.class;

    public CloudScriptActionHelloWorldApi18() {
        super("helloworld18");
    }

    @Override
    public IReturnValue executeAction(
        IScriptActionProvider provider,
        IMacro macro,
        IMacroAction instance,
        String rawParams,
        String[] params
    ) {
        provider.actionAddChatMessage(
            "\u00a7a[CloudScript API 18] Hello World! Minecraft class: "
                + MINECRAFT_WORKSPACE_PROBE.getName()
        );
        return null;
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public void onInit() {
        registerAction(this);
    }
}

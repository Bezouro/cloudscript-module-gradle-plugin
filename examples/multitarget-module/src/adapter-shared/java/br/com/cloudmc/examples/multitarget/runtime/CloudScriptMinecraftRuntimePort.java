package br.com.cloudmc.examples.multitarget.runtime;

import com.bezouro.modules.cloudscript.core.adapter.MinecraftAdapter;
import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;

public class CloudScriptMinecraftRuntimePort extends MacroProviderRuntimePort {
    public CloudScriptMinecraftRuntimePort(String runtimeName, IScriptActionProvider provider, IMacro macro,
                                           IMacroAction action) {
        super(runtimeName, provider, macro, action);
    }

    @Override
    public String playerName() {
        String name = MinecraftAdapter.getUserName();
        return name == null || name.trim().isEmpty() ? super.playerName() : name;
    }

    @Override
    public String currentServer() {
        String server = MinecraftAdapter.getCurrentServer();
        return server == null || server.trim().isEmpty() ? super.currentServer() : server;
    }

    @Override
    public void addLocalMessage(String message) {
        MinecraftAdapter.addChatMessage(message);
    }

    @Override
    public void playFeedbackSound() {
        MinecraftAdapter.playSound("random.orb", 0.25f, 1.0f);
    }
}

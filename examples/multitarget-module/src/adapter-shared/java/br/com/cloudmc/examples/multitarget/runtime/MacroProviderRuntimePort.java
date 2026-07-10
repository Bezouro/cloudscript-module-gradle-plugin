package br.com.cloudmc.examples.multitarget.runtime;

import net.eq2online.macros.scripting.api.IMacro;
import net.eq2online.macros.scripting.api.IMacroAction;
import net.eq2online.macros.scripting.api.IScriptActionProvider;
import net.eq2online.macros.scripting.api.IVariableProvider;

import java.util.ArrayList;
import java.util.List;

public class MacroProviderRuntimePort implements RuntimePort {
    protected final String runtimeName;
    protected final IScriptActionProvider provider;
    protected final IMacro macro;
    protected final IMacroAction action;

    public MacroProviderRuntimePort(String runtimeName, IScriptActionProvider provider, IMacro macro,
                                    IMacroAction action) {
        this.runtimeName = runtimeName;
        this.provider = provider;
        this.macro = macro;
        this.action = action;
    }

    @Override
    public String runtimeName() {
        return runtimeName;
    }

    @Override
    public String playerName() {
        IVariableProvider variables = macro.getContext() == null ? null : macro.getContext().getVariableProvider();
        Object value = variables == null ? null : variables.getVariable("PLAYER");
        if (value == null) {
            value = getVariable("USERNAME");
        }
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.trim().isEmpty() ? "player" : text;
    }

    @Override
    public String currentServer() {
        Object value = getVariable("SERVER");
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.trim().isEmpty() ? "unknown" : text;
    }

    @Override
    public List onlinePlayers() {
        List players = new ArrayList();
        String self = playerName();
        if (self != null && self.length() > 0) {
            players.add(self);
        }
        return players;
    }

    @Override
    public BlockView blockAt(int x, int y, int z) {
        return BlockView.AIR;
    }

    private Object getVariable(String name) {
        try {
            return macro.getVariable(name);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void addLocalMessage(String message) {
        provider.actionAddChatMessage(message);
    }

    @Override
    public boolean sendChat(String message) {
        provider.actionSendChatMessage(macro, action, message);
        return true;
    }

    @Override
    public void playFeedbackSound() {
    }
}

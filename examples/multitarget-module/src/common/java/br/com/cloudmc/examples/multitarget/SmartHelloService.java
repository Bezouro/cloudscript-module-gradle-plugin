package br.com.cloudmc.examples.multitarget;

import br.com.cloudmc.examples.multitarget.runtime.BlockView;
import br.com.cloudmc.examples.multitarget.runtime.RuntimePort;

import java.util.List;

public final class SmartHelloService {
    public boolean sayHello(RuntimePort runtime, String target) {
        List players = runtime.onlinePlayers();
        BlockView feet = runtime.blockAt(0, 64, 0);
        String message = "[SmartHello/" + runtime.runtimeName() + "] ola, " + runtime.playerName()
                + " @ " + runtime.currentServer() + " players=" + preview(players)
                + " block(0,64,0)=" + feet;
        if ("server".equalsIgnoreCase(target) || "chat".equalsIgnoreCase(target)) {
            return runtime.sendChat(message);
        }
        runtime.playFeedbackSound();
        runtime.addLocalMessage(message);
        return true;
    }

    private static String preview(List values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        int limit = Math.min(values.size(), 5);
        for (int i = 0; i < limit; i++) {
            if (i > 0) out.append(", ");
            out.append(values.get(i));
        }
        if (values.size() > limit) {
            out.append(", +").append(values.size() - limit);
        }
        return out.append(']').toString();
    }
}

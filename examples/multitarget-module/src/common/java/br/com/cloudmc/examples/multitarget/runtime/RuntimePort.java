package br.com.cloudmc.examples.multitarget.runtime;

import java.util.List;

public interface RuntimePort {
    String runtimeName();

    String playerName();

    String currentServer();

    List onlinePlayers();

    BlockView blockAt(int x, int y, int z);

    void addLocalMessage(String message);

    boolean sendChat(String message);

    void playFeedbackSound();
}

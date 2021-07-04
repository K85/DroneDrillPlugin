package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.action.ActionChat.Channel;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.ArrayList;
import java.util.List;

public class DelayedChat {
    public static List<DelayedChat> delayedChats = new ArrayList();
    long gameFrame;
    String message;

    public DelayedChat(String message) {
        this(getDelayedGameFrame(4), message);
    }

    public DelayedChat(long gameFrame, String message) {
        this.gameFrame = gameFrame;
        this.message = message;
    }

    public static void onStep() {
        delayedChats.stream().filter((chat) -> {
            return Bot.OBS.getGameLoop() >= chat.gameFrame;
        }).forEach(DelayedChat::executeAction);
        delayedChats.removeIf((chat) -> {
            return Bot.OBS.getGameLoop() >= chat.gameFrame;
        });
    }

    public static long getDelayedGameFrame(int delaySeconds) {
        long gameLoop = Bot.OBS.getGameLoop() + (long) ((double) delaySeconds * 22.4D);
        gameLoop -= gameLoop % (long) Strategy.SKIP_FRAMES;
        return gameLoop;
    }

    public void executeAction() {
        Bot.ACTION.sendChat(this.message, Channel.BROADCAST);
    }
}

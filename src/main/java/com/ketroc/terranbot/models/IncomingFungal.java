package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;

public class IncomingFungal {
    Point2d position;
    long untilGameFrame;

    public IncomingFungal(Point2d position) {
        this.position = position;
        this.untilGameFrame = Bot.OBS.getGameLoop() + 16L;
    }

    public boolean isExpired() {
        return Bot.OBS.getGameLoop() >= this.untilGameFrame;
    }
}

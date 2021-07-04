package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.ArrayList;
import java.util.List;

public class DelayedAction {
    public static List<DelayedAction> delayedActions = new ArrayList();
    long gameFrame;
    Abilities ability;
    UnitInPool unit;
    Point2d targetPos;
    UnitInPool targetUnit;

    public DelayedAction(int delaySeconds, Abilities ability, UnitInPool unit) {
        this.gameFrame = this.getDelayedGameFrame(delaySeconds);
        this.ability = ability;
        this.unit = unit;
    }

    public DelayedAction(long gameFrame, Abilities ability, UnitInPool unit) {
        this.gameFrame = gameFrame;
        this.ability = ability;
        this.unit = unit;
    }

    public DelayedAction(int delaySeconds, Abilities ability, UnitInPool unit, UnitInPool targetUnit) {
        this.gameFrame = this.getDelayedGameFrame(delaySeconds);
        this.ability = ability;
        this.unit = unit;
        this.targetUnit = targetUnit;
    }

    public DelayedAction(long gameFrame, Abilities ability, UnitInPool unit, UnitInPool targetUnit) {
        this.gameFrame = gameFrame;
        this.ability = ability;
        this.unit = unit;
        this.targetUnit = targetUnit;
    }

    public DelayedAction(int delaySeconds, Abilities ability, UnitInPool unit, Point2d targetPos) {
        this.gameFrame = this.getDelayedGameFrame(delaySeconds);
        this.ability = ability;
        this.unit = unit;
        this.targetPos = targetPos;
    }

    public DelayedAction(long gameFrame, Abilities ability, UnitInPool unit, Point2d targetPos) {
        this.gameFrame = gameFrame;
        this.ability = ability;
        this.unit = unit;
        this.targetPos = targetPos;
    }

    public static void onStep() {
        delayedActions.stream().filter((action) -> {
            return Bot.OBS.getGameLoop() >= action.gameFrame;
        }).forEach((delayedAction) -> {
            if (!delayedAction.executeAction()) {
                System.out.println("Action not performed: " + delayedAction.toString());
            }

        });
        delayedActions.removeIf((action) -> {
            return Bot.OBS.getGameLoop() >= action.gameFrame;
        });
    }

    public static long nextFrame() {
        return Bot.OBS.getGameLoop() + (long) Strategy.SKIP_FRAMES;
    }

    public long getDelayedGameFrame(int delaySeconds) {
        if (delaySeconds == -1) {
            return nextFrame();
        } else {
            long gameFrame = Bot.OBS.getGameLoop() + (long) ((double) delaySeconds * 22.4D);
            return gameFrame - gameFrame % (long) Strategy.SKIP_FRAMES;
        }
    }

    public boolean executeAction() {
        if (this.unit.isAlive() && (this.targetUnit == null || this.targetUnit.isAlive() && UnitUtils.isVisible(this.targetUnit))) {
            if (this.targetUnit == null && this.targetPos == null) {
                Bot.ACTION.unitCommand(this.unit.unit(), this.ability, false);
            } else if (this.targetUnit == null) {
                Bot.ACTION.unitCommand(this.unit.unit(), this.ability, this.targetPos, false);
            } else {
                Bot.ACTION.unitCommand(this.unit.unit(), this.ability, this.targetUnit.unit(), false);
            }

            return true;
        } else {
            return false;
        }
    }
}

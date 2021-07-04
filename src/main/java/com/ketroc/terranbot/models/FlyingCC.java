package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.bots.Bot;

import java.util.ArrayList;
import java.util.List;

public class FlyingCC {
    public static List<FlyingCC> flyingCCs = new ArrayList();
    public UnitInPool unit;
    public Point2d destination;
    public boolean makeMacroOC;

    public FlyingCC(UnitInPool unit, Point2d destination, boolean makeMacroOC) {
        this.unit = unit;
        this.destination = destination;
        this.makeMacroOC = makeMacroOC;
    }

    public static void addFlyingCC(UnitInPool cc, Point2d destination, boolean makeMacroOC) {
        flyingCCs.add(new FlyingCC(cc, destination, makeMacroOC));
        Bot.ACTION.unitCommand(cc.unit(), Abilities.LIFT_COMMAND_CENTER, false);
    }

    public static void addFlyingCC(Unit cc, Point2d destination, boolean makeMacroOC) {
        addFlyingCC(Bot.OBS.getUnit(cc.getTag()), destination, makeMacroOC);
    }

    public static void onStep() {
        for (int i = 0; i < flyingCCs.size(); ++i) {
            FlyingCC flyingCC = flyingCCs.get(i);
            if (flyingCC.hasDied() && flyingCC.makeMacroOC) {
                LocationConstants.MACRO_OCS.add(flyingCC.destination);
            }

            if (flyingCC.hasDied() || flyingCC.hasLanded()) {
                flyingCCs.remove(i--);
                break;
            }

            flyingCC.keepCCMoving();
        }

    }

    public boolean isMoving() {
        return this.unit.unit().getType() == Units.TERRAN_COMMAND_CENTER_FLYING && !this.unit.unit().getOrders().isEmpty();
    }

    public boolean hasLanded() {
        return this.unit.unit().getType() == Units.TERRAN_COMMAND_CENTER;
    }

    public boolean hasDied() {
        return !this.unit.isAlive();
    }

    public void keepCCMoving() {
        Unit cc = this.unit.unit();
        if (cc.getType() == Units.TERRAN_COMMAND_CENTER_FLYING && cc.getOrders().isEmpty()) {
            Bot.ACTION.unitCommand(cc, Abilities.LAND, this.destination, false);
        }

    }
}

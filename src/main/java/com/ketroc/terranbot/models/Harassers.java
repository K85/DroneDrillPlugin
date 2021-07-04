package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

public class Harassers {
    public static BansheeHarasser clockwiseBanshee;
    public static BansheeHarasser counterClockwiseBanshee;

    public static void onStep() {
        removeHarassers();
        getNewHarassers();
        giveBansheeCommands();
    }

    private static void giveBansheeCommands() {
        if (clockwiseBanshee != null) {
            clockwiseBanshee.bansheeMicro();
        }

        if (counterClockwiseBanshee != null) {
            counterClockwiseBanshee.bansheeMicro();
        }

    }

    private static void removeHarassers() {
        if (clockwiseBanshee != null && doRemoveBanshee(clockwiseBanshee)) {
            Ignored.remove(clockwiseBanshee.banshee.getTag());
            clockwiseBanshee = null;
        }

        if (counterClockwiseBanshee != null && doRemoveBanshee(counterClockwiseBanshee)) {
            Ignored.remove(counterClockwiseBanshee.banshee.getTag());
            counterClockwiseBanshee = null;
        }

    }

    private static boolean doRemoveBanshee(BansheeHarasser bansheeHarasser) {
        UnitInPool banshee = bansheeHarasser.banshee;
        return !banshee.isAlive() || bansheeHarasser.retreatForRepairs && UnitUtils.getDistance(banshee.unit(), LocationConstants.REPAIR_BAY) < 10.0F;
    }

    private static void getNewHarassers() {
        Tag newBansheeTag;
        if (clockwiseBanshee == null) {
            newBansheeTag = getNewBanshee();
            if (newBansheeTag != null) {
                clockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), true);
                Ignored.add(new IgnoredUnit(newBansheeTag));
            }
        } else if (counterClockwiseBanshee == null) {
            newBansheeTag = getNewBanshee();
            if (newBansheeTag != null) {
                counterClockwiseBanshee = new BansheeHarasser(Bot.OBS.getUnit(newBansheeTag), false);
                Ignored.add(new IgnoredUnit(newBansheeTag));
            }
        }

    }

    private static Tag getNewBanshee() {
        return UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BANSHEE).stream().filter((banshee) -> {
            return UnitUtils.getHealthPercentage(banshee) >= 99;
        }).filter((banshee) -> {
            return banshee.getCloakState().orElse(CloakState.CLOAKED_ALLIED) == CloakState.NOT_CLOAKED;
        }).filter((banshee) -> {
            return banshee.getEnergy().orElse(0.0F) >= 50.0F;
        }).map(Unit::getTag).findFirst().orElse(null);
    }
}

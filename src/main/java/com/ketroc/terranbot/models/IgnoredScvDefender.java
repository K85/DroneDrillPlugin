package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

public class IgnoredScvDefender extends Ignored {
    public UnitInPool target;

    public IgnoredScvDefender(Tag unitTag, UnitInPool target) {
        super(unitTag);
        this.target = target;
    }

    public boolean doReleaseUnit() {
        if (this.target.isAlive() && !(UnitUtils.getDistance(this.target.unit(), LocationConstants.baseLocations.get(0)) >= 40.0F)) {
            return false;
        } else {
            Bot.ACTION.unitCommand(this.unitTag, Abilities.STOP, false);
            return true;
        }
    }
}

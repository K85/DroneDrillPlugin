package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;

import java.util.ArrayList;
import java.util.List;

public class ScvTarget {
    public static List<ScvTarget> targets = new ArrayList();
    public int numScvs;
    public List<UnitInPool> scvs = new ArrayList();
    public UnitInPool targetUnit;
    public boolean giveUp = false;

    public ScvTarget(UnitInPool targetUnit) {
        this.targetUnit = targetUnit;
        this.numScvs = 0;
        switch ((Units) targetUnit.unit().getType()) {
            case PROTOSS_PHOTON_CANNON:
                if ((double) targetUnit.unit().getBuildProgress() > 0.65D) {
                    this.giveUp = true;
                } else if ((double) targetUnit.unit().getBuildProgress() > 0.5D) {
                    this.numScvs = 7;
                } else if ((double) targetUnit.unit().getBuildProgress() > 0.35D) {
                    this.numScvs = 6;
                } else if ((double) targetUnit.unit().getBuildProgress() > 0.2D) {
                    this.numScvs = 5;
                } else {
                    this.numScvs = 4;
                }
                break;
            case PROTOSS_PROBE:
                this.numScvs = 0;
                break;
            case PROTOSS_PYLON:
                if (UnitUtils.getDistance(targetUnit.unit(), LocationConstants.baseLocations.get(1)) < 3.5F) {
                    this.numScvs = 1;
                } else {
                    this.numScvs = 0;
                }
        }

    }

    public static void removeDeadTargets() {
        for (int i = 0; i < targets.size(); ++i) {
            ScvTarget scvTarget = targets.get(i);
            if (!scvTarget.targetUnit.isAlive() || !UnitUtils.isVisible(scvTarget.targetUnit) || scvTarget.targetUnit.unit().getPosition().toPoint2d().distance(LocationConstants.baseLocations.get(0)) > 50.0D) {
                if (!scvTarget.scvs.isEmpty()) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvTarget.scvs), Abilities.HARVEST_GATHER, GameCache.defaultRallyNode, false);
                    scvTarget.scvs.stream().forEach((scv) -> {
                        Ignored.remove(scv.getTag());
                    });
                }

                targets.remove(i--);
            }
        }

    }

    public void addScv(UnitInPool scvToAdd) {
        this.scvs.add(scvToAdd);
        Ignored.add(new IgnoredUnit(scvToAdd.getTag()));
    }

    public void removeScv(UnitInPool scvToRemove) {
        this.scvs.remove(scvToRemove);
        Ignored.remove(scvToRemove.getTag());
    }
}

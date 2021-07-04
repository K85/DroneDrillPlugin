package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.ActionInterface;
import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.raw.StartRaw;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;

import java.util.*;

public class _12PoolBot extends S2Agent {
    public static final List<Units> MINERAL_NODE_TYPE;
    public static final Map<Units, Integer> myUnitCounts;
    public static final Map<Units, List<Unit>> myUnits;
    public static ObservationInterface obs;
    public static ActionInterface action;
    public static UnitInPool mainHatchery;
    public static Point2d enemyMainPos;
    public static Point2d poolPos;
    public static UnitInPool mineralNode;

    static {
        MINERAL_NODE_TYPE = new ArrayList(Arrays.asList(Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_MINERAL_FIELD750, Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750, Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750));
        myUnitCounts = new HashMap();
        myUnits = new HashMap();
    }

    public void onGameStart() {
        obs = this.observation();
        action = this.actions();
        mainHatchery = obs.getUnits(Alliance.SELF, (unitInPool) -> {
            return unitInPool.unit().getType() == Units.ZERG_HATCHERY;
        }).get(0);
        enemyMainPos = obs.getGameInfo().getStartRaw().get().getStartLocations().iterator().next();
        poolPos = Point2d.of(mainHatchery.unit().getPosition().getX(), mainHatchery.unit().getPosition().getY() + 9.0F);
        mineralNode = obs.getUnits(Alliance.NEUTRAL, (unitInPool) -> {
            return unitInPool.unit().getType() instanceof Units && MINERAL_NODE_TYPE.contains(unitInPool.unit().getType()) && unitInPool.unit().getDisplayType() == DisplayType.VISIBLE;
        }).get(0);
    }

    public void onStep() {
        myUnitCounts.clear();
        myUnits.clear();

        Iterator var1;
        UnitInPool uip;
        Units unitType;
        for (var1 = obs.getUnits(Alliance.SELF).iterator(); var1.hasNext(); myUnits.get(unitType).add(uip.unit())) {
            uip = (UnitInPool) var1.next();
            unitType = (Units) uip.unit().getType();
            myUnitCounts.put(unitType, myUnitCounts.getOrDefault(unitType, 0) + 1);
            if (!myUnits.containsKey(unitType)) {
                myUnits.put(unitType, new ArrayList());
            }
        }

        if (myUnitCounts.getOrDefault(Units.ZERG_SPAWNING_POOL, 0) == 0) {
            if (obs.getMinerals() >= 200) {
                action.unitCommand((Unit) ((List) myUnits.get(Units.ZERG_DRONE)).get(0), Abilities.BUILD_SPAWNING_POOL, poolPos, false);
            }

        } else {
            List larvaList;
            if (obs.getFoodWorkers() < 13) {
                larvaList = myUnits.getOrDefault(Units.ZERG_LARVA, Collections.emptyList());
                if (!larvaList.isEmpty() && obs.getMinerals() >= 50) {
                    action.unitCommand((Unit) larvaList.get(0), Abilities.TRAIN_DRONE, false).unitCommand((Unit) larvaList.get(0), Abilities.SMART, mineralNode.unit(), true);
                }

            } else if (myUnitCounts.get(Units.ZERG_OVERLORD) < 2) {
                if (obs.getMinerals() >= 100 && !this.isUnitInEgg(Abilities.TRAIN_OVERLORD)) {
                    larvaList = myUnits.getOrDefault(Units.ZERG_LARVA, Collections.emptyList());
                    action.unitCommand((Unit) larvaList.get(0), Abilities.TRAIN_OVERLORD, false);
                }

            } else if (!(((Unit) ((List) myUnits.get(Units.ZERG_SPAWNING_POOL)).get(0)).getBuildProgress() < 1.0F)) {
                var1 = ((List) myUnits.getOrDefault(Units.ZERG_LARVA, Collections.emptyList())).iterator();

                Unit zergling;
                while (var1.hasNext()) {
                    zergling = (Unit) var1.next();
                    if (obs.getMinerals() < 50) {
                        break;
                    }

                    action.unitCommand(zergling, Abilities.TRAIN_ZERGLING, false);
                }

                var1 = ((List) myUnits.getOrDefault(Units.ZERG_ZERGLING, Collections.emptyList())).iterator();

                while (var1.hasNext()) {
                    zergling = (Unit) var1.next();
                    if (zergling.getOrders().isEmpty()) {
                        action.unitCommand(zergling, Abilities.ATTACK, enemyMainPos, false);
                    }
                }

                action.sendActions();
            }
        }
    }

    private boolean isUnitInEgg(Abilities trainUnitType) {
        List<Unit> eggs = myUnits.getOrDefault(Units.ZERG_EGG, Collections.emptyList());
        Iterator var3 = eggs.iterator();

        Unit egg;
        do {
            if (!var3.hasNext()) {
                return false;
            }

            egg = (Unit) var3.next();
        } while (egg.getOrders().get(0).getAbility() != trainUnitType);

        return true;
    }

    public void onUnitCreated(UnitInPool unitInPool) {
    }

    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
    }

    public void onUnitIdle(UnitInPool unitInPool) {
    }
}

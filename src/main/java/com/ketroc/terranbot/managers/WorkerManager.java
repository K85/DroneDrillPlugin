package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.*;
import java.util.stream.Collectors;

public class WorkerManager {
    public static int scvsPerGas = 3;

    public static void onStep() {
        Strategy.setMaxScvs();
        repairLogic();
        fixOverSaturation();
        toggleWorkersInGas();
        buildRefineryLogic();
        defendWorkerHarass();
    }

    private static void defendWorkerHarass() {
        if (Bot.OBS.getGameLoop() <= 4008L) {
            List<Unit> enemyScoutWorkers = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType);
            List<UnitInPool> myScvs = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, LocationConstants.baseLocations.get(0), 50.0F);
            Iterator var2 = enemyScoutWorkers.iterator();

            while (var2.hasNext()) {
                Unit enemyWorker = (Unit) var2.next();
                Point2d enemyWorkerPos = enemyWorker.getPosition().toPoint2d();
                if (UnitUtils.getDistance(enemyWorker, LocationConstants.baseLocations.get(0)) < 40.0F) {
                    Optional<Unit> attackingScv = myScvs.stream().map(UnitInPool::unit).filter((scv) -> {
                        return UnitUtils.isAttacking(scv, enemyWorker);
                    }).findFirst();
                    if (attackingScv.isPresent()) {
                        if (attackingScv.get().getHealth().orElse(0.0F) <= 10.0F) {
                            Bot.ACTION.unitCommand(attackingScv.get(), Abilities.STOP, false);
                            Ignored.remove(attackingScv.get().getTag());
                            sendScvToAttack(enemyWorker);
                        }
                    } else {
                        sendScvToAttack(enemyWorker);
                    }
                }
            }

        }
    }

    private static void sendScvToAttack(Unit enemy) {
        List<UnitInPool> availableScvs = getAvailableScvs(enemy.getPosition().toPoint2d()).stream().filter((scv) -> {
            return scv.unit().getHealth().orElse(1.0F) > 39.0F;
        }).collect(Collectors.toList());
        if (!availableScvs.isEmpty()) {
            Ignored.add(new IgnoredScvDefender(availableScvs.get(0).getTag(), Bot.OBS.getUnit(enemy.getTag())));
            Bot.ACTION.unitCommand(availableScvs.get(0).unit(), Abilities.ATTACK, enemy, false);
        }

    }

    private static void repairLogic() {
        List<Unit> unitsToRepair = new ArrayList(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_PLANETARY_FORTRESS, new ArrayList()));
        unitsToRepair.addAll(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_MISSILE_TURRET, Collections.emptyList()));
        unitsToRepair.addAll(GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_BUNKER, Collections.emptyList()));
        if (LocationConstants.opponentRace != Race.PROTOSS) {
            unitsToRepair.addAll(GameCache.liberatorList);
        }

        unitsToRepair.addAll(GameCache.siegeTankList);
        unitsToRepair.addAll(GameCache.wallStructures);
        unitsToRepair.addAll(GameCache.burningStructures);
        Iterator var1 = unitsToRepair.iterator();

        while (var1.hasNext()) {
            Unit unit = (Unit) var1.next();
            int structureHealth = UnitUtils.getHealthPercentage(unit);
            if (structureHealth < 100) {
                int numScvsToAdd = UnitUtils.getIdealScvsToRepair(unit) - UnitUtils.numRepairingScvs(unit);
                if (numScvsToAdd > 0) {
                    List availableScvs;
                    if (numScvsToAdd > 9999) {
                        availableScvs = getAllScvUnits(unit.getPosition().toPoint2d());
                    } else {
                        if (GameCache.wallStructures.contains(unit)) {
                            availableScvs = UnitUtils.toUnitList(Bot.OBS.getUnits(Alliance.SELF, (u) -> {
                                return u.unit().getType() == Units.TERRAN_SCV && Math.round(u.unit().getPosition().getZ()) == Math.round(unit.getPosition().getZ()) && u.unit().getPosition().distance(unit.getPosition()) < 30.0D && (u.unit().getOrders().isEmpty() || u.unit().getOrders().size() == 1 && u.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && isMiningMinerals(u));
                            }));
                        } else {
                            availableScvs = getAvailableScvUnits(unit.getPosition().toPoint2d());
                        }

                        availableScvs = availableScvs.subList(0, Math.max(0, Math.min(availableScvs.size() - 1, numScvsToAdd)));
                    }

                    if (!availableScvs.isEmpty()) {
                        System.out.println("sending " + availableScvs.size() + " scvs to repair.");
                        Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR_SCV, unit, false);
                    }
                }
            }
        }

    }

    public static void fix3ScvsOn1MineralPatch() {
        Iterator var0 = GameCache.baseList.iterator();

        while (var0.hasNext()) {
            Base base = (Base) var0.next();
            List<Unit> harvestingScvs = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, base.getCcPos(), 10.0F).stream().map(UnitInPool::unit).filter((scvx) -> {
                return !scvx.getOrders().isEmpty() && scvx.getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER && scvx.getOrders().get(0).getTargetedUnitTag().isPresent();
            }).collect(Collectors.toList());
            Set<Tag> uniquePatchTags = new HashSet();
            Tag overloadedPatch = null;
            Unit thirdScv = null;
            Iterator var6 = harvestingScvs.iterator();

            while (var6.hasNext()) {
                Unit scv = (Unit) var6.next();
                Tag targetPatchTag = scv.getOrders().get(0).getTargetedUnitTag().get();
                if (uniquePatchTags.add(targetPatchTag)) {
                    overloadedPatch = targetPatchTag;
                    thirdScv = scv;
                }
            }

            if (overloadedPatch == null) {
                return;
            }

            Unit availablePatch = null;
            Iterator var12 = base.getMineralPatches().iterator();

            while (var12.hasNext()) {
                Unit mineral = (Unit) var12.next();
                if (!uniquePatchTags.contains(mineral.getTag())) {
                    availablePatch = mineral;
                    Bot.ACTION.unitCommand(thirdScv, Abilities.HARVEST_GATHER, mineral, false);
                    break;
                }
            }

            if (availablePatch == null) {
                return;
            }

            if (BansheeBot.isDebugOn) {
                Point2d thirdScvPos = thirdScv.getPosition().toPoint2d();
                Point2d overloadedPatchPos = Bot.OBS.getUnit(overloadedPatch).unit().getPosition().toPoint2d();
                Point2d availablePatchPos = availablePatch.getPosition().toPoint2d();
                float z = Bot.OBS.terrainHeight(thirdScvPos) + 0.8F;
                Bot.DEBUG.debugBoxOut(Point.of(thirdScvPos.getX() - 0.3F, thirdScvPos.getY() - 0.3F, z), Point.of(thirdScvPos.getX() + 0.3F, thirdScvPos.getY() + 0.3F, z), Color.YELLOW);
                Bot.DEBUG.debugBoxOut(Point.of(overloadedPatchPos.getX() - 0.3F, overloadedPatchPos.getY() - 0.3F, z), Point.of(overloadedPatchPos.getX() + 0.3F, overloadedPatchPos.getY() + 0.3F, z), Color.YELLOW);
                Bot.DEBUG.debugBoxOut(Point.of(availablePatchPos.getX() - 0.3F, availablePatchPos.getY() - 0.3F, z), Point.of(availablePatchPos.getX() + 0.3F, availablePatchPos.getY() + 0.3F, z), Color.YELLOW);
            }
        }

    }

    private static void buildRefineryLogic() {
        if ((LocationConstants.opponentRace != Race.ZERG || GameCache.ccList.size() >= 3) && (LocationConstants.opponentRace != Race.PROTOSS || GameCache.ccList.size() >= 3)) {
            Iterator var0 = GameCache.baseList.iterator();

            while (true) {
                Base base;
                do {
                    do {
                        if (!var0.hasNext()) {
                            return;
                        }

                        base = (Base) var0.next();
                    } while (!base.isMyBase());
                } while (!base.isComplete(0.6F));

                Iterator var2 = base.getGases().iterator();

                while (var2.hasNext()) {
                    Gas gas = (Gas) var2.next();
                    if (gas.getRefinery() == null && gas.getGeyser().getVespeneContents().orElse(0) > 1 && StructureScv.scvBuildingList.stream().noneMatch((scv) -> {
                        return scv.buildAbility == Abilities.BUILD_REFINERY && scv.structurePos.distance(gas.getLocation()) < 1.0D;
                    }) && !Purchase.isStructureQueued(Units.TERRAN_REFINERY)) {
                        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_REFINERY));
                        return;
                    }
                }
            }
        }
    }

    public static List<Unit> getAvailableScvUnits(Point2d targetPosition) {
        return UnitUtils.toUnitList(getAvailableScvs(targetPosition, 10));
    }

    public static List<Unit> getAllScvUnits(Point2d targetPosition) {
        return UnitUtils.toUnitList(getAllScvs(targetPosition, 10));
    }

    public static List<UnitInPool> getOneScv(Point2d targetPosition) {
        return getAvailableScvs(targetPosition, 20, false);
    }

    public static List<UnitInPool> getOneScv(Point2d targetPosition, int distance) {
        return getAvailableScvs(targetPosition, distance, true);
    }

    public static List<UnitInPool> getOneScv() {
        return getAvailableScvs(ArmyManager.retreatPos, Integer.MAX_VALUE, true);
    }

    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition) {
        return getAvailableScvs(targetPosition, 20, false);
    }

    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance) {
        return getAvailableScvs(targetPosition, distance, true);
    }

    public static List<UnitInPool> getAllAvailableScvs() {
        return getAvailableScvs(ArmyManager.retreatPos, Integer.MAX_VALUE, true);
    }

    public static List<UnitInPool> getAvailableScvs(Point2d targetPosition, int distance, boolean isDistanceEnforced) {
        List<UnitInPool> scvList = Bot.OBS.getUnits(Alliance.SELF, (scv) -> {
            return scv.unit().getType() == Units.TERRAN_SCV && (scv.unit().getOrders().isEmpty() || isMiningMinerals(scv)) && UnitUtils.getDistance(scv.unit(), targetPosition) < (float) distance && !Ignored.contains(scv.getTag());
        });
        return scvList.isEmpty() && !isDistanceEnforced ? getAvailableScvs(targetPosition, Integer.MAX_VALUE, true) : scvList;
    }

    public static List<UnitInPool> getAllScvs(Point2d targetPosition, int distance) {
        return Bot.OBS.getUnits(Alliance.SELF, (scv) -> {
            return scv.unit().getType() == Units.TERRAN_SCV && !Ignored.contains(scv.getTag()) && targetPosition.distance(scv.unit().getPosition().toPoint2d()) < (double) distance;
        });
    }

    public static boolean isMiningMinerals(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.MINERAL_NODE_TYPE);
    }

    public static boolean isMiningGas(UnitInPool scv) {
        return isMiningNode(scv, UnitUtils.REFINERY_TYPE);
    }

    private static boolean isMiningNode(UnitInPool scv, Set<Units> nodeType) {
        if (scv.unit().getOrders().size() == 1 && scv.unit().getOrders().get(0).getAbility() == Abilities.HARVEST_GATHER) {
            Optional<Tag> scvTargetTag = scv.unit().getOrders().get(0).getTargetedUnitTag();
            if (scvTargetTag.isEmpty()) {
                return false;
            } else {
                UnitInPool targetNode = Bot.OBS.getUnit(scvTargetTag.get());
                return targetNode != null && nodeType.contains(targetNode.unit().getType());
            }
        } else {
            return false;
        }
    }

    private static void fixOverSaturation() {
        List<Unit> scvsToMove = new ArrayList();
        Iterator var1 = GameCache.baseList.iterator();

        label98:
        while (true) {
            Base base;
            List scvsForThisBase;
            do {
                do {
                    if (!var1.hasNext()) {
                        if (Bot.OBS.getIdleWorkerCount() > 0) {
                            List<Unit> idleScvs = UnitUtils.toUnitList(Bot.OBS.getUnits(Alliance.SELF, (scv) -> {
                                return scv.unit().getType() == Units.TERRAN_SCV && scv.unit().getOrders().isEmpty() && !Ignored.contains(scv.getTag());
                            }));
                            scvsToMove.addAll(0, idleScvs);
                        }

                        var1 = GameCache.baseList.iterator();

                        while (var1.hasNext()) {
                            base = (Base) var1.next();
                            if (base.isMyBase() && base.isComplete(0.9F)) {
                                if (scvsToMove.isEmpty()) {
                                    break;
                                }

                                if (!base.getMineralPatches().isEmpty()) {
                                    int scvsNeeded = base.getExtraScvs() * -1;
                                    if (scvsNeeded > 0) {
                                        scvsForThisBase = scvsToMove.subList(0, Math.min(scvsNeeded, scvsToMove.size()));
                                        Bot.ACTION.unitCommand(scvsForThisBase, Abilities.SMART, base.getRallyNode(), false);
                                        scvsForThisBase.clear();
                                    }
                                }
                            }
                        }

                        scvsToMove.removeIf((scv) -> {
                            return !scv.getOrders().isEmpty();
                        });
                        if (!scvsToMove.isEmpty()) {
                            if (GameCache.defaultRallyNode == null) {
                                scvsToMove.stream().filter((scv) -> {
                                    return scv.getBuffs().contains(Buffs.AUTOMATED_REPAIR);
                                }).forEach((scv) -> {
                                    Bot.ACTION.unitCommand(scv, Abilities.EFFECT_REPAIR_SCV, false);
                                });
                                UnitUtils.queueUpAttackOfEveryBase(scvsToMove);
                            } else {
                                Bot.ACTION.unitCommand(scvsToMove, Abilities.SMART, GameCache.defaultRallyNode, false);
                            }
                        }

                        return;
                    }

                    base = (Base) var1.next();
                } while (!base.isMyBase());
            } while (!base.isComplete());

            Unit cc = base.getCc().get().unit();
            scvsForThisBase = getAvailableScvs(base.getCcPos(), 10);
            int numScvsMovingToGas = 0;
            Iterator var6 = base.getGases().iterator();

            while (true) {
                Gas gas;
                do {
                    if (!var6.hasNext()) {
                        base.setExtraScvs(cc.getAssignedHarvesters().get() - numScvsMovingToGas - cc.getIdealHarvesters().get());

                        for (int i = 0; i < base.getExtraScvs() && i < scvsForThisBase.size(); ++i) {
                            scvsToMove.add(((UnitInPool) scvsForThisBase.get(i)).unit());
                        }
                        continue label98;
                    }

                    gas = (Gas) var6.next();
                } while (gas.getRefinery() == null);

                Unit refinery = gas.getRefinery();

                for (int i = refinery.getAssignedHarvesters().get(); i < Math.min(refinery.getIdealHarvesters().get(), scvsPerGas) && !scvsForThisBase.isEmpty(); ++i) {
                    Bot.ACTION.unitCommand(((UnitInPool) scvsForThisBase.remove(0)).unit(), Abilities.SMART, refinery, false);
                    ++numScvsMovingToGas;
                }

                if (refinery.getAssignedHarvesters().get() > scvsPerGas) {
                    List<UnitInPool> availableGasScvs = Bot.OBS.getUnits(Alliance.SELF, (u) -> {
                        if (u.unit().getType() == Units.TERRAN_SCV && !u.unit().getOrders().isEmpty()) {
                            UnitOrder order = u.unit().getOrders().get(0);
                            return order.getTargetedUnitTag().isPresent() && order.getAbility() == Abilities.HARVEST_GATHER && order.getTargetedUnitTag().get().equals(refinery.getTag());
                        } else {
                            return false;
                        }
                    });
                    if (!availableGasScvs.isEmpty()) {
                        scvsToMove.add(0, availableGasScvs.get(0).unit());
                    }
                }
            }
        }
    }

    public static boolean toggleWorkersInGas() {
        int numRefineries = UnitUtils.getNumUnits(UnitUtils.REFINERY_TYPE, false);
        if (numRefineries <= 1) {
            return false;
        } else {
            int mins = GameCache.mineralBank;
            int gas = GameCache.gasBank;
            if (scvsPerGas == 1) {
                if ((double) gasBankRatio() < 0.6D) {
                    scvsPerGas = 2;
                    return true;
                }
            } else if (scvsPerGas != 2) {
                if (scvsPerGas == 3 && mins < 2750 && gas > 80 * GameCache.starportList.size() && (double) gasBankRatio() > 0.5D) {
                    scvsPerGas = 2;
                    return true;
                }
            } else {
                if (Bot.OBS.getGameLoop() > 4000L && (mins > 3000 || mins > 300 && (double) gasBankRatio() < 0.3D)) {
                    scvsPerGas = 3;
                    return true;
                }

                if (gas > 700 && (double) gasBankRatio() > 0.75D) {
                    scvsPerGas = 1;
                    return true;
                }
            }

            return false;
        }
    }

    private static float gasBankRatio() {
        return Math.max((float) GameCache.gasBank, 1.0F) / (Math.max((float) GameCache.gasBank, 1.0F) + Math.max((float) GameCache.mineralBank, 1.0F));
    }
}

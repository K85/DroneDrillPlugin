package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.DebugInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.DroneDrill;
import com.ketroc.terranbot.models.DelayedAction;
import com.ketroc.terranbot.models.TriangleOfNodes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class DroneRush {
    public static final Point2d ENEMY_MAIN_POS;
    public static int droneRushStep;
    public static int clusterTriangleStep;
    public static int noTriangleStep;
    public static List<UnitInPool> droneList;
    public static UnitInPool target;
    public static boolean isAttackingCommand;
    private static boolean clusterNow;
    private static long giveUpTargetFrame;

    static {
        ENEMY_MAIN_POS = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1);
    }

    public static void onStep() {
        if (Bot.isDebugOn) {
            int lines = 0;
            DebugInterface var10000 = Bot.DEBUG;
            String var10001 = "clusterEnemyNodeStep: " + clusterTriangleStep;
            int var3 = lines + 1;
            var10000.debugTextOut(var10001, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) lines) / 1080.0D)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("droneRushStep: " + droneRushStep, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (var3++)) / 1080.0D)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("0", LocationConstants.enemyMineralTriangle.getMiddle().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("1", LocationConstants.enemyMineralTriangle.getInner().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("2", LocationConstants.enemyMineralTriangle.getOuter().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.sendDebug();
        }

        try {
            LocationConstants.enemyMineralTriangle.updateNodes();
            if (droneRushStep > 0) {
                UnitUtils.removeDeadUnits(droneList);
                if (droneList.isEmpty()) {
                    return;
                }
            }

            if (Bot.OBS.getGameLoop() == 6000L) {
                System.out.println("Drone list contents:");
                droneList.forEach((unitInPool) -> {
                    System.out.println(unitInPool);
                });
            }

            switch (droneRushStep) {
                case 0:
                    if (droneList == null) {
                        droneList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.ZERG_DRONE, LocationConstants.baseLocations.get(0), 20.0F);
                    }

                    if (LocationConstants.MAP.equals("Pillars of Gold LE")) {
                        noTriangleRush();
                    } else if (clusterTriangleNode(LocationConstants.myMineralTriangle, true)) {
                        UnitInPool drone = droneList.remove(0);
                        DroneDrill.lateDrones.add(drone);
                        Bot.ACTION.unitCommand(drone.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), false);
                        ++droneRushStep;
                    }
                    break;
                case 1:
                    if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
                        ++droneRushStep;
                    }
                    break;
                case 2:
                    addNewDrones();
                    attackingBase();
                    break;
                case 3:
                    List<UnitInPool> enemyList = Bot.OBS.getUnits(Alliance.ENEMY, (enemy) -> {
                        return !(Boolean) enemy.unit().getFlying().orElse(false);
                    });
                    droneList.addAll(DroneDrill.lateDrones);
                    DroneDrill.lateDrones.clear();
                    if (!enemyList.isEmpty()) {
                        Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, enemyList.get(0).unit().getPosition().toPoint2d(), false);
                    } else {
                        ++droneRushStep;
                    }
                    break;
                case 4:
                    if (!droneList.isEmpty()) {
                        Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, ENEMY_MAIN_POS, false).unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 2), true).unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.myMineralTriangle.getOuter().unit(), true);
                    }

                    droneList.clear();
            }
        } catch (Exception var1) {
            var1.printStackTrace();
        }

    }

    private static void noTriangleRush() {
        switch (noTriangleStep) {
            case 0:
                List<Unit> dronesWithMinerals = UnitUtils.toUnitList(droneList.stream().filter((dronex) -> {
                    return UnitUtils.isCarryingResources(dronex.unit());
                }).collect(Collectors.toList()));
                List<Unit> dronesEmpty = UnitUtils.toUnitList(droneList.stream().filter((dronex) -> {
                    return !UnitUtils.isCarryingResources(dronex.unit());
                }).collect(Collectors.toList()));
                if (!dronesEmpty.isEmpty()) {
                    Bot.ACTION.unitCommand(dronesEmpty, Abilities.ATTACK, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), false);
                }

                if (!dronesWithMinerals.isEmpty()) {
                    Bot.ACTION.unitCommand(dronesWithMinerals, Abilities.HARVEST_RETURN_DRONE, false);
                }

                if (UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EGG).isEmpty()) {
                    droneList.addAll(DroneDrill.lateDrones);
                    DroneDrill.lateDrones.clear();
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), false);
                    ++noTriangleStep;
                }
                break;
            case 1:
                for (int i = 0; i < droneList.size(); ++i) {
                    UnitInPool drone = droneList.get(i);
                    if (!drone.isAlive()) {
                        droneList.remove(i--);
                    } else if (drone.unit().getHealth().get() <= 10.0F) {
                        Bot.ACTION.unitCommand(drone.unit(), Abilities.SMART, LocationConstants.myMineralTriangle.getOuter().unit(), false);
                        droneList.remove(i--);
                    }
                }
        }

    }

    private static void addNewDrones() {
        List<UnitInPool> dronesReady = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.ZERG_DRONE, LocationConstants.enemyMineralTriangle.getInner().unit().getPosition().toPoint2d(), 2.0F);
        Iterator var1 = dronesReady.iterator();

        while (var1.hasNext()) {
            UnitInPool drone = (UnitInPool) var1.next();
            if (!droneList.contains(drone)) {
                droneList.add(drone);
                DroneDrill.lateDrones.remove(drone);
            }
        }

    }

    private static void attackingBase() {
        Unit enemyCommand = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyCommandStructures, ENEMY_MAIN_POS, 1.0F).stream().map(UnitInPool::unit).findFirst().orElse(null);
        if (enemyCommand != null && !(Boolean) enemyCommand.getFlying().orElse(false)) {
            List<UnitInPool> enemyWorkers = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, Position.midPointUnitsMedian(UnitUtils.toUnitList(droneList)), 5.0F);
            if ((droneList.size() < enemyWorkers.size() * 2 || !enemyWorkers.stream().noneMatch((enemy) -> {
                return UnitUtils.getDistance(enemy.unit(), LocationConstants.enemyMineralTriangle.getClusterPos()) < 2.0F;
            })) && !enemyWorkers.stream().noneMatch((enemy) -> {
                return UnitUtils.getDistance(enemy.unit(), LocationConstants.enemyMineralTriangle.getClusterPos()) < 3.0F;
            })) {
                if (isAttackingCommand) {
                    if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
                        isAttackingCommand = false;
                    }
                } else {
                    updateTarget();
                    droneDrillMicro3(enemyWorkers);
                }
            } else {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, ENEMY_MAIN_POS, false);
                isAttackingCommand = true;
            }

        } else {
            ++droneRushStep;
        }
    }

    public static void droneDrillMicro1(List<UnitInPool> enemyWorkers) {
        if (clusterNow) {
            if (isDronesClustered()) {
                clusterNow = false;
            } else {
                clusterUp();
            }
        }

        if (!clusterNow) {
            if (!isDronesClusteredAndReady()) {
                clusterNow = true;
                clusterUp();
            } else {
                UnitInPool temp = getTargetWorker(enemyWorkers);
                Unit targetWorker = temp == null ? null : temp.unit();
                if (targetWorker != null) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, targetWorker, false);
                } else {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
                }
            }
        }

    }

    public static void droneDrillMicro2() {
        List<Unit> attackDrones = new ArrayList();
        List<Unit> clusterDrones = new ArrayList();
        Iterator var2 = droneList.iterator();

        while (true) {
            while (var2.hasNext()) {
                UnitInPool scv = (UnitInPool) var2.next();
                if (isDroneOffCooldown(scv.unit()) && !isDroneTooFar(scv.unit())) {
                    attackDrones.add(scv.unit());
                } else {
                    clusterDrones.add(scv.unit());
                }
            }

            if (!attackDrones.isEmpty()) {
                Bot.ACTION.unitCommand(attackDrones, Abilities.ATTACK, LocationConstants.myMineralPos, false);
            }

            if (!clusterDrones.isEmpty()) {
                Bot.ACTION.unitCommand(clusterDrones, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }

            return;
        }
    }

    public static void droneDrillMicro3(List<UnitInPool> enemyWorkers) {
        if (target != null) {
            List<Unit> attackDrones = new ArrayList();
            List<Unit> clusterDrones = new ArrayList();
            Iterator var3 = droneList.iterator();

            while (var3.hasNext()) {
                UnitInPool drone = (UnitInPool) var3.next();
                if (isDroneOffCooldown(drone.unit())) {
                    attackDrones.add(drone.unit());
                } else {
                    clusterDrones.add(drone.unit());
                }
            }

            if (!attackDrones.isEmpty()) {
                System.out.println("attackDrones.size() = " + attackDrones.size());
                Bot.ACTION.unitCommand(attackDrones, Abilities.ATTACK, target.unit(), false);
            }

            if (!clusterDrones.isEmpty()) {
                Bot.ACTION.unitCommand(clusterDrones, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }
        } else if (isDronesOffCooldown()) {
            target = oneShotTarget(enemyWorkers, droneList);
            if (target != null) {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, target.unit(), false);
            } else {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.ATTACK, LocationConstants.myMineralPos, false);
            }
        } else {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }

    }

    private static void updateTarget() {
        if (target != null && (!target.isAlive() || UnitUtils.getDistance(target.unit(), LocationConstants.enemyMineralTriangle.getClusterPos()) > 3.0F)) {
            System.out.println(!target.isAlive() ? "target killed" : "target cleared cuz out of range");
            target = null;
        }

    }

    private static void droneDrillMicro4() {
        if (DelayedAction.delayedActions.isEmpty()) {
            if (isDronesClustered()) {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
                long attackFrame = Bot.OBS.getGameLoop() + (long) (Strategy.SKIP_FRAMES * 3);
                droneList.forEach((scv) -> {
                    DelayedAction.delayedActions.add(new DelayedAction(attackFrame, Abilities.ATTACK, scv, LocationConstants.myMineralPos));
                });
                long clusterFrame = attackFrame + (long) (Strategy.SKIP_FRAMES * 3);
                droneList.forEach((scv) -> {
                    DelayedAction.delayedActions.add(new DelayedAction(clusterFrame, Abilities.SMART, scv, LocationConstants.enemyMineralTriangle.getMiddle()));
                });
            } else {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }

        }
    }

    private static UnitInPool oneShotTarget(List<UnitInPool> enemyWorkers, List<UnitInPool> attackDrones) {
        UnitInPool enemy = enemyWorkers.stream().min(Comparator.comparing((unit) -> {
            return UnitUtils.getDistance(unit.unit(), LocationConstants.enemyMineralTriangle.getClusterPos());
        })).orElse(null);
        int numAttackers = (int) attackDrones.stream().filter((drone) -> {
            return UnitUtils.getDistance(drone.unit(), enemy.unit()) < 3.0F;
        }).count();
        if ((float) (numAttackers * 5) >= enemy.unit().getHealth().get()) {
            System.out.println("target found.  #drones: " + numAttackers + ". enemy health: " + enemy.unit().getHealth().get());
            return enemy;
        } else {
            return null;
        }
    }

    private static boolean isScvAttacking(Unit scv) {
        return !scv.getOrders().isEmpty() && scv.getOrders().get(0).getAbility() == Abilities.ATTACK;
    }

    private static boolean isDronesTooFar() {
        return droneList.stream().anyMatch((scv) -> {
            return isDroneTooFar(scv.unit());
        });
    }

    private static boolean isDroneTooFar(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) > 1.4F;
    }

    private static void clusterUp() {
        Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
    }

    private static boolean isDronesClustered() {
        return droneList.stream().allMatch((scv) -> {
            return isByClusterPatch(scv.unit());
        });
    }

    private static boolean isByClusterPatch(Unit scv) {
        return (double) UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) < 1.4D;
    }

    private static boolean isScvsClustering() {
        return droneList.stream().anyMatch((scv) -> {
            return !scv.unit().getOrders().isEmpty() && scv.unit().getOrders().get(0).getTargetedUnitTag().isPresent() && scv.unit().getOrders().get(0).getTargetedUnitTag().equals(LocationConstants.enemyMineralTriangle.getMiddle().getTag());
        });
    }

    private static boolean isDronesClusteredAndReady() {
        return droneList.stream().allMatch((drone) -> {
            return isDroneOffCooldown(drone.unit()) && (double) UnitUtils.getDistance(drone.unit(), LocationConstants.enemyMineralTriangle.getMiddle().unit()) < 1.4D;
        });
    }

    private static boolean isDronesOffCooldown() {
        return droneList.stream().allMatch((drone) -> {
            return isDroneOffCooldown(drone.unit());
        });
    }

    private static boolean isOneShotAvailable() {
        return droneList.stream().filter((drone) -> {
            return isDroneOffCooldown(drone.unit());
        }).count() >= 9L;
    }

    private static boolean isDroneOffCooldown(Unit drone) {
        return drone.getWeaponCooldown().orElse(0.0F) == 0.0F;
    }

    private static boolean isDroneReadyAndInRange(Unit drone, Unit target) {
        return drone.getWeaponCooldown().orElse(0.0F) == 0.0F && UnitUtils.getDistance(drone, target) <= 0.8F;
    }

    private static UnitInPool getTargetWorker(List<UnitInPool> enemyWorkers) {
        Iterator var1 = enemyWorkers.iterator();

        UnitInPool enemyWorker = null;
        UnitInPool finalEnemyWorker = enemyWorker;
        do {
            if (!var1.hasNext()) {
                return null;
            }

            enemyWorker = (UnitInPool) var1.next();
        } while (!droneList.stream().allMatch((scv) -> {
            return (double) UnitUtils.getDistance(scv.unit(), finalEnemyWorker.unit()) < 0.8D;
        }));

        return enemyWorker;
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle) {
        return clusterTriangleNode(triangle, false);
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle, Boolean returnMinerals) {
        switch (clusterTriangleStep) {
            case 0:
                if (droneList.stream().anyMatch((u) -> {
                    return UnitUtils.getDistance(u.unit(), triangle.getInner().unit()) > 2.0F;
                })) {
                    if (returnMinerals) {
                        List<Unit> dronesWithMinerals = UnitUtils.toUnitList(droneList.stream().filter((drone) -> {
                            return UnitUtils.isCarryingResources(drone.unit());
                        }).collect(Collectors.toList()));
                        List<Unit> dronesEmpty = UnitUtils.toUnitList(droneList.stream().filter((drone) -> {
                            return !UnitUtils.isCarryingResources(drone.unit());
                        }).collect(Collectors.toList()));
                        if (!dronesEmpty.isEmpty()) {
                            Bot.ACTION.unitCommand(dronesEmpty, Abilities.SMART, triangle.getInner().unit(), false);
                        }

                        if (!dronesWithMinerals.isEmpty()) {
                            Bot.ACTION.unitCommand(dronesWithMinerals, Abilities.HARVEST_RETURN_DRONE, false);
                        }
                    } else {
                        Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, triangle.getInner().unit(), false);
                    }
                } else {
                    ++clusterTriangleStep;
                }
                break;
            case 1:
                if (droneList.stream().anyMatch((u) -> {
                    return UnitUtils.getDistance(u.unit(), triangle.getOuter().unit()) > 2.0F;
                })) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, triangle.getOuter().unit(), false);
                } else {
                    ++clusterTriangleStep;
                }
                break;
            case 2:
                if (!droneList.stream().anyMatch((u) -> {
                    return (double) UnitUtils.getDistance(u.unit(), triangle.getMiddle().unit()) > 1.4D;
                })) {
                    clusterTriangleStep = 0;
                    return true;
                }

                Bot.ACTION.unitCommand(UnitUtils.toUnitList(droneList), Abilities.SMART, triangle.getMiddle().unit(), false);
        }

        return false;
    }
}

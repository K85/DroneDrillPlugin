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
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.DelayedAction;
import com.ketroc.terranbot.models.TriangleOfNodes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ScvRush {
    public static int clusterMyNodeStep;
    public static int clusterTriangleStep;
    public static int scvRushStep;
    public static List<UnitInPool> scvList;
    public static UnitInPool target;
    public static boolean isAttackingCommand;
    private static boolean clusterNow = false;

    public static boolean onStep() {
        if (BansheeBot.isDebugOn) {
            int lines = 0;
            DebugInterface var10000 = Bot.DEBUG;
            String var10001 = "clusterEnemyNodeStep: " + clusterTriangleStep;
            int var7 = lines + 1;
            var10000.debugTextOut(var10001, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) lines) / 1080.0D)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("scvRushStep: " + scvRushStep, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (var7++)) / 1080.0D)), Color.WHITE, 12);
            Bot.DEBUG.debugTextOut("0", LocationConstants.enemyMineralTriangle.getMiddle().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("1", LocationConstants.enemyMineralTriangle.getInner().unit().getPosition(), Color.WHITE, 10);
            Bot.DEBUG.debugTextOut("2", LocationConstants.enemyMineralTriangle.getOuter().unit().getPosition(), Color.WHITE, 10);
        }

        try {
            LocationConstants.enemyMineralTriangle.updateNodes();
            switch (scvRushStep) {
                case 0:
                    if (scvList == null) {
                        scvList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, GameCache.ccList.get(0).getPosition().toPoint2d(), 20.0F);
                    }

                    if (clusterTriangleNode(LocationConstants.myMineralTriangle)) {
                        ++scvRushStep;
                    }
                case 1:
                    if (clusterTriangleNode(LocationConstants.myMineralTriangle)) {
                        ++scvRushStep;
                    }
                case 2:
                    if (clusterTriangleNode(LocationConstants.enemyMineralTriangle)) {
                        ++scvRushStep;
                    }
                case 3:
                    UnitUtils.removeDeadUnits(scvList);
                    if (scvList.isEmpty()) {
                        ++scvRushStep;
                    }

                    attackingBase();
                case 4:
                    Switches.scvRushComplete = true;
            }
        } catch (Exception var4) {
            var4.printStackTrace();
        } finally {
            return scvRushStep == 4;
        }
    }

    private static void attackingBase() {
        if (UnitUtils.getEnemyUnitsOfTypes(UnitUtils.enemyCommandStructures).isEmpty()) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, Position.towards(LocationConstants.myMineralPos, LocationConstants.baseLocations.get(0), 2.0F), false).unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralPos, true);
            ++scvRushStep;
        } else {
            Unit enemyCommand = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyCommandStructures).get(0);
            List<UnitInPool> enemyWorkers = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, enemyCommand.getPosition().toPoint2d(), 10.0F);
            if (!((double) scvList.size() >= (double) enemyWorkers.size() * 1.5D) && !enemyWorkers.stream().noneMatch((enemy) -> {
                return UnitUtils.getDistance(enemy.unit(), LocationConstants.enemyMineralPos) < 4.0F;
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
                Iterator var2 = scvList.iterator();

                while (true) {
                    while (var2.hasNext()) {
                        UnitInPool scv = (UnitInPool) var2.next();
                        if (!Bot.OBS.getUnits(Alliance.ENEMY, (worker) -> {
                            return worker.unit().getType() == UnitUtils.enemyWorkerType && UnitUtils.getDistance(worker.unit(), scv.unit()) < 1.0F;
                        }).isEmpty()) {
                            Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.enemyMineralPos, false);
                        } else {
                            List<UnitInPool> nearbyWeakScvs = Bot.OBS.getUnits(Alliance.SELF, (weakScv) -> {
                                return weakScv.unit().getType() == Units.TERRAN_SCV && UnitUtils.getDistance(weakScv.unit(), scv.unit()) < 2.0F && weakScv.unit().getHealth().get() < weakScv.unit().getHealthMax().get();
                            });
                            if (GameCache.mineralBank > 0 && !nearbyWeakScvs.isEmpty()) {
                                Bot.ACTION.unitCommand(scv.unit(), Abilities.EFFECT_REPAIR, nearbyWeakScvs.get(0).unit(), false);
                            } else {
                                Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, enemyCommand, false);
                            }
                        }
                    }

                    isAttackingCommand = true;
                    break;
                }
            }

        }
    }

    public static void droneDrillMicro1(List<UnitInPool> enemyWorkers) {
        if (clusterNow) {
            if (isScvsClustered()) {
                clusterNow = false;
            } else {
                clusterUp();
            }
        }

        if (!clusterNow) {
            if (!isScvsTooFar() && isScvsOffCooldown()) {
                UnitInPool temp = getTargetWorker(enemyWorkers);
                Unit targetWorker = temp == null ? null : temp.unit();
                if (targetWorker != null) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, targetWorker, false);
                } else {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
                }
            } else {
                clusterNow = true;
                clusterUp();
            }
        }

    }

    public static void droneDrillMicro2() {
        List<Unit> attackScvs = new ArrayList();
        List<Unit> clusterScvs = new ArrayList();
        Iterator var2 = scvList.iterator();

        while (true) {
            while (var2.hasNext()) {
                UnitInPool scv = (UnitInPool) var2.next();
                if (isScvOffCooldown(scv.unit()) && !isScvTooFar(scv.unit())) {
                    attackScvs.add(scv.unit());
                } else {
                    clusterScvs.add(scv.unit());
                }
            }

            if (!attackScvs.isEmpty()) {
                Bot.ACTION.unitCommand(attackScvs, Abilities.ATTACK, LocationConstants.myMineralPos, false);
            }

            if (!clusterScvs.isEmpty()) {
                Bot.ACTION.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }

            return;
        }
    }

    public static void droneDrillMicro3(List<UnitInPool> enemyWorkers) {
        if (target != null) {
            List<Unit> attackScvs = new ArrayList();
            List<Unit> clusterScvs = new ArrayList();
            Iterator var3 = scvList.iterator();

            while (var3.hasNext()) {
                UnitInPool scv = (UnitInPool) var3.next();
                if (isScvOffCooldown(scv.unit())) {
                    attackScvs.add(scv.unit());
                } else {
                    clusterScvs.add(scv.unit());
                }
            }

            if (!attackScvs.isEmpty()) {
                Bot.ACTION.unitCommand(attackScvs, Abilities.ATTACK, target.unit(), false);
            }

            if (!clusterScvs.isEmpty()) {
                Bot.ACTION.unitCommand(clusterScvs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }
        } else if (isScvsOffCooldown()) {
            target = oneShotTarget(enemyWorkers);
            if (target != null) {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, target.unit(), false);
            } else {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, LocationConstants.myMineralPos, false);
            }
        } else {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
        }

    }

    private static void updateTarget() {
        if (target != null && (!target.isAlive() || UnitUtils.getDistance(target.unit(), LocationConstants.enemyMineralPos) > 2.5F)) {
            target = null;
        }

    }

    private static void droneDrillMicro4(List<UnitInPool> enemyWorkers) {
        if (DelayedAction.delayedActions.isEmpty()) {
            if (isScvsClustered()) {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
                long attackFrame = Bot.OBS.getGameLoop() + (long) (Strategy.SKIP_FRAMES * 3);
                scvList.stream().forEach((scv) -> {
                    DelayedAction.delayedActions.add(new DelayedAction(attackFrame, Abilities.ATTACK, scv, LocationConstants.myMineralPos));
                });
                long clusterFrame = attackFrame + (long) (Strategy.SKIP_FRAMES * 3);
                scvList.stream().forEach((scv) -> {
                    DelayedAction.delayedActions.add(new DelayedAction(clusterFrame, Abilities.SMART, scv, LocationConstants.enemyMineralTriangle.getMiddle()));
                });
            } else {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            }

        }
    }

    private static UnitInPool oneShotTarget(List<UnitInPool> enemyWorkers) {
        Iterator var1 = enemyWorkers.iterator();

        UnitInPool enemy;
        int numAttackers;
        do {
            if (!var1.hasNext()) {
                return null;
            }

            enemy = (UnitInPool) var1.next();
            UnitInPool finalEnemy = enemy;
            numAttackers = Bot.OBS.getUnits(Alliance.SELF, (scv) -> {
                return scv.unit().getType() == Units.TERRAN_SCV && (double) UnitUtils.getDistance(scv.unit(), finalEnemy.unit()) <= 0.9D;
            }).size();
        } while (!((float) (numAttackers * 5) >= enemy.unit().getHealth().get()));

        return enemy;
    }

    private static boolean isScvAttacking(Unit scv) {
        return !scv.getOrders().isEmpty() && scv.getOrders().get(0).getAbility() == Abilities.ATTACK;
    }

    private static boolean isScvsTooFar() {
        return scvList.stream().anyMatch((scv) -> {
            return isScvTooFar(scv.unit());
        });
    }

    private static boolean isScvTooFar(Unit scv) {
        return UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) > 1.5F;
    }

    private static void clusterUp() {
        Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
    }

    private static boolean isScvsClustered() {
        return scvList.stream().allMatch((scv) -> {
            return isByClusterPatch(scv.unit());
        });
    }

    private static boolean isByClusterPatch(Unit scv) {
        return (double) UnitUtils.getDistance(scv, LocationConstants.enemyMineralPos) < 1.4D;
    }

    private static boolean isScvsClustering() {
        return scvList.stream().anyMatch((scv) -> {
            return !scv.unit().getOrders().isEmpty() && scv.unit().getOrders().get(0).getTargetedUnitTag().isPresent() && scv.unit().getOrders().get(0).getTargetedUnitTag().equals(LocationConstants.enemyMineralTriangle.getMiddle().getTag());
        });
    }

    private static boolean isScvsOffCooldown() {
        return scvList.stream().allMatch((scv) -> {
            return isScvOffCooldown(scv.unit());
        });
    }

    private static boolean isOneShotAvailable() {
        return scvList.stream().filter((scv) -> {
            return isScvOffCooldown(scv.unit());
        }).count() >= 9L;
    }

    private static boolean isScvOffCooldown(Unit scv) {
        return scv.getWeaponCooldown().orElse(0.0F) == 0.0F;
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
        } while (!scvList.stream().allMatch((scv) -> {
            return (double) UnitUtils.getDistance(scv.unit(), finalEnemyWorker.unit()) < 0.8D;
        }));

        return enemyWorker;
    }

    public static boolean clusterTriangleNode(TriangleOfNodes triangle) {
        switch (clusterTriangleStep) {
            case 0:
                if (scvList.stream().filter((u) -> {
                    return (double) UnitUtils.getDistance(u.unit(), triangle.getInner().unit()) > 2.7D;
                }).count() > 2L) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getInner().unit(), false);
                } else {
                    ++clusterTriangleStep;
                }
                break;
            case 1:
                if (scvList.stream().filter((u) -> {
                    return (double) UnitUtils.getDistance(u.unit(), triangle.getOuter().unit()) > 2.7D;
                }).count() > 2L) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getOuter().unit(), false);
                } else {
                    ++clusterTriangleStep;
                }
                break;
            case 2:
                if (!scvList.stream().allMatch((u) -> {
                    return (double) UnitUtils.getDistance(u.unit(), triangle.getMiddle().unit()) > 1.4D;
                })) {
                    clusterTriangleStep = 0;
                    return true;
                }

                Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, triangle.getMiddle().unit(), false);
        }

        return false;
    }
}

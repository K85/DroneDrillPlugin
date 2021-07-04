package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.BuildManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.models.DelayedAction;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.IgnoredUnit;
import com.ketroc.terranbot.models.StructureScv;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;
import com.ketroc.terranbot.purchases.PurchaseUnit;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BunkerContain {
    public static final float BUNKER_RANGE = 7.5F;
    private static boolean isFirstScvSent;
    private static boolean isScoutScvsSent;
    private static int marinesNeeded;
    public static int proxyBunkerLevel;
    public static boolean scoutProxy;
    public static Point2d barracksPos;
    public static Point2d bunkerPos;
    public static List<UnitInPool> repairScvList = new ArrayList();
    public static List<UnitInPool> scoutScvs = new ArrayList();
    public static UnitInPool barracks;
    public static UnitInPool factory;
    public static UnitInPool bunker;
    public static Point2d behindBunkerPos;
    public static Point2d siegeTankPos;
    public static final Set<Units> defenders;
    public static final Set<Units> repairTargets;
    private static boolean isBarracksSentHome;

    public static void onGameStart() {
        if (proxyBunkerLevel != 0) {
            barracksPos = LocationConstants.proxyBarracksPos;
            bunkerPos = LocationConstants.proxyBunkerPos;
            behindBunkerPos = getBehindBunkerPos();
            siegeTankPos = Position.towards(behindBunkerPos, bunkerPos, -1.2F);
            marinesNeeded = LocationConstants.opponentRace == Race.TERRAN ? 5 : 4;
            scoutProxy = LocationConstants.opponentRace == Race.TERRAN;
        }
    }

    public static void addRepairScv(UnitInPool scv) {
        repairScvList.add(scv);
        Ignored.add(new IgnoredUnit(scv.getTag()));
    }

    public static void removeRepairScv(UnitInPool scv) {
        repairScvList.remove(scv);
        Ignored.remove(scv.getTag());
    }

    private static Point2d getBehindBunkerPos() {
        Point2d enemyMain = (Point2d) LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1);
        Point2d enemyNat = (Point2d) LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 2);
        Point2d betweenMainAndNat = Position.towards(enemyNat, enemyMain, (float) enemyMain.distance(enemyNat) / 4.0F);
        return Position.towards(bunkerPos, betweenMainAndNat, -2.0F);
    }

    public static void onStep() {
        if (proxyBunkerLevel != 0) {
            sendFirstScv();
            if (scoutProxy) {
                sendScoutScvs();
            }

            updateScvs();
            List allFactories;
            if (barracks == null) {
                allFactories = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BARRACKS, barracksPos, 1.0F);
                if (!allFactories.isEmpty()) {
                    onBarracksStarted((UnitInPool) allFactories.get(0));
                }
            } else if (!barracks.isAlive()) {
                if (bunker == null || (Integer) bunker.unit().getCargoSpaceTaken().orElse(0) < 1) {
                    abandonProxy();
                }
            } else if (!(Boolean) barracks.unit().getActive().orElse(true)) {
                if (getMarineCount() < marinesNeeded) {
                    buildMarines();
                    return;
                }

                sendBarracksHome();
            }

            if (bunker == null) {
                allFactories = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BUNKER, bunkerPos, 1.0F);
                if (!allFactories.isEmpty()) {
                    onBunkerStarted((UnitInPool) allFactories.get(0));
                }
            } else {
                if (!bunker.isAlive()) {
                    abandonProxy();
                    return;
                }

                bunkerTargetting();
            }

            marineMicro();
            if (proxyBunkerLevel == 2) {
                if (factory == null) {
                    allFactories = Bot.OBS.getUnits(Alliance.SELF, (factory) -> {
                        return factory.unit().getType() == Units.TERRAN_FACTORY;
                    });
                    if (!allFactories.isEmpty()) {
                        onFactoryStarted((UnitInPool) allFactories.get(0));
                    }
                } else if (!(Boolean) factory.unit().getActive().orElse(true)) {
                    if (UnitUtils.getNumUnits(UnitUtils.SIEGE_TANK_TYPE, false) < 2) {
                        buildTanks();
                    } else if (!(Boolean) factory.unit().getFlying().orElse(true)) {
                        BuildManager.liftFactory();
                    }
                }

                tankMicro();
                siegeTankTargetting();
            }

        }
    }

    private static void bunkerTargetting() {
        if ((Integer) bunker.unit().getCargoSpaceTaken().orElse(0) != 0) {
            List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, (enemy) -> {
                return UnitUtils.getDistance(enemy.unit(), bunker.unit()) < 7.5F;
            });
            UnitInPool target = selectTarget(enemiesInRange);
            if (target != null) {
                Bot.ACTION.unitCommand(bunker.unit(), Abilities.ATTACK, target.unit(), false);
            }

        }
    }

    private static void siegeTankTargetting() {
        Iterator var0 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK_SIEGED).iterator();

        while (var0.hasNext()) {
            Unit tank = (Unit) var0.next();
            if (!((Float) tank.getWeaponCooldown().orElse(1.0F) > 0.05F)) {
                List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, (enemy) -> {
                    float distance = UnitUtils.getDistance(enemy.unit(), tank);
                    return distance > 2.0F && distance < 13.0F && enemy.unit().getDisplayType() == DisplayType.VISIBLE;
                });
                UnitInPool target = selectTarget(enemiesInRange);
                if (target != null) {
                    Bot.ACTION.unitCommand(tank, Abilities.ATTACK, target.unit(), false);
                }
            }
        }

    }

    private static UnitInPool selectTarget(List<UnitInPool> enemiesInRange) {
        UnitInPool bestTarget = null;
        float bestTargetHP = Float.MAX_VALUE;
        Iterator var3 = enemiesInRange.iterator();

        while (var3.hasNext()) {
            UnitInPool enemy = (UnitInPool) var3.next();
            float enemyHP = (Float) enemy.unit().getHealth().orElse(0.0F) + (Float) enemy.unit().getShield().orElse(0.0F);
            if (enemyHP < bestTargetHP && !enemy.unit().getBuffs().contains(Buffs.PROTECTIVE_BARRIER)) {
                bestTargetHP = enemyHP;
                bestTarget = enemy;
            }
        }

        return bestTarget;
    }

    private static void buildTanks() {
        if (!GameCache.factoryList.isEmpty()) {
            Unit factory = ((UnitInPool) GameCache.factoryList.get(0)).unit();
            if (!(Boolean) factory.getActive().get()) {
                if (factory.getAddOnTag().isPresent()) {
                    if (GameCache.siegeTankList.size() < Math.min(12, 2 * (Base.numMyBases() - 1)) && UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                        Bot.ACTION.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                        Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                    }
                } else if (!(Boolean) factory.getFlying().orElse(true) && !Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    BansheeBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                    Bot.ACTION.unitCommand(factory, Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
                }
            }
        }

    }

    private static void sendBarracksHome() {
        if (!isBarracksSentHome) {
            if (!barracks.unit().getOrders().isEmpty() && ((UnitOrder) barracks.unit().getOrders().get(0)).getAbility() == Abilities.TRAIN_MARINE) {
                Bot.ACTION.unitCommand(barracks.unit(), Abilities.CANCEL_LAST, false);
            }

            DelayedAction.delayedActions.add(new DelayedAction(Bot.OBS.getGameLoop() + 2L, Abilities.LIFT_BARRACKS, barracks));
            DelayedAction.delayedActions.add(new DelayedAction(1, Abilities.LAND, barracks, (Point2d) LocationConstants._3x3Structures.remove(0)));
            isBarracksSentHome = true;
        }

    }

    private static void sendFirstScv() {
        if (!isFirstScvSent && Bot.OBS.getGameLoop() >= 222L) {
            Unit firstScv = ((UnitInPool) repairScvList.get(0)).unit();
            if (UnitUtils.isCarryingResources(firstScv)) {
                Bot.ACTION.unitCommand(firstScv, Abilities.HARVEST_RETURN, false).unitCommand(firstScv, Abilities.MOVE, barracksPos, true).unitCommand(firstScv, Abilities.HOLD_POSITION, true);
            } else {
                Bot.ACTION.unitCommand(firstScv, Abilities.MOVE, barracksPos, false).unitCommand(firstScv, Abilities.HOLD_POSITION, true);
            }

            isFirstScvSent = true;
        }

    }

    private static void sendScoutScvs() {
        List enemyBarracks;
        if (!isScoutScvsSent && Bot.OBS.getGameLoop() >= 864L) {
            enemyBarracks = WorkerManager.getAvailableScvs((Point2d) LocationConstants.baseLocations.get(0), 10);
            scoutScvs = enemyBarracks.subList(0, 2);
            Bot.ACTION.unitCommand(((UnitInPool) scoutScvs.get(0)).unit(), Abilities.MOVE, (Point2d) LocationConstants.baseLocations.get(2), false).unitCommand(((UnitInPool) scoutScvs.get(0)).unit(), Abilities.MOVE, (Point2d) LocationConstants.baseLocations.get(3), true).unitCommand(((UnitInPool) scoutScvs.get(0)).unit(), Abilities.HOLD_POSITION, true);
            Bot.ACTION.unitCommand(((UnitInPool) scoutScvs.get(1)).unit(), Abilities.MOVE, (Point2d) LocationConstants.baseLocations.get(4), false).unitCommand(((UnitInPool) scoutScvs.get(1)).unit(), Abilities.HOLD_POSITION, true);
            isScoutScvsSent = true;
        } else if (!scoutScvs.isEmpty()) {
            if (Bot.OBS.getGameLoop() < 4032L) {
                enemyBarracks = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BARRACKS);
                if (!enemyBarracks.isEmpty()) {
                    List<UnitInPool> enemyScv = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, Units.TERRAN_SCV, ((UnitInPool) enemyBarracks.get(0)).unit().getPosition().toPoint2d(), 5.0F);
                    if (!enemyScv.isEmpty()) {
                        Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.ATTACK, ((UnitInPool) enemyScv.get(0)).unit(), false).unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.HOLD_POSITION, true);
                    } else {
                        Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.ATTACK, ((UnitInPool) enemyBarracks.get(0)).unit(), false).unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.HOLD_POSITION, true);
                    }
                } else if (scoutScvs.stream().anyMatch((scv) -> {
                    return !scv.unit().getOrders().isEmpty() && ((UnitOrder) scv.unit().getOrders().get(0)).getAbility() == Abilities.ATTACK;
                })) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(scoutScvs), Abilities.MOVE, behindBunkerPos, false);
                }
            }

            scoutScvs.stream().filter((scv) -> {
                return UnitUtils.getDistance(scv.unit(), behindBunkerPos) < 2.0F;
            }).forEach((scv) -> {
                addRepairScv(scv);
            });
            scoutScvs.removeIf((scv) -> {
                return UnitUtils.getDistance(scv.unit(), behindBunkerPos) < 2.0F;
            });
            scoutScvs.stream().map(UnitInPool::unit).filter((scv) -> {
                return !scv.getOrders().isEmpty() && ((UnitOrder) scv.getOrders().get(0)).getAbility() == Abilities.HOLD_POSITION;
            }).forEach((scv) -> {
                Bot.ACTION.unitCommand(scv, Abilities.MOVE, behindBunkerPos, false);
            });
        }

    }

    private static void marineMicro() {
        if (LocationConstants.opponentRace == Race.TERRAN && getMarineCount() == 1) {
            Bot.ACTION.unitCommand(barracks.unit(), Abilities.RALLY_BUILDING, behindBunkerPos, false);
        } else {
            boolean isBunkerComplete = bunker != null && bunker.unit().getBuildProgress() == 1.0F;
            List<Unit> proxyMarines = (List) UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).stream().filter((marinex) -> {
                return UnitUtils.getDistance(marinex, bunkerPos) < 60.0F;
            }).collect(Collectors.toList());
            Iterator var2 = proxyMarines.iterator();

            while (true) {
                while (var2.hasNext()) {
                    Unit marine = (Unit) var2.next();
                    List<Unit> allVisibleDefenders = UnitUtils.getVisibleEnemyUnitsOfType(defenders);
                    Unit closestEnemy = (Unit) allVisibleDefenders.stream().filter((enemy) -> {
                        return UnitUtils.getDistance(enemy, marine) <= 5.0F;
                    }).findFirst().orElse( null);
                    if (isBunkerComplete && UnitUtils.getDistance(marine, bunker.unit()) < 6.0F) {
                        if (bunker.unit().getPassengers().stream().anyMatch((unitInBunker) -> {
                            return unitInBunker.getType() == Units.TERRAN_SCV;
                        })) {
                            Bot.ACTION.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL, false);
                        }

                        Bot.ACTION.unitCommand(marine, Abilities.SMART, bunker.unit(), false);
                    } else if (closestEnemy == null) {
                        Bot.ACTION.unitCommand(marine, Abilities.MOVE, behindBunkerPos, false);
                    } else if ((Float) marine.getWeaponCooldown().orElse(1.0F) == 0.0F) {
                        Bot.ACTION.unitCommand(marine, Abilities.ATTACK, behindBunkerPos, false);
                    } else if (isBunkerComplete) {
                        Point2d retreatPos = Position.towards(marine.getPosition().toPoint2d(), closestEnemy.getPosition().toPoint2d(), -10.0F);
                        Bot.ACTION.unitCommand(marine, Abilities.MOVE, retreatPos, false);
                    } else {
                        Bot.ACTION.unitCommand(marine, Abilities.MOVE, behindBunkerPos, false);
                    }
                }

                return;
            }
        }
    }

    private static void tankMicro() {
        Iterator var0 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK).iterator();

        while (var0.hasNext()) {
            Unit tank = (Unit) var0.next();
            List<Unit> allVisibleDefenders = UnitUtils.getVisibleEnemyUnitsOfType(defenders);
            Unit closestEnemy = (Unit) allVisibleDefenders.stream().filter((enemy) -> {
                return UnitUtils.getDistance(enemy, tank) <= 7.0F;
            }).findFirst().orElse( null);
            if ((double) UnitUtils.getDistance(tank, siegeTankPos) < 1.5D) {
                Bot.ACTION.unitCommand(tank, Abilities.MORPH_SIEGE_MODE, false);
            } else if (closestEnemy == null) {
                Bot.ACTION.unitCommand(tank, Abilities.ATTACK, siegeTankPos, false);
            } else if ((Float) tank.getWeaponCooldown().orElse(1.0F) == 0.0F) {
                Bot.ACTION.unitCommand(tank, Abilities.ATTACK, siegeTankPos, false);
            } else {
                Point2d retreatPos = Position.towards(tank.getPosition().toPoint2d(), closestEnemy.getPosition().toPoint2d(), -10.0F);
                Bot.ACTION.unitCommand(tank, Abilities.MOVE, retreatPos, false);
            }
        }

    }

    private static void buildMarines() {
        if (UnitUtils.canAfford(Units.TERRAN_MARINE)) {
            Bot.ACTION.unitCommand(barracks.unit(), Abilities.TRAIN_MARINE, false);
            Cost.updateBank(Units.TERRAN_MARINE);
        }

    }

    private static int getMarineCount() {
        int marineCount = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).size();
        marineCount += UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER).stream().mapToInt((bunker) -> {
            return (Integer) bunker.getCargoSpaceTaken().orElse(0);
        }).sum();
        return marineCount;
    }

    private static void updateScvs() {
        replaceDeadScvs();
        giveScvsCommands();
    }

    private static void giveScvsCommands() {
        scvRepairMicro();
    }

    private static void scvRepairMicro() {
        List<Unit> availableScvs = getAvailableRepairScvs();
        if (!availableScvs.isEmpty()) {
            List<UnitInPool> injuredUnits = (List) Bot.OBS.getUnits(Alliance.SELF, (repairTarget) -> {
                return repairTargets.contains(repairTarget.unit().getType()) && repairTarget.unit().getBuildProgress() == 1.0F && (Float) repairTarget.unit().getHealth().orElse(0.0F) < (Float) repairTarget.unit().getHealthMax().orElse(0.0F) && (repairTarget.unit().getType() != Units.TERRAN_SCV || repairScvList.contains(repairTarget)) && UnitUtils.getDistance(repairTarget.unit(), bunkerPos) < 8.0F;
            }).stream().sorted(Comparator.comparing((unit) -> {
                return UnitUtils.getHealthPercentage(unit.unit());
            })).collect(Collectors.toList());
            if (injuredUnits.isEmpty()) {
                List<Unit> scvsToMove = (List) availableScvs.stream().filter((scv) -> {
                    return scv.getOrders().isEmpty();
                }).filter((scv) -> {
                    return UnitUtils.getDistance(scv, behindBunkerPos) > 2.0F;
                }).collect(Collectors.toList());
                if (!scvsToMove.isEmpty()) {
                    Bot.ACTION.unitCommand(scvsToMove, Abilities.MOVE, behindBunkerPos, false);
                }
            } else {
                UnitInPool injuredScv = null;
                Iterator var3 = injuredUnits.iterator();

                while (var3.hasNext()) {
                    UnitInPool injuredUnit = (UnitInPool) var3.next();
                    if (injuredScv != null) {
                        Bot.ACTION.unitCommand(injuredScv.unit(), Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                        break;
                    }

                    if (!availableScvs.remove(injuredUnit)) {
                        Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                        break;
                    }

                    injuredScv = injuredUnit;
                    if (!availableScvs.isEmpty()) {
                        Bot.ACTION.unitCommand(availableScvs, Abilities.EFFECT_REPAIR, injuredUnit.unit(), false);
                    }
                }
            }

        }
    }

    private static List<Unit> getAvailableRepairScvs() {
        return (List) repairScvList.stream().map(UnitInPool::unit).filter((scv) -> {
            return scv.getOrders().isEmpty() || !((UnitOrder) scv.getOrders().get(0)).getAbility().toString().contains("BUILD");
        }).collect(Collectors.toList());
    }

    private static void replaceDeadScvs() {
        for (int i = 0; i < repairScvList.size(); ++i) {
            UnitInPool oldScv = (UnitInPool) repairScvList.get(i);
            if (!oldScv.isAlive() || (Float) oldScv.unit().getHealth().orElse(45.0F) < 10.0F) {
                if (oldScv.isAlive()) {
                    Bot.ACTION.unitCommand(oldScv.unit(), Abilities.SMART, ((Base) GameCache.baseList.get(0)).getRallyNode(), false);
                }

                UnitInPool newScv = (UnitInPool) WorkerManager.getAvailableScvs((Point2d) LocationConstants.baseLocations.get(0), 10).get(0);
                Ignored.remove(oldScv.getTag());
                Ignored.add(new IgnoredUnit(newScv.getTag()));
                repairScvList.set(i, newScv);
                if (isFactoryScv(oldScv.getTag())) {
                    setFactoryScv();
                }

                Bot.ACTION.unitCommand(newScv.unit(), Abilities.MOVE, behindBunkerPos, false);
            }
        }

    }

    public static void addAnotherRepairScv() {
        UnitInPool newScv = (UnitInPool) WorkerManager.getAvailableScvs((Point2d) LocationConstants.baseLocations.get(0), 10).get(0);
        addRepairScv(newScv);
        Bot.ACTION.unitCommand(newScv.unit(), Abilities.MOVE, behindBunkerPos, false);
    }

    private static void abandonProxy() {
        for (int i = 0; i < StructureScv.scvBuildingList.size(); ++i) {
            StructureScv structureScv = (StructureScv) StructureScv.scvBuildingList.get(i);
            if (structureScv.structurePos.distance(LocationConstants.REPAIR_BAY) > 50.0D) {
                switch (structureScv.structureType) {
                    case TERRAN_FACTORY:
                        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                        break;
                    case TERRAN_BARRACKS:
                        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS));
                }

                StructureScv.remove(structureScv);
                --i;
            }
        }

        if (barracks.isAlive()) {
            if (barracks.unit().getBuildProgress() < 1.0F) {
                Bot.ACTION.unitCommand(barracks.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS));
            } else {
                sendBarracksHome();
            }
        }

        if (bunker.isAlive()) {
            if (bunker.unit().getBuildProgress() < 1.0F) {
                Bot.ACTION.unitCommand(bunker.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
            } else {
                Bot.ACTION.unitCommand(bunker.unit(), Abilities.UNLOAD_ALL_BUNKER, false);
                DelayedAction.delayedActions.add(new DelayedAction(Bot.OBS.getGameLoop() + 2L, Abilities.EFFECT_SALVAGE, bunker));
            }
        } else {
            List<Unit> marines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
            Bot.ACTION.unitCommand(marines, Abilities.ATTACK, LocationConstants.insideMainWall, false);
        }

        if (!repairScvList.isEmpty()) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(repairScvList), Abilities.STOP, false);
            repairScvList.forEach((scv) -> {
                Ignored.remove(scv.getTag());
            });
        }

        proxyBunkerLevel = 0;
    }

    private static Point2d calcTurretPosition() {
        boolean xBigger = Math.abs(bunkerPos.getX() - behindBunkerPos.getX()) > Math.abs(bunkerPos.getY() - behindBunkerPos.getY());
        Point2d testPoint;
        if (xBigger) {
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 0.5F, 2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 1.5F, 2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, -0.5F, 2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5F, 2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5F, -1.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 1.5F, -2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 0.5F, -2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, -0.5F, -2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5F, -2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
        } else {
            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5F, 0.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5F, 1.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5F, -0.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, 2.5F, 2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, -1.5F, 2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5F, 1.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5F, 0.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5F, -0.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }

            testPoint = Position.towards(bunkerPos, behindBunkerPos, -2.5F, 2.5F);
            if (Bot.QUERY.placement(Abilities.BUILD_MISSILE_TURRET, testPoint)) {
                return testPoint;
            }
        }

        System.out.println("--- ERROR ---- NO PROXY TURRET POSITION FOUND -----");
        return null;
    }

    private static void onBarracksStarted(UnitInPool bar) {
        barracks = bar;
        Bot.ACTION.unitCommand(barracks.unit(), Abilities.RALLY_BUILDING, LocationConstants.BUNKER_NATURAL, false);
    }

    public static void onBarracksComplete() {
        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(UnitUtils.getClosestUnitOfType(Alliance.SELF, Units.TERRAN_SCV, bunkerPos), Units.TERRAN_BUNKER, LocationConstants.proxyBunkerPos));
    }

    private static void onBunkerStarted(UnitInPool bunk) {
        bunker = bunk;
        Bot.ACTION.unitCommand(bunker.unit(), Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
        if (!scoutProxy) {
            addAnotherRepairScv();
        }

        setFactoryScv();
    }

    private static boolean isFactoryScv(Tag scv) {
        if (proxyBunkerLevel == 2 && Purchase.isStructureQueued(Units.TERRAN_FACTORY)) {
            Iterator var1 = BansheeBot.purchaseQueue.iterator();

            while (var1.hasNext()) {
                Purchase purchase = (Purchase) var1.next();
                if (purchase instanceof PurchaseStructure) {
                    PurchaseStructure p = (PurchaseStructure) purchase;
                    if (p.getStructureType() == Units.TERRAN_FACTORY) {
                        return p.getScv().getTag().equals(scv);
                    }
                }
            }
        }

        return false;
    }

    private static void setFactoryScv() {
        if (proxyBunkerLevel == 2 && Purchase.isStructureQueued(Units.TERRAN_FACTORY)) {
            UnitInPool factoryScv = (UnitInPool) repairScvList.get(0);
            Iterator var1 = BansheeBot.purchaseQueue.iterator();

            while (var1.hasNext()) {
                Purchase purchase = (Purchase) var1.next();
                if (purchase instanceof PurchaseStructure) {
                    PurchaseStructure p = (PurchaseStructure) purchase;
                    if (p.getStructureType() == Units.TERRAN_FACTORY) {
                        p.setScv(factoryScv.unit());
                        Bot.ACTION.unitCommand(factoryScv.unit(), Abilities.MOVE, p.getPosition(), false).unitCommand(factoryScv.unit(), Abilities.HOLD_POSITION, true);
                        break;
                    }
                }
            }
        }

    }

    public static void onBunkerComplete() {
        Bot.ACTION.unitCommand(bunker.unit(), Abilities.RALLY_BUILDING, behindBunkerPos, false);
    }

    private static void onFactoryStarted(UnitInPool fact) {
        factory = fact;
        Bot.ACTION.unitCommand(factory.unit(), Abilities.RALLY_BUILDING, siegeTankPos, false);
        WorkerManager.scvsPerGas = 2;
    }

    public static void onFactoryComplete() {
        BansheeBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
    }

    public static void onTechLabComplete() {
        BansheeBot.purchaseQueue.addFirst(new PurchaseUnit(Units.TERRAN_SIEGE_TANK, factory.unit()));
    }

    public static void onFactoryLift() {
        BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        BansheeBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(((Unit) UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ENGINEERING_BAY).get(0)).getTag())));
    }

    public static void onEngineeringBayComplete(UnitInPool engBay) {
        BansheeBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, engBay));
        List<Unit> availableScvs = getAvailableRepairScvs();
        Point2d turretPos = calcTurretPosition();
        if (turretPos != null) {
            if (!availableScvs.isEmpty()) {
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure((Unit) availableScvs.get(0), Units.TERRAN_MISSILE_TURRET, turretPos));
            } else {
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turretPos));
            }
        }

    }

    static {
        defenders = Set.of(Units.PROTOSS_PROBE, Units.PROTOSS_ZEALOT);
        repairTargets = Set.of(Units.TERRAN_SCV, Units.TERRAN_BUNKER, Units.TERRAN_SIEGE_TANK_SIEGED, Units.TERRAN_SIEGE_TANK, Units.TERRAN_MISSILE_TURRET);
    }
}

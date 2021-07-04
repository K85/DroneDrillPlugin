package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat.Channel;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.data.Weapon.TargetType;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.strategies.DroneRush;
import com.ketroc.terranbot.strategies.MapNameTransfer;
import com.ketroc.terranbot.strategies.ScvRush;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class DroneDrill extends Bot {
    public static int droneRushBuildStep;
    public static int mutaRushBuildStep;
    public static int curMinerals;
    public static int curGas;
    public static boolean eggRallyComplete;
    public static int baseScoutIndex = 2;
    public static Unit geyser1;
    public static Unit geyser2;
    public static List<UnitInPool> availableDrones;
    public static UnitInPool mainHatch;
    public static List<Unit> larvaList;
    public static List<UnitInPool> lateDrones = new ArrayList();
    public static List<Abilities> mutasBuildOrder;

    static {
        mutasBuildOrder = new ArrayList(List.of(Abilities.BUILD_SPAWNING_POOL, Abilities.BUILD_EXTRACTOR, Abilities.BUILD_EXTRACTOR, Abilities.MORPH_LAIR, Abilities.BUILD_SPIRE));
    }

    public DroneDrill(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }

    public static UnitInPool getClosest(List<UnitInPool> unitList, Point2d pos) {
        return unitList.stream().min(Comparator.comparing((u) -> {
            return UnitUtils.getDistance(u.unit(), pos);
        })).orElse(unitList.get(0));
    }

    public static List<UnitInPool> getAvailableDrones() {
        return Bot.OBS.getUnits(Alliance.SELF, (drone) -> {
            return drone.unit().getType() == Units.ZERG_DRONE && (drone.unit().getOrders().isEmpty() || WorkerManager.isMiningMinerals(drone)) && (DroneRush.droneList == null || !DroneRush.droneList.contains(drone));
        });
    }

    public static boolean isProducing(Abilities training) {
        Iterator var1 = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EGG).iterator();

        Unit egg;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            egg = (Unit) var1.next();
        } while (egg.getOrders().get(0).getAbility() != training);

        return true;
    }

    public void onAlert(Alert alert) {
    }

    public void onGameStart() {
        try {
            super.onGameStart();
            Strategy.SKIP_FRAMES = Bot.isRealTime ? 6 : 2;

            // 设置地图名
            LocationConstants.MAP = MapNameTransfer.getTransferredMapName(OBS.getGameInfo().getMapName());

            LocationConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream().filter((playerInfo) -> {
                return playerInfo.getPlayerId() != Bot.OBS.getPlayerId();
            }).findFirst().get().getRequestedRace();
            mainHatch = OBS.getUnits(Alliance.SELF, (hatch) -> {
                return hatch.unit().getType() == Units.ZERG_HATCHERY;
            }).get(0);
            LocationConstants.onGameStart(mainHatch);
            DebugHelper.onGameStart();
            GameCache.onStep();
            LocationConstants.setRepairBayLocation();
            Bot.OBS.getUnitTypeData(false).forEach((unitType, unitTypeData) -> {
                unitTypeData.getAbility().ifPresent((ability) -> {
                    if (ability instanceof Abilities && unitType instanceof Units) {
                        abilityToUnitType.put((Abilities) ability, (Units) unitType);
                    }

                });
            });
            Bot.OBS.getUpgradeData(false).forEach((upgrade, upgradeData) -> {
                upgradeData.getAbility().ifPresent((ability) -> {
                    if (ability instanceof Abilities && upgrade instanceof Upgrades) {
                        switch ((Abilities) ability) {
                            case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1_V2:
                                ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1;
                                break;
                            case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2_V2:
                                ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2;
                                break;
                            case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3_V2:
                                ability = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3;
                        }

                        abilityToUpgrade.put((Abilities) ability, (Upgrades) upgrade);
                    }

                });
            });
            ACTION.sendActions();
            geyser1 = GameCache.baseList.get(0).getGases().get(0).getGeyser();
            geyser2 = GameCache.baseList.get(0).getGases().get(1).getGeyser();
        } catch (Exception var2) {
            var2.printStackTrace();
        }

    }

    public void onStep() {
        availableDrones = getAvailableDrones();

        try {
            if (OBS.getGameLoop() % (long) Strategy.SKIP_FRAMES == 0L) {
                if (OBS.getGameLoop() == (long) Strategy.SKIP_FRAMES) {
                    ACTION.sendChat("Last updated: Sept 24, 2020", Channel.BROADCAST);
                }

                Ignored.onStep();
                EnemyScan.onStep();
                GameCache.onStep();
                Switches.onStep();
                DelayedAction.onStep();
                DelayedChat.onStep();
                FlyingCC.onStep();
                StructureScv.checkScvsActivelyBuilding();
                if (!Switches.scvRushComplete) {
                    Switches.scvRushComplete = ScvRush.onStep();
                }

                GameCache.baseList.stream().forEach(Base::onStep);
                curMinerals = Bot.OBS.getMinerals();
                curGas = Bot.OBS.getVespene();
                larvaList = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_LARVA);
                if (LocationConstants.MAP.equals("Pillars of Gold LE")) {
                    droneRushBuildStep = -1;
                    this.mutaRushBuild();
                }

                switch (droneRushBuildStep) {
                    case 0:
                        if (Bot.OBS.getFoodUsed() == Bot.OBS.getFoodCap()) {
                            ++droneRushBuildStep;
                        }
                        break;
                    case 1:
                        if (curMinerals >= 125) {
                            UnitInPool closestDrone = getClosest(availableDrones, geyser1.getPosition().toPoint2d());
                            availableDrones.remove(closestDrone);
                            Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser1, false);
                            closestDrone = getClosest(availableDrones, geyser2.getPosition().toPoint2d());
                            availableDrones.remove(closestDrone);
                            Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser2, false);
                            ++droneRushBuildStep;
                        }
                        break;
                    case 2:
                        if (Bot.OBS.getFoodUsed() == Bot.OBS.getFoodCap() && Bot.OBS.getUnits(Alliance.SELF, (gas) -> {
                            return gas.unit().getType() == Units.ZERG_EXTRACTOR;
                        }).size() == 2) {
                            ++droneRushBuildStep;
                        }
                        break;
                    case 3:
                        List<Unit> extractors = UnitUtils.toUnitList(Bot.OBS.getUnits(Alliance.SELF, (gas) -> {
                            return gas.unit().getType() == Units.ZERG_EXTRACTOR;
                        }));
                        Bot.ACTION.unitCommand(extractors, Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                        ++droneRushBuildStep;
                        break;
                    case 4:
                        if (!eggRallyComplete && Bot.OBS.getFoodUsed() == 16) {
                            List<Unit> eggs = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EGG);
                            if (!eggs.isEmpty()) {
                                Bot.ACTION.unitCommand(eggs, Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), false);
                            }

                            eggRallyComplete = true;
                        }

                        this.lateDronesHitBarracksScv();
                        DroneRush.onStep();
                        this.mutaRushBuild();
                }

                if (Bot.OBS.getMinerals() >= 50 && Bot.OBS.getFoodWorkers() < 14 && !larvaList.isEmpty()) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_DRONE, false);
                }

                ACTION.sendActions();
            }
        } catch (Exception var3) {
            System.out.println("Bot.onStep() error At game frame: " + OBS.getGameLoop());
            var3.printStackTrace();
        }

    }

    private void mutaRushBuild() {
        if (mutaRushBuildStep >= 3 && curMinerals >= 100 && Bot.OBS.getFoodUsed() + 6 > Bot.OBS.getFoodCap() && !isProducing(Abilities.TRAIN_OVERLORD) && !larvaList.isEmpty()) {
            Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
        }

        UnitInPool closestDrone;
        if (Bot.OBS.getFoodWorkers() > 6 && !availableDrones.isEmpty()) {
            Iterator var1 = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_EXTRACTOR).iterator();

            while (var1.hasNext()) {
                Unit extractor = (Unit) var1.next();
                if (extractor.getBuildProgress() == 1.0F && extractor.getAssignedHarvesters().orElse(3) < 3) {
                    closestDrone = getClosest(availableDrones, extractor.getPosition().toPoint2d());
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.SMART, extractor, false);
                    availableDrones.remove(closestDrone);
                    break;
                }
            }
        }


        switch (mutaRushBuildStep) {
            case 0:
                if (Bot.OBS.getFoodWorkers() > 6 && curMinerals >= 75) {
                    closestDrone = getClosest(availableDrones, geyser1.getPosition().toPoint2d());
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser1, false);
                    availableDrones.remove(closestDrone);
                    ++mutaRushBuildStep;
                }
                break;
            case 1:
                if (Bot.OBS.getFoodWorkers() > 6 && curMinerals >= 75) {
                    closestDrone = getClosest(availableDrones, geyser2.getPosition().toPoint2d());
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_EXTRACTOR, geyser2, false);
                    availableDrones.remove(closestDrone);
                    ++mutaRushBuildStep;
                }
                break;
            case 2:
                if (curMinerals >= 200) {
                    Point2d poolPos = Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), -5.0F);
                    closestDrone = getClosest(availableDrones, poolPos);
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_SPAWNING_POOL, poolPos, false);
                    availableDrones.remove(closestDrone);
                    ++mutaRushBuildStep;
                }
                break;
            case 3:
                if (curMinerals >= 150 && curGas >= 100 && !UnitUtils.getFriendlyUnitsOfType(Units.ZERG_SPAWNING_POOL).isEmpty()) {
                    Bot.ACTION.unitCommand(mainHatch.unit(), Abilities.MORPH_LAIR, false);
                    ++mutaRushBuildStep;
                }

                if (curMinerals >= 251 && larvaList.size() >= 3) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                break;
            case 4:
                List<Unit> lair = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_LAIR);
                if (!lair.isEmpty() && curMinerals >= 200 && curGas >= 200) {
                    Point2d spirePos = Position.rotate(Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), -5.0F), GameCache.baseList.get(0).getCcPos(), 60.0D);
                    closestDrone = getClosest(availableDrones, spirePos);
                    Bot.ACTION.unitCommand(closestDrone.unit(), Abilities.BUILD_SPIRE, spirePos, false);
                    availableDrones.remove(closestDrone);
                    mainHatch = Bot.OBS.getUnit(lair.get(0).getTag());
                    ++mutaRushBuildStep;
                }

                if (curMinerals >= 301 && larvaList.size() >= 3) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }
                break;
            case 5:
                if (curMinerals >= curGas && larvaList.size() >= 3 && Bot.OBS.getUnits(Alliance.SELF, (spire) -> {
                    return spire.unit().getType() == Units.ZERG_SPIRE && spire.unit().getBuildProgress() > 0.7F;
                }).isEmpty()) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_OVERLORD, false);
                }

                if (curMinerals >= 100 && curGas >= 100 && !larvaList.isEmpty()) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_MUTALISK, false);
                }

                if (curGas < 100) {
                    LocationConstants.baseAttackIndex = LocationConstants.baseLocations.size() - 2;
                    ++mutaRushBuildStep;
                }
                break;
            case 6:
                if (curMinerals >= 100 && curGas >= 100 && !larvaList.isEmpty()) {
                    Bot.ACTION.unitCommand(larvaList.remove(0), Abilities.TRAIN_MUTALISK, false);
                }

                List<Unit> mutas = UnitUtils.getFriendlyUnitsOfType(Units.ZERG_MUTALISK);
                List<UnitInPool> enemyAA = Bot.OBS.getUnits(Alliance.ENEMY, (enemy) -> {
                    return Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType()).getWeapons().stream().anyMatch((weapon) -> {
                        return weapon.getTargetType() == TargetType.AIR || weapon.getTargetType() == TargetType.ANY;
                    });
                });
                if (!mutas.isEmpty()) {
                    if (!enemyAA.isEmpty()) {
                        Bot.ACTION.unitCommand(mutas, Abilities.ATTACK, enemyAA.get(0).unit(), false);
                    } else {
                        List<UnitInPool> enemies = Bot.OBS.getUnits(Alliance.ENEMY);
                        if (!enemies.isEmpty()) {
                            Bot.ACTION.unitCommand(mutas, Abilities.ATTACK, enemies.get(0).unit().getPosition().toPoint2d(), false);
                        } else if (Switches.finishHim) {
                            ArmyManager.spreadArmy(mutas);
                        } else {
                            Point2d attackPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
                            Point2d finalAttackPos = attackPos;
                            if (mutas.stream().anyMatch((muta) -> {
                                return UnitUtils.getDistance(muta, finalAttackPos) < 3.0F;
                            })) {
                                LocationConstants.rotateBaseAttackIndex();
                                attackPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
                            }

                            Bot.ACTION.unitCommand(mutas, Abilities.ATTACK, LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex), false);
                        }
                    }
                }
            case 7:
            case 8:
        }

    }

    private void lateDronesHitBarracksScv() {
        if (LocationConstants.opponentRace == Race.TERRAN && !lateDrones.isEmpty()) {
            List<UnitInPool> enemyBarracksList = UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BARRACKS);
            if (!enemyBarracksList.isEmpty() && enemyBarracksList.get(0).unit().getBuildProgress() < 1.0F) {
                Unit producingScv = UnitUtils.getClosestUnitOfType(Alliance.ENEMY, Units.TERRAN_SCV, enemyBarracksList.get(0).unit().getPosition().toPoint2d());
                if (producingScv != null && UnitUtils.getDistance(producingScv, enemyBarracksList.get(0).unit()) < 3.5F) {
                    Bot.ACTION.unitCommand(UnitUtils.toUnitList(lateDrones), Abilities.ATTACK, producingScv, false);
                }
            }

            List<Unit> idleDrones = lateDrones.stream().map(UnitInPool::unit).filter((drone) -> {
                return drone.getOrders().isEmpty();
            }).collect(Collectors.toList());
            if (!idleDrones.isEmpty()) {
                Bot.ACTION.unitCommand(UnitUtils.toUnitList(lateDrones), Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), false);
            }

        }
    }

    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
    }

    public void onUnitIdle(UnitInPool unitInPool) {
    }

    public void onUnitCreated(UnitInPool drone) {
        if (drone.unit().getType() == Units.ZERG_DRONE && !drone.unit().getOrders().isEmpty() && UnitUtils.getDistance(Bot.OBS.getUnit(drone.unit().getOrders().get(0).getTargetedUnitTag().get()).unit(), LocationConstants.enemyMineralTriangle.getInner().unit()) < 1.0F) {
            lateDrones.add(drone);
            if (LocationConstants.opponentRace == Race.TERRAN) {
                Bot.ACTION.unitCommand(drone.unit(), Abilities.MOVE, LocationConstants.baseLocations.get(baseScoutIndex++), false).unitCommand(drone.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), true);
            }
        }

    }

    public void onUnitDestroyed(UnitInPool unitInPool) {
    }

    public void onUpgradeCompleted(Upgrade upgrade) {
    }

    public void onUnitEnterVision(UnitInPool unitInPool) {
    }

    public void onNydusDetected() {
    }

    public void onNuclearLaunchDetected() {
    }

    public void onGameEnd() {
    }

    public boolean afterTime(String time) {
        long seconds = this.convertStringToSeconds(time);
        return (double) this.observation().getGameLoop() / 22.4D > (double) seconds;
    }

    public boolean beforeTime(String time) {
        long seconds = this.convertStringToSeconds(time);
        return (double) this.observation().getGameLoop() / 22.4D < (double) seconds;
    }

    public long convertStringToSeconds(String time) {
        String[] arrTime = time.split(":");
        return Integer.parseInt(arrTime[0]) * 60 + Integer.parseInt(arrTime[1]);
    }

    public String convertGameLoopToStringTime(long gameLoop) {
        return this.convertSecondsToString(Math.round((double) gameLoop / 22.4D));
    }

    public String convertSecondsToString(long seconds) {
        return seconds / 60L + ":" + String.format("%02d", seconds % 60L);
    }

    public String currentGameTime() {
        return this.convertGameLoopToStringTime(this.observation().getGameLoop());
    }

    public Unit findScvNearestBase(Unit cc) {
        return this.findNearestScv(cc.getPosition().toPoint2d(), true);
    }

    public Unit findNearestScv(Point2d pt, boolean isHoldingMinerals) {
        List<UnitInPool> scvList = this.observation().getUnits(Alliance.SELF, (scvx) -> {
            return scvx.unit().getType() == Units.TERRAN_SCV && (!isHoldingMinerals || scvx.unit().getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS));
        });
        UnitInPool closestScv = scvList.get(0);
        double closestDistance = pt.distance(closestScv.unit().getPosition().toPoint2d());
        scvList.remove(0);
        Iterator var7 = scvList.iterator();

        while (var7.hasNext()) {
            UnitInPool scv = (UnitInPool) var7.next();
            double curDistance = pt.distance(scv.unit().getPosition().toPoint2d());
            if (curDistance < closestDistance) {
                closestScv = scv;
                closestDistance = curDistance;
            }
        }

        return closestScv.unit();
    }

    public Unit findNearestScv(Point2d pt) {
        return this.findNearestScv(pt, false);
    }
}

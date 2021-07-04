package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.*;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.DefenseUnitPositions;
import com.ketroc.terranbot.models.StructureScv;
import com.ketroc.terranbot.strategies.BunkerContain;
import com.ketroc.terranbot.strategies.CannonRushDefense;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.*;
import java.util.stream.Collectors;

public class ArmyManager {
    public static final List<UnitInPool> leftRavens = new ArrayList();
    public static final List<UnitInPool> rightRavens = new ArrayList();
    public static Point2d retreatPos;
    public static Point2d attackPos;
    public static Unit attackUnit;
    public static List<Unit> armyRetreating;
    public static List<Unit> armyAMoving;
    public static long prevSeekerFrame;

    public static void onStep() {
        raiseAndLowerDepots();
        setAttackLocation();
        nydusResponse();
        pfTargetting();
        if (BunkerContain.proxyBunkerLevel != 2) {
            positionTanks();
        }

        positionLiberators();
        emptyBunker();
        if (BunkerContain.proxyBunkerLevel == 0) {
            positionMarines();
        }

        manageRepairBay();
        if (attackPos == null && Switches.finishHim) {
            spreadArmy(GameCache.bansheeList);
            spreadArmy(GameCache.vikingList);
        } else {
            armyRetreating = new ArrayList();
            armyAMoving = new ArrayList();
            if (Switches.bansheeDiveTarget != null && !giveDiversCommand(GameCache.bansheeDivers, Switches.bansheeDiveTarget)) {
                Switches.bansheeDiveTarget = null;
            }

            Iterator var0 = GameCache.bansheeList.iterator();

            Unit raven;
            while (var0.hasNext()) {
                raven = (Unit) var0.next();
                giveBansheeCommand(raven);
            }

            if (Switches.vikingDiveTarget != null) {
                if (!Switches.isDivingTempests) {
                    if (!giveDiversCommand(GameCache.vikingDivers, Switches.vikingDiveTarget)) {
                        Switches.vikingDiveTarget = null;
                    }
                } else {
                    List<Unit> moveVikings = new ArrayList();
                    List<Unit> attackVikings = new ArrayList();
                    if (!UnitUtils.isVisible(Switches.vikingDiveTarget)) {
                        moveVikings.addAll(GameCache.vikingDivers);
                    } else {
                        Iterator var2 = GameCache.vikingDivers.iterator();

                        label89:
                        while (true) {
                            while (true) {
                                if (!var2.hasNext()) {
                                    break label89;
                                }

                                Unit viking = (Unit) var2.next();
                                if (viking.getWeaponCooldown().get() == 0.0F && (double) UnitUtils.getDistance(viking, Switches.vikingDiveTarget.unit()) < 8.5D) {
                                    attackVikings.add(viking);
                                } else {
                                    moveVikings.add(viking);
                                }
                            }
                        }
                    }

                    if (!attackVikings.isEmpty()) {
                        if (Switches.vikingDiveTarget.unit().getCloakState().get() == CloakState.CLOAKED) {
                            if (UnitUtils.canScan()) {
                                List<Unit> orbitals = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
                                Bot.ACTION.unitCommand(orbitals, Abilities.EFFECT_SCAN, Position.towards(Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), retreatPos, -5.0F), false);
                            }

                            Bot.ACTION.unitCommand(attackVikings, Abilities.MOVE, Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), false);
                        } else {
                            Bot.ACTION.unitCommand(attackVikings, Abilities.ATTACK, Switches.vikingDiveTarget.unit(), false);
                        }
                    }

                    if (!moveVikings.isEmpty()) {
                        Bot.ACTION.unitCommand(moveVikings, Abilities.MOVE, Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), false);
                    }
                }
            }

            var0 = GameCache.vikingList.iterator();

            while (var0.hasNext()) {
                raven = (Unit) var0.next();
                giveVikingCommand(raven);
            }

            var0 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_RAVEN).iterator();

            while (var0.hasNext()) {
                raven = (Unit) var0.next();
                giveRavenCommand(raven);
            }

            if (!armyRetreating.isEmpty()) {
                Bot.ACTION.unitCommand(armyRetreating, Abilities.MOVE, retreatPos, false);
            }

            if (!armyAMoving.isEmpty()) {
                Bot.ACTION.unitCommand(armyAMoving, Abilities.ATTACK, attackPos, false);
            }
        }

    }

    private static boolean giveDiversCommand(List<Unit> divers, UnitInPool diveTarget) {
        if (divers.isEmpty()) {
            return false;
        } else {
            boolean canAttack = divers.get(0).getWeaponCooldown().orElse(1.0F) == 0.0F;
            float attackRange = Bot.OBS.getUnitTypeData(false).get(divers.get(0).getType()).getWeapons().iterator().next().getRange();
            List<Unit> attackers = new ArrayList();
            List<Unit> retreaters = new ArrayList();
            Iterator var6 = divers.iterator();

            while (true) {
                while (var6.hasNext()) {
                    Unit diver = (Unit) var6.next();
                    boolean inRange = UnitUtils.getDistance(diveTarget.unit(), diver) < attackRange;
                    if (!canAttack && inRange) {
                        retreaters.add(diver);
                    } else {
                        attackers.add(diver);
                    }
                }

                if (!attackers.isEmpty()) {
                    Bot.ACTION.unitCommand(attackers, Abilities.ATTACK, diveTarget.unit(), false);
                }

                if (!retreaters.isEmpty()) {
                    Bot.ACTION.unitCommand(retreaters, Abilities.MOVE, retreatPos, false);
                }

                return true;
            }
        }
    }

    private static void manageRepairBay() {
        int numInjured = Bot.OBS.getUnits(Alliance.SELF, (u) -> {
            return (u.unit().getType() == Units.TERRAN_VIKING_FIGHTER || u.unit().getType() == Units.TERRAN_BANSHEE || u.unit().getType() == Units.TERRAN_RAVEN) && UnitUtils.getHealthPercentage(u.unit()) < 100 && u.unit().getPosition().toPoint2d().distance(LocationConstants.REPAIR_BAY) < 5.0D;
        }).size();
        if (numInjured > 0) {
            int numRepairingScvs = Bot.OBS.getUnits(Alliance.SELF, (u) -> {
                return u.unit().getType() == Units.TERRAN_SCV && !u.unit().getOrders().isEmpty() && (u.unit().getOrders().get(0).getAbility() == Abilities.ATTACK || u.unit().getOrders().get(0).getAbility() == Abilities.EFFECT_REPAIR);
            }).size();
            int numScvsToSend = 7 - numRepairingScvs;
            if (numScvsToSend > 1) {
                List<Unit> availableScvs = UnitUtils.toUnitList(WorkerManager.getAvailableScvs(LocationConstants.REPAIR_BAY, 30, false));
                if (availableScvs.size() > numScvsToSend) {
                    availableScvs = availableScvs.subList(0, numScvsToSend);
                }

                if (!availableScvs.isEmpty()) {
                    Iterator var4 = availableScvs.iterator();

                    while (var4.hasNext()) {
                        Unit scv = (Unit) var4.next();
                        if (!scv.getBuffs().contains(Buffs.AUTOMATED_REPAIR)) {
                            Bot.ACTION.toggleAutocast(scv.getTag(), Abilities.EFFECT_REPAIR_SCV);
                        }
                    }

                    Bot.ACTION.unitCommand(availableScvs, Abilities.ATTACK, retreatPos, false);
                }
            }
        }

    }

    private static void setAttackLocation() {
        UnitInPool closestEnemy = GameCache.allEnemiesList.stream().filter((u) -> {
            return (Switches.finishHim || u.unit().getDisplayType() != DisplayType.SNAPSHOT) && u.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) != CloakState.CLOAKED && !(Boolean) u.unit().getBurrowed().orElse(false) && u.unit().getType() != Units.ZERG_PARASITIC_BOMB_DUMMY && u.unit().getType() != Units.ZERG_CHANGELING_MARINE && u.unit().getType() != Units.ZERG_BROODLING && (!GameCache.vikingList.isEmpty() || !(Boolean) u.unit().getFlying().orElse(false)) && !(Boolean) u.unit().getHallucination().orElse(false) && UnitUtils.isVisible(u);
        }).min(Comparator.comparing((u) -> {
            return u.unit().getPosition().toPoint2d().distance(LocationConstants.baseLocations.get(0));
        })).orElse(null);
        if (closestEnemy != null) {
            attackPos = closestEnemy.unit().getPosition().toPoint2d();
            attackUnit = closestEnemy.unit();
            if (!UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_BANSHEE, attackPos, 1.0F).isEmpty() && !UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, attackPos, 1.0F).isEmpty() && !UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_RAVEN, attackPos, 1.0F).isEmpty()) {
                System.out.println("\n\n=============== PHANTOM ENEMY FOUND ===============\n");
                System.out.println("closestEnemy.isAlive() = " + closestEnemy.isAlive());
                System.out.println("closestEnemy.unit().getType() = " + closestEnemy.unit().getType());
                GameCache.allEnemiesList.remove(closestEnemy);
            }
        } else if (Switches.finishHim) {
            attackPos = null;
        } else {
            attackPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
            if (UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_BANSHEE, attackPos, 3.0F) && (GameCache.vikingList.size() < 3 || UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, attackPos, 3.0F)) && (GameCache.ravenList.isEmpty() || GameCache.ravenList.stream().noneMatch((raven) -> {
                return UnitUtils.getHealthPercentage(raven) >= 40 && UnitUtils.getDistance(raven, retreatPos) > 10.0F;
            }) || UnitUtils.isUnitTypesNearby(Alliance.SELF, Units.TERRAN_RAVEN, attackPos, 3.0F))) {
                LocationConstants.rotateBaseAttackIndex();
                attackPos = LocationConstants.baseLocations.get(LocationConstants.baseAttackIndex);
            }
        }

    }

    private static void nydusResponse() {
        Optional<UnitInPool> nydusWorm = UnitUtils.getEnemyUnitsOfType(Units.ZERG_NYDUS_CANAL).stream().findFirst();
        if (nydusWorm.isPresent()) {
            GameResult.setNydusRushed();
            List<Unit> nydusDivers = new ArrayList();
            nydusDivers.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE));
            nydusDivers.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARAUDER));
            nydusDivers.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK));
            List<UnitInPool> scvs = Bot.OBS.getUnits(Alliance.SELF, (scv) -> {
                return scv.unit().getType() == Units.TERRAN_SCV && Position.isSameElevation(scv.unit().getPosition(), nydusWorm.get().unit().getPosition()) && UnitUtils.getDistance(scv.unit(), nydusWorm.get().unit()) < 35.0F && !StructureScv.isScvProducing(scv.unit());
            }).stream().sorted(Comparator.comparing((scv) -> {
                return Bot.QUERY.pathingDistance(scv.unit(), nydusWorm.get().unit().getPosition().toPoint2d());
            })).collect(Collectors.toList());
            if (scvs.size() > 10) {
                scvs.subList(10, scvs.size()).clear();
            }

            nydusDivers.addAll(UnitUtils.toUnitList(scvs));
            attackPos = nydusWorm.get().unit().getPosition().toPoint2d();
            attackUnit = nydusWorm.get().unit();
            if (!nydusDivers.isEmpty()) {
                Bot.ACTION.unitCommand(nydusDivers, Abilities.ATTACK, attackUnit, false);
            }

            if (!GameCache.bansheeList.isEmpty()) {
                GameCache.bansheeDivers.addAll(GameCache.bansheeList);
                Switches.bansheeDiveTarget = nydusWorm.get();
            }
        }

    }

    public static void pfTargetting() {
        List<Unit> pfsAndTanks = new ArrayList();
        pfsAndTanks.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK_SIEGED));
        pfsAndTanks.addAll(UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_PLANETARY_FORTRESS));
        pfsAndTanks = pfsAndTanks.stream().filter((unit) -> {
            return unit.getBuildProgress() == 1.0F && unit.getWeaponCooldown().orElse(1.0F) == 0.0F && InfluenceMaps.getValue(InfluenceMaps.pointGroundUnitWithin13, unit.getPosition().toPoint2d());
        }).collect(Collectors.toList());
        Iterator var1 = pfsAndTanks.iterator();

        while (var1.hasNext()) {
            Unit pfTank = (Unit) var1.next();
            float x_pfTank = pfTank.getPosition().getX();
            float y_pfTank = pfTank.getPosition().getY();
            byte range;
            if (pfTank.getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
                range = 12;
            } else if (Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) {
                range = 9;
            } else {
                range = 8;
            }

            int xMin = 0;
            int xMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getX());
            int yMin = 0;
            int yMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getY());
            int xStart = Math.max(Math.round(2.0F * (x_pfTank - (float) range)), xMin);
            int yStart = Math.max(Math.round(2.0F * (y_pfTank - (float) range)), yMin);
            int xEnd = Math.min(Math.round(2.0F * (x_pfTank + (float) range)), xMax);
            int yEnd = Math.min(Math.round(2.0F * (y_pfTank + (float) range)), yMax);
            int bestValueX = -1;
            int bestValueY = -1;
            int bestValue = 0;

            for (int x = xStart; x <= xEnd; ++x) {
                for (int y = yStart; y <= yEnd; ++y) {
                    if (InfluenceMaps.pointPFTargetValue[x][y] > bestValue && Position.distance((float) x / 2.0F, (float) y / 2.0F, x_pfTank, y_pfTank) < (float) range) {
                        bestValueX = x;
                        bestValueY = y;
                        bestValue = InfluenceMaps.pointPFTargetValue[x][y];
                    }
                }
            }

            if (bestValue == 0) {
                return;
            }

            Point2d bestTargetPos = Point2d.of((float) bestValueX / 2.0F, (float) bestValueY / 2.0F);
            List<UnitInPool> enemyTargets = Bot.OBS.getUnits(Alliance.ENEMY, (enemy) -> {
                return UnitUtils.getDistance(enemy.unit(), bestTargetPos) < 1.0F && !(Boolean) enemy.unit().getFlying().orElse(false);
            });
            if (!enemyTargets.isEmpty()) {
                Bot.ACTION.unitCommand(pfTank, Abilities.ATTACK, enemyTargets.get(0).unit(), false);
            }
        }

    }

    private static void positionMarines() {
        List<Unit> bunkerList = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER);
        if (!bunkerList.isEmpty()) {
            Iterator var1 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).iterator();

            while (var1.hasNext()) {
                Unit marine = (Unit) var1.next();
                if (marine.getOrders().isEmpty()) {
                    Bot.ACTION.unitCommand(marine, Abilities.SMART, bunkerList.get(0), false);
                }
            }
        }

    }

    private static void positionLiberators() {
        Unit idleLib = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_LIBERATOR).stream().filter((unit) -> {
            return unit.getOrders().isEmpty();
        }).findFirst().orElse(null);
        if (idleLib != null) {
            boolean isLibPlaced = false;
            List<Base> allButEnemyStarterBases = GameCache.baseList.subList(0, GameCache.baseList.size() - BuildManager.getNumEnemyBasesIgnored());
            Iterator var3 = allButEnemyStarterBases.iterator();

            label43:
            while (true) {
                Base base;
                do {
                    do {
                        do {
                            if (!var3.hasNext()) {
                                break label43;
                            }

                            base = (Base) var3.next();
                        } while (!base.isMyBase());
                    } while (base.isMyMainBase());
                } while (base.isDryedUp());

                Iterator var5 = base.getLiberators().iterator();

                while (var5.hasNext()) {
                    DefenseUnitPositions libPos = (DefenseUnitPositions) var5.next();
                    if (libPos.getUnit().isEmpty()) {
                        libPos.setUnit(Bot.OBS.getUnit(idleLib.getTag()));
                        Bot.ACTION.unitCommand(idleLib, Abilities.MOVE, Position.towards(libPos.getPos(), base.getCcPos(), -2.0F), false).unitCommand(idleLib, Abilities.MORPH_LIBERATOR_AG_MODE, Position.towards(libPos.getPos(), base.getCcPos(), 5.0F), true);
                        isLibPlaced = true;
                        break label43;
                    }
                }
            }

            if (!isLibPlaced && allButEnemyStarterBases.stream().noneMatch((basex) -> {
                return basex.isUntakenBase() && !basex.isDryedUp();
            })) {
                GameCache.baseList.stream().filter((basex) -> {
                    return basex.isEnemyBase;
                }).findFirst().ifPresentOrElse((basex) -> {
                    Bot.ACTION.unitCommand(idleLib, Abilities.MORPH_LIBERATOR_AG_MODE, Position.towards(basex.getCcPos(), idleLib.getPosition().toPoint2d(), 1.7F), true);
                }, () -> {
                    Bot.ACTION.unitCommand(idleLib, Abilities.MORPH_LIBERATOR_AG_MODE, Position.towards(GameCache.baseList.get(GameCache.baseList.size() - 3).getCcPos(), idleLib.getPosition().toPoint2d(), 1.7F), true);
                });
            }
        }

    }

    private static void positionTanks() {
        Unit idleTank = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SIEGE_TANK).stream().filter((unit) -> {
            return unit.getOrders().isEmpty();
        }).findFirst().orElse(null);
        if (idleTank != null) {
            boolean isTankPlaced = false;
            List<Base> allButEnemyStarterBases = GameCache.baseList.subList(0, GameCache.baseList.size() - BuildManager.getNumEnemyBasesIgnored());
            Iterator var3 = allButEnemyStarterBases.iterator();

            label43:
            while (true) {
                Base base;
                do {
                    do {
                        do {
                            if (!var3.hasNext()) {
                                break label43;
                            }

                            base = (Base) var3.next();
                        } while (!base.isMyBase());
                    } while (base.isMyMainBase());
                } while (base.isDryedUp());

                Iterator var5 = base.getTanks().iterator();

                while (var5.hasNext()) {
                    DefenseUnitPositions tankPos = (DefenseUnitPositions) var5.next();
                    if (tankPos.getUnit().isEmpty()) {
                        tankPos.setUnit(Bot.OBS.getUnit(idleTank.getTag()));
                        Bot.ACTION.unitCommand(idleTank, Abilities.ATTACK, tankPos.getPos(), false).unitCommand(idleTank, Abilities.MORPH_SIEGE_MODE, true);
                        isTankPlaced = true;
                        break label43;
                    }
                }
            }

            if (!isTankPlaced && allButEnemyStarterBases.stream().noneMatch((basex) -> {
                return basex.isUntakenBase() && !basex.isDryedUp();
            })) {
                GameCache.baseList.stream().filter((basex) -> {
                    return basex.isEnemyBase;
                }).forEach((basex) -> {
                    Bot.ACTION.unitCommand(idleTank, Abilities.ATTACK, basex.getCcPos(), true);
                });
                Bot.ACTION.unitCommand(idleTank, Abilities.ATTACK, GameCache.baseList.get(GameCache.baseList.size() - 1).getCcPos(), true);
            }
        }

    }

    private static void emptyBunker() {
        if (GameCache.baseList.get(1).getCc().map(UnitInPool::unit).map(Unit::getType).orElse(null) == Units.TERRAN_PLANETARY_FORTRESS) {
            List<Unit> bunkerList = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER);
            if (!bunkerList.isEmpty()) {
                bunkerList.stream().filter((bunker) -> {
                    return UnitUtils.getDistance(bunker, LocationConstants.BUNKER_NATURAL) < 1.0F;
                }).forEach((bunker) -> {
                    Bot.ACTION.unitCommand(bunker, Abilities.UNLOAD_ALL_BUNKER, false);
                    Bot.ACTION.unitCommand(bunker, Abilities.EFFECT_SALVAGE, false);
                });
            }
        }

    }

    private static void raiseAndLowerDepots() {
        Iterator var0 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SUPPLY_DEPOT).iterator();

        Unit depot;
        Point2d depotPos;
        while (var0.hasNext()) {
            depot = (Unit) var0.next();
            depotPos = depot.getPosition().toPoint2d();
            if (!InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depotPos)) {
                Bot.ACTION.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
            }
        }

        var0 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_SUPPLY_DEPOT_LOWERED).iterator();

        while (var0.hasNext()) {
            depot = (Unit) var0.next();
            depotPos = depot.getPosition().toPoint2d();
            if (InfluenceMaps.getValue(InfluenceMaps.pointRaiseDepots, depotPos) && CannonRushDefense.cannonRushStep == 0) {
                Bot.ACTION.unitCommand(depot, Abilities.MORPH_SUPPLY_DEPOT_RAISE, false);
            }
        }

    }

    public static void spreadArmy(List<Unit> army) {
        Iterator var1 = army.iterator();

        while (var1.hasNext()) {
            Unit unit = (Unit) var1.next();
            if (unit.getOrders().isEmpty()) {
                Bot.ACTION.unitCommand(unit, Abilities.ATTACK, Bot.OBS.getGameInfo().findRandomLocation(), false);
            }
        }

    }

    private static Point2d calculateTankPosition(Point2d ccPos) {
        float xCC = ccPos.getX();
        float yCC = ccPos.getY();
        float xEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1).getX();
        float yEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1).getY();
        float xDistance = xEnemy - xCC;
        float yDistance = yEnemy - yCC;
        float xMove = 2.0F;
        float yMove = 2.0F;
        if (Math.abs(xDistance) > Math.abs(yDistance)) {
            xMove = 5.0F;
        } else {
            yMove = 5.0F;
        }

        if (xDistance > 0.0F) {
            xMove *= -1.0F;
        }

        if (yDistance > 0.0F) {
            yMove *= -1.0F;
        }

        return Point2d.of(xCC + xMove, yCC + yMove);
    }

    public static int calcNumVikingsNeeded() {
        float answer = 0.0F;
        boolean hasDetector = false;
        boolean hasTempests = false;
        Iterator var3 = GameCache.allEnemiesList.iterator();

        while (var3.hasNext()) {
            UnitInPool enemy = (UnitInPool) var3.next();
            switch ((Units) enemy.unit().getType()) {
                case TERRAN_RAVEN:
                case ZERG_OVERSEER:
                case PROTOSS_OBSERVER:
                    hasDetector = true;
                    ++answer;
                    break;
                case TERRAN_VIKING_FIGHTER:
                case TERRAN_VIKING_ASSAULT:
                    answer = (float) ((double) answer + 1.5D);
                    break;
                case TERRAN_LIBERATOR:
                case TERRAN_LIBERATOR_AG:
                case TERRAN_BANSHEE:
                case ZERG_CORRUPTOR:
                case ZERG_MUTALISK:
                case ZERG_VIPER:
                case ZERG_BROODLORD_COCOON:
                case ZERG_BROODLORD:
                case PROTOSS_ORACLE:
                    ++answer;
                    break;
                case PROTOSS_PHOENIX:
                    answer += 3.0F;
                    break;
                case PROTOSS_VOIDRAY:
                    answer = (float) ((double) answer + 1.5D);
                    break;
                case TERRAN_BATTLECRUISER:
                case PROTOSS_CARRIER:
                    answer = (float) ((double) answer + 3.67D);
                    break;
                case PROTOSS_TEMPEST:
                    hasTempests = true;
                    answer += 2.0F;
                    break;
                case PROTOSS_MOTHERSHIP:
                    answer += 4.0F;
            }
        }

        if (hasTempests) {
            answer = Math.max(10.0F, answer);
        } else if (Switches.enemyCanProduceAir) {
            if (LocationConstants.opponentRace == Race.PROTOSS) {
                answer = Math.max(6.0F, answer);
            } else {
                answer = Math.max(4.0F, answer);
            }
        } else if (hasDetector && UnitUtils.getNumUnits(Units.TERRAN_BANSHEE, true) > 0) {
            answer = Math.max(LocationConstants.opponentRace == Race.PROTOSS ? 2.0F : 3.0F, answer);
        }

        answer = Math.max(answer, (float) (GameCache.bansheeList.size() / 5));
        return (int) answer;
    }

    public static void giveBansheeCommand(Unit banshee) {
        ArmyCommands lastCommand = getCurrentCommand(banshee);
        int x = InfluenceMaps.toMapCoord(banshee.getPosition().getX());
        int y = InfluenceMaps.toMapCoord(banshee.getPosition().getY());
        boolean isUnsafe = !Switches.isDivingTempests && InfluenceMaps.pointThreatToAir[x][y] > 2;
        boolean isInDetectionRange = InfluenceMaps.pointDetected[x][y];
        boolean isInBansheeRange = InfluenceMaps.pointInBansheeRange[x][y];
        boolean canAttack = banshee.getWeaponCooldown().orElse(1.0F) == 0.0F && InfluenceMaps.pointThreatToAir[x][y] < 200;
        CloakState cloakState = banshee.getCloakState().orElse(CloakState.NOT_CLOAKED);
        boolean canCloak = banshee.getEnergy().orElse(0.0F) > 80.0F && GameCache.upgradesCompleted.contains(Upgrades.BANSHEE_CLOAK);
        boolean isParasitic = banshee.getBuffs().contains(Buffs.PARASITIC_BOMB);
        boolean isDecloakBuffed = UnitUtils.hasDecloakBuff(banshee);
        boolean isHomeUnderAttack = attackPos.distance(retreatPos) < 50.0D;
        if (banshee.getBuffs().contains(Buffs.LOCK_ON)) {
            if (!isInDetectionRange && canCloak && !isDecloakBuffed) {
                Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
            } else if (lastCommand != ArmyCommands.HOME) {
                armyRetreating.add(banshee);
            }
        } else if (canAttack && isInBansheeRange) {
            if (lastCommand != ArmyCommands.ATTACK) {
                armyAMoving.add(banshee);
            }
        } else if (isParasitic) {
            Bot.ACTION.unitCommand(banshee, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), false);
        } else if (!(UnitUtils.getDistance(banshee, retreatPos) < 3.0F) || UnitUtils.getHealthPercentage(banshee) >= 100 && (canCloak || isHomeUnderAttack)) {
            if (UnitUtils.getHealthPercentage(banshee) < 40) {
                if (lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(banshee);
                }
            } else if (isUnsafe) {
                if (isInDetectionRange) {
                    if (lastCommand != ArmyCommands.HOME) {
                        armyRetreating.add(banshee);
                    }
                } else if (cloakState == CloakState.CLOAKED_ALLIED && banshee.getEnergy().get() > (float) (3 + (UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size() > 2 ? 2 : 0))) {
                    if (isInBansheeRange) {
                        if (lastCommand != ArmyCommands.HOME) {
                            armyRetreating.add(banshee);
                        }
                    } else if (lastCommand != ArmyCommands.ATTACK) {
                        armyAMoving.add(banshee);
                    }
                } else if (canCloak && !isDecloakBuffed) {
                    Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
                } else if (lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(banshee);
                }
            } else if (!canCloak && !isHomeUnderAttack && cloakState != CloakState.CLOAKED_ALLIED) {
                if (lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(banshee);
                }
            } else if (isInBansheeRange) {
                if (lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(banshee);
                }
            } else if (lastCommand != ArmyCommands.ATTACK) {
                armyAMoving.add(banshee);
            }
        } else if (cloakState == CloakState.CLOAKED_ALLIED && !isUnsafe) {
            Bot.ACTION.unitCommand(banshee, Abilities.BEHAVIOR_CLOAK_OFF_BANSHEE, false);
        } else if (lastCommand != ArmyCommands.HOME) {
            armyRetreating.add(banshee);
        }

    }

    private static ArmyCommands getCurrentCommand(Unit unit) {
        ArmyCommands currentCommand = ArmyCommands.EMPTY;
        if (!unit.getOrders().isEmpty()) {
            UnitOrder order = unit.getOrders().get(0);
            if (order.getAbility() == Abilities.ATTACK) {
                if (order.getTargetedWorldSpacePosition().isPresent() && order.getTargetedWorldSpacePosition().get().toPoint2d().distance(attackPos) < 1.0D) {
                    currentCommand = ArmyCommands.ATTACK;
                } else if (order.getTargetedUnitTag().isPresent()) {
                    currentCommand = ArmyCommands.DIVE;
                }
            } else if (order.getAbility() == Abilities.MOVE && order.getTargetedWorldSpacePosition().isPresent() && order.getTargetedWorldSpacePosition().get().toPoint2d().distance(retreatPos) < 1.0D) {
                currentCommand = ArmyCommands.HOME;
            }
        }

        return currentCommand;
    }

    private static void giveVikingCommand(Unit viking) {
        ArmyCommands lastCommand = getCurrentCommand(viking);
        int x = InfluenceMaps.toMapCoord(viking.getPosition().getX());
        int y = InfluenceMaps.toMapCoord(viking.getPosition().getY());
        boolean isUnsafe = InfluenceMaps.pointThreatToAirFromGround[x][y] > 0;
        if (!UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).isEmpty() && Bot.OBS.getUnits(Alliance.ENEMY, (e) -> {
            return (e.unit().getType() == Units.PROTOSS_PHOENIX || e.unit().getType() == Units.PROTOSS_VOIDRAY || e.unit().getType() == Units.PROTOSS_INTERCEPTOR) && !(Boolean) e.unit().getHallucination().orElse(false);
        }).isEmpty()) {
            isUnsafe = InfluenceMaps.pointVikingsStayBack[x][y];
        } else if (GameCache.vikingList.size() <= UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).size()) {
            isUnsafe = InfluenceMaps.pointThreatToAir[x][y] > 0;
        }

        boolean isInVikingRange = InfluenceMaps.pointInVikingRange[x][y];
        boolean canAttack = viking.getWeaponCooldown().orElse(1.0F) == 0.0F;
        boolean isParasitic = viking.getBuffs().contains(Buffs.PARASITIC_BOMB);
        if (viking.getBuffs().contains(Buffs.LOCK_ON)) {
            if (lastCommand != ArmyCommands.HOME) {
                armyRetreating.add(viking);
            }
        } else if (canAttack && isInVikingRange) {
            if (lastCommand != ArmyCommands.ATTACK) {
                armyAMoving.add(viking);
            }
        } else if (isParasitic) {
            Bot.ACTION.unitCommand(viking, Abilities.MOVE, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), false);
        } else if (UnitUtils.getHealthPercentage(viking) < 100 && viking.getPosition().toPoint2d().distance(retreatPos) < 3.0D) {
            if (lastCommand != ArmyCommands.HOME) {
                armyRetreating.add(viking);
            }
        } else if (!Strategy.enemyHasAirThreat && viking.getHealth().get() < viking.getHealthMax().get()) {
            if (lastCommand != ArmyCommands.HOME) {
                armyRetreating.add(viking);
            }
        } else if (UnitUtils.getHealthPercentage(viking) < 40) {
            if (lastCommand != ArmyCommands.HOME) {
                armyRetreating.add(viking);
            }
        } else if (!isUnsafe && !isInVikingRange) {
            if (lastCommand != ArmyCommands.ATTACK) {
                armyAMoving.add(viking);
            }
        } else if (lastCommand != ArmyCommands.HOME) {
            armyRetreating.add(viking);
        }

    }

    private static void giveRavenCommand(Unit raven) {
        if (raven.getOrders().isEmpty() || raven.getOrders().get(0).getAbility() != Abilities.EFFECT_AUTO_TURRET) {
            ArmyCommands lastCommand = getCurrentCommand(raven);
            int x = InfluenceMaps.toMapCoord(raven.getPosition().getX());
            int y = InfluenceMaps.toMapCoord(raven.getPosition().getY());
            boolean isUnsafe = InfluenceMaps.pointThreatToAirPlusBuffer[x][y];
            if (raven.getBuffs().contains(Buffs.LOCK_ON)) {
                if (lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(raven);
                }
            } else if (UnitUtils.getHealthPercentage(raven) < 100 && raven.getPosition().toPoint2d().distance(retreatPos) < 3.0D) {
                if (lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(raven);
                }
            } else if (UnitUtils.getHealthPercentage(raven) < 40) {
                if (!doAutoTurretOnRetreat(raven) && lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(raven);
                }
            } else if (isUnsafe) {
                if (!castSeeker(raven) && !doAutoTurretOnMaxEnergy(raven) && lastCommand != ArmyCommands.HOME) {
                    armyRetreating.add(raven);
                }
            } else if (lastCommand != ArmyCommands.ATTACK) {
                armyAMoving.add(raven);
            }

        }
    }

    private static boolean doAutoTurretOnRetreat(Unit raven) {
        return raven.getEnergy().orElse(0.0F) >= 50.0F && UnitUtils.getDistance(raven, attackPos) < 12.0F && attackUnit != null && castAutoTurret(raven, false);
    }

    private static boolean doAutoTurretOnMaxEnergy(Unit raven) {
        return raven.getEnergy().orElse(0.0F) >= 180.0F && castAutoTurret(raven, true);
    }

    private static boolean castAutoTurret(Unit raven, boolean useForwardPosition) {
        float castRange = useForwardPosition ? 4.0F : 2.0F;
        Point2d turretPos = Position.towards(raven.getPosition().toPoint2d(), attackPos, castRange);
        if (Bot.QUERY.placement(Abilities.BUILD_SUPPLY_DEPOT, turretPos)) {
            Bot.ACTION.unitCommand(raven, Abilities.EFFECT_AUTO_TURRET, turretPos, false);
            return true;
        } else {
            return false;
        }
    }

    private static boolean castSeeker(Unit raven) {
        if (Bot.OBS.getGameLoop() < prevSeekerFrame + 70L) {
            return false;
        } else {
            float ravenEnergy = raven.getEnergy().orElse(0.0F);
            if (ravenEnergy >= 75.0F) {
                Point2d targetPos = findSeekerTarget((int) raven.getPosition().getX(), (int) raven.getPosition().getY(), ravenEnergy > 150.0F);
                if (targetPos == null) {
                    return false;
                } else {
                    List<UnitInPool> targetUnitList = Bot.OBS.getUnits(Alliance.ENEMY, (unit) -> {
                        return unit.unit().getPosition().toPoint2d().distance(targetPos) < 0.5D;
                    });
                    if (targetUnitList.isEmpty()) {
                        return false;
                    } else {
                        Bot.ACTION.unitCommand(raven, Abilities.EFFECT_ANTI_ARMOR_MISSILE, targetUnitList.get(0).unit(), false);
                        prevSeekerFrame = Bot.OBS.getGameLoop();
                        return true;
                    }
                }
            } else {
                return false;
            }
        }
    }

    private static Point2d findSeekerTarget(int ravenX, int ravenY, boolean isMaxEnergy) {
        int bestX = 0;
        int bestY = 0;
        float bestValue = 0.0F;
        int xMin = Math.max(0, ravenX - 15);
        int xMax = Math.min(InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getX()), (ravenX + 15) * 2);
        int yMin = Math.max(0, ravenY - 15);
        int yMax = Math.min(InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getY()), (ravenY + 15) * 2);

        for (int x = xMin; x < xMax; ++x) {
            for (int y = yMin; y < yMax; ++y) {
                if (InfluenceMaps.pointSupplyInSeekerRange[x][y] > bestValue) {
                    bestX = x;
                    bestY = y;
                    bestValue = InfluenceMaps.pointSupplyInSeekerRange[x][y];
                }
            }
        }

        float minSupplyToSeeker = isMaxEnergy ? 15.0F : 22.0F;
        return bestValue < minSupplyToSeeker ? null : Point2d.of((float) bestX / 2.0F, (float) bestY / 2.0F);
    }

    public static boolean shouldDive(Units unitType, Unit enemy) {
        int numAttackersNearby = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, unitType, enemy.getPosition().toPoint2d(), (float) Strategy.DIVE_RANGE).size();
        if (numAttackersNearby < 2) {
            return false;
        } else if (unitType == Units.TERRAN_VIKING_FIGHTER && UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER).size() >= 6) {
            return false;
        } else {
            Point2d threatPoint = getPointFromA(enemy.getPosition().toPoint2d(), retreatPos, Bot.OBS.getUnitTypeData(false).get(unitType).getWeapons().iterator().next().getRange());
            int x = InfluenceMaps.toMapCoord(threatPoint.getX());
            int y = InfluenceMaps.toMapCoord(threatPoint.getY());
            return numAttackersNearby >= numNeededToDive(enemy, InfluenceMaps.pointThreatToAir[x][y]);
        }
    }

    public static boolean shouldDiveTempests(Point2d closestTempest, int numVikingsNearby) {
        if ((double) numVikingsNearby < Math.min(20.0D, (double) calcNumVikingsNeeded() * 0.8D)) {
            return false;
        } else if (Bot.OBS.getFoodUsed() >= 197) {
            return true;
        } else {
            List<UnitInPool> aaThreats = Bot.OBS.getUnits(Alliance.ENEMY, (ux) -> {
                return (ux.unit().getType() == Units.PROTOSS_VOIDRAY || ux.unit().getType() == Units.PROTOSS_STALKER || ux.unit().getType() == Units.PROTOSS_INTERCEPTOR || ux.unit().getType() == Units.PROTOSS_PHOENIX) && UnitUtils.getDistance(ux.unit(), closestTempest) < 15.0F;
            });
            int threatTotal = 0;
            Iterator var4 = aaThreats.iterator();

            while (var4.hasNext()) {
                UnitInPool u = (UnitInPool) var4.next();
                Unit threat = u.unit();
                switch ((Units) threat.getType()) {
                    case PROTOSS_PHOENIX:
                    case PROTOSS_STALKER:
                        threatTotal += 2;
                        break;
                    case PROTOSS_VOIDRAY:
                        threatTotal += 4;
                    case TERRAN_BATTLECRUISER:
                    case PROTOSS_CARRIER:
                    case PROTOSS_TEMPEST:
                    case PROTOSS_MOTHERSHIP:
                    default:
                        break;
                    case PROTOSS_INTERCEPTOR:
                        ++threatTotal;
                }
            }

            float ratio = UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).size() < 3 ? 0.65F : 1.2F;
            return (float) threatTotal < (float) numVikingsNearby * ratio;
        }
    }

    private static int numNeededToDive(Unit enemy, int threatLevel) {
        float enemyHP = enemy.getHealth().orElse(60.0F) + enemy.getShield().orElse(0.0F);
        if (enemy.getType() == Units.ZERG_OVERSEER) {
            enemyHP = (float) ((double) enemyHP * 0.71D);
        }

        threatLevel = (int) ((double) threatLevel * 1.3D);
        return Math.min(2, (int) ((enemyHP * (float) threatLevel + 2500.0F) / 1500.0F + enemyHP / 500.0F + (float) (threatLevel / 20))) + 1;
    }

    public static Point2d getPointFromA(Point2d a, Point2d b, float distance) {
        double ratio = (double) distance / a.distance(b);
        int newX = (int) ((double) (b.getX() - a.getX()) * ratio + (double) a.getX());
        int newY = (int) ((double) (b.getY() - a.getY()) * ratio + (double) a.getY());
        return Point2d.of((float) newX, (float) newY);
    }

    public static Point2d getNextRavenPosition(Point2d curPosition, boolean isLeft, boolean isAttacking) {
        boolean moveClockwise = isLeft && isAttacking || !isLeft && !isAttacking;
        int x = (int) curPosition.getX();
        int y = (int) curPosition.getY();
        if (y == LocationConstants.MAX_Y && x == 0) {
            return moveClockwise ? Point2d.of((float) LocationConstants.MAX_X, (float) LocationConstants.MAX_Y) : Point2d.of(0.0F, 0.0F);
        } else if (y == LocationConstants.MAX_Y && x == LocationConstants.MAX_X) {
            return moveClockwise ? Point2d.of((float) LocationConstants.MAX_X, 0.0F) : Point2d.of(0.0F, (float) LocationConstants.MAX_Y);
        } else if (y == 0 && x == 0) {
            return moveClockwise ? Point2d.of(0.0F, (float) LocationConstants.MAX_Y) : Point2d.of((float) LocationConstants.MAX_X, 0.0F);
        } else if (y == 0 && x == LocationConstants.MAX_X) {
            return moveClockwise ? Point2d.of(0.0F, 0.0F) : Point2d.of((float) LocationConstants.MAX_X, (float) LocationConstants.MAX_Y);
        } else if (y == 0) {
            return moveClockwise ? Point2d.of(0.0F, (float) y) : Point2d.of((float) LocationConstants.MAX_X, (float) y);
        } else if (y == LocationConstants.MAX_Y) {
            return moveClockwise ? Point2d.of((float) LocationConstants.MAX_X, (float) y) : Point2d.of(0.0F, (float) y);
        } else if (x == 0) {
            return moveClockwise ? Point2d.of((float) x, (float) LocationConstants.MAX_Y) : Point2d.of((float) x, 0.0F);
        } else if (x == LocationConstants.MAX_X) {
            return moveClockwise ? Point2d.of((float) x, 0.0F) : Point2d.of((float) x, (float) LocationConstants.MAX_Y);
        } else {
            float xDistanceFromEdge = (float) x;
            int newX = 0;
            float yDistanceFromEdge = (float) y;
            int newY = 0;
            if (yDistanceFromEdge > (float) (LocationConstants.MAX_Y - y)) {
                yDistanceFromEdge = (float) (LocationConstants.MAX_Y - y);
                newY = LocationConstants.MAX_Y;
            }

            if (xDistanceFromEdge > (float) (LocationConstants.MAX_X - x)) {
                xDistanceFromEdge = (float) (LocationConstants.MAX_X - x);
                newX = LocationConstants.MAX_X;
            }

            return xDistanceFromEdge < yDistanceFromEdge ? Point2d.of((float) newX, (float) y) : Point2d.of((float) x, (float) newY);
        }
    }
}

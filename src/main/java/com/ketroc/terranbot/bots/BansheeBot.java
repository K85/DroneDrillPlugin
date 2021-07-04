package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.DebugInterface;
import com.github.ocraft.s2client.bot.gateway.QueryInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat.Channel;
import com.github.ocraft.s2client.protocol.data.*;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.Alert;
import com.github.ocraft.s2client.protocol.observation.ChatReceived;
import com.github.ocraft.s2client.protocol.observation.PlayerResult;
import com.github.ocraft.s2client.protocol.observation.Result;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.managers.*;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.*;
import com.ketroc.terranbot.strategies.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BansheeBot extends Bot {
    public static LinkedList<Purchase> purchaseQueue = new LinkedList();

    public BansheeBot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }

    public void onAlert(Alert alert) {
    }

    public void onGameStart() {
        try {
            super.onGameStart();

            // 设置地图名
            LocationConstants.MAP = MapNameTransfer.getTransferredMapName(Bot.OBS.getGameInfo().getMapName());

            LocationConstants.opponentRace = OBS.getGameInfo().getPlayersInfo().stream().filter((playerInfo) -> {
                return playerInfo.getPlayerId() != Bot.OBS.getPlayerId();
            }).findFirst().get().getRequestedRace();
            Strategy.onGameStart();
            UnitInPool mainCC = Bot.OBS.getUnits(Alliance.SELF, (cc) -> {
                return cc.unit().getType() == Units.TERRAN_COMMAND_CENTER;
            }).get(0);
            Bot.ACTION.unitCommand(mainCC.unit(), Abilities.TRAIN_SCV, false);
            Bot.ACTION.sendActions();
            LocationConstants.onGameStart(mainCC);
            DebugHelper.onGameStart();
            GameCache.onStep();
            LocationConstants.setRepairBayLocation();
            Bot.OBS.getUnitTypeData(false).forEach((unitType, unitTypeData) -> {
                unitTypeData.getAbility().ifPresent((ability) -> {
                    if (ability instanceof Abilities && unitType instanceof Units) {
                        Bot.abilityToUnitType.put((Abilities) ability, (Units) unitType);
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

                        Bot.abilityToUpgrade.put((Abilities) ability, (Upgrades) upgrade);
                    }

                });
            });
            BuildOrder.onGameStart();
            BunkerContain.onGameStart();
            Bot.ACTION.sendActions();
        } catch (Exception var2) {
            var2.printStackTrace();
        }

    }

    public void onStep() {
        try {
            Iterator var1 = Bot.OBS.getChatMessages().iterator();

            while (var1.hasNext()) {
                ChatReceived chat = (ChatReceived) var1.next();
                Chat.respondToBots(chat);
            }

            if (Bot.OBS.getGameLoop() % (long) Strategy.SKIP_FRAMES == 0L) {
                if (Bot.OBS.getGameLoop() == (long) Strategy.SKIP_FRAMES) {
                    Bot.ACTION.sendChat("Last updated: Sept 18, 2020", Channel.BROADCAST);
                }

                Ignored.onStep();
                EnemyScan.onStep();
                GameCache.onStep();
                Switches.onStep();
                DelayedAction.onStep();
                DelayedChat.onStep();
                FlyingCC.onStep();
                StructureScv.checkScvsActivelyBuilding();
                if (ProbeRushDefense.onStep()) {
                    return;
                }

                if (!Switches.scvRushComplete) {
                    Switches.scvRushComplete = ScvRush.onStep();
                }

                CannonRushDefense.onStep();
                BunkerContain.onStep();
                Harassers.onStep();
                GameCache.baseList.stream().forEach(Base::onStep);
                Purchase toRemove = null;
                int i;
                if (Switches.tvtFastStart) {
                    Strategy.onStep_TvtFaststart();
                } else {
                    i = 0;

                    label70:
                    while (i < purchaseQueue.size()) {
                        PurchaseResult result = purchaseQueue.get(i).build();
                        switch (result) {
                            case SUCCESS:
                                toRemove = purchaseQueue.get(i);
                                break label70;
                            case CANCEL:
                                if (toRemove instanceof PurchaseStructure) {
                                    PrintStream var10000 = System.out;
                                    Units var10001 = ((PurchaseStructure) toRemove).getStructureType();
                                    var10000.println(var10001 + " failed to build at: " + ((PurchaseStructure) toRemove).getPosition());
                                }

                                purchaseQueue.remove(i--);
                            case WAITING:
                            default:
                                ++i;
                        }
                    }
                }

                UpgradeManager.onStep();
                BuildManager.onStep();
                WorkerManager.onStep();
                ArmyManager.onStep();
                LocationConstants.onStep();
                purchaseQueue.remove(toRemove);
                if (isDebugOn) {
                    int lines = 0;
                    DebugInterface var11 = Bot.DEBUG;
                    String var13 = "banshees: " + GameCache.bansheeList.size();
                    i = lines + 1;
                    var11.debugTextOut(var13, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) lines) / 1080.0D)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("liberators: " + GameCache.liberatorList.size(), Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("ravens: " + GameCache.ravenList.size(), Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("vikings: " + GameCache.vikingList.size(), Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    Units var10002;
                    if (LocationConstants.opponentRace == Race.PROTOSS) {
                        Map var14 = GameCache.allEnemiesMap;
                        var10002 = Units.PROTOSS_TEMPEST;
                        Bot.DEBUG.debugTextOut("tempests: " + ((List) var14.getOrDefault(var10002, Collections.emptyList())).size(), Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    }

                    UnitInPool tempest = UnitUtils.getClosestEnemyUnitOfType(Units.PROTOSS_TEMPEST, ArmyManager.retreatPos);
                    if (tempest != null) {
                        Alliance var15 = Alliance.SELF;
                        var10002 = Units.TERRAN_VIKING_FIGHTER;
                        Bot.DEBUG.debugTextOut("vikings near tempest: " + UnitUtils.getUnitsNearbyOfType(var15, var10002, tempest.unit().getPosition().toPoint2d(), (float) Strategy.DIVE_RANGE).size(), Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    }

                    Bot.DEBUG.debugTextOut("vikings wanted: " + (double) ArmyManager.calcNumVikingsNeeded() * 0.7D, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("Purchase Queue: " + purchaseQueue.size(), Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("BaseTarget: " + LocationConstants.baseAttackIndex, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    Bot.DEBUG.debugTextOut("Switches.enemyCanProduceAir: " + Switches.enemyCanProduceAir, Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);

                    if (ArmyManager.attackPos != null) {
                        i = (int) ArmyManager.attackPos.getX();
                        int y = (int) ArmyManager.attackPos.getY();
                        Bot.DEBUG.debugBoxOut(Point.of((float) i - 0.3F, (float) y - 0.3F, Position.getZ((float) i, (float) y)), Point.of((float) i + 0.3F, (float) y + 0.3F, Position.getZ((float) i, (float) y)), Color.YELLOW);
                    }

                    for (i = 0; i < purchaseQueue.size() && i < 5; ++i) {
                        Bot.DEBUG.debugTextOut(purchaseQueue.get(i).getType(), Point2d.of(0.1F, (float) ((100.0D + 20.0D * (double) (i++)) / 1080.0D)), Color.WHITE, 12);
                    }

                    Bot.DEBUG.debugBoxOut(Point.of(LocationConstants.enemyMineralPos.getX() - 0.33F, LocationConstants.enemyMineralPos.getY() - 0.33F, Position.getZ(LocationConstants.enemyMineralPos)), Point.of(LocationConstants.enemyMineralPos.getX() + 0.33F, LocationConstants.enemyMineralPos.getY() + 0.33F, Position.getZ(LocationConstants.enemyMineralPos)), Color.BLUE);
                    Bot.DEBUG.sendDebug();
                }

                Bot.ACTION.sendActions();
            }
        } catch (Exception var6) {
            System.out.println("Bot.onStep() error At game frame: " + Bot.OBS.getGameLoop());
            var6.printStackTrace();
        }

    }

    private void printCurrentGameInfo() {
        PrintStream var10000 = System.out;
        String var10001 = this.convertGameLoopToStringTime(Bot.OBS.getGameLoop());
        var10000.println("\n\nGame info at " + var10001);
        System.out.println("===================\n");
        var10000 = System.out;
        int var4 = GameCache.liberatorList.size();
        var10000.println("GameState.liberatorList.size() = " + var4);
        var10000 = System.out;
        var4 = GameCache.siegeTankList.size();
        var10000.println("GameState.siegeTankList.size() = " + var4);
        var10000 = System.out;
        var4 = GameCache.vikingList.size();
        var10000.println("GameState.vikingList.size() = " + var4);
        var10000 = System.out;
        var4 = GameCache.bansheeList.size();
        var10000.println("GameState.bansheeList.size() = " + var4);
        boolean var5 = Strategy.DO_INCLUDE_LIBS;
        System.out.println("Strategy.DO_INCLUDE_LIBS = " + var5);
        var5 = Strategy.DO_INCLUDE_TANKS;
        System.out.println("Strategy.DO_INCLUDE_TANKS = " + var5);
        var4 = Strategy.maxScvs;
        System.out.println("Strategy.maxScvs = " + var4);
        var5 = Switches.enemyCanProduceAir;
        System.out.println("Switches.enemyCanProduceAir = " + var5);
        var5 = Switches.phoenixAreReal;
        System.out.println("Switches.phoenixAreReal = " + var5);
        var5 = Switches.isDivingTempests;
        System.out.println("Switches.isDivingTempests = " + var5);
        var5 = Switches.includeTanks;
        System.out.println("Switches.includeTanks = " + var5);
        var10000 = System.out;
        var10001 = Boolean.valueOf(Switches.vikingDiveTarget == null).toString();
        var10000.println("Switches.vikingDiveTarget == null? = " + var10001);
        System.out.println("Switches.bansheeDiveTarget == null? = " + Boolean.valueOf(Switches.bansheeDiveTarget == null).toString());
        System.out.println("UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER) = " + UnitUtils.getEnemyUnitsOfType(Units.TERRAN_VIKING_FIGHTER));
        System.out.println("UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST) = " + UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST));
        System.out.println("LocationConstants.STARPORTS.toString() = " + LocationConstants.STARPORTS.toString());
        System.out.println("LocationConstants.MACRO_OCS.toString() = " + LocationConstants.MACRO_OCS.toString());
        System.out.println("UpgradeManager.shipArmor.toString() = " + UpgradeManager.shipArmor.toString());
        System.out.println("UpgradeManager.shipAttack.toString() = " + UpgradeManager.shipAttack.toString());
        System.out.println("BansheeBot.purchaseQueue.size() = " + purchaseQueue.size());
        System.out.println("\n\n");

        for (int i = 0; i < GameCache.baseList.size(); ++i) {
            Base base = GameCache.baseList.get(i);
            System.out.println("\nBase " + i);
            if (base.isMyBase()) {
                System.out.println("isMyBase");
            }

            if (base.isEnemyBase) {
                System.out.println("isEnemyBase");
            }

            if (base.isUntakenBase()) {
                System.out.println("isUntakenBase()");
            }

            System.out.println("base.isDryedUp() = " + base.isDryedUp());
            QueryInterface var3 = Bot.QUERY;
            Abilities var10002 = Abilities.BUILD_COMMAND_CENTER;
            System.out.println("Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos()) = " + var3.placement(var10002, base.getCcPos()));
            System.out.println("base.lastScoutedFrame = " + base.lastScoutedFrame);
            var10000 = System.out;
            var10001 = Bot.OBS.getVisibility(base.getCcPos()).toString();
            var10000.println("Bot.OBS.getVisibility(base.getCcPos()).toString() = " + var10001);
        }

        System.out.println("\n\n");
    }

    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        PrintStream var10000;
        try {
            Unit unit = unitInPool.unit();
            var10000 = System.out;
            String var12 = unit.getType().toString();
            var10000.println(var12 + " = (" + unit.getPosition().getX() + ", " + unit.getPosition().getY() + ") at: " + this.currentGameTime());
            if (unit.getType() instanceof Units) {
                Units unitType = (Units) unit.getType();
                StructureScv.removeScvFromList(unitInPool.unit());
                List nearbyMarines;
                switch (unitType) {
                    case TERRAN_BARRACKS:
                        if (BunkerContain.proxyBunkerLevel != 0) {
                            BunkerContain.onBarracksComplete();
                        } else {
                            Point2d bunkerPos = Purchase.getPositionOfQueuedStructure(Units.TERRAN_BUNKER);
                            Point2d barracksRally;
                            if (bunkerPos != null) {
                                barracksRally = Position.towards(bunkerPos, unit.getPosition().toPoint2d(), 2.0F);
                            } else {
                                barracksRally = LocationConstants.insideMainWall;
                            }

                            Bot.ACTION.unitCommand(unit, Abilities.SMART, barracksRally, false);
                            if (Strategy.ANTI_NYDUS_BUILD) {
                                purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_BARRACKS, unit));
                            }
                        }

                        if (GameCache.baseList.get(0).getCc().map(UnitInPool::unit).map(Unit::getType).orElse(Units.INVALID) == Units.TERRAN_COMMAND_CENTER) {
                            if (BunkerContain.proxyBunkerLevel == 2) {
                                purchaseQueue.add(purchaseQueue.size() - 2, new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc().get()));
                            } else {
                                purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, GameCache.baseList.get(0).getCc().get()));
                            }
                        }

                        if (GameCache.factoryList.isEmpty() && !Purchase.isStructureQueued(Units.TERRAN_FACTORY) && !StructureScv.isAlreadyInProduction(Units.TERRAN_FACTORY)) {
                            purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.getFactoryPos()));
                        }
                        break;
                    case TERRAN_BARRACKS_TECHLAB:
                        Unit barracks = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BARRACKS).get(0);
                        int insertIndex = Purchase.isStructureQueued(Units.TERRAN_SUPPLY_DEPOT) ? 1 : 0;
                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
                        purchaseQueue.add(insertIndex, new PurchaseUnit(Units.TERRAN_MARAUDER, barracks));
                        break;
                    case TERRAN_BUNKER:
                        if (UnitUtils.getDistance(unit, LocationConstants.proxyBunkerPos) < 1.0F) {
                            BunkerContain.onBunkerComplete();
                        } else {
                            Bot.ACTION.unitCommand(unit, Abilities.SMART, LocationConstants.insideMainWall, false);
                            nearbyMarines = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_MARINE, unit.getPosition().toPoint2d(), 60.0F);
                            if (!nearbyMarines.isEmpty()) {
                                Bot.ACTION.unitCommand(UnitUtils.toUnitList(nearbyMarines), Abilities.SMART, unit, false);
                            }
                        }
                        break;
                    case TERRAN_ENGINEERING_BAY:
                        if (BunkerContain.proxyBunkerLevel == 2) {
                            BunkerContain.onEngineeringBayComplete(unitInPool);
                        }
                        break;
                    case TERRAN_FACTORY:
                        if (BunkerContain.proxyBunkerLevel == 2) {
                            BunkerContain.onFactoryComplete();
                        } else {
                            purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
                        }
                        break;
                    case TERRAN_FACTORY_TECHLAB:
                        if (BunkerContain.proxyBunkerLevel == 2) {
                            BunkerContain.onTechLabComplete();
                        }
                        break;
                    case TERRAN_STARPORT:
                        Bot.ACTION.unitCommand(unit, Abilities.RALLY_BUILDING, ArmyManager.retreatPos, false);
                        break;
                    case TERRAN_SUPPLY_DEPOT:
                        Bot.ACTION.unitCommand(unit, Abilities.MORPH_SUPPLY_DEPOT_LOWER, false);
                        break;
                    case TERRAN_ORBITAL_COMMAND:
                        Iterator var11 = GameCache.baseList.iterator();

                        Base base;
                        do {
                            if (!var11.hasNext()) {
                                return;
                            }

                            base = (Base) var11.next();
                        } while (!base.isMyBase() || base.getMineralPatches().isEmpty());

                        Bot.ACTION.unitCommand(unit, Abilities.RALLY_COMMAND_CENTER, base.getMineralPatches().get(0), false);
                        break;
                    case TERRAN_PLANETARY_FORTRESS:
                        nearbyMarines = Bot.OBS.getUnits(Alliance.NEUTRAL, (u) -> {
                            return UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(u.unit().getType()) && UnitUtils.getDistance(unit, u.unit()) < 10.0F;
                        });
                        if (!nearbyMarines.isEmpty()) {
                            Bot.ACTION.unitCommand(unit, Abilities.RALLY_COMMAND_CENTER, ((UnitInPool) nearbyMarines.get(0)).unit(), false);
                        }
                }
            }
        } catch (Exception var8) {
            var10000 = System.out;
            UnitType var10001 = unitInPool.unit().getType();
            var10000.println(var10001 + " at " + unitInPool.unit().getPosition().toPoint2d());
            var8.printStackTrace();
        }

    }

    public void onUnitIdle(UnitInPool unitInPool) {
    }

    public void onUnitCreated(UnitInPool unitInPool) {
    }

    public void onUnitDestroyed(UnitInPool unitInPool) {
        try {
            Unit unit = unitInPool.unit();
            Alliance alliance = unit.getAlliance();
            if (alliance == Alliance.SELF && unit.getBuildProgress() < 1.0F) {
                return;
            }

            if (unit.getType() instanceof Units) {
                switch (alliance) {
                    case SELF:
                        Iterator var4;
                        Base base;
                        Iterator var6;
                        DefenseUnitPositions libPos;
                        switch ((Units) unit.getType()) {
                            case TERRAN_BARRACKS:
                            case TERRAN_ENGINEERING_BAY:
                            case TERRAN_GHOST_ACADEMY:
                                LocationConstants._3x3Structures.add(unit.getPosition().toPoint2d());
                                purchaseQueue.addFirst(new PurchaseStructure((Units) unit.getType()));
                            case TERRAN_BARRACKS_TECHLAB:
                            case TERRAN_BUNKER:
                            case TERRAN_FACTORY_TECHLAB:
                            case TERRAN_ORBITAL_COMMAND:
                            case TERRAN_PLANETARY_FORTRESS:
                            default:
                                break;
                            case TERRAN_FACTORY:
                            case TERRAN_FACTORY_FLYING:
                                if (!LocationConstants.STARPORTS.isEmpty()) {
                                    purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, LocationConstants.STARPORTS.get(LocationConstants.STARPORTS.size() - 1)));
                                }
                                break;
                            case TERRAN_STARPORT:
                                LocationConstants.STARPORTS.add(unit.getPosition().toPoint2d());
                                break;
                            case TERRAN_SUPPLY_DEPOT:
                                LocationConstants.extraDepots.add(unit.getPosition().toPoint2d());
                                break;
                            case TERRAN_SIEGE_TANK:
                            case TERRAN_SIEGE_TANK_SIEGED:
                                var4 = GameCache.baseList.iterator();

                                while (var4.hasNext()) {
                                    base = (Base) var4.next();
                                    var6 = base.getTanks().iterator();

                                    while (var6.hasNext()) {
                                        libPos = (DefenseUnitPositions) var6.next();
                                        if (unit.getTag().equals(libPos.getUnit().map(UnitInPool::unit).map(Unit::getTag).orElse(null))) {
                                            libPos.setUnit(null);
                                        }
                                    }
                                }

                                return;
                            case TERRAN_LIBERATOR:
                            case TERRAN_LIBERATOR_AG:
                                var4 = GameCache.baseList.iterator();

                                while (var4.hasNext()) {
                                    base = (Base) var4.next();
                                    var6 = base.getLiberators().iterator();

                                    while (var6.hasNext()) {
                                        libPos = (DefenseUnitPositions) var6.next();
                                        if (unit.getTag().equals(libPos.getUnit().map(UnitInPool::unit).map(Unit::getTag).orElse(null))) {
                                            libPos.setUnit(null);
                                        }
                                    }
                                }
                        }
                }
            }
        } catch (Exception var8) {
            PrintStream var10000 = System.out;
            UnitType var10001 = unitInPool.unit().getType();
            var10000.println(var10001 + " at " + unitInPool.unit().getPosition().toPoint2d());
            var8.printStackTrace();
        }

    }

    public void onUpgradeCompleted(Upgrade upgrade) {
        System.out.println(upgrade + " finished at: " + this.currentGameTime());
        GameCache.upgradesCompleted.add((Upgrades) upgrade);
        switch ((Upgrades) upgrade) {
            case TERRAN_BUILDING_ARMOR:
                if (!Bot.OBS.getUpgrades().contains(Upgrades.HISEC_AUTO_TRACKING)) {
                    purchaseQueue.add(new PurchaseUpgrade(Upgrades.HISEC_AUTO_TRACKING, Bot.OBS.getUnit(((Unit) ((List) GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY)).get(0)).getTag())));
                }
                break;
            case BANSHEE_CLOAK:
                purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_SPEED, Bot.OBS.getUnit(((Unit) ((List) GameCache.allFriendliesMap.get(Units.TERRAN_STARPORT_TECHLAB)).get(0)).getTag())));
                break;
            case TERRAN_SHIP_WEAPONS_LEVEL1:
            case TERRAN_SHIP_WEAPONS_LEVEL2:
            case TERRAN_SHIP_WEAPONS_LEVEL3:
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL1:
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL2:
            case TERRAN_VEHICLE_AND_SHIP_ARMORS_LEVEL3:
                UpgradeManager.updateUpgradeList();
        }

    }

    public void onUnitEnterVision(UnitInPool unitInPool) {
    }

    public void onNydusDetected() {
        GameResult.setNydusRushed();
    }

    public void onNuclearLaunchDetected() {
    }

    public void onGameEnd() {
        this.setNextGameStrategy();
    }

    private void setNextGameStrategy() {
        Result result = Bot.OBS.getResults().stream().filter((playerResult) -> {
            return playerResult.getPlayerId() == Bot.OBS.getPlayerId();
        }).findFirst().get().getResult();
        Path path = Paths.get("./data/prevResult.txt");
        if (result == Result.DEFEAT) {
            ++Strategy.selectedStrategy;
        }

        try {
            Files.write(path, (Bot.opponentId + "~" + Strategy.selectedStrategy).getBytes());
        } catch (IOException var4) {
            var4.printStackTrace();
        }

        System.out.println("==========================");
        System.out.println("  Result: " + result.toString());
        System.out.println("==========================");
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

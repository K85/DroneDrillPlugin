package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.*;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseStructureMorph;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;
import com.ketroc.terranbot.strategies.BunkerContain;
import com.ketroc.terranbot.strategies.CannonRushDefense;
import com.ketroc.terranbot.strategies.Strategy;

import java.util.*;

public class BuildManager {
    public static final List<Abilities> BUILD_ACTIONS;

    static {
        BUILD_ACTIONS = Arrays.asList(Abilities.BUILD_REFINERY, Abilities.BUILD_COMMAND_CENTER, Abilities.BUILD_STARPORT, Abilities.BUILD_SUPPLY_DEPOT, Abilities.BUILD_ARMORY, Abilities.BUILD_BARRACKS, Abilities.BUILD_BUNKER, Abilities.BUILD_ENGINEERING_BAY, Abilities.BUILD_FACTORY, Abilities.BUILD_FUSION_CORE, Abilities.BUILD_GHOST_ACADEMY, Abilities.BUILD_MISSILE_TURRET, Abilities.BUILD_SENSOR_TOWER);
    }

    public static void onStep() {
        build2ndLayerOfTech();
        cancelStructureLogic();
        buildDepotLogic();
        buildTurretLogic();
        ccActivityLogic();
        saveDyingCCs();
        if (BunkerContain.proxyBunkerLevel == 0) {
            buildBarracksUnitsLogic();
        }

        if (BunkerContain.proxyBunkerLevel != 2) {
            if (Strategy.DO_INCLUDE_TANKS) {
                buildFactoryUnitsLogic();
            } else if (!UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_FACTORY).isEmpty()) {
                liftFactory();
            }
        }

        buildStarportUnitsLogic();
        buildStarportLogic();
        buildCCLogic();
    }

    private static void build2ndLayerOfTech() {
        if (!Strategy.techBuilt && Base.numMyBases() >= 4) {
            BansheeBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(((Unit) ((List) GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY)).get(0)).getTag())));
            BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
            BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ARMORY));
            if (LocationConstants.opponentRace == Race.ZERG) {
                BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(0)));
                BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(1)));
            }

            Strategy.techBuilt = true;
        }

    }

    private static void cancelStructureLogic() {
        Iterator var0 = GameCache.inProductionList.iterator();

        while (var0.hasNext()) {
            Unit structure = (Unit) var0.next();
            if (structure.getBuildProgress() < 1.0F && UnitUtils.getHealthPercentage(structure) < 8) {
                Bot.ACTION.unitCommand(structure, Abilities.CANCEL_BUILD_IN_PROGRESS, false);
            }
        }

    }

    private static void buildDepotLogic() {
        if (GameCache.mineralBank > 100 && checkIfDepotNeeded() && !LocationConstants.extraDepots.isEmpty()) {
            BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
        }

    }

    private static void buildTurretLogic() {
        int turretsRequired = 0;
        if (!UnitUtils.getEnemyUnitsOfType(Units.ZERG_MUTALISK).isEmpty()) {
            turretsRequired = 3;
        } else if (Switches.enemyCanProduceAir || Switches.enemyHasCloakThreat || Bot.OBS.getGameLoop() > 6000L) {
            turretsRequired = 1;
        }

        if (turretsRequired > 0) {
            Iterator var1 = GameCache.baseList.iterator();

            while (true) {
                Base base;
                do {
                    do {
                        do {
                            if (!var1.hasNext()) {
                                if (Switches.doBuildMainBaseTurrets && (LocationConstants.opponentRace == Race.PROTOSS || LocationConstants.opponentRace == Race.TERRAN)) {
                                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(0)));
                                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, LocationConstants.TURRETS.get(1)));
                                    Switches.doBuildMainBaseTurrets = false;
                                }

                                return;
                            }

                            base = (Base) var1.next();
                        } while (!base.isMyBase());
                    } while (base.isMyMainBase());
                } while (!base.isComplete());

                for (int i = 0; i < turretsRequired; ++i) {
                    DefenseUnitPositions turret = base.getTurrets().get(i);
                    if (turret.getUnit().isEmpty() && !Purchase.isStructureQueued(Units.TERRAN_MISSILE_TURRET, turret.getPos()) && !StructureScv.isAlreadyInProductionAt(Units.TERRAN_MISSILE_TURRET, turret.getPos())) {
                        BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, turret.getPos()));
                    }
                }
            }
        }
    }

    private static void ccActivityLogic() {
        Iterator var0 = GameCache.ccList.iterator();

        while (true) {
            Unit cc;
            label111:
            while (true) {
                do {
                    do {
                        if (!var0.hasNext()) {
                            return;
                        }

                        cc = (Unit) var0.next();
                    } while (cc.getBuildProgress() != 1.0F);
                } while (cc.getActive().get());

                switch ((Units) cc.getType()) {
                    case TERRAN_COMMAND_CENTER:
                        if (ccToBeOC(cc.getPosition().toPoint2d())) {
                            if (UnitUtils.hasTechToBuild(Units.TERRAN_ORBITAL_COMMAND)) {
                                if (UnitUtils.getDistance(cc, LocationConstants.baseLocations.get(0)) > 1.0F && isNeededForExpansion()) {
                                    Base nextFreeBase = GameCache.baseList.stream().filter((basex) -> {
                                        return basex.isUntakenBase();
                                    }).findFirst().orElse(null);
                                    if (nextFreeBase == null) {
                                        break;
                                    }

                                    Point2d floatTo = nextFreeBase.getCcPos();
                                    FlyingCC.addFlyingCC(cc, floatTo, false);
                                    LocationConstants.MACRO_OCS.add(cc.getPosition().toPoint2d());
                                    GameCache.baseList.stream().filter((basex) -> {
                                        return basex.isUntakenBase();
                                    }).findFirst().get().setCc(Bot.OBS.getUnit(cc.getTag()));
                                    int i = 0;

                                    while (true) {
                                        if (i >= BansheeBot.purchaseQueue.size()) {
                                            continue label111;
                                        }

                                        Purchase p = BansheeBot.purchaseQueue.get(i);
                                        if (p instanceof PurchaseStructureMorph && ((PurchaseStructureMorph) p).getStructure().getTag().equals(cc.getTag())) {
                                            BansheeBot.purchaseQueue.remove(i);
                                            continue label111;
                                        }

                                        ++i;
                                    }
                                }

                                if (!isMorphQueued(Abilities.MORPH_ORBITAL_COMMAND)) {
                                    BansheeBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_ORBITAL_COMMAND, cc));
                                }
                                break;
                            }
                        } else if (UnitUtils.hasTechToBuild(Units.TERRAN_PLANETARY_FORTRESS)) {
                            if (!isMorphQueued(Abilities.MORPH_PLANETARY_FORTRESS)) {
                                BansheeBot.purchaseQueue.addFirst(new PurchaseStructureMorph(Abilities.MORPH_PLANETARY_FORTRESS, cc));
                            }
                            break;
                        }

                        if (Bot.OBS.getMinerals() >= 50 && Bot.OBS.getFoodWorkers() < Math.min(Base.totalScvsRequiredForMyBases() + 10, Strategy.maxScvs)) {
                            Bot.ACTION.unitCommand(cc, Abilities.TRAIN_SCV, false);
                            Cost.updateBank(Units.TERRAN_SCV);
                        }
                        break;
                    case TERRAN_ORBITAL_COMMAND:
                        if (cc.getEnergy().get() >= (float) Strategy.energyToMuleAt) {
                            if (LocationConstants.opponentRace == Race.PROTOSS && !Switches.scoutScanComplete && Bot.OBS.getGameLoop() > 6000L) {
                                Bot.ACTION.unitCommand(cc, Abilities.EFFECT_SCAN, Position.towards(LocationConstants.enemyMainBaseMidPos, LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1), 3.0F), false);
                                Switches.scoutScanComplete = true;
                                int delay = 120;
                                Iterator var7 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).iterator();

                                while (true) {
                                    if (!var7.hasNext()) {
                                        break label111;
                                    }

                                    Unit marine = (Unit) var7.next();
                                    DelayedAction.delayedActions.add(new DelayedAction(delay, Abilities.MOVE, Bot.OBS.getUnit(marine.getTag()), GameCache.baseList.get(GameCache.baseList.size() - 1).getCcPos()));
                                    delay += 60;
                                }
                            }

                            boolean didMule = false;

                            for (int i = GameCache.baseList.size() - 1; i >= 0; --i) {
                                Base base = GameCache.baseList.get(i);
                                if (base.isMyBase() && base.getCc().map(UnitInPool::unit).map(Unit::getType).orElse(Units.INVALID) == Units.TERRAN_PLANETARY_FORTRESS && base.getCc().map(UnitInPool::unit).map(Unit::getBuildProgress).orElse(0.0F) == 1.0F && !base.getMineralPatches().isEmpty()) {
                                    Bot.ACTION.unitCommand(cc, Abilities.EFFECT_CALL_DOWN_MULE, base.getMineralPatches().get(0), false);
                                    didMule = true;
                                    break;
                                }
                            }

                            if (!didMule) {
                                Unit finalCc = cc;
                                Unit finalCc1 = cc;
                                Bot.OBS.getUnits(Alliance.NEUTRAL, (node) -> {
                                    return UnitUtils.MINERAL_NODE_TYPE.contains(node.unit().getType()) && node.unit().getDisplayType() == DisplayType.VISIBLE;
                                }).stream().min(Comparator.comparing((u) -> {
                                    return UnitUtils.getDistance(u.unit(), finalCc);
                                })).map(UnitInPool::unit).ifPresent((nearestMineral) -> {
                                    Bot.ACTION.unitCommand(finalCc1, Abilities.EFFECT_CALL_DOWN_MULE, nearestMineral, false);
                                });
                            }
                        }
                    case TERRAN_PLANETARY_FORTRESS:
                        break label111;
                }
            }

            if (Bot.OBS.getFoodWorkers() < Math.min(Base.totalScvsRequiredForMyBases() + 10, Strategy.maxScvs)) {
                Bot.ACTION.unitCommand(cc, Abilities.TRAIN_SCV, false);
                Cost.updateBank(Units.TERRAN_SCV);
            }
        }
    }

    private static boolean isNeededForExpansion() {
        return !UnitUtils.wallUnderAttack() && CannonRushDefense.isSafe && Base.totalScvsRequiredForMyBases() < Math.min(Strategy.maxScvs, Bot.OBS.getFoodWorkers() + 5);
    }

    private static void saveDyingCCs() {
        if (!LocationConstants.MACRO_OCS.isEmpty()) {
            Iterator var0 = GameCache.baseList.iterator();

            while (true) {
                while (true) {
                    while (true) {
                        Base base;
                        Unit cc = null;
                        Unit finalCc = cc;
                        do {
                            do {
                                do {
                                    do {
                                        do {
                                            if (!var0.hasNext()) {
                                                return;
                                            }

                                            base = (Base) var0.next();
                                        } while (!base.isMyBase());

                                        cc = base.getCc().get().unit();
                                    } while (cc.getType() != Units.TERRAN_COMMAND_CENTER);
                                } while (cc.getBuildProgress() != 1.0F);
                            } while (UnitUtils.getHealthPercentage(cc) >= Strategy.floatBaseAt);
                        } while (Bot.OBS.getUnits(Alliance.ENEMY, (u) -> {
                            return UnitUtils.getDistance(u.unit(), finalCc) <= 10.0F && UnitUtils.doesAttackGround(u.unit());
                        }).isEmpty());

                        if (cc.getOrders().isEmpty() && !LocationConstants.MACRO_OCS.isEmpty()) {
                            FlyingCC.addFlyingCC(cc, LocationConstants.MACRO_OCS.remove(0), true);
                            base.setCc(null);

                            for (int i = 0; i < BansheeBot.purchaseQueue.size(); ++i) {
                                Purchase p = BansheeBot.purchaseQueue.get(i);
                                if (p instanceof PurchaseStructureMorph && ((PurchaseStructureMorph) p).getStructure().getTag().equals(cc.getTag())) {
                                    BansheeBot.purchaseQueue.remove(i);
                                    break;
                                }
                            }
                        } else if (cc.getOrders().get(0).getAbility() == Abilities.MORPH_PLANETARY_FORTRESS) {
                            Bot.ACTION.unitCommand(cc, Abilities.CANCEL_MORPH_PLANETARY_FORTRESS, false);
                        } else {
                            Bot.ACTION.unitCommand(cc, Abilities.CANCEL_LAST, false);
                        }
                    }
                }
            }
        }
    }

    private static void buildBarracksUnitsLogic() {
        if (!GameCache.barracksList.isEmpty() && !(Boolean) GameCache.barracksList.get(0).unit().getActive().get()) {
            Unit barracks = GameCache.barracksList.get(0).unit();
            if (Strategy.ANTI_NYDUS_BUILD && UnitUtils.getDistance(barracks, LocationConstants.MID_WALL_3x3) > 1.0F) {
                if (UnitUtils.getNumUnits(Units.TERRAN_MARAUDER, false) >= 2) {
                    if (barracks.getType() == Units.TERRAN_BARRACKS) {
                        LocationConstants.STARPORTS.add(0, barracks.getPosition().toPoint2d());
                        Bot.ACTION.unitCommand(barracks, Abilities.LIFT, false);
                    } else if (barracks.getOrders().isEmpty()) {
                        Bot.ACTION.unitCommand(barracks, Abilities.LAND, LocationConstants.MID_WALL_3x3, false);
                    }
                }
            } else if (!GameCache.baseList.get(1).isMyBase() || !GameCache.baseList.get(1).getCc().isPresent() || GameCache.baseList.get(1).getCc().get().unit().getType() != Units.TERRAN_PLANETARY_FORTRESS) {
                if (UnitUtils.wallUnderAttack()) {
                    if (UnitUtils.canAfford(Units.TERRAN_MARINE)) {
                        Bot.ACTION.unitCommand(barracks, Abilities.TRAIN_MARINE, false);
                        Cost.updateBank(Units.TERRAN_MARINE);
                    }

                    return;
                }

                if (UnitUtils.getNumUnits(Units.TERRAN_MARAUDER, false) > 0) {
                    return;
                }

                int marineCount = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE).size();

                Unit bunker;
                for (Iterator var2 = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_BUNKER).iterator(); var2.hasNext(); marineCount += bunker.getCargoSpaceTaken().orElse(0)) {
                    bunker = (Unit) var2.next();
                }

                if ((marineCount < 2 || marineCount < 4 && LocationConstants.opponentRace == Race.TERRAN) && Bot.OBS.getMinerals() >= 50 && Bot.OBS.getMinerals() >= 50) {
                    Bot.ACTION.unitCommand(barracks, Abilities.TRAIN_MARINE, false);
                    Cost.updateBank(Units.TERRAN_MARINE);
                }
            }
        }

    }

    private static void buildFactoryUnitsLogic() {
        if (!GameCache.factoryList.isEmpty()) {
            Unit factory = GameCache.factoryList.get(0).unit();
            if (!(Boolean) factory.getActive().get()) {
                if (factory.getAddOnTag().isPresent()) {
                    if (GameCache.siegeTankList.size() < Math.min(12, 2 * (Base.numMyBases() - 1)) && UnitUtils.canAfford(Units.TERRAN_SIEGE_TANK)) {
                        Bot.ACTION.unitCommand(factory, Abilities.TRAIN_SIEGE_TANK, false);
                        Cost.updateBank(Units.TERRAN_SIEGE_TANK);
                    }
                } else if (!Purchase.isMorphQueued(Abilities.BUILD_TECHLAB_FACTORY)) {
                    BansheeBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_FACTORY, factory));
                    Bot.ACTION.unitCommand(factory, Abilities.RALLY_BUILDING, LocationConstants.insideMainWall, false);
                }
            }
        }

    }

    public static void liftFactory() {
        UnitInPool factory = GameCache.factoryList.get(0);
        if (factory.unit().getBuildProgress() == 1.0F) {
            if (factory.unit().getActive().orElse(true)) {
                Bot.ACTION.unitCommand(factory.unit(), Abilities.CANCEL_LAST, false);
            } else {
                Point2d behindMainBase = Position.towards(GameCache.baseList.get(0).getCcPos(), GameCache.baseList.get(0).getResourceMidPoint(), 10.0F);
                if (BunkerContain.proxyBunkerLevel == 2) {
                    BunkerContain.onFactoryLift();
                }

                Bot.ACTION.unitCommand(factory.unit(), Abilities.LIFT, false);
                DelayedAction.delayedActions.add(new DelayedAction(1, Abilities.MOVE, factory, behindMainBase));
                Point2d factoryPos = factory.unit().getPosition().toPoint2d();
                if (InfluenceMaps.getValue(InfluenceMaps.pointInMainBase, factoryPos)) {
                    LocationConstants.STARPORTS.add(0, factoryPos);
                }
            }
        }

    }

    private static void buildStarportUnitsLogic() {
        Iterator var0 = GameCache.starportList.iterator();

        while (true) {
            while (true) {
                UnitInPool starport;
                Abilities unitToProduce;
                Units unitType;
                do {
                    do {
                        if (!var0.hasNext()) {
                            return;
                        }

                        starport = (UnitInPool) var0.next();
                    } while (starport.unit().getActive().get());

                    unitToProduce = decideStarportUnit();
                    unitType = Bot.abilityToUnitType.get(unitToProduce);
                } while (!UnitUtils.canAfford(unitType));

                if (starport.unit().getAddOnTag().isEmpty() && (unitToProduce == Abilities.TRAIN_RAVEN || unitToProduce == Abilities.TRAIN_BANSHEE || unitToProduce == Abilities.TRAIN_BATTLECRUISER) && !Purchase.isStructureQueued(Units.TERRAN_STARPORT_TECHLAB)) {
                    BansheeBot.purchaseQueue.add(new PurchaseStructureMorph(Abilities.BUILD_TECHLAB_STARPORT, starport));
                } else {
                    if (unitToProduce == Abilities.TRAIN_BANSHEE && !Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) && !Purchase.isUpgradeQueued(Upgrades.BANSHEE_CLOAK) && !isCloakInProduction()) {
                        Unit availableTechLab = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream().filter((techLab) -> {
                            return techLab.getOrders().isEmpty();
                        }).findFirst().get();
                        BansheeBot.purchaseQueue.add(new PurchaseUpgrade(Upgrades.BANSHEE_CLOAK, Bot.OBS.getUnit(availableTechLab.getTag())));
                    }

                    Bot.ACTION.unitCommand(starport.unit(), unitToProduce, false);
                    Cost.updateBank(unitType);
                }
            }
        }
    }

    private static boolean isCloakInProduction() {
        return UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_STARPORT_TECHLAB).stream().anyMatch((techLab) -> {
            return !techLab.getOrders().isEmpty() && techLab.getOrders().get(0).getAbility() == Abilities.RESEARCH_BANSHEE_CLOAKING_FIELD;
        });
    }

    public static Abilities decideStarportUnit() {
        int numBanshees = UnitUtils.getNumUnits(Units.TERRAN_BANSHEE, true);
        int numRavens = UnitUtils.getNumUnits(Units.TERRAN_RAVEN, true);
        int numVikings = UnitUtils.getNumUnits(Units.TERRAN_VIKING_FIGHTER, true);
        int numLiberators = UnitUtils.getNumUnits(Units.TERRAN_LIBERATOR, true) + UnitUtils.getNumUnits(Units.TERRAN_LIBERATOR_AG, false);
        int vikingsRequired = ArmyManager.calcNumVikingsNeeded();
        int vikingsByRatio = (int) ((float) numBanshees * Strategy.VIKING_BANSHEE_RATIO);
        if (vikingsByRatio > vikingsRequired) {
            vikingsRequired = Math.min((int) ((double) vikingsRequired * 1.8D), vikingsByRatio);
        }

        int ravensRequired = LocationConstants.opponentRace == Race.ZERG ? 2 : 1;
        if (Bot.OBS.getFoodUsed() >= 196 && numRavens == 0) {
            return Abilities.TRAIN_RAVEN;
        } else if (numRavens == 0 && Switches.enemyHasCloakThreat) {
            return Abilities.TRAIN_RAVEN;
        } else if (numVikings < vikingsRequired) {
            return Abilities.TRAIN_VIKING_FIGHTER;
        } else if (numBanshees < 1) {
            return Abilities.TRAIN_BANSHEE;
        } else {
            if (LocationConstants.opponentRace == Race.ZERG) {
                if (numRavens < 1) {
                    return Abilities.TRAIN_RAVEN;
                }

                if (numVikings < 1) {
                    return Abilities.TRAIN_VIKING_FIGHTER;
                }
            }

            if (Strategy.DO_INCLUDE_LIBS && numLiberators < Math.min(12, 2 * (Base.numMyBases() - 1))) {
                return Abilities.TRAIN_LIBERATOR;
            } else if (numRavens == 0 && numBanshees > 0 && numVikings > 0 && !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_OBSERVER).isEmpty()) {
                return Abilities.TRAIN_RAVEN;
            } else {
                if (LocationConstants.opponentRace == Race.ZERG) {
                    if (numBanshees + numVikings > 15 && numRavens < ravensRequired) {
                        return Abilities.TRAIN_RAVEN;
                    }
                } else if (numRavens < ravensRequired && numBanshees + numVikings >= 6) {
                    return Abilities.TRAIN_RAVEN;
                }

                return Abilities.TRAIN_BANSHEE;
            }
        }
    }

    private static void buildCCLogic() {
        if (GameCache.mineralBank > 400 && !Purchase.isStructureQueued(Units.TERRAN_COMMAND_CENTER) && (Base.numMyBases() < LocationConstants.baseLocations.size() - 2 || !LocationConstants.MACRO_OCS.isEmpty())) {
            addCCToPurchaseQueue();
        }

    }

    private static void buildStarportLogic() {
        if (UnitUtils.canAfford(Units.TERRAN_STARPORT) && UnitUtils.hasTechToBuild(Units.TERRAN_STARPORT) && !LocationConstants.STARPORTS.isEmpty() && (Bot.OBS.getFoodUsed() > 197 || GameCache.inProductionMap.getOrDefault(Units.TERRAN_STARPORT, 0) < 3 && areAllProductionStructuresBusy())) {
            BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_STARPORT));
        }

    }

    private static boolean areAllProductionStructuresBusy() {
        return GameCache.starportList.stream().noneMatch((u) -> {
            return u.unit().getOrders().isEmpty() || u.unit().getOrders().get(0).getAbility() == Abilities.BUILD_TECHLAB || u.unit().getOrders().get(0).getProgress().orElse(0.0F) > 0.8F;
        }) && GameCache.factoryList.stream().noneMatch((u) -> {
            return u.unit().getType() == Units.TERRAN_FACTORY && (u.unit().getOrders().isEmpty() || u.unit().getOrders().get(0).getAbility() == Abilities.BUILD_TECHLAB || u.unit().getOrders().get(0).getProgress().orElse(0.0F) > 0.8F);
        });
    }

    private static void addCCToPurchaseQueue() {
        int scvsForMaxSaturation = Base.totalScvsRequiredForMyBases();
        int numScvs = Bot.OBS.getFoodWorkers();
        if (!UnitUtils.wallUnderAttack() && CannonRushDefense.isSafe) {
            if (Math.min(numScvs + 25, Strategy.maxScvs) <= scvsForMaxSaturation) {
                if (!purchaseMacroCC()) {
                    purchaseExpansionCC();
                }
            } else if (!purchaseExpansionCC()) {
                purchaseMacroCC();
            }
        } else {
            purchaseMacroCC();
        }

    }

    private static boolean purchaseExpansionCC() {
        Optional<Point2d> expansionPos = getNextAvailableExpansionPosition();
        if (expansionPos.isPresent()) {
            BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, expansionPos.get()));
            List<Unit> marines = GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_MARINE, Collections.emptyList());
            if (!marines.isEmpty()) {
                Bot.ACTION.unitCommand(marines, Abilities.ATTACK, expansionPos.get(), true);
            }
        }

        return expansionPos.isPresent();
    }

    private static Optional<Point2d> getNextAvailableExpansionPosition() {
        return GameCache.baseList.subList(0, GameCache.baseList.size() - getNumEnemyBasesIgnored()).stream().filter((base) -> {
            return base.isUntakenBase() && !base.isDryedUp() && Bot.QUERY.placement(Abilities.BUILD_COMMAND_CENTER, base.getCcPos());
        }).findFirst().map(Base::getCcPos);
    }

    public static int getNumEnemyBasesIgnored() {
        return LocationConstants.MACRO_OCS.isEmpty() ? 2 : 5;
    }

    private static boolean purchaseMacroCC() {
        if (!LocationConstants.MACRO_OCS.isEmpty()) {
            BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER, LocationConstants.MACRO_OCS.remove(0)));
            ++GameCache.numMacroOCs;
            return true;
        } else {
            return false;
        }
    }

    public static boolean ccToBeOC(Point2d ccPos) {
        for (int i = 1; i < LocationConstants.baseLocations.size(); ++i) {
            if (ccPos.distance(LocationConstants.baseLocations.get(i)) < 1.0D) {
                return false;
            }
        }

        return true;
    }

    private static boolean checkIfDepotNeeded() {
        if (Purchase.isStructureQueued(Units.TERRAN_SUPPLY_DEPOT)) {
            return false;
        } else {
            int curSupply = Bot.OBS.getFoodUsed();
            int supplyCap = Bot.OBS.getFoodCap();
            if (supplyCap == 200) {
                return false;
            } else {
                return supplyCap - curSupply + supplyInProduction() < supplyPerProductionCycle();
            }
        }
    }

    private static int supplyPerProductionCycle() {
        return Math.min(Strategy.maxScvs - Bot.OBS.getFoodWorkers(), Base.numMyBases()) * 2 + GameCache.starportList.size() * 2;
    }

    private static int supplyInProduction() {
        int supply = 8 * (int) StructureScv.scvBuildingList.stream().filter((scvx) -> {
            return scvx.buildAbility == Abilities.BUILD_SUPPLY_DEPOT;
        }).count();
        Iterator var1 = StructureScv.scvBuildingList.iterator();

        while (var1.hasNext()) {
            StructureScv scv = (StructureScv) var1.next();
            List<UnitInPool> cc = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_COMMAND_CENTER, scv.structurePos, 1.0F);
            if (!cc.isEmpty() && (double) cc.get(0).unit().getBuildProgress() > 0.4D) {
                supply += 14;
            }
        }

        return supply;
    }

    public static int numStructuresQueued(Units structureType) {
        int count = 0;
        Iterator var2 = BansheeBot.purchaseQueue.iterator();

        while (var2.hasNext()) {
            Purchase p = (Purchase) var2.next();
            if (p instanceof PurchaseStructure && ((PurchaseStructure) p).getStructureType().equals(structureType)) {
                ++count;
            }
        }

        return count;
    }

    public static boolean isMorphQueued(Abilities morphType) {
        Iterator var1 = BansheeBot.purchaseQueue.iterator();

        Purchase p;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            p = (Purchase) var1.next();
        } while (!(p instanceof PurchaseStructureMorph) || ((PurchaseStructureMorph) p).getMorphOrAddOn() != morphType);

        return true;
    }

    public static boolean isUpgradeQueued(Upgrades upgrade) {
        Iterator var1 = BansheeBot.purchaseQueue.iterator();

        Purchase p;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            p = (Purchase) var1.next();
        } while (!(p instanceof PurchaseUpgrade) || ((PurchaseUpgrade) p).getUpgrade() != upgrade);

        return true;
    }

    public static List<Point2d> calculateTurretPositions(Point2d ccPos) {
        float xCC = ccPos.getX();
        float yCC = ccPos.getY();
        float xEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1).getX();
        float yEnemy = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1).getY();
        float xDistance = xEnemy - xCC;
        float yDistance = yEnemy - yCC;
        float xMove = 1.5F;
        float yMove = 1.5F;
        if (Math.abs(xDistance) > Math.abs(yDistance)) {
            yMove = 3.5F;
        } else {
            xMove = 3.5F;
        }

        float xTurret1 = xCC + xMove;
        float xTurret2 = xCC - xMove;
        float yTurret1;
        float yTurret2;
        if (xDistance * yDistance > 0.0F) {
            yTurret1 = yCC - yMove;
            yTurret2 = yCC + yMove;
        } else {
            yTurret1 = yCC + yMove;
            yTurret2 = yCC - yMove;
        }

        return List.of(Point2d.of(xTurret1, yTurret1), Point2d.of(xTurret2, yTurret2));
    }

    public static boolean isPlaceable(Point2d pos, Abilities buildAction) {
        if (Bot.OBS.hasCreep(pos)) {
            return false;
        } else {
            float distance = getStructureRadius(buildAction);
            return Bot.OBS.getUnits(Alliance.ENEMY, (enemy) -> {
                return UnitUtils.getDistance(enemy.unit(), pos) < distance && !(Boolean) enemy.unit().getFlying().orElse(false);
            }).isEmpty();
        }
    }

    public static float getStructureRadius(Abilities buildAction) {
        StructureSize size = getSize(buildAction);
        switch (size) {
            case _1x1:
                return 0.3F;
            case _2x2:
                return 0.7F;
            case _3x3:
                return 1.1F;
            default:
                return 2.2F;
        }
    }

    public static StructureSize getSize(Abilities buildAction) {
        switch (buildAction) {
            case BUILD_COMMAND_CENTER:
                return StructureSize._5x5;
            case BUILD_ENGINEERING_BAY:
            case BUILD_BARRACKS:
            case BUILD_BUNKER:
            case BUILD_ARMORY:
            case BUILD_FACTORY:
            case BUILD_STARPORT:
            case BUILD_FUSION_CORE:
            case BUILD_GHOST_ACADEMY:
                return StructureSize._3x3;
            case BUILD_MISSILE_TURRET:
            case BUILD_SUPPLY_DEPOT:
                return StructureSize._2x2;
            default:
                return StructureSize._1x1;
        }
    }
}

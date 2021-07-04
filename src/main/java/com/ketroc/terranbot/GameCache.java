package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat.Channel;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.data.Units.Other;
import com.github.ocraft.s2client.protocol.data.Weapon.TargetType;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.DefenseUnitPositions;
import com.ketroc.terranbot.models.EnemyScan;
import com.ketroc.terranbot.models.EnemyUnit;
import com.ketroc.terranbot.models.FlyingCC;
import com.ketroc.terranbot.models.Gas;
import com.ketroc.terranbot.models.Ignored;
import com.ketroc.terranbot.models.Infestor;
import com.ketroc.terranbot.strategies.Strategy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GameCache {
   public static int mineralBank;
   public static int gasBank;
   public static int freeSupply;
   public static final List<Upgrades> upgradesCompleted = new ArrayList();
   public static int numMacroOCs = 0;
   public static final List<Unit> ccList = new ArrayList();
   public static final List<UnitInPool> availableScvs = new ArrayList();
   public static final List<UnitInPool> allScvs = new ArrayList();
   public static final List<UnitInPool> barracksList = new ArrayList();
   public static final List<UnitInPool> factoryList = new ArrayList();
   public static final List<UnitInPool> starportList = new ArrayList();
   public static final List<Unit> refineryList = new ArrayList();
   public static final List<Unit> siegeTankList = new ArrayList();
   public static final List<Unit> liberatorList = new ArrayList();
   public static final List<Unit> bansheeList = new ArrayList();
   public static final List<Unit> ravenList = new ArrayList();
   public static final List<Unit> vikingList = new ArrayList();
   public static final List<Unit> mineralNodeList = new ArrayList();
   public static final List<Unit> geyserList = new ArrayList();
   public static final List<Base> baseList = new ArrayList();
   public static final List<Unit> inProductionList = new ArrayList();
   public static final Map<Units, Integer> inProductionMap = new HashMap();
   public static final List<Unit> bansheeDivers = new ArrayList();
   public static final List<Unit> vikingDivers = new ArrayList();
   public static final Map<Units, List<Unit>> allVisibleEnemiesMap = new HashMap();
   public static final List<UnitInPool> allEnemiesList = new ArrayList();
   public static final Map<Units, List<UnitInPool>> allEnemiesMap = new HashMap();
   public static final List<Unit> enemyAttacksAir = new ArrayList();
   public static final List<Unit> enemyIsGround = new ArrayList();
   public static final List<Unit> enemyIsAir = new ArrayList();
   public static final List<Unit> enemyDetector = new ArrayList();
   public static final List<EnemyUnit> enemyMappingList = new ArrayList();
   public static final Map<Units, List<Unit>> allFriendliesMap = new HashMap();
   public static Unit defaultRallyNode;
   public static List<Unit> wallStructures = new ArrayList();
   public static List<Unit> burningStructures = new ArrayList();

   public static void onStep() throws Exception {
      mineralBank = Bot.OBS.getMinerals();
      gasBank = Bot.OBS.getVespene();
      freeSupply = Bot.OBS.getFoodCap() - Bot.OBS.getFoodUsed();
      defaultRallyNode = null;
      refineryList.clear();
      geyserList.clear();
      mineralNodeList.clear();
      ccList.clear();
      vikingList.clear();
      siegeTankList.clear();
      liberatorList.clear();
      bansheeList.clear();
      ravenList.clear();
      bansheeDivers.clear();
      vikingDivers.clear();
      starportList.clear();
      factoryList.clear();
      barracksList.clear();
      availableScvs.clear();
      allScvs.clear();
      allFriendliesMap.clear();
      allVisibleEnemiesMap.clear();
      inProductionList.clear();
      inProductionMap.clear();
      allEnemiesMap.clear();
      enemyAttacksAir.clear();
      enemyDetector.clear();
      enemyIsGround.clear();
      enemyIsAir.clear();
      enemyMappingList.clear();
      wallStructures.clear();
      burningStructures.clear();

      UnitInPool unitInPool;
      for(int i = 0; i < allEnemiesList.size(); ++i) {
         unitInPool = (UnitInPool)allEnemiesList.get(i);
         if (UnitUtils.isVisible(unitInPool) || unitInPool.unit().getDisplayType() == DisplayType.SNAPSHOT || !unitInPool.isAlive()) {
            allEnemiesList.remove(i--);
         }
      }

      Iterator var8 = Bot.OBS.getUnits().iterator();

      while(true) {
         label296:
         while(true) {
            Unit unit;
            do {
               if (!var8.hasNext()) {
                  Units unitType;
                  for(var8 = allEnemiesList.iterator(); var8.hasNext(); ((List)allEnemiesMap.get(unitType)).add(unitInPool)) {
                     unitInPool = (UnitInPool)var8.next();
                     unitType = (Units)unitInPool.unit().getType();
                     if (!allEnemiesMap.containsKey(unitType)) {
                        allEnemiesMap.put(unitType, new ArrayList());
                     }
                  }

                  List<UnitInPool> enemyCCs = Bot.OBS.getUnits(Alliance.ENEMY, (enemyCC) -> {
                     return UnitUtils.enemyCommandStructures.contains(enemyCC.unit().getType());
                  });
                  Iterator var10 = baseList.iterator();

                  while(true) {
                     Base base;
                     do {
                        do {
                           do {
                              do {
                                 if (!var10.hasNext()) {
                                    if (defaultRallyNode == null) {
                                       defaultRallyNode = UnitUtils.getClosestUnitOfType(Alliance.NEUTRAL, UnitUtils.MINERAL_NODE_TYPE, (Point2d)LocationConstants.baseLocations.get(0));
                                    }

                                    var10 = Bot.OBS.getEffects().iterator();

                                    while(var10.hasNext()) {
                                       EffectLocations effect = (EffectLocations)var10.next();
                                       if (effect.getAlliance().orElse(Alliance.SELF) == Alliance.ENEMY) {
                                          switch((Effects)effect.getEffect()) {
                                          case SCANNER_SWEEP:
                                             if (!EnemyScan.contains(effect)) {
                                                EnemyScan.add(effect);
                                             }
                                             break;
                                          case RAVAGER_CORROSIVE_BILE_CP:
                                          case PSI_STORM_PERSISTENT:
                                             enemyMappingList.add(new EnemyUnit(effect));
                                          }
                                       }
                                    }

                                    EnemyScan.enemyScanSet.stream().forEach((enemyScan) -> {
                                       enemyMappingList.add(new EnemyUnit(enemyScan.scanEffect));
                                    });
                                    allEnemiesList.stream().filter((enemy) -> {
                                       return (enemy.unit().getBuildProgress() > 0.95F || enemy.unit().getBuildProgress() < 0.0F) && enemy.getLastSeenGameLoop() + (long)(UnitUtils.LONG_RANGE_ENEMIES.contains(enemy.unit().getType()) ? 112 : 0) >= Bot.OBS.getGameLoop();
                                    }).forEach((enemy) -> {
                                       enemyMappingList.add(new EnemyUnit(enemy.unit()));
                                    });
                                    Infestor.onStep();
                                    buildInfluenceMap();
                                    updateDiverStatus();
                                    return;
                                 }

                                 base = (Base)var10.next();
                              } while(base.isMyBase() && Bot.OBS.getVisibility(base.getCcPos()) != Visibility.VISIBLE);

                              base.lastScoutedFrame = Bot.OBS.getGameLoop();
                              base.setCc(base.getUpdatedUnit(Units.TERRAN_PLANETARY_FORTRESS, base.getCc(), base.getCcPos()));
                              Base finalBase = base;
                              base.isEnemyBase = enemyCCs.stream().anyMatch((enemyCC) -> {
                                 return UnitUtils.getDistance(enemyCC.unit(), finalBase.getCcPos()) < 2.0F;
                              });
                              base.getMineralPatches().clear();
                              Base finalBase1 = base;
                              base.getMineralPatches().addAll((Collection)mineralNodeList.stream().filter((node) -> {
                                 return UnitUtils.getDistance(node, finalBase1.getCcPos()) < 10.0F;
                              }).collect(Collectors.toList()));
                              if (!base.isDryedUp() && base.getMineralPatches().isEmpty()) {
                                 base.setDryedUp(true);
                              }
                           } while(base.isEnemyBase);
                        } while(base.getCc().isEmpty());

                        if (!base.getMineralPatches().isEmpty()) {
                           base.setRallyNode((Unit)base.getMineralPatches().get(0));
                        }

                        if (base.getCc().isEmpty()) {
                           System.out.println("error on GameCache::407");
                        }

                        Unit cc = ((UnitInPool)base.getCc().get()).unit();
                        if (cc.getAssignedHarvesters().isEmpty()) {
                           System.out.println("error on GameCache::411");
                           System.out.println("base index: " + baseList.indexOf(base));
                           System.out.println("base.getCcPos() = " + base.getCcPos());
                           System.out.println("cc.getType() = " + cc.getType());
                           System.out.println("cc.getBuildProgress() = " + cc.getBuildProgress());
                           System.out.println("FlyingCC.flyingCCs.size() = " + FlyingCC.flyingCCs.size());
                           System.out.println("base.isEnemyBase = " + base.isEnemyBase);
                           System.out.println("base.getCc().isPresent() = " + base.getCc().isPresent());
                        }

                        if (cc.getIdealHarvesters().isEmpty()) {
                           System.out.println("error on GameCache::414");
                        }

                        if ((Integer)cc.getAssignedHarvesters().get() < (Integer)cc.getIdealHarvesters().get() && !base.getMineralPatches().isEmpty() && defaultRallyNode == null) {
                           defaultRallyNode = base.getRallyNode();
                        }

                        base.getGases().clear();
                        Base finalBase2 = base;
                        Base finalBase3 = base;
                        geyserList.stream().filter((geyser) -> {
                           return UnitUtils.getDistance(geyser, finalBase2.getCcPos()) < 10.0F;
                        }).forEach((geyser) -> {
                           Gas gas = new Gas(geyser);
                           refineryList.stream().filter((refinery) -> {
                              return UnitUtils.getDistance(geyser, refinery) < 1.0F;
                           }).findFirst().ifPresent((refinery) -> {
                              gas.setRefinery(refinery);
                           });
                           finalBase3.getGases().add(gas);
                        });
                     } while(base.isMyMainBase());

                     Iterator var17 = base.getTurrets().iterator();

                     while(var17.hasNext()) {
                        DefenseUnitPositions turret = (DefenseUnitPositions)var17.next();
                        turret.setUnit(base.getUpdatedUnit(Units.TERRAN_MISSILE_TURRET, turret.getUnit(), turret.getPos()));
                     }
                  }
               }

               unitInPool = (UnitInPool)var8.next();
               unit = unitInPool.unit();
            } while(Ignored.contains(unit.getTag()));

            if (unit.getType() instanceof Other) {
               float x = unit.getPosition().getX();
               float y = unit.getPosition().getY();
               if (Bot.isDebugOn) {
                  Bot.DEBUG.debugBoxOut(Point.of(x - 0.22F, y - 0.22F, Position.getZ(x, y)), Point.of(x + 0.22F, y + 0.22F, Position.getZ(x, y)), Color.GREEN);
               }
            } else {
               Units unitType = (Units)unit.getType();
               Alliance alliance = unit.getAlliance();
               if (unit.getBuffs().contains(Buffs.PARASITIC_BOMB)) {
                  enemyMappingList.add(new EnemyUnit(unit, true));
               }

               switch(alliance) {
               case SELF:
                  if (unit.getBuildProgress() < 1.0F) {
                     inProductionList.add(unit);
                     inProductionMap.put((Units)unit.getType(), (Integer)inProductionMap.getOrDefault((Units)unit.getType(), 0) + 1);
                     switch(unitType) {
                     case TERRAN_REFINERY:
                     case TERRAN_REFINERY_RICH:
                     case TERRAN_COMMAND_CENTER:
                        break;
                     default:
                        continue;
                     }
                  }

                  if (UnitUtils.isStructure(unitType)) {
                     if (LocationConstants.isWallStructure(unit)) {
                        wallStructures.add(unit);
                     } else if (unit.getBuildProgress() == 1.0F && UnitUtils.getHealthPercentage(unit) <= 35) {
                        burningStructures.add(unit);
                     }
                  }

                  if (!allFriendliesMap.containsKey(unitType)) {
                     allFriendliesMap.put(unitType, new ArrayList());
                  }

                  ((List)allFriendliesMap.get(unitType)).add(unit);
                  Units unitProducing;
                  switch(unitType) {
                  case TERRAN_REFINERY:
                  case TERRAN_REFINERY_RICH:
                     refineryList.add(unit);
                     continue;
                  case TERRAN_COMMAND_CENTER:
                  case TERRAN_PLANETARY_FORTRESS:
                  case TERRAN_ORBITAL_COMMAND:
                  case TERRAN_COMMAND_CENTER_FLYING:
                  case TERRAN_ORBITAL_COMMAND_FLYING:
                     ccList.add(unit);
                     continue;
                  case TERRAN_SUPPLY_DEPOT:
                     if (LocationConstants.isWallStructure(unit)) {
                        wallStructures.add(unit);
                     }
                     continue;
                  case TERRAN_ENGINEERING_BAY:
                     if (LocationConstants.isWallStructure(unit)) {
                        wallStructures.add(unit);
                     }
                     continue;
                  case TERRAN_BARRACKS:
                     if (LocationConstants.isWallStructure(unit)) {
                        wallStructures.add(unit);
                     }

                     if (!unit.getOrders().isEmpty()) {
                        unitProducing = (Units)Bot.abilityToUnitType.get(((UnitOrder)unit.getOrders().get(0)).getAbility());
                        inProductionMap.put(unitProducing, (Integer)inProductionMap.getOrDefault(unitProducing, 0) + 1);
                     }
                  case TERRAN_BARRACKS_FLYING:
                     barracksList.add(unitInPool);
                     continue;
                  case TERRAN_FACTORY:
                     if (!unit.getOrders().isEmpty()) {
                        unitProducing = (Units)Bot.abilityToUnitType.get(((UnitOrder)unit.getOrders().get(0)).getAbility());
                        inProductionMap.put(unitProducing, (Integer)inProductionMap.getOrDefault(unitProducing, 0) + 1);
                     }
                  case TERRAN_FACTORY_FLYING:
                     factoryList.add(unitInPool);
                     continue;
                  case TERRAN_STARPORT:
                     if (!unit.getOrders().isEmpty()) {
                        unitProducing = (Units)Bot.abilityToUnitType.get(((UnitOrder)unit.getOrders().get(0)).getAbility());
                        inProductionMap.put(unitProducing, (Integer)inProductionMap.getOrDefault(unitProducing, 0) + 1);
                     }
                  case TERRAN_STARPORT_FLYING:
                     starportList.add(unitInPool);
                     continue;
                  case TERRAN_SCV:
                     allScvs.add(unitInPool);
                     if (unit.getOrders().isEmpty() || WorkerManager.isMiningMinerals(unitInPool)) {
                        availableScvs.add(unitInPool);
                     }
                     continue;
                  case TERRAN_SIEGE_TANK:
                  case TERRAN_SIEGE_TANK_SIEGED:
                     siegeTankList.add(unit);
                     continue;
                  case TERRAN_LIBERATOR:
                  case TERRAN_LIBERATOR_AG:
                     liberatorList.add(unit);
                     continue;
                  case TERRAN_BANSHEE:
                     bansheeList.add(unit);
                     continue;
                  case TERRAN_RAVEN:
                     ravenList.add(unit);
                     continue;
                  case TERRAN_VIKING_FIGHTER:
                  case TERRAN_VIKING_ASSAULT:
                     vikingList.add(unit);
                  default:
                     continue;
                  }
               case NEUTRAL:
                  switch(unitType) {
                  case NEUTRAL_MINERAL_FIELD:
                  case NEUTRAL_MINERAL_FIELD750:
                  case NEUTRAL_RICH_MINERAL_FIELD:
                  case NEUTRAL_RICH_MINERAL_FIELD750:
                  case NEUTRAL_LAB_MINERAL_FIELD:
                  case NEUTRAL_LAB_MINERAL_FIELD750:
                  case NEUTRAL_PURIFIER_MINERAL_FIELD:
                  case NEUTRAL_PURIFIER_MINERAL_FIELD750:
                  case NEUTRAL_BATTLE_STATION_MINERAL_FIELD:
                  case NEUTRAL_BATTLE_STATION_MINERAL_FIELD750:
                  case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD:
                  case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750:
                     mineralNodeList.add(unit);
                     continue;
                  case NEUTRAL_VESPENE_GEYSER:
                  case NEUTRAL_RICH_VESPENE_GEYSER:
                  case NEUTRAL_PROTOSS_VESPENE_GEYSER:
                  case NEUTRAL_PURIFIER_VESPENE_GEYSER:
                  case NEUTRAL_SHAKURAS_VESPENE_GEYSER:
                  case NEUTRAL_SPACE_PLATFORM_GEYSER:
                     geyserList.add(unit);
                  default:
                     continue;
                  }
               case ENEMY:
                  if (LocationConstants.opponentRace == Race.RANDOM) {
                     LocationConstants.opponentRace = (Race)((UnitTypeData)Bot.OBS.getUnitTypeData(false).get(unitType)).getRace().get();
                     LocationConstants.initEnemyRaceSpecifics();
                  }

                  if (!(Boolean)unit.getHallucination().orElse(false)) {
                     if (!Switches.enemyCanProduceAir && UnitUtils.EVIDENCE_OF_AIR.contains(unitType)) {
                        Bot.ACTION.sendChat("Have our viking pilots updated their wills?  I smell enemy air units.", Channel.BROADCAST);
                        Switches.enemyCanProduceAir = true;
                        Strategy.DO_INCLUDE_LIBS = false;
                        Strategy.DO_INCLUDE_TANKS = false;
                     }

                     switch(unitType) {
                     case TERRAN_BANSHEE:
                     case PROTOSS_DARK_TEMPLAR:
                     case PROTOSS_DARK_SHRINE:
                     case PROTOSS_MOTHERSHIP:
                     case ZERG_LURKER_DEN_MP:
                     case ZERG_LURKER_MP:
                     case ZERG_LURKER_MP_EGG:
                     case ZERG_LURKER_MP_BURROWED:
                     case TERRAN_WIDOWMINE:
                     case TERRAN_WIDOWMINE_BURROWED:
                     case TERRAN_GHOST:
                     case TERRAN_GHOST_ACADEMY:
                        if (!Switches.enemyHasCloakThreat) {
                           Bot.ACTION.sendChat("I can't see those. Time to up my detection.", Channel.BROADCAST);
                        }

                        Switches.enemyHasCloakThreat = true;
                     case TERRAN_RAVEN:
                     case TERRAN_VIKING_FIGHTER:
                     case TERRAN_VIKING_ASSAULT:
                     case NEUTRAL_MINERAL_FIELD:
                     case NEUTRAL_MINERAL_FIELD750:
                     case NEUTRAL_RICH_MINERAL_FIELD:
                     case NEUTRAL_RICH_MINERAL_FIELD750:
                     case NEUTRAL_LAB_MINERAL_FIELD:
                     case NEUTRAL_LAB_MINERAL_FIELD750:
                     case NEUTRAL_PURIFIER_MINERAL_FIELD:
                     case NEUTRAL_PURIFIER_MINERAL_FIELD750:
                     case NEUTRAL_BATTLE_STATION_MINERAL_FIELD:
                     case NEUTRAL_BATTLE_STATION_MINERAL_FIELD750:
                     case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD:
                     case NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750:
                     case NEUTRAL_VESPENE_GEYSER:
                     case NEUTRAL_RICH_VESPENE_GEYSER:
                     case NEUTRAL_PROTOSS_VESPENE_GEYSER:
                     case NEUTRAL_PURIFIER_VESPENE_GEYSER:
                     case NEUTRAL_SHAKURAS_VESPENE_GEYSER:
                     case NEUTRAL_SPACE_PLATFORM_GEYSER:
                     }

                     switch(unitType) {
                     case PROTOSS_TEMPEST:
                        Strategy.VIKING_BANSHEE_RATIO = 1.0F;
                        break;
                     case PROTOSS_PHOENIX:
                        if (!Switches.phoenixAreReal && !(Boolean)unit.getHallucination().orElse(false)) {
                           Unit finalUnit = unit;
                           if (!Bot.OBS.getUnits(Alliance.SELF, (u) -> {
                              return (u.unit().getType() == Units.TERRAN_MISSILE_TURRET || u.unit().getType() == Units.TERRAN_RAVEN) && u.unit().getBuildProgress() == 1.0F && (double)UnitUtils.getDistance(u.unit(), finalUnit) < 9.5D;
                           }).isEmpty()) {
                              Switches.phoenixAreReal = true;
                              if (!Switches.enemyCanProduceAir) {
                                 UnitUtils.EVIDENCE_OF_AIR.add(Units.PROTOSS_PHOENIX);
                              }
                           }
                           continue;
                        }
                     }

                     allEnemiesList.add(unitInPool);
                     if (!allVisibleEnemiesMap.containsKey(unitType)) {
                        allVisibleEnemiesMap.put(unitType, new ArrayList());
                     }

                     ((List)allVisibleEnemiesMap.get(unitType)).add(unit);
                     if (!(Boolean)unit.getFlying().orElse(false)) {
                        enemyIsGround.add(unit);
                     } else {
                        enemyIsAir.add(unit);
                        if (!Strategy.enemyHasAirThreat && !(Boolean)unit.getHallucination().orElse(false) && unit.getType() != Units.ZERG_OVERLORD) {
                           Strategy.enemyHasAirThreat = true;
                        }
                     }

                     if ((Float)unit.getDetectRange().orElse(0.0F) > 0.0F || unit.getType() == Units.PROTOSS_OBSERVER) {
                        enemyDetector.add(unit);
                     }

                     Set<Weapon> weapons = ((UnitTypeData)Bot.OBS.getUnitTypeData(false).get(unit.getType())).getWeapons();
                     Iterator var6 = weapons.iterator();

                     Weapon weapon;
                     do {
                        if (!var6.hasNext()) {
                           continue label296;
                        }

                        weapon = (Weapon)var6.next();
                     } while(weapon.getTargetType() != TargetType.AIR && weapon.getTargetType() != TargetType.ANY);

                     enemyAttacksAir.add(unit);
                  }
               }
            }
         }
      }
   }

   private static void updateDiverStatus() throws Exception {
      Iterator var0;
      Unit detector;
      if (Switches.bansheeDiveTarget == null) {
         var0 = enemyDetector.iterator();

         while(var0.hasNext()) {
            detector = (Unit)var0.next();
            if (!(Boolean)detector.getFlying().orElse(true) && ArmyManager.shouldDive(Units.TERRAN_BANSHEE, detector)) {
               Switches.bansheeDiveTarget = Bot.OBS.getUnit(detector.getTag());
               break;
            }
         }
      }

      int i;
      if (Switches.bansheeDiveTarget != null) {
         if (Switches.bansheeDiveTarget.isAlive() && UnitUtils.isVisible(Switches.bansheeDiveTarget)) {
            for(i = 0; i < bansheeList.size(); ++i) {
               if (UnitUtils.getDistance((Unit)bansheeList.get(i), Switches.bansheeDiveTarget.unit()) < 15.0F) {
                  bansheeDivers.add((Unit)bansheeList.remove(i--));
               }
            }

            if (bansheeDivers.isEmpty()) {
               Switches.bansheeDiveTarget = null;
            }
         } else {
            Switches.bansheeDiveTarget = null;
         }
      }

      if (Switches.vikingDiveTarget == null && LocationConstants.opponentRace == Race.PROTOSS && !vikingList.isEmpty()) {
         List<UnitInPool> tempests = getProtossCapitalShips();
         if (!tempests.isEmpty()) {
            UnitInPool closestTempest = UnitUtils.getClosestUnitFromUnitList(tempests, Position.midPointUnitsMedian(vikingList));
            if (closestTempest != null) {
               Point2d closestTempestPos = closestTempest.unit().getPosition().toPoint2d();
               List<UnitInPool> nearbyVikings = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_VIKING_FIGHTER, closestTempestPos, 23.0F);
               if (ArmyManager.shouldDiveTempests(closestTempestPos, nearbyVikings.size())) {
                  Switches.vikingDiveTarget = closestTempest;
                  if (Switches.vikingDiveTarget != null) {
                     Switches.isDivingTempests = true;
                     Bot.ACTION.sendChat(Chat.getRandomMessage(Chat.VIKING_DIVE), Channel.BROADCAST);
                  }
               }
            }
         }
      }

      if (Switches.vikingDiveTarget == null && LocationConstants.opponentRace != Race.TERRAN && bansheeList.isEmpty() && UnitUtils.getVisibleEnemyUnitsOfType(Units.PROTOSS_PHOENIX).size() + UnitUtils.getVisibleEnemyUnitsOfType(Units.PROTOSS_VOIDRAY).size() == 0) {
         var0 = enemyDetector.iterator();

         while(var0.hasNext()) {
            detector = (Unit)var0.next();
            if ((Boolean)detector.getFlying().orElse(false) && ArmyManager.shouldDive(Units.TERRAN_VIKING_FIGHTER, detector)) {
               Switches.vikingDiveTarget = Bot.OBS.getUnit(detector.getTag());
               if (Switches.vikingDiveTarget.unit().getType() == Units.PROTOSS_OBSERVER && Switches.vikingDiveTarget.unit().getCloakState().orElse(CloakState.CLOAKED_DETECTED) == CloakState.CLOAKED) {
                  if (UnitUtils.canScan()) {
                     List<Unit> orbitals = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
                     Bot.ACTION.unitCommand(orbitals, Abilities.EFFECT_SCAN, Position.towards(Switches.vikingDiveTarget.unit().getPosition().toPoint2d(), ArmyManager.retreatPos, -2.0F), false);
                  } else {
                     Switches.vikingDiveTarget = null;
                  }
               }
               break;
            }
         }
      }

      if (Switches.vikingDiveTarget != null) {
         if (!Switches.isDivingTempests && (!Switches.vikingDiveTarget.isAlive() || !UnitUtils.isVisible(Switches.vikingDiveTarget))) {
            Switches.vikingDiveTarget = null;
         } else {
            if (Switches.isDivingTempests && (!Switches.vikingDiveTarget.isAlive() || !UnitUtils.isVisible(Switches.vikingDiveTarget) && vikingList.stream().anyMatch((viking) -> {
               return UnitUtils.getDistance(viking, Switches.vikingDiveTarget.unit()) < 1.0F;
            }))) {
               Switches.vikingDiveTarget = UnitUtils.getClosestUnitFromUnitList(getProtossCapitalShips(), Position.midPointUnitsMedian(vikingList));
            }

            if (Switches.vikingDiveTarget != null) {
               for(i = 0; i < vikingList.size(); ++i) {
                  if (UnitUtils.getDistance((Unit)vikingList.get(i), Switches.vikingDiveTarget.unit()) < (float)(Switches.isDivingTempests ? 23 : Strategy.DIVE_RANGE)) {
                     vikingDivers.add((Unit)vikingList.remove(i--));
                  }
               }
            }

            if (vikingDivers.isEmpty() || Switches.vikingDiveTarget == null) {
               Switches.vikingDiveTarget = null;
               Switches.isDivingTempests = false;
            }
         }
      }

   }

   private static List<UnitInPool> getProtossCapitalShips() {
      List<UnitInPool> protossCapitalShips = new ArrayList();
      protossCapitalShips.addAll(UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST));
      protossCapitalShips.addAll(UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_CARRIER));
      protossCapitalShips.addAll(UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_MOTHERSHIP));
      return protossCapitalShips;
   }

   private static boolean tempestFarFromVikings(Unit tempest) {
      return Bot.OBS.getUnits(Alliance.SELF, (u) -> {
         return u.unit().getType() == Units.TERRAN_VIKING_FIGHTER && UnitUtils.getDistance(tempest, u.unit()) < 8.0F;
      }).isEmpty();
   }

   public static void buildInfluenceMap() throws Exception {
      int xMin = 0;
      int xMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getX());
      int yMin = 0;
      int yMax = InfluenceMaps.toMapCoord(LocationConstants.SCREEN_TOP_RIGHT.getY());
      InfluenceMaps.pointDetected = new boolean[800][800];
      InfluenceMaps.pointInBansheeRange = new boolean[800][800];
      InfluenceMaps.pointInVikingRange = new boolean[800][800];
      InfluenceMaps.pointThreatToAirPlusBuffer = new boolean[800][800];
      InfluenceMaps.pointSupplyInSeekerRange = new float[800][800];
      InfluenceMaps.pointThreatToAir = new int[800][800];
      InfluenceMaps.pointThreatToAirFromGround = new int[800][800];
      InfluenceMaps.pointThreatToGround = new float[800][800];
      InfluenceMaps.pointPFTargetValue = new int[800][800];
      InfluenceMaps.pointGroundUnitWithin13 = new boolean[800][800];
      InfluenceMaps.pointRaiseDepots = new boolean[800][800];
      InfluenceMaps.pointVikingsStayBack = new boolean[800][800];
      Iterator var4 = enemyMappingList.iterator();

      while(var4.hasNext()) {
         EnemyUnit enemy = (EnemyUnit)var4.next();
         int xStart = Math.max(InfluenceMaps.toMapCoord(enemy.x - enemy.maxRange), xMin);
         int yStart = Math.max(InfluenceMaps.toMapCoord(enemy.y - enemy.maxRange), yMin);
         int xEnd = Math.min(InfluenceMaps.toMapCoord(enemy.x + enemy.maxRange), xMax);
         int yEnd = Math.min(InfluenceMaps.toMapCoord(enemy.y + enemy.maxRange), yMax);

         for(int x = xStart; x <= xEnd; ++x) {
            for(int y = yStart; y <= yEnd; ++y) {
               float distance = Position.distance((float)x / 2.0F, (float)y / 2.0F, enemy.x, enemy.y);
               if (!enemy.isAir && enemy.isArmy && distance < 9.0F) {
                  InfluenceMaps.pointRaiseDepots[x][y] = true;
               }

               if (distance < 17.5F) {
                  InfluenceMaps.pointVikingsStayBack[x][y] = true;
               }

               float[] var10000;
               if (distance < enemy.groundAttackRange) {
                  var10000 = InfluenceMaps.pointThreatToGround[x];
                  var10000[y] += (float)enemy.threatLevel;
               }

               if (enemy.airAttackRange != 0.0F && distance < enemy.airAttackRange + 2.0F) {
                  InfluenceMaps.pointThreatToAirPlusBuffer[x][y] = true;
               }

               if (distance < 3.0F && enemy.isArmy && !enemy.isSeekered) {
                  var10000 = InfluenceMaps.pointSupplyInSeekerRange[x];
                  var10000[y] += enemy.supply;
               }

               if (enemy.isDetector && distance < enemy.detectRange) {
                  InfluenceMaps.pointDetected[x][y] = true;
               }

               int[] var13;
               if (enemy.isAir) {
                  if (distance < 9.1F) {
                     InfluenceMaps.pointInVikingRange[x][y] = true;
                  }

                  if (distance < enemy.airAttackRange) {
                     var13 = InfluenceMaps.pointThreatToAir[x];
                     var13[y] += enemy.threatLevel;
                  }
               } else {
                  if (distance < 6.1F && !enemy.isEffect) {
                     InfluenceMaps.pointInBansheeRange[x][y] = true;
                  }

                  if (distance < enemy.airAttackRange) {
                     var13 = InfluenceMaps.pointThreatToAir[x];
                     var13[y] += enemy.threatLevel;
                     var13 = InfluenceMaps.pointThreatToAirFromGround[x];
                     var13[y] += enemy.threatLevel;
                  }

                  if ((double)distance < 1.25D) {
                     var13 = InfluenceMaps.pointPFTargetValue[x];
                     var13[y] += enemy.pfTargetLevel;
                  }

                  if (distance < 13.0F) {
                     InfluenceMaps.pointGroundUnitWithin13[x][y] = true;
                  }
               }
            }
         }
      }

   }

   public static boolean inDetectionRange(int x1, int y1, Unit enemy) throws Exception {
      return inRange(x1, y1, enemy.getPosition().getX(), enemy.getPosition().getY(), (Float)enemy.getDetectRange().orElse(0.0F));
   }

   public static boolean inAirAttackRange(int x1, int y1, Unit enemy) throws Exception {
      return inRange(x1, y1, enemy.getPosition().getX(), enemy.getPosition().getY(), UnitUtils.getAirAttackRange(enemy));
   }

   public static boolean inRange(int x1, int y1, float x2, float y2, float range) throws Exception {
      float width = Math.abs(x2 - (float)x1);
      float height = Math.abs(y2 - (float)y1);
      return Math.sqrt((double)(width * width + height * height)) < (double)range;
   }
}

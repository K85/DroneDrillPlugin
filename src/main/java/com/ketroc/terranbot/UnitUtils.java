package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buff;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Weapon;
import com.github.ocraft.s2client.protocol.data.Weapon.TargetType;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.models.Base;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UnitUtils {
   public static final Set<Units> WORKER_TYPE;
   public static final Set<Units> GAS_GEYSER_TYPE;
   public static final Set<Units> REFINERY_TYPE;
   public static final Set<Units> MINERAL_NODE_TYPE;
   public static final Set<Units> MINERAL_NODE_TYPE_LARGE;
   public static final Set<Units> COMMAND_CENTER_TYPE;
   public static final Set<Units> COMMAND_STRUCTURE_TYPE;
   public static final Set<Units> SIEGE_TANK_TYPE;
   public static final Set<Units> LIBERATOR_TYPE;
   public static final Set<Units> WIDOW_MINE_TYPE;
   public static final Set<Units> STRUCTURE_TYPE;
   public static Set<Units> EVIDENCE_OF_AIR;
   public static final Set<Units> INFESTOR_TYPE;
   public static final Set<Units> IGNORED_TARGETS;
   public static final Set<Units> LONG_RANGE_ENEMIES;
   public static Set<Units> enemyCommandStructures;
   public static Units enemyWorkerType;

   public static boolean hasTechToBuild(Abilities abilityType) {
      return hasTechToBuild((Units)Bot.abilityToUnitType.get(abilityType));
   }

   public static boolean hasTechToBuild(Units unitType) {
      switch(unitType) {
      case TERRAN_ORBITAL_COMMAND:
         return !GameCache.barracksList.isEmpty();
      case TERRAN_PLANETARY_FORTRESS:
         if (!getFriendlyUnitsOfType(Units.TERRAN_ENGINEERING_BAY).isEmpty()) {
            return true;
         }
      default:
         return false;
      case TERRAN_STARPORT:
         return !GameCache.factoryList.isEmpty();
      }
   }

   public static int getNumUnits(Units unitType, boolean includeProducing) {
      int numUnits = getFriendlyUnitsOfType(unitType).size();
      if (includeProducing) {
         numUnits += numInProductionOfType(unitType);
      }

      return numUnits;
   }

   public static int getNumUnits(Set<Units> unitTypes, boolean includeProducing) {
      int numUnits = 0;
      Iterator var3 = unitTypes.iterator();

      while(var3.hasNext()) {
         Units unitType = (Units)var3.next();
         numUnits += getFriendlyUnitsOfType(unitType).size();
         if (includeProducing) {
            numUnits += numInProductionOfType(unitType);
         }
      }

      return numUnits;
   }

   public static boolean canAfford(Units unitType) {
      return canAfford(unitType, GameCache.mineralBank, GameCache.gasBank, GameCache.freeSupply);
   }

   public static boolean canAfford(Units unitType, int minerals, int gas, int supply) {
      UnitTypeData unitData = (UnitTypeData)Bot.OBS.getUnitTypeData(false).get(unitType);
      int mineralCost = (Integer)unitData.getMineralCost().orElse(0);
      int gasCost = (Integer)unitData.getVespeneCost().orElse(0);
      int supplyCost = ((Float)unitData.getFoodRequired().orElse(0.0F)).intValue();
      switch(unitType) {
      case TERRAN_ORBITAL_COMMAND:
      case TERRAN_PLANETARY_FORTRESS:
         mineralCost -= 400;
      }

      return minerals >= mineralCost && gas >= gasCost && supply >= supplyCost;
   }

   public static boolean isUnitTypesNearby(Alliance alliance, Units unitType, Point2d position, float distance) {
      return !getUnitsNearbyOfType(alliance, unitType, position, distance).isEmpty();
   }

   public static List<UnitInPool> getUnitsNearbyOfType(Alliance alliance, Units unitType, Point2d position, float distance) {
      try {
         return Bot.OBS.getUnits(alliance, (unit) -> {
            return unit.unit().getType() == unitType && unit.unit().getPosition().toPoint2d().distance(position) < (double)distance;
         });
      } catch (Exception var5) {
         var5.printStackTrace();
         return Collections.emptyList();
      }
   }

   public static boolean isUnitTypesNearby(Alliance alliance, Set<Units> unitTypes, Point2d position, float distance) {
      return !getUnitsNearbyOfType(alliance, unitTypes, position, distance).isEmpty();
   }

   public static List<UnitInPool> getUnitsNearbyOfType(Alliance alliance, Set<Units> unitTypes, Point2d position, float distance) {
      return Bot.OBS.getUnits(alliance, (unit) -> {
         return unitTypes.contains(unit.unit().getType()) && getDistance(unit.unit(), position) < distance;
      });
   }

   public static List<UnitInPool> getUnitsNearby(Alliance alliance, Point2d position, float distance) {
      return Bot.OBS.getUnits(alliance, (unit) -> {
         return getDistance(unit.unit(), position) < distance;
      });
   }

   public static int numRepairingScvs(Unit repairTarget) {
      return (int)getFriendlyUnitsOfType(Units.TERRAN_SCV).stream().filter((scv) -> {
         return !scv.getOrders().isEmpty() && ((UnitOrder)scv.getOrders().get(0)).getAbility() == Abilities.EFFECT_REPAIR && ((Tag)((UnitOrder)scv.getOrders().get(0)).getTargetedUnitTag().orElse(Tag.of(0L))).equals(repairTarget.getTag());
      }).count();
   }

   public static int getHealthPercentage(Unit unit) {
      return ((Float)unit.getHealth().get()).intValue() * 100 / ((Float)unit.getHealthMax().get()).intValue();
   }

   public static int getIdealScvsToRepair(Unit unit) {
      int pfHealthPercentage;
      if (GameCache.wallStructures.contains(unit)) {
         pfHealthPercentage = getHealthPercentage(unit);
         return pfHealthPercentage > 75 ? 1 : 2;
      } else {
         switch((Units)unit.getType()) {
         case TERRAN_PLANETARY_FORTRESS:
            pfHealthPercentage = getHealthPercentage(unit);
            if (pfHealthPercentage > 95) {
               return 5;
            } else {
               if (pfHealthPercentage > 70) {
                  return 10;
               }

               return Integer.MAX_VALUE;
            }
         case TERRAN_STARPORT:
         default:
            return 1;
         case TERRAN_MISSILE_TURRET:
         case TERRAN_BUNKER:
            return 6;
         case TERRAN_LIBERATOR_AG:
         case TERRAN_SIEGE_TANK_SIEGED:
            return 2;
         }
      }
   }

   public static float getAirAttackRange(Unit unit) {
      return getAttackRange(unit, TargetType.AIR);
   }

   public static float getGroundAttackRange(Unit unit) {
      return getAttackRange(unit, TargetType.GROUND);
   }

   public static float getAttackRange(Unit unit, TargetType targetType) {
      float attackRange = 0.0F;
      switch((Units)unit.getType()) {
      case TERRAN_BUNKER:
         attackRange = 8.0F;
      case TERRAN_LIBERATOR_AG:
      case TERRAN_SIEGE_TANK_SIEGED:
      default:
         break;
      case TERRAN_BATTLECRUISER:
      case PROTOSS_HIGH_TEMPLAR:
      case PROTOSS_VOIDRAY:
         attackRange = 6.0F;
         break;
      case PROTOSS_SENTRY:
         attackRange = 5.0F;
      }

      Set<Weapon> weapons = ((UnitTypeData)Bot.OBS.getUnitTypeData(false).get(unit.getType())).getWeapons();
      Iterator var4 = weapons.iterator();

      while(true) {
         Weapon weapon;
         do {
            if (!var4.hasNext()) {
               if (attackRange > 0.0F) {
                  attackRange += unit.getRadius();
               }

               return attackRange;
            }

            weapon = (Weapon)var4.next();
         } while(weapon.getTargetType() != targetType && weapon.getTargetType() != TargetType.ANY);

         attackRange = weapon.getRange() + unit.getRadius();
      }
   }

   public static float getDistance(Unit unit1, Unit unit2) {
      return getDistance(unit1, unit2.getPosition().toPoint2d());
   }

   public static float getDistance(Unit unit1, Point2d point) {
      return (float)unit1.getPosition().toPoint2d().distance(point);
   }

   public static List<Unit> toUnitList(List<UnitInPool> unitInPoolList) {
      return (List)unitInPoolList.stream().map(UnitInPool::unit).collect(Collectors.toList());
   }

   public static boolean hasDecloakBuff(Unit unit) {
      Set<Buff> buffs = unit.getBuffs();
      return buffs.contains(Buffs.EMP_DECLOAK) || buffs.contains(Buffs.ORACLE_REVELATION) || buffs.contains(Buffs.FUNGAL_GROWTH);
   }

   public static boolean isVisible(UnitInPool unitInPool) {
      return unitInPool.getLastSeenGameLoop() == Bot.OBS.getGameLoop();
   }

   public static boolean canMove(UnitType unitType) {
      return (Float)((UnitTypeData)Bot.OBS.getUnitTypeData(false).get(unitType)).getMovementSpeed().orElse(0.0F) > 0.0F;
   }

   public static Unit getClosestEnemyOfType(Units unitType, Point2d pos) {
      List<UnitInPool> enemyList = getUnitsNearbyOfType(Alliance.ENEMY, unitType, pos, 2.14748365E9F);
      if (enemyList.isEmpty()) {
         return null;
      } else {
         UnitInPool closestEnemy = (UnitInPool)enemyList.remove(0);
         double closestDistance = closestEnemy.unit().getPosition().toPoint2d().distance(pos);
         Iterator var6 = enemyList.iterator();

         while(var6.hasNext()) {
            UnitInPool enemy = (UnitInPool)var6.next();
            double distance = enemy.unit().getPosition().toPoint2d().distance(pos);
            if (distance < closestDistance) {
               closestDistance = distance;
               closestEnemy = enemy;
            }
         }

         return closestEnemy.unit();
      }
   }

   public static UnitInPool getClosestEnemyUnitOfType(Units unitType, Point2d pos) {
      return (UnitInPool)getEnemyUnitsOfType(unitType).stream().min(Comparator.comparing((u) -> {
         return getDistance(u.unit(), ArmyManager.retreatPos);
      })).orElse(null);
   }

   public static Unit getClosestUnitOfType(Alliance alliance, Units unitType, Point2d pos) {
      List<UnitInPool> unitList = getUnitsNearbyOfType(alliance, unitType, pos, 2.14748365E9F);
      UnitInPool result = getClosestUnitFromUnitList(unitList, pos);
      return result == null ? null : result.unit();
   }

   public static Unit getClosestUnitOfType(Alliance alliance, Set<Units> unitType, Point2d pos) {
      List<UnitInPool> unitList = getUnitsNearbyOfType(alliance, unitType, pos, 2.14748365E9F);
      UnitInPool result = getClosestUnitFromUnitList(unitList, pos);
      return result == null ? null : result.unit();
   }

   public static UnitInPool getClosestUnitFromUnitList(List<UnitInPool> unitList, Point2d pos) {
      return (UnitInPool)unitList.stream().filter((u) -> {
         return isVisible(u);
      }).min(Comparator.comparing((u) -> {
         return getDistance(u.unit(), pos);
      })).orElse(null);
   }

   public static void removeDeadUnits(List<UnitInPool> unitList) {
      if (unitList != null) {
         unitList.removeIf((unitInPool) -> {
            return !unitInPool.isAlive();
         });
      }

   }

   public static boolean isStructure(Units unitType) {
      return STRUCTURE_TYPE.contains(unitType);
   }

   public static boolean canScan() {
      List<Unit> orbitals = getFriendlyUnitsOfType(Units.TERRAN_ORBITAL_COMMAND);
      return orbitals.stream().anyMatch((unit) -> {
         return (Float)unit.getEnergy().orElse(0.0F) >= 50.0F;
      });
   }

   public static List<UnitInPool> getEnemyUnitsOfType(Units unitType) {
      return (List)GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList());
   }

   public static List<UnitInPool> getEnemyUnitsOfTypes(Collection<Units> unitTypes) {
      List<UnitInPool> result = new ArrayList();
      Iterator var2 = unitTypes.iterator();

      while(var2.hasNext()) {
         Units unitType = (Units)var2.next();
         if (!((List)GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList())).isEmpty()) {
            result.addAll((Collection)GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList()));
         }
      }

      return result;
   }

   public static List<Unit> getVisibleEnemyUnitsOfType(Units unitType) {
      return (List)GameCache.allVisibleEnemiesMap.getOrDefault(unitType, Collections.emptyList());
   }

   public static List<Unit> getVisibleEnemyUnitsOfType(Set<Units> unitTypes) {
      List<Unit> result = new ArrayList();
      Iterator var2 = unitTypes.iterator();

      while(var2.hasNext()) {
         Units unitType = (Units)var2.next();
         List<Unit> enemyOfTypeList = (List)GameCache.allVisibleEnemiesMap.getOrDefault(unitType, Collections.emptyList());
         if (!enemyOfTypeList.isEmpty()) {
            result.addAll(enemyOfTypeList);
         }
      }

      return result;
   }

   public static List<Unit> getFriendlyUnitsOfType(Units unitType) {
      return (List)GameCache.allFriendliesMap.getOrDefault(unitType, Collections.emptyList());
   }

   public static int numInProductionOfType(Units unitType) {
      return (Integer)GameCache.inProductionMap.getOrDefault(unitType, 0);
   }

   public static void queueUpAttackOfEveryBase(List<Unit> units) {
      Bot.ACTION.unitCommand(units, Abilities.ATTACK, (Point2d)LocationConstants.baseLocations.get(2), false);

      for(int i = 3; i < LocationConstants.baseLocations.size(); ++i) {
         Point2d basePos = (Point2d)LocationConstants.baseLocations.get(i);
         Bot.ACTION.unitCommand(units, Abilities.ATTACK, (Point2d)LocationConstants.baseLocations.get(i), true);
      }

   }

   public static boolean doesAttackGround(Unit unit) {
      return ((UnitTypeData)Bot.OBS.getUnitTypeData(false).get(unit.getType())).getWeapons().stream().anyMatch((weapon) -> {
         return weapon.getTargetType() == TargetType.GROUND || weapon.getTargetType() == TargetType.GROUND;
      });
   }

   public static boolean isCarryingResources(Unit worker) {
      return worker.getBuffs().contains(Buffs.CARRY_MINERAL_FIELD_MINERALS) || worker.getBuffs().contains(Buffs.CARRY_HIGH_YIELD_MINERAL_FIELD_MINERALS) || worker.getBuffs().contains(Buffs.CARRY_HARVESTABLE_VESPENE_GEYSER_GAS) || worker.getBuffs().contains(Buffs.CARRY_HARVESTABLE_VESPENE_GEYSER_GAS_PROTOSS) || worker.getBuffs().contains(Buffs.CARRY_HARVESTABLE_VESPENE_GEYSER_GAS_ZERG);
   }

   public static Unit getSafestMineralPatch() {
      List<Unit> mineralPatches = (List)GameCache.baseList.stream().filter((base) -> {
         return base.isMyBase() && !base.getMineralPatches().isEmpty();
      }).findFirst().map(Base::getMineralPatches).orElse(null);
      return mineralPatches == null ? null : (Unit)mineralPatches.get(0);
   }

   public static boolean isAttacking(Unit unit, Unit target) {
      return !unit.getOrders().isEmpty() && ((UnitOrder)unit.getOrders().get(0)).getAbility() == Abilities.ATTACK && target.getTag().equals(((UnitOrder)unit.getOrders().get(0)).getTargetedUnitTag().orElse(null));
   }

   public static boolean hasOrderTarget(Unit unit) {
      return !unit.getOrders().isEmpty() && ((UnitOrder)unit.getOrders().get(0)).getTargetedUnitTag().isPresent();
   }

   public static boolean wallUnderAttack() {
      return GameCache.wallStructures.stream().anyMatch((unit) -> {
         return unit.getType() == Units.TERRAN_SUPPLY_DEPOT;
      });
   }

   static {
      WORKER_TYPE = new HashSet(Set.of(Units.ZERG_DRONE, Units.ZERG_DRONE_BURROWED, Units.PROTOSS_PROBE, Units.TERRAN_SCV, Units.TERRAN_MULE));
      GAS_GEYSER_TYPE = new HashSet(Set.of(Units.NEUTRAL_RICH_VESPENE_GEYSER, Units.NEUTRAL_SPACE_PLATFORM_GEYSER, Units.NEUTRAL_VESPENE_GEYSER, Units.NEUTRAL_PROTOSS_VESPENE_GEYSER, Units.NEUTRAL_PURIFIER_VESPENE_GEYSER, Units.NEUTRAL_SHAKURAS_VESPENE_GEYSER));
      REFINERY_TYPE = new HashSet(Set.of(Units.TERRAN_REFINERY, Units.TERRAN_REFINERY_RICH));
      MINERAL_NODE_TYPE = new HashSet(Set.of(Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_MINERAL_FIELD750, Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750, Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD750, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750, Units.NEUTRAL_MINERAL_FIELD_OPAQUE, Units.NEUTRAL_MINERAL_FIELD_OPAQUE900, Units.NEUTRAL_MINERAL_FIELD450));
      MINERAL_NODE_TYPE_LARGE = new HashSet(Set.of(Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD));
      COMMAND_CENTER_TYPE = new HashSet(Set.of(Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING));
      COMMAND_STRUCTURE_TYPE = new HashSet(Set.of(Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING, Units.PROTOSS_NEXUS, Units.ZERG_HATCHERY, Units.ZERG_LAIR, Units.ZERG_HIVE));
      SIEGE_TANK_TYPE = new HashSet(Set.of(Units.TERRAN_SIEGE_TANK, Units.TERRAN_SIEGE_TANK_SIEGED));
      LIBERATOR_TYPE = new HashSet(Set.of(Units.TERRAN_LIBERATOR, Units.TERRAN_LIBERATOR_AG));
      WIDOW_MINE_TYPE = new HashSet(Set.of(Units.TERRAN_WIDOWMINE, Units.TERRAN_WIDOWMINE_BURROWED));
      STRUCTURE_TYPE = new HashSet(Set.of(Units.TERRAN_FUSION_CORE, Units.TERRAN_SUPPLY_DEPOT, Units.TERRAN_SUPPLY_DEPOT_LOWERED, Units.TERRAN_ENGINEERING_BAY, Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING, Units.TERRAN_ARMORY, Units.TERRAN_MISSILE_TURRET, Units.TERRAN_BUNKER, Units.TERRAN_GHOST_ACADEMY, Units.TERRAN_SENSOR_TOWER, Units.TERRAN_BARRACKS, Units.TERRAN_BARRACKS_FLYING, Units.TERRAN_FACTORY, Units.TERRAN_FACTORY_FLYING, Units.TERRAN_STARPORT, Units.TERRAN_STARPORT_FLYING, Units.TERRAN_REFINERY, Units.TERRAN_REFINERY_RICH, Units.TERRAN_BARRACKS_TECHLAB, Units.TERRAN_FACTORY_TECHLAB, Units.TERRAN_STARPORT_TECHLAB, Units.TERRAN_TECHLAB, Units.TERRAN_BARRACKS_REACTOR, Units.TERRAN_FACTORY_REACTOR, Units.TERRAN_STARPORT_REACTOR, Units.TERRAN_REACTOR));
      EVIDENCE_OF_AIR = new HashSet(Set.of(Units.TERRAN_FUSION_CORE, Units.TERRAN_STARPORT, Units.TERRAN_VIKING_FIGHTER, Units.TERRAN_VIKING_ASSAULT, Units.TERRAN_BANSHEE, Units.TERRAN_BATTLECRUISER, Units.TERRAN_RAVEN, Units.TERRAN_MEDIVAC, Units.TERRAN_LIBERATOR, Units.TERRAN_LIBERATOR_AG, Units.PROTOSS_STARGATE, Units.PROTOSS_FLEET_BEACON, Units.PROTOSS_TEMPEST, Units.PROTOSS_ORACLE, Units.PROTOSS_ORACLE_STASIS_TRAP, Units.PROTOSS_VOIDRAY, Units.ZERG_SPIRE, Units.ZERG_GREATER_SPIRE, Units.ZERG_MUTALISK, Units.ZERG_CORRUPTOR, Units.ZERG_BROODLORD, Units.ZERG_BROODLORD_COCOON));
      INFESTOR_TYPE = new HashSet(Set.of(Units.ZERG_INFESTOR, Units.ZERG_INFESTOR_BURROWED));
      IGNORED_TARGETS = new HashSet(Set.of(Units.ZERG_LARVA, Units.ZERG_EGG, Units.ZERG_BROODLING));
      LONG_RANGE_ENEMIES = new HashSet(Set.of(Units.PROTOSS_TEMPEST, Units.PROTOSS_OBSERVER, Units.ZERG_OVERSEER, Units.TERRAN_RAVEN, Units.TERRAN_THOR, Units.TERRAN_THOR_AP));
   }
}

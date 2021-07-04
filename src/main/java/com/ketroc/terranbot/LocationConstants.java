package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat.Channel;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.game.raw.StartRaw;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.ArmyManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.TriangleOfNodes;
import com.ketroc.terranbot.strategies.Strategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LocationConstants {
   public static final Point2d SCREEN_BOTTOM_LEFT;
   public static final Point2d SCREEN_TOP_RIGHT;
   public static final int MAX_X;
   public static final int MAX_Y;
   public static Point2d insideMainWall;
   public static Point2d mainBaseMidPos;
   public static Point2d enemyMainBaseMidPos;
   public static boolean isTopSpawn;
   public static String MAP;
   public static int numReaperWall;
   public static TriangleOfNodes myMineralTriangle;
   public static TriangleOfNodes enemyMineralTriangle;
   public static Point2d myMineralPos;
   public static Point2d enemyMineralPos;
   public static Point2d REPAIR_BAY;
   public static Point2d REAPER_JUMP2;
   public static Point2d BUNKER_NATURAL;
   public static Point2d FACTORY;
   public static Point2d WALL_2x2;
   public static Point2d WALL_3x3;
   public static Point2d MID_WALL_3x3;
   public static Point2d MID_WALL_2x2;
   public static List<Point2d> _3x3Structures;
   public static List<Point2d> extraDepots;
   public static List<Point2d> STARPORTS;
   public static List<Point2d> TURRETS;
   public static List<Point2d> MACRO_OCS;
   public static Point2d proxyBarracksPos;
   public static Point2d proxyBunkerPos;
   public static List<Point2d> baseLocations;
   public static List<Point2d> clockBasePositions;
   public static List<Point2d> counterClockBasePositions;
   public static int baseAttackIndex;
   public static Race opponentRace;

   public static void onGameStart(UnitInPool mainCC) {
      if (MAP.equals("Golden Wall LE")) {
         isTopSpawn = mainCC.unit().getPosition().getX() < 100.0F;
      } else {
         isTopSpawn = mainCC.unit().getPosition().getY() > 100.0F;
      }

      setStructureLocations();
      setBaseLocations();
      setClockBaseLists();
      createBaseList(mainCC);
      insideMainWall = Position.towards(MID_WALL_3x3, (Point2d)baseLocations.get(0), 2.5F);
      initEnemyRaceSpecifics();
      mapMainAndNatBases();
      mainBaseMidPos = getMainBaseMidPoint(false);
      enemyMainBaseMidPos = getMainBaseMidPoint(true);
      enemyMineralTriangle = new TriangleOfNodes(enemyMineralPos);
      myMineralTriangle = new TriangleOfNodes(myMineralPos);
   }

   public static void onStep() {
      if (Bot.OBS.getGameLoop() % 6720L == 0L && Base.numMyBases() >= 4) {
         baseAttackIndex = Math.max(2, getNewEnemyBaseIndex());
         skipBasesIOwn();
      }

   }

   private static int getNewEnemyBaseIndex() {
      if (UnitUtils.enemyCommandStructures != null) {
         for(int i = 0; i < baseLocations.size(); ++i) {
            Point2d basePos = (Point2d)baseLocations.get(i);
            Iterator var2 = UnitUtils.enemyCommandStructures.iterator();

            while(var2.hasNext()) {
               Units unitType = (Units)var2.next();
               List<UnitInPool> enemyCommandStructures = (List)GameCache.allEnemiesMap.getOrDefault(unitType, Collections.emptyList());
               if (enemyCommandStructures.stream().anyMatch((enemyBase) -> {
                  return UnitUtils.getDistance(enemyBase.unit(), basePos) < 1.0F;
               })) {
                  return i;
               }
            }
         }
      }

      return 1;
   }

   public static void rotateBaseAttackIndex() {
      if (baseAttackIndex == baseLocations.size() - 1) {
         Switches.finishHim = true;
         Bot.ACTION.sendChat("Finish Him!", Channel.BROADCAST);
      } else {
         ++baseAttackIndex;
      }

      skipBasesIOwn();
   }

   private static void skipBasesIOwn() {
      while(baseAttackIndex < baseLocations.size() - 1 && ((Base)GameCache.baseList.get(baseAttackIndex)).isMyBase()) {
         ++baseAttackIndex;
      }

   }

   private static void mapMainAndNatBases() {
      int xMin = 0;
      int xMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getX());
      int yMin = 0;
      int yMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getY());
      Point2d homePos = (Point2d)baseLocations.get(0);
      float homeZ = Bot.OBS.terrainHeight(homePos);
      Point2d enemyPos = (Point2d)baseLocations.get(baseLocations.size() - 1);
      float enemyZ = Bot.OBS.terrainHeight(enemyPos);
      Point2d natPos = (Point2d)baseLocations.get(1);
      float natZ = Bot.OBS.terrainHeight(natPos);
      Point2d enemyNatPos = (Point2d)baseLocations.get(baseLocations.size() - 2);
      float enemyNatZ = Bot.OBS.terrainHeight(enemyNatPos);

      for(int x = xMin; x <= xMax; ++x) {
         for(int y = yMin; y <= yMax; ++y) {
            Point2d thisPos = Point2d.of((float)x / 2.0F, (float)y / 2.0F);
            float thisZ = Bot.OBS.terrainHeight(thisPos);
            if (thisPos.distance(homePos) < 30.0D && Math.abs(thisZ - homeZ) < 1.2F && Bot.OBS.isPathable(thisPos)) {
               InfluenceMaps.pointInMainBase[x][y] = true;
            } else if (thisPos.distance(enemyPos) < 30.0D && Math.abs(thisZ - enemyZ) < 1.2F && Bot.OBS.isPathable(thisPos)) {
               InfluenceMaps.pointInEnemyMainBase[x][y] = true;
            } else if (thisPos.distance(natPos) < 20.0D && Math.abs(thisZ - natZ) < 1.2F && Bot.OBS.isPathable(thisPos)) {
               InfluenceMaps.pointInNat[x][y] = true;
            } else if (thisPos.distance(enemyNatPos) < 20.0D && Math.abs(thisZ - enemyNatZ) < 1.2F && Bot.OBS.isPathable(thisPos)) {
               InfluenceMaps.pointInEnemyNat[x][y] = true;
            }
         }
      }

   }

   public static void setRepairBayLocation() {
      Base mainBase = (Base)GameCache.baseList.get(0);
      REPAIR_BAY = mainBase.getResourceMidPoint();
      ArmyManager.retreatPos = ArmyManager.attackPos = REPAIR_BAY;
   }

   private static void createBaseList(UnitInPool mainCC) {
      Iterator var1 = baseLocations.iterator();

      while(var1.hasNext()) {
         Point2d baseLocation = (Point2d)var1.next();
         GameCache.baseList.add(new Base(baseLocation));
      }

      ((Base)GameCache.baseList.get(0)).setCc(mainCC);
   }

   public static void setRaceStrategies() {
      switch(opponentRace) {
      case ZERG:
         Strategy.DO_INCLUDE_LIBS = true;
         Strategy.DO_INCLUDE_TANKS = true;
         break;
      case PROTOSS:
         Strategy.DIVE_RANGE = 25;
         Strategy.DO_INCLUDE_LIBS = true;
         Strategy.DO_INCLUDE_TANKS = true;
         break;
      case TERRAN:
         Strategy.DO_INCLUDE_LIBS = false;
         Strategy.DO_INCLUDE_TANKS = false;
      }

   }

   public static void initEnemyRaceSpecifics() {
      setEnemyTypes();
      setRaceStrategies();
   }

   public static boolean setEnemyTypes() {
      switch(opponentRace) {
      case ZERG:
         UnitUtils.enemyCommandStructures = new HashSet(Set.of(Units.ZERG_HATCHERY, Units.ZERG_LAIR, Units.ZERG_HIVE));
         UnitUtils.enemyWorkerType = Units.ZERG_DRONE;
         return true;
      case PROTOSS:
         UnitUtils.enemyCommandStructures = new HashSet(Set.of(Units.PROTOSS_NEXUS));
         UnitUtils.enemyWorkerType = Units.PROTOSS_PROBE;
         return true;
      case TERRAN:
         UnitUtils.enemyCommandStructures = new HashSet(Set.of(Units.TERRAN_COMMAND_CENTER, Units.TERRAN_ORBITAL_COMMAND, Units.TERRAN_PLANETARY_FORTRESS, Units.TERRAN_COMMAND_CENTER_FLYING, Units.TERRAN_ORBITAL_COMMAND_FLYING));
         UnitUtils.enemyWorkerType = Units.TERRAN_SCV;
         return true;
      default:
         return false;
      }
   }

   private static void setStructureLocations() {
      String var0 = MAP;
      byte var1 = -1;
      switch(var0.hashCode()) {
      case -1794024318:
         if (var0.equals("Deathaura LE")) {
            var1 = 1;
         }
         break;
      case -1625047871:
         if (var0.equals("Ice and Chrome LE")) {
            var1 = 8;
         }
         break;
      case -1621757898:
         if (var0.equals("Zen LE")) {
            var1 = 17;
         }
         break;
      case -1571483726:
         if (var0.equals("Disco Bloodbath LE")) {
            var1 = 2;
         }
         break;
      case -1473241860:
         if (var0.equals("Thunderbird LE")) {
            var1 = 13;
         }
         break;
      case -1364344577:
         if (var0.equals("Simulacrum LE")) {
            var1 = 11;
         }
         break;
      case -1148468991:
         if (var0.equals("Acropolis LE")) {
            var1 = 0;
         }
         break;
      case -1125614728:
         if (var0.equals("Ever Dream LE")) {
            var1 = 6;
         }
         break;
      case -869678110:
         if (var0.equals("Eternal Empire LE")) {
            var1 = 5;
         }
         break;
      case -738120668:
         if (var0.equals("[TLMC12] Ephemeron")) {
            var1 = 3;
         }
         break;
      case -727670153:
         if (var0.equals("Submarine LE")) {
            var1 = 12;
         }
         break;
      case -699257656:
         if (var0.equals("Ephemeron LE")) {
            var1 = 4;
         }
         break;
      case -590794265:
         if (var0.equals("Pillars of Gold LE")) {
            var1 = 10;
         }
         break;
      case -74849212:
         if (var0.equals("Nightshade LE")) {
            var1 = 9;
         }
         break;
      case 556774015:
         if (var0.equals("Winter's Gate LE")) {
            var1 = 15;
         }
         break;
      case 805896056:
         if (var0.equals("Golden Wall LE")) {
            var1 = 7;
         }
         break;
      case 1661143439:
         if (var0.equals("World of Sleepers LE")) {
            var1 = 16;
         }
         break;
      case 1734971569:
         if (var0.equals("Triton LE")) {
            var1 = 14;
         }
      }

      switch(var1) {
      case 0:
         setLocationsForAcropolis(isTopSpawn);
         break;
      case 1:
         setLocationsForDeathAura(isTopSpawn);
         break;
      case 2:
         setLocationsForDiscoBloodBath(isTopSpawn);
         break;
      case 3:
      case 4:
         setLocationsForEphemeron(isTopSpawn);
         break;
      case 5:
         setLocationsForEternalEmpire(isTopSpawn);
         break;
      case 6:
         setLocationsForEverDream(isTopSpawn);
         break;
      case 7:
         setLocationsForGoldenWall(isTopSpawn);
         break;
      case 8:
         setLocationsForIceAndChrome(isTopSpawn);
         break;
      case 9:
         setLocationsForNightshade(isTopSpawn);
         break;
      case 10:
         setLocationsForPillarsOfGold(isTopSpawn);
         break;
      case 11:
         setLocationsForSimulacrum(isTopSpawn);
         break;
      case 12:
         setLocationsForSubmarine(isTopSpawn);
         break;
      case 13:
         setLocationsForThunderBird(isTopSpawn);
         break;
      case 14:
         setLocationsForTriton(isTopSpawn);
         break;
      case 15:
         setLocationsForWintersGate(isTopSpawn);
         break;
      case 16:
         setLocationsForWorldOfSleepers(isTopSpawn);
         break;
      case 17:
         setLocationsForZen(isTopSpawn);
      }

   }

   private static void setLocationsForAcropolis(boolean isTopPos) {
      numReaperWall = 1;
      if (isTopPos) {
         myMineralPos = Point2d.of(33.0F, 145.5F);
         enemyMineralPos = Point2d.of(143.0F, 26.5F);
         WALL_2x2 = Point2d.of(40.0F, 125.0F);
         MID_WALL_3x3 = Point2d.of(42.5F, 125.5F);
         WALL_3x3 = Point2d.of(43.5F, 122.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(41.5F, 144.5F));
         _3x3Structures.add(Point2d.of(22.5F, 135.5F));
         BUNKER_NATURAL = Point2d.of(36.5F, 105.5F);
         STARPORTS.add(Point2d.of(22.5F, 138.5F));
         STARPORTS.add(Point2d.of(29.5F, 149.5F));
         STARPORTS.add(Point2d.of(26.5F, 146.5F));
         STARPORTS.add(Point2d.of(35.5F, 148.5F));
         STARPORTS.add(Point2d.of(40.5F, 148.5F));
         STARPORTS.add(Point2d.of(22.5F, 132.5F));
         STARPORTS.add(Point2d.of(25.5F, 128.5F));
         STARPORTS.add(Point2d.of(30.5F, 125.5F));
         STARPORTS.add(Point2d.of(28.5F, 131.5F));
         STARPORTS.add(Point2d.of(30.5F, 134.5F));
         STARPORTS.add(Point2d.of(41.5F, 129.5F));
         STARPORTS.add(Point2d.of(47.5F, 130.5F));
         STARPORTS.add(Point2d.of(24.5F, 120.5F));
         STARPORTS.add(Point2d.of(20.5F, 113.5F));
         STARPORTS.add(Point2d.of(33.5F, 127.5F));
         STARPORTS.add(Point2d.of(22.5F, 141.5F));
         TURRETS.add(Point2d.of(38.0F, 143.0F));
         TURRETS.add(Point2d.of(29.0F, 138.0F));
         TURRETS.add(Point2d.of(31.0F, 146.0F));
         MACRO_OCS.add(Point2d.of(38.5F, 138.5F));
         MACRO_OCS.add(Point2d.of(43.5F, 138.5F));
         MACRO_OCS.add(Point2d.of(48.5F, 139.5F));
         MACRO_OCS.add(Point2d.of(45.5F, 144.5F));
         MACRO_OCS.add(Point2d.of(42.5F, 133.5F));
         MACRO_OCS.add(Point2d.of(37.5F, 132.5F));
         MACRO_OCS.add(Point2d.of(48.5F, 134.5F));
         MACRO_OCS.add(Point2d.of(47.5F, 125.5F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(52.0F, 132.0F));
         extraDepots.add(Point2d.of(23.0F, 144.0F));
         extraDepots.add(Point2d.of(25.0F, 144.0F));
         extraDepots.add(Point2d.of(33.0F, 147.0F));
         extraDepots.add(Point2d.of(35.0F, 146.0F));
         extraDepots.add(Point2d.of(42.0F, 142.0F));
         extraDepots.add(Point2d.of(40.0F, 142.0F));
         extraDepots.add(Point2d.of(23.0F, 130.0F));
         extraDepots.add(Point2d.of(27.0F, 126.0F));
         extraDepots.add(Point2d.of(33.0F, 132.0F));
         extraDepots.add(Point2d.of(31.0F, 128.0F));
         extraDepots.add(Point2d.of(33.0F, 130.0F));
         extraDepots.add(Point2d.of(36.0F, 129.0F));
         extraDepots.add(Point2d.of(49.0F, 143.0F));
      } else {
         myMineralPos = Point2d.of(143.0F, 26.5F);
         enemyMineralPos = Point2d.of(33.0F, 145.5F);
         WALL_2x2 = Point2d.of(136.0F, 47.0F);
         MID_WALL_3x3 = Point2d.of(133.5F, 46.5F);
         WALL_3x3 = Point2d.of(132.5F, 49.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(153.5F, 35.5F));
         _3x3Structures.add(Point2d.of(128.5F, 27.5F));
         BUNKER_NATURAL = Point2d.of(139.5F, 67.5F);
         STARPORTS.add(Point2d.of(133.5F, 24.5F));
         STARPORTS.add(Point2d.of(132.5F, 27.5F));
         STARPORTS.add(Point2d.of(139.5F, 23.5F));
         STARPORTS.add(Point2d.of(144.5F, 23.5F));
         STARPORTS.add(Point2d.of(147.5F, 26.5F));
         STARPORTS.add(Point2d.of(151.5F, 30.5F));
         STARPORTS.add(Point2d.of(144.5F, 37.5F));
         STARPORTS.add(Point2d.of(144.5F, 40.5F));
         STARPORTS.add(Point2d.of(150.5F, 39.5F));
         STARPORTS.add(Point2d.of(149.5F, 43.5F));
         STARPORTS.add(Point2d.of(124.5F, 37.5F));
         STARPORTS.add(Point2d.of(126.5F, 40.5F));
         STARPORTS.add(Point2d.of(127.5F, 43.5F));
         STARPORTS.add(Point2d.of(127.5F, 46.5F));
         STARPORTS.add(Point2d.of(133.5F, 42.5F));
         STARPORTS.add(Point2d.of(149.5F, 51.5F));
         TURRETS.add(Point2d.of(138.0F, 29.0F));
         TURRETS.add(Point2d.of(147.0F, 34.0F));
         TURRETS.add(Point2d.of(152.0F, 28.0F));
         MACRO_OCS.add(Point2d.of(137.5F, 33.5F));
         MACRO_OCS.add(Point2d.of(132.5F, 32.5F));
         MACRO_OCS.add(Point2d.of(127.5F, 32.5F));
         MACRO_OCS.add(Point2d.of(138.5F, 38.5F));
         MACRO_OCS.add(Point2d.of(132.5F, 37.5F));
         MACRO_OCS.add(Point2d.of(144.5F, 45.5F));
         MACRO_OCS.add(Point2d.of(139.5F, 43.5F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(124.0F, 40.0F));
         extraDepots.add(Point2d.of(141.0F, 26.0F));
         extraDepots.add(Point2d.of(137.0F, 22.0F));
         extraDepots.add(Point2d.of(135.0F, 22.0F));
         extraDepots.add(Point2d.of(135.0F, 29.0F));
         extraDepots.add(Point2d.of(145.0F, 26.0F));
         extraDepots.add(Point2d.of(150.0F, 28.0F));
         extraDepots.add(Point2d.of(129.0F, 36.0F));
         extraDepots.add(Point2d.of(129.0F, 38.0F));
         extraDepots.add(Point2d.of(131.0F, 41.0F));
         extraDepots.add(Point2d.of(142.0F, 37.0F));
         extraDepots.add(Point2d.of(142.0F, 39.0F));
         extraDepots.add(Point2d.of(136.0F, 44.0F));
         extraDepots.add(Point2d.of(154.0F, 41.0F));
         extraDepots.add(Point2d.of(149.0F, 46.0F));
         extraDepots.add(Point2d.of(126.0F, 29.0F));
      }

   }

   private static void setLocationsForDeathAura(boolean isTopPos) {
      numReaperWall = 3;
      if (isTopPos) {
         myMineralPos = Point2d.of(37.0F, 146.5F);
         enemyMineralPos = Point2d.of(155.0F, 41.5F);
         WALL_2x2 = Point2d.of(47.0F, 140.0F);
         WALL_3x3 = Point2d.of(49.5F, 136.5F);
         MID_WALL_3x3 = Point2d.of(46.5F, 137.5F);
         MID_WALL_2x2 = Point2d.of(47.0F, 138.0F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(38.5F, 132.5F));
         _3x3Structures.add(Point2d.of(26.5F, 139.5F));
         REAPER_JUMP2 = Point2d.of(46.5F, 128.5F);
         BUNKER_NATURAL = Point2d.of(62.5F, 138.5F);
         STARPORTS.add(Point2d.of(37.5F, 135.5F));
         STARPORTS.add(Point2d.of(40.5F, 148.5F));
         STARPORTS.add(Point2d.of(29.5F, 146.5F));
         STARPORTS.add(Point2d.of(41.5F, 144.5F));
         STARPORTS.add(Point2d.of(32.5F, 148.5F));
         STARPORTS.add(Point2d.of(35.5F, 150.5F));
         STARPORTS.add(Point2d.of(26.5F, 142.5F));
         STARPORTS.add(Point2d.of(27.5F, 134.5F));
         STARPORTS.add(Point2d.of(28.5F, 130.5F));
         STARPORTS.add(Point2d.of(34.5F, 129.5F));
         STARPORTS.add(Point2d.of(47.5F, 146.5F));
         STARPORTS.add(Point2d.of(46.5F, 152.5F));
         STARPORTS.add(Point2d.of(48.5F, 156.5F));
         STARPORTS.add(Point2d.of(65.5F, 155.5F));
         STARPORTS.add(Point2d.of(60.5F, 158.5F));
         STARPORTS.add(Point2d.of(54.5F, 158.5F));
         TURRETS.add(Point2d.of(38.0F, 144.0F));
         TURRETS.add(Point2d.of(33.0F, 136.0F));
         TURRETS.add(Point2d.of(33.0F, 139.0F));
         MACRO_OCS.add(Point2d.of(42.5F, 138.5F));
         MACRO_OCS.add(Point2d.of(45.5F, 133.5F));
         MACRO_OCS.add(Point2d.of(30.5F, 125.5F));
         MACRO_OCS.add(Point2d.of(35.5F, 125.5F));
         MACRO_OCS.add(Point2d.of(35.5F, 120.5F));
         MACRO_OCS.add(Point2d.of(40.5F, 122.5F));
         MACRO_OCS.add(Point2d.of(41.5F, 127.5F));
         extraDepots.add(Point2d.of(49.0F, 130.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(51.0F, 134.0F));
         extraDepots.add(Point2d.of(44.0F, 146.0F));
         extraDepots.add(Point2d.of(49.0F, 134.0F));
         extraDepots.add(Point2d.of(43.0F, 150.0F));
         extraDepots.add(Point2d.of(49.0F, 132.0F));
         extraDepots.add(Point2d.of(26.0F, 137.0F));
         extraDepots.add(Point2d.of(38.0F, 152.0F));
         extraDepots.add(Point2d.of(28.0F, 137.0F));
         extraDepots.add(Point2d.of(29.0F, 144.0F));
         extraDepots.add(Point2d.of(29.0F, 139.0F));
         extraDepots.add(Point2d.of(37.0F, 148.0F));
         extraDepots.add(Point2d.of(31.0F, 132.0F));
         extraDepots.add(Point2d.of(30.0F, 149.0F));
         extraDepots.add(Point2d.of(41.0F, 133.0F));
      } else {
         myMineralPos = Point2d.of(155.0F, 41.5F);
         enemyMineralPos = Point2d.of(37.0F, 146.5F);
         WALL_2x2 = Point2d.of(145.0F, 48.0F);
         MID_WALL_3x3 = Point2d.of(145.5F, 50.5F);
         MID_WALL_2x2 = Point2d.of(145.0F, 50.0F);
         WALL_3x3 = Point2d.of(142.5F, 51.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(164.5F, 51.5F));
         _3x3Structures.add(Point2d.of(162.5F, 57.5F));
         REAPER_JUMP2 = Point2d.of(145.5F, 59.5F);
         BUNKER_NATURAL = Point2d.of(129.5F, 49.5F);
         STARPORTS.add(Point2d.of(148.5F, 39.5F));
         STARPORTS.add(Point2d.of(154.5F, 39.5F));
         STARPORTS.add(Point2d.of(153.5F, 36.5F));
         STARPORTS.add(Point2d.of(148.5F, 42.5F));
         STARPORTS.add(Point2d.of(160.5F, 40.5F));
         STARPORTS.add(Point2d.of(163.5F, 45.5F));
         STARPORTS.add(Point2d.of(162.5F, 54.5F));
         STARPORTS.add(Point2d.of(156.5F, 58.5F));
         STARPORTS.add(Point2d.of(152.5F, 52.5F));
         STARPORTS.add(Point2d.of(152.5F, 55.5F));
         STARPORTS.add(Point2d.of(141.5F, 42.5F));
         STARPORTS.add(Point2d.of(143.5F, 35.5F));
         STARPORTS.add(Point2d.of(141.5F, 31.5F));
         STARPORTS.add(Point2d.of(136.5F, 28.5F));
         STARPORTS.add(Point2d.of(131.5F, 28.5F));
         STARPORTS.add(Point2d.of(125.5F, 32.5F));
         TURRETS.add(Point2d.of(153.0F, 44.0F));
         TURRETS.add(Point2d.of(159.0F, 52.0F));
         TURRETS.add(Point2d.of(159.0F, 49.0F));
         MACRO_OCS.add(Point2d.of(149.5F, 48.5F));
         MACRO_OCS.add(Point2d.of(146.5F, 54.5F));
         MACRO_OCS.add(Point2d.of(161.5F, 62.5F));
         MACRO_OCS.add(Point2d.of(156.5F, 62.5F));
         MACRO_OCS.add(Point2d.of(156.5F, 67.5F));
         MACRO_OCS.add(Point2d.of(151.5F, 65.5F));
         MACRO_OCS.add(Point2d.of(150.5F, 60.5F));
         extraDepots.add(Point2d.of(143.0F, 58.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(141.0F, 54.0F));
         extraDepots.add(Point2d.of(166.0F, 61.0F));
         extraDepots.add(Point2d.of(143.0F, 54.0F));
         extraDepots.add(Point2d.of(160.0F, 66.0F));
         extraDepots.add(Point2d.of(143.0F, 56.0F));
         extraDepots.add(Point2d.of(166.0F, 47.0F));
         extraDepots.add(Point2d.of(157.0F, 41.0F));
         extraDepots.add(Point2d.of(166.0F, 49.0F));
         extraDepots.add(Point2d.of(162.0F, 43.0F));
         extraDepots.add(Point2d.of(158.0F, 37.0F));
         extraDepots.add(Point2d.of(164.0F, 42.0F));
         extraDepots.add(Point2d.of(161.0F, 38.0F));
         extraDepots.add(Point2d.of(164.0F, 48.0F));
         extraDepots.add(Point2d.of(147.0F, 62.0F));
      }

   }

   private static void setLocationsForDiscoBloodBath(boolean isTopPos) {
      numReaperWall = 2;
      if (isTopPos) {
         myMineralPos = Point2d.of(39.0F, 108.5F);
         enemyMineralPos = Point2d.of(161.0F, 71.5F);
         WALL_2x2 = Point2d.of(39.0F, 132.0F);
         MID_WALL_3x3 = Point2d.of(38.5F, 129.5F);
         WALL_3x3 = Point2d.of(41.5F, 128.5F);
         MID_WALL_2x2 = Point2d.of(39.0F, 130.0F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(37.5F, 104.5F));
         _3x3Structures.add(Point2d.of(30.5F, 112.5F));
         REAPER_JUMP2 = Point2d.of(56.5F, 122.5F);
         BUNKER_NATURAL = Point2d.of(56.5F, 139.5F);
         STARPORTS.add(Point2d.of(43.5F, 113.5F));
         STARPORTS.add(Point2d.of(43.5F, 116.5F));
         STARPORTS.add(Point2d.of(43.5F, 119.5F));
         STARPORTS.add(Point2d.of(36.5F, 107.5F));
         STARPORTS.add(Point2d.of(42.5F, 105.5F));
         STARPORTS.add(Point2d.of(48.5F, 105.5F));
         STARPORTS.add(Point2d.of(47.5F, 108.5F));
         STARPORTS.add(Point2d.of(52.5F, 108.5F));
         STARPORTS.add(Point2d.of(54.5F, 126.5F));
         STARPORTS.add(Point2d.of(37.5F, 119.5F));
         STARPORTS.add(Point2d.of(37.5F, 122.5F));
         STARPORTS.add(Point2d.of(37.5F, 125.5F));
         STARPORTS.add(Point2d.of(31.5F, 121.5F));
         STARPORTS.add(Point2d.of(30.5F, 108.5F));
         TURRETS.add(Point2d.of(35.0F, 116.0F));
         TURRETS.add(Point2d.of(43.0F, 111.0F));
         TURRETS.add(Point2d.of(37.0F, 111.0F));
         MACRO_OCS.add(Point2d.of(34.5F, 130.5F));
         MACRO_OCS.add(Point2d.of(50.5F, 113.5F));
         MACRO_OCS.add(Point2d.of(50.5F, 118.5F));
         MACRO_OCS.add(Point2d.of(50.5F, 123.5F));
         MACRO_OCS.add(Point2d.of(44.5F, 123.5F));
         MACRO_OCS.add(Point2d.of(32.5F, 125.5F));
         extraDepots.add(Point2d.of(59.0F, 124.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(45.0F, 111.0F));
         extraDepots.add(Point2d.of(31.0F, 106.0F));
         extraDepots.add(Point2d.of(33.0F, 106.0F));
         extraDepots.add(Point2d.of(41.0F, 108.0F));
         extraDepots.add(Point2d.of(40.0F, 104.0F));
         extraDepots.add(Point2d.of(42.0F, 103.0F));
         extraDepots.add(Point2d.of(46.0F, 103.0F));
         extraDepots.add(Point2d.of(29.0F, 124.0F));
         extraDepots.add(Point2d.of(29.0F, 128.0F));
         extraDepots.add(Point2d.of(29.0F, 116.0F));
         extraDepots.add(Point2d.of(29.0F, 120.0F));
         extraDepots.add(Point2d.of(31.0F, 116.0F));
         extraDepots.add(Point2d.of(30.0F, 118.0F));
         extraDepots.add(Point2d.of(31.0F, 129.0F));
      } else {
         myMineralPos = Point2d.of(161.0F, 71.5F);
         enemyMineralPos = Point2d.of(39.0F, 108.5F);
         WALL_2x2 = Point2d.of(161.0F, 48.0F);
         MID_WALL_3x3 = Point2d.of(161.5F, 50.5F);
         MID_WALL_2x2 = Point2d.of(161.0F, 50.0F);
         WALL_3x3 = Point2d.of(158.5F, 51.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(154.5F, 74.5F));
         _3x3Structures.add(Point2d.of(169.5F, 67.5F));
         REAPER_JUMP2 = Point2d.of(142.5F, 55.5F);
         BUNKER_NATURAL = Point2d.of(143.5F, 40.5F);
         STARPORTS.add(Point2d.of(168.5F, 72.5F));
         STARPORTS.add(Point2d.of(162.5F, 74.5F));
         STARPORTS.add(Point2d.of(157.5F, 74.5F));
         STARPORTS.add(Point2d.of(145.5F, 58.5F));
         STARPORTS.add(Point2d.of(160.5F, 60.5F));
         STARPORTS.add(Point2d.of(160.5F, 57.5F));
         STARPORTS.add(Point2d.of(161.5F, 54.5F));
         STARPORTS.add(Point2d.of(156.5F, 55.5F));
         STARPORTS.add(Point2d.of(166.5F, 58.5F));
         STARPORTS.add(Point2d.of(168.5F, 52.5F));
         STARPORTS.add(Point2d.of(165.5F, 50.5F));
         STARPORTS.add(Point2d.of(167.5F, 55.5F));
         STARPORTS.add(Point2d.of(160.5F, 32.5F));
         STARPORTS.add(Point2d.of(151.5F, 28.5F));
         STARPORTS.add(Point2d.of(157.5F, 29.5F));
         TURRETS.add(Point2d.of(157.0F, 68.0F));
         TURRETS.add(Point2d.of(165.0F, 64.0F));
         TURRETS.add(Point2d.of(165.0F, 72.0F));
         MACRO_OCS.add(Point2d.of(149.5F, 73.5F));
         MACRO_OCS.add(Point2d.of(155.5F, 64.5F));
         MACRO_OCS.add(Point2d.of(149.5F, 68.5F));
         MACRO_OCS.add(Point2d.of(150.5F, 63.5F));
         MACRO_OCS.add(Point2d.of(146.5F, 54.5F));
         MACRO_OCS.add(Point2d.of(152.5F, 58.5F));
         extraDepots.add(Point2d.of(143.0F, 58.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(154.0F, 70.0F));
         extraDepots.add(Point2d.of(154.0F, 72.0F));
         extraDepots.add(Point2d.of(153.0F, 68.0F));
         extraDepots.add(Point2d.of(155.0F, 68.0F));
         extraDepots.add(Point2d.of(156.0F, 61.0F));
         extraDepots.add(Point2d.of(158.0F, 59.0F));
         extraDepots.add(Point2d.of(156.0F, 59.0F));
         extraDepots.add(Point2d.of(158.0F, 61.0F));
         extraDepots.add(Point2d.of(169.0F, 64.0F));
         extraDepots.add(Point2d.of(171.0F, 64.0F));
         extraDepots.add(Point2d.of(170.0F, 60.0F));
         extraDepots.add(Point2d.of(170.0F, 62.0F));
         extraDepots.add(Point2d.of(146.0F, 73.0F));
         extraDepots.add(Point2d.of(159.0F, 72.0F));
         extraDepots.add(Point2d.of(163.0F, 72.0F));
         extraDepots.add(Point2d.of(160.0F, 76.0F));
         extraDepots.add(Point2d.of(146.0F, 71.0F));
         extraDepots.add(Point2d.of(154.0F, 77.0F));
         extraDepots.add(Point2d.of(150.0F, 77.0F));
      }

   }

   private static void setLocationsForEphemeron(boolean isTopPos) {
      numReaperWall = 3;
      if (isTopPos) {
         myMineralPos = Point2d.of(22.0F, 139.5F);
         enemyMineralPos = Point2d.of(138.0F, 20.5F);
         WALL_2x2 = Point2d.of(34.0F, 125.0F);
         MID_WALL_2x2 = Point2d.of(36.0F, 125.0F);
         MID_WALL_3x3 = Point2d.of(36.5F, 125.5F);
         WALL_3x3 = Point2d.of(37.5F, 122.5F);
         BUNKER_NATURAL = Point2d.of(38.5F, 111.5F);
         REAPER_JUMP2 = Point2d.of(43.5F, 123.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(35.5F, 129.5F));
         _3x3Structures.add(Point2d.of(34.5F, 142.5F));
         STARPORTS.add(Point2d.of(25.5F, 149.5F));
         STARPORTS.add(Point2d.of(22.5F, 147.5F));
         STARPORTS.add(Point2d.of(31.5F, 148.5F));
         STARPORTS.add(Point2d.of(18.5F, 143.5F));
         STARPORTS.add(Point2d.of(18.5F, 138.5F));
         STARPORTS.add(Point2d.of(26.5F, 134.5F));
         STARPORTS.add(Point2d.of(37.5F, 146.5F));
         STARPORTS.add(Point2d.of(20.5F, 129.5F));
         STARPORTS.add(Point2d.of(23.5F, 131.5F));
         STARPORTS.add(Point2d.of(25.5F, 128.5F));
         STARPORTS.add(Point2d.of(30.5F, 127.5F));
         STARPORTS.add(Point2d.of(28.5F, 131.5F));
         STARPORTS.add(Point2d.of(40.5F, 148.5F));
         STARPORTS.add(Point2d.of(42.5F, 145.5F));
         STARPORTS.add(Point2d.of(20.5F, 118.5F));
         TURRETS.add(Point2d.of(25.0F, 138.0F));
         TURRETS.add(Point2d.of(30.0F, 143.0F));
         TURRETS.add(Point2d.of(26.0F, 142.0F));
         MACRO_OCS.add(Point2d.of(34.5F, 138.5F));
         MACRO_OCS.add(Point2d.of(34.5F, 133.5F));
         MACRO_OCS.add(Point2d.of(39.5F, 134.5F));
         MACRO_OCS.add(Point2d.of(44.5F, 135.5F));
         MACRO_OCS.add(Point2d.of(44.5F, 140.5F));
         MACRO_OCS.add(Point2d.of(39.5F, 141.5F));
         MACRO_OCS.add(Point2d.of(45.5F, 130.5F));
         extraDepots.add(Point2d.of(46.0F, 125.0F));
         extraDepots.add(Point2d.of(42.0F, 121.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(28.0F, 151.0F));
         extraDepots.add(Point2d.of(35.0F, 146.0F));
         extraDepots.add(Point2d.of(21.0F, 145.0F));
         extraDepots.add(Point2d.of(17.0F, 136.0F));
         extraDepots.add(Point2d.of(19.0F, 134.0F));
         extraDepots.add(Point2d.of(27.0F, 147.0F));
         extraDepots.add(Point2d.of(29.0F, 147.0F));
         extraDepots.add(Point2d.of(23.0F, 150.0F));
         extraDepots.add(Point2d.of(20.0F, 147.0F));
         extraDepots.add(Point2d.of(20.0F, 132.0F));
         extraDepots.add(Point2d.of(18.0F, 141.0F));
         extraDepots.add(Point2d.of(20.0F, 141.0F));
         extraDepots.add(Point2d.of(32.0F, 151.0F));
         extraDepots.add(Point2d.of(38.0F, 149.0F));
      } else {
         myMineralPos = Point2d.of(138.0F, 20.5F);
         enemyMineralPos = Point2d.of(22.0F, 139.5F);
         REAPER_JUMP2 = Point2d.of(116.5F, 36.5F);
         BUNKER_NATURAL = Point2d.of(121.5F, 48.5F);
         WALL_2x2 = Point2d.of(126.0F, 35.0F);
         WALL_3x3 = Point2d.of(122.5F, 37.5F);
         MID_WALL_2x2 = Point2d.of(124.0F, 35.0F);
         MID_WALL_3x3 = Point2d.of(123.5F, 34.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(132.5F, 26.5F));
         _3x3Structures.add(Point2d.of(123.5F, 13.5F));
         STARPORTS.add(Point2d.of(127.5F, 27.5F));
         STARPORTS.add(Point2d.of(130.5F, 29.5F));
         STARPORTS.add(Point2d.of(135.5F, 28.5F));
         STARPORTS.add(Point2d.of(138.5F, 30.5F));
         STARPORTS.add(Point2d.of(140.5F, 24.5F));
         STARPORTS.add(Point2d.of(140.5F, 20.5F));
         STARPORTS.add(Point2d.of(140.5F, 16.5F));
         STARPORTS.add(Point2d.of(137.5F, 14.5F));
         STARPORTS.add(Point2d.of(134.5F, 12.5F));
         STARPORTS.add(Point2d.of(129.5F, 11.5F));
         STARPORTS.add(Point2d.of(133.5F, 31.5F));
         STARPORTS.add(Point2d.of(128.5F, 32.5F));
         STARPORTS.add(Point2d.of(120.5F, 32.5F));
         STARPORTS.add(Point2d.of(117.5F, 30.5F));
         STARPORTS.add(Point2d.of(114.5F, 28.5F));
         TURRETS.add(Point2d.of(127.0F, 17.0F));
         TURRETS.add(Point2d.of(135.0F, 22.0F));
         TURRETS.add(Point2d.of(132.0F, 13.0F));
         MACRO_OCS.add(Point2d.of(125.5F, 21.5F));
         MACRO_OCS.add(Point2d.of(120.5F, 22.5F));
         MACRO_OCS.add(Point2d.of(120.5F, 17.5F));
         MACRO_OCS.add(Point2d.of(119.5F, 12.5F));
         MACRO_OCS.add(Point2d.of(115.5F, 17.5F));
         MACRO_OCS.add(Point2d.of(114.5F, 23.5F));
         MACRO_OCS.add(Point2d.of(123.5F, 27.5F));
         extraDepots.add(Point2d.of(114.0F, 35.0F));
         extraDepots.add(Point2d.of(118.0F, 39.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(139.0F, 12.0F));
         extraDepots.add(Point2d.of(137.0F, 10.0F));
         extraDepots.add(Point2d.of(135.0F, 10.0F));
         extraDepots.add(Point2d.of(132.0F, 9.0F));
         extraDepots.add(Point2d.of(128.0F, 9.0F));
         extraDepots.add(Point2d.of(130.0F, 14.0F));
         extraDepots.add(Point2d.of(140.0F, 28.0F));
         extraDepots.add(Point2d.of(136.0F, 33.0F));
         extraDepots.add(Point2d.of(138.0F, 33.0F));
         extraDepots.add(Point2d.of(136.0F, 35.0F));
         extraDepots.add(Point2d.of(134.0F, 34.0F));
         extraDepots.add(Point2d.of(132.0F, 34.0F));
         extraDepots.add(Point2d.of(128.0F, 30.0F));
         extraDepots.add(Point2d.of(127.0F, 12.0F));
      }

   }

   private static void setLocationsForEternalEmpire(boolean isTopPos) {
      numReaperWall = 3;
      if (isTopPos) {
         proxyBarracksPos = Point2d.of(77.5F, 54.5F);
         proxyBunkerPos = Point2d.of(42.5F, 58.5F);
         myMineralPos = Point2d.of(150.0F, 141.5F);
         enemyMineralPos = Point2d.of(26.0F, 30.5F);
         WALL_2x2 = Point2d.of(144.0F, 125.0F);
         MID_WALL_2x2 = Point2d.of(146.0F, 125.0F);
         MID_WALL_3x3 = Point2d.of(146.5F, 125.5F);
         WALL_3x3 = Point2d.of(147.5F, 122.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(137.5F, 135.5F));
         _3x3Structures.add(Point2d.of(153.5F, 137.5F));
         FACTORY = Point2d.of(149.5F, 134.5F);
         BUNKER_NATURAL = Point2d.of(128.5F, 117.5F);
         REAPER_JUMP2 = Point2d.of(128.5F, 129.5F);
         STARPORTS.add(Point2d.of(128.5F, 132.5F));
         STARPORTS.add(Point2d.of(152.5F, 142.5F));
         STARPORTS.add(Point2d.of(153.5F, 146.5F));
         STARPORTS.add(Point2d.of(151.5F, 150.5F));
         STARPORTS.add(Point2d.of(147.5F, 148.5F));
         STARPORTS.add(Point2d.of(142.5F, 149.5F));
         STARPORTS.add(Point2d.of(146.5F, 151.5F));
         STARPORTS.add(Point2d.of(136.5F, 151.5F));
         STARPORTS.add(Point2d.of(133.5F, 145.5F));
         STARPORTS.add(Point2d.of(131.5F, 136.5F));
         STARPORTS.add(Point2d.of(131.5F, 129.5F));
         STARPORTS.add(Point2d.of(132.5F, 126.5F));
         STARPORTS.add(Point2d.of(151.5F, 125.5F));
         STARPORTS.add(Point2d.of(138.5F, 126.5F));
         STARPORTS.add(Point2d.of(153.5F, 111.5F));
         STARPORTS.add(Point2d.of(133.5F, 132.5F));
         TURRETS.add(Point2d.of(139.0F, 145.0F));
         TURRETS.add(Point2d.of(147.0F, 140.0F));
         TURRETS.add(Point2d.of(142.0F, 145.0F));
         MACRO_OCS.add(Point2d.of(132.5F, 150.5F));
         MACRO_OCS.add(Point2d.of(129.5F, 145.5F));
         MACRO_OCS.add(Point2d.of(137.5F, 140.5F));
         MACRO_OCS.add(Point2d.of(132.5F, 140.5F));
         MACRO_OCS.add(Point2d.of(141.5F, 135.5F));
         MACRO_OCS.add(Point2d.of(141.5F, 130.5F));
         MACRO_OCS.add(Point2d.of(154.5F, 130.5F));
         MACRO_OCS.add(Point2d.of(149.5F, 130.5F));
         extraDepots.add(Point2d.of(130.0F, 127.0F));
         extraDepots.add(Point2d.of(130.0F, 125.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(135.0F, 154.0F));
         extraDepots.add(Point2d.of(137.0F, 154.0F));
         extraDepots.add(Point2d.of(136.0F, 149.0F));
         extraDepots.add(Point2d.of(136.0F, 147.0F));
         extraDepots.add(Point2d.of(144.0F, 152.0F));
         extraDepots.add(Point2d.of(142.0F, 152.0F));
         extraDepots.add(Point2d.of(129.0F, 142.0F));
         extraDepots.add(Point2d.of(136.0F, 128.0F));
         extraDepots.add(Point2d.of(136.0F, 130.0F));
         extraDepots.add(Point2d.of(138.0F, 133.0F));
         extraDepots.add(Point2d.of(138.0F, 131.0F));
         extraDepots.add(Point2d.of(138.0F, 129.0F));
         extraDepots.add(Point2d.of(131.0F, 134.0F));
      } else {
         proxyBarracksPos = Point2d.of(96.5F, 117.5F);
         proxyBunkerPos = Point2d.of(133.5F, 113.5F);
         myMineralPos = Point2d.of(26.0F, 30.5F);
         enemyMineralPos = Point2d.of(150.0F, 141.5F);
         WALL_2x2 = Point2d.of(32.0F, 47.0F);
         MID_WALL_2x2 = Point2d.of(30.0F, 47.0F);
         MID_WALL_3x3 = Point2d.of(29.5F, 46.5F);
         WALL_3x3 = Point2d.of(28.5F, 49.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(22.5F, 35.5F));
         _3x3Structures.add(Point2d.of(27.5F, 20.5F));
         REAPER_JUMP2 = Point2d.of(47.5F, 42.5F);
         BUNKER_NATURAL = Point2d.of(47.5F, 54.5F);
         FACTORY = Point2d.of(31.5F, 36.5F);
         STARPORTS.add(Point2d.of(36.5F, 21.5F));
         STARPORTS.add(Point2d.of(22.5F, 21.5F));
         STARPORTS.add(Point2d.of(31.5F, 21.5F));
         STARPORTS.add(Point2d.of(22.5F, 32.5F));
         STARPORTS.add(Point2d.of(20.5F, 28.5F));
         STARPORTS.add(Point2d.of(20.5F, 25.5F));
         STARPORTS.add(Point2d.of(26.5F, 23.5F));
         STARPORTS.add(Point2d.of(40.5F, 26.5F));
         STARPORTS.add(Point2d.of(42.5F, 35.5F));
         STARPORTS.add(Point2d.of(43.5F, 38.5F));
         STARPORTS.add(Point2d.of(41.5F, 41.5F));
         STARPORTS.add(Point2d.of(20.5F, 59.5F));
         STARPORTS.add(Point2d.of(20.5F, 62.5F));
         STARPORTS.add(Point2d.of(20.5F, 65.5F));
         STARPORTS.add(Point2d.of(22.5F, 46.5F));
         TURRETS.add(Point2d.of(37.0F, 27.0F));
         TURRETS.add(Point2d.of(29.0F, 32.0F));
         TURRETS.add(Point2d.of(34.0F, 24.0F));
         MACRO_OCS.add(Point2d.of(38.5F, 31.5F));
         MACRO_OCS.add(Point2d.of(43.5F, 31.5F));
         MACRO_OCS.add(Point2d.of(46.5F, 26.5F));
         MACRO_OCS.add(Point2d.of(43.5F, 21.5F));
         MACRO_OCS.add(Point2d.of(37.5F, 36.5F));
         MACRO_OCS.add(Point2d.of(36.5F, 41.5F));
         MACRO_OCS.add(Point2d.of(21.5F, 41.5F));
         MACRO_OCS.add(Point2d.of(26.5F, 41.5F));
         MACRO_OCS.add(Point2d.of(42.5F, 46.5F));
         extraDepots.add(Point2d.of(46.0F, 45.0F));
         extraDepots.add(Point2d.of(46.0F, 47.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(41.0F, 18.0F));
         extraDepots.add(Point2d.of(39.0F, 18.0F));
         extraDepots.add(Point2d.of(39.0F, 23.0F));
         extraDepots.add(Point2d.of(26.0F, 26.0F));
         extraDepots.add(Point2d.of(23.0F, 30.0F));
         extraDepots.add(Point2d.of(25.0F, 29.0F));
         extraDepots.add(Point2d.of(21.0F, 38.0F));
         extraDepots.add(Point2d.of(23.0F, 38.0F));
         extraDepots.add(Point2d.of(25.0F, 38.0F));
         extraDepots.add(Point2d.of(27.0F, 38.0F));
         extraDepots.add(Point2d.of(34.0F, 38.0F));
         extraDepots.add(Point2d.of(47.0F, 30.0F));
         extraDepots.add(Point2d.of(43.0F, 28.0F));
      }

   }

   private static void setLocationsForEverDream(boolean isTopPos) {
      numReaperWall = 2;
      if (isTopPos) {
         proxyBarracksPos = Point2d.of(97.5F, 88.5F);
         proxyBunkerPos = Point2d.of(49.5F, 73.5F);
         myMineralPos = Point2d.of(147.0F, 164.5F);
         enemyMineralPos = Point2d.of(53.0F, 47.5F);
         WALL_2x2 = Point2d.of(144.0F, 151.0F);
         MID_WALL_2x2 = Point2d.of(142.0F, 151.0F);
         MID_WALL_3x3 = Point2d.of(141.5F, 151.5F);
         WALL_3x3 = Point2d.of(140.5F, 148.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(135.5F, 173.5F));
         _3x3Structures.add(Point2d.of(138.5F, 173.5F));
         BUNKER_NATURAL = Point2d.of(147.5F, 137.5F);
         REAPER_JUMP2 = Point2d.of(138.5F, 144.5F);
         FACTORY = Point2d.of(144.5F, 157.5F);
         STARPORTS.add(Point2d.of(149.5F, 161.5F));
         STARPORTS.add(Point2d.of(149.5F, 165.5F));
         STARPORTS.add(Point2d.of(147.5F, 169.5F));
         STARPORTS.add(Point2d.of(142.5F, 172.5F));
         STARPORTS.add(Point2d.of(129.5F, 172.5F));
         STARPORTS.add(Point2d.of(124.5F, 172.5F));
         STARPORTS.add(Point2d.of(129.5F, 168.5F));
         STARPORTS.add(Point2d.of(136.5F, 151.5F));
         STARPORTS.add(Point2d.of(131.5F, 151.5F));
         STARPORTS.add(Point2d.of(137.5F, 155.5F));
         STARPORTS.add(Point2d.of(137.5F, 158.5F));
         STARPORTS.add(Point2d.of(131.5F, 155.5F));
         STARPORTS.add(Point2d.of(131.5F, 158.5F));
         STARPORTS.add(Point2d.of(121.5F, 159.5F));
         STARPORTS.add(Point2d.of(160.5F, 154.5F));
         TURRETS.add(Point2d.of(135.0F, 168.0F));
         TURRETS.add(Point2d.of(144.0F, 163.0F));
         TURRETS.add(Point2d.of(138.0F, 168.0F));
         MACRO_OCS.add(Point2d.of(134.5F, 163.5F));
         MACRO_OCS.add(Point2d.of(129.5F, 163.5F));
         MACRO_OCS.add(Point2d.of(124.5F, 163.5F));
         MACRO_OCS.add(Point2d.of(120.5F, 168.5F));
         MACRO_OCS.add(Point2d.of(125.5F, 168.5F));
         MACRO_OCS.add(Point2d.of(134.5F, 147.5F));
         MACRO_OCS.add(Point2d.of(127.5F, 158.5F));
         extraDepots.add(Point2d.of(136.0F, 144.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(152.0F, 163.0F));
         extraDepots.add(Point2d.of(150.0F, 159.0F));
         extraDepots.add(Point2d.of(138.0F, 171.0F));
         extraDepots.add(Point2d.of(132.0F, 170.0F));
         extraDepots.add(Point2d.of(132.0F, 174.0F));
         extraDepots.add(Point2d.of(127.0F, 174.0F));
         extraDepots.add(Point2d.of(129.0F, 155.0F));
         extraDepots.add(Point2d.of(134.0F, 153.0F));
         extraDepots.add(Point2d.of(139.0F, 153.0F));
         extraDepots.add(Point2d.of(134.0F, 160.0F));
         extraDepots.add(Point2d.of(140.0F, 160.0F));
         extraDepots.add(Point2d.of(146.0F, 155.0F));
         extraDepots.add(Point2d.of(144.0F, 160.0F));
         extraDepots.add(Point2d.of(142.0F, 160.0F));
         extraDepots.add(Point2d.of(119.0F, 163.0F));
      } else {
         proxyBarracksPos = Point2d.of(100.5F, 123.5F);
         proxyBunkerPos = Point2d.of(151.5F, 138.5F);
         myMineralPos = Point2d.of(53.0F, 47.5F);
         enemyMineralPos = Point2d.of(147.0F, 164.5F);
         WALL_2x2 = Point2d.of(56.0F, 61.0F);
         WALL_3x3 = Point2d.of(59.5F, 63.5F);
         MID_WALL_2x2 = Point2d.of(58.0F, 61.0F);
         MID_WALL_3x3 = Point2d.of(58.5F, 60.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(68.5F, 42.5F));
         _3x3Structures.add(Point2d.of(52.5F, 42.5F));
         REAPER_JUMP2 = Point2d.of(61.5F, 67.5F);
         BUNKER_NATURAL = Point2d.of(52.5F, 74.5F);
         FACTORY = Point2d.of(57.5F, 55.5F);
         STARPORTS.add(Point2d.of(57.5F, 38.5F));
         STARPORTS.add(Point2d.of(62.5F, 38.5F));
         STARPORTS.add(Point2d.of(67.5F, 38.5F));
         STARPORTS.add(Point2d.of(72.5F, 38.5F));
         STARPORTS.add(Point2d.of(55.5F, 41.5F));
         STARPORTS.add(Point2d.of(48.5F, 50.5F));
         STARPORTS.add(Point2d.of(48.5F, 47.5F));
         STARPORTS.add(Point2d.of(71.5F, 42.5F));
         STARPORTS.add(Point2d.of(60.5F, 52.5F));
         STARPORTS.add(Point2d.of(62.5F, 55.5F));
         STARPORTS.add(Point2d.of(62.5F, 58.5F));
         STARPORTS.add(Point2d.of(73.5F, 52.5F));
         STARPORTS.add(Point2d.of(79.5F, 50.5F));
         STARPORTS.add(Point2d.of(73.5F, 55.5F));
         STARPORTS.add(Point2d.of(37.5F, 58.5F));
         TURRETS.add(Point2d.of(65.0F, 44.0F));
         TURRETS.add(Point2d.of(56.0F, 49.0F));
         TURRETS.add(Point2d.of(62.0F, 44.0F));
         MACRO_OCS.add(Point2d.of(65.5F, 48.5F));
         MACRO_OCS.add(Point2d.of(70.5F, 47.5F));
         MACRO_OCS.add(Point2d.of(75.5F, 47.5F));
         MACRO_OCS.add(Point2d.of(80.5F, 46.5F));
         MACRO_OCS.add(Point2d.of(77.5F, 41.5F));
         MACRO_OCS.add(Point2d.of(69.5F, 53.5F));
         MACRO_OCS.add(Point2d.of(68.5F, 58.5F));
         MACRO_OCS.add(Point2d.of(65.5F, 64.5F));
         extraDepots.add(Point2d.of(64.0F, 68.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(70.0F, 40.0F));
         extraDepots.add(Point2d.of(74.0F, 44.0F));
         extraDepots.add(Point2d.of(60.0F, 40.0F));
         extraDepots.add(Point2d.of(62.0F, 41.0F));
         extraDepots.add(Point2d.of(51.0F, 52.0F));
         extraDepots.add(Point2d.of(60.0F, 57.0F));
         extraDepots.add(Point2d.of(51.0F, 54.0F));
         extraDepots.add(Point2d.of(53.0F, 54.0F));
         extraDepots.add(Point2d.of(53.0F, 56.0F));
         extraDepots.add(Point2d.of(62.0F, 65.0F));
         extraDepots.add(Point2d.of(62.0F, 63.0F));
         extraDepots.add(Point2d.of(65.0F, 60.0F));
         extraDepots.add(Point2d.of(54.0F, 59.0F));
         extraDepots.add(Point2d.of(79.0F, 53.0F));
      }

   }

   private static void setLocationsForGoldenWall(boolean isTopPos) {
      numReaperWall = 1;
      if (isTopPos) {
         proxyBarracksPos = Point2d.of(132.5F, 97.5F);
         proxyBunkerPos = Point2d.of(158.5F, 76.5F);
         myMineralPos = Point2d.of(25.0F, 51.5F);
         enemyMineralPos = Point2d.of(183.0F, 51.5F);
         WALL_2x2 = Point2d.of(41.0F, 64.0F);
         MID_WALL_3x3 = Point2d.of(40.5F, 61.5F);
         WALL_3x3 = Point2d.of(43.5F, 60.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(22.5F, 48.5F));
         _3x3Structures.add(Point2d.of(22.5F, 53.5F));
         BUNKER_NATURAL = Point2d.of(48.5F, 78.5F);
         REAPER_JUMP2 = Point2d.of(128.5F, 129.5F);
         FACTORY = Point2d.of(34.5F, 57.5F);
         STARPORTS.add(Point2d.of(34.5F, 54.5F));
         STARPORTS.add(Point2d.of(36.5F, 50.5F));
         STARPORTS.add(Point2d.of(36.5F, 47.5F));
         STARPORTS.add(Point2d.of(33.5F, 45.5F));
         STARPORTS.add(Point2d.of(34.5F, 42.5F));
         STARPORTS.add(Point2d.of(39.5F, 44.5F));
         STARPORTS.add(Point2d.of(23.5F, 45.5F));
         STARPORTS.add(Point2d.of(23.5F, 42.5F));
         STARPORTS.add(Point2d.of(29.5F, 40.5F));
         STARPORTS.add(Point2d.of(25.5F, 38.5F));
         STARPORTS.add(Point2d.of(22.5F, 36.5F));
         STARPORTS.add(Point2d.of(32.5F, 36.5F));
         STARPORTS.add(Point2d.of(22.5F, 56.5F));
         STARPORTS.add(Point2d.of(25.5F, 58.5F));
         STARPORTS.add(Point2d.of(30.5F, 61.5F));
         STARPORTS.add(Point2d.of(31.5F, 64.5F));
         TURRETS.add(Point2d.of(30.0F, 46.0F));
         TURRETS.add(Point2d.of(30.0F, 55.0F));
         TURRETS.add(Point2d.of(29.0F, 50.0F));
         MACRO_OCS.add(Point2d.of(26.5F, 64.5F));
         MACRO_OCS.add(Point2d.of(46.5F, 55.5F));
         MACRO_OCS.add(Point2d.of(41.5F, 54.5F));
         MACRO_OCS.add(Point2d.of(45.5F, 49.5F));
         MACRO_OCS.add(Point2d.of(52.5F, 64.5F));
         MACRO_OCS.add(Point2d.of(51.5F, 69.5F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(46.0F, 87.0F));
         extraDepots.add(Point2d.of(23.0F, 51.0F));
         extraDepots.add(Point2d.of(23.0F, 62.0F));
         extraDepots.add(Point2d.of(25.0F, 61.0F));
         extraDepots.add(Point2d.of(27.0F, 61.0F));
         extraDepots.add(Point2d.of(28.0F, 43.0F));
         extraDepots.add(Point2d.of(28.0F, 45.0F));
         extraDepots.add(Point2d.of(21.0F, 46.0F));
         extraDepots.add(Point2d.of(21.0F, 44.0F));
         extraDepots.add(Point2d.of(23.0F, 40.0F));
         extraDepots.add(Point2d.of(39.0F, 42.0F));
         extraDepots.add(Point2d.of(39.0F, 40.0F));
         extraDepots.add(Point2d.of(41.0F, 42.0F));
         extraDepots.add(Point2d.of(37.0F, 40.0F));
         extraDepots.add(Point2d.of(35.0F, 40.0F));
         extraDepots.add(Point2d.of(42.0F, 46.0F));
      } else {
         proxyBarracksPos = Point2d.of(68.5F, 98.5F);
         proxyBunkerPos = Point2d.of(49.5F, 76.5F);
         myMineralPos = Point2d.of(183.0F, 51.5F);
         enemyMineralPos = Point2d.of(25.0F, 51.5F);
         WALL_2x2 = Point2d.of(167.0F, 64.0F);
         WALL_3x3 = Point2d.of(164.5F, 60.5F);
         MID_WALL_3x3 = Point2d.of(167.5F, 61.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(171.5F, 50.5F));
         _3x3Structures.add(Point2d.of(169.5F, 40.5F));
         BUNKER_NATURAL = Point2d.of(159.5F, 78.5F);
         FACTORY = Point2d.of(174.5F, 61.5F);
         STARPORTS.add(Point2d.of(184.5F, 45.5F));
         STARPORTS.add(Point2d.of(181.5F, 43.5F));
         STARPORTS.add(Point2d.of(172.5F, 37.5F));
         STARPORTS.add(Point2d.of(172.5F, 40.5F));
         STARPORTS.add(Point2d.of(178.5F, 40.5F));
         STARPORTS.add(Point2d.of(178.5F, 36.5F));
         STARPORTS.add(Point2d.of(167.5F, 54.5F));
         STARPORTS.add(Point2d.of(167.5F, 57.5F));
         STARPORTS.add(Point2d.of(164.5F, 46.5F));
         STARPORTS.add(Point2d.of(183.5F, 56.5F));
         STARPORTS.add(Point2d.of(180.5F, 59.5F));
         STARPORTS.add(Point2d.of(181.5F, 62.5F));
         STARPORTS.add(Point2d.of(175.5F, 64.5F));
         STARPORTS.add(Point2d.of(179.5F, 66.5F));
         STARPORTS.add(Point2d.of(175.5F, 71.5F));
         TURRETS.add(Point2d.of(178.0F, 46.0F));
         TURRETS.add(Point2d.of(178.0F, 55.0F));
         TURRETS.add(Point2d.of(184.0F, 49.0F));
         MACRO_OCS.add(Point2d.of(166.5F, 50.5F));
         MACRO_OCS.add(Point2d.of(161.5F, 50.5F));
         MACRO_OCS.add(Point2d.of(162.5F, 56.5F));
         MACRO_OCS.add(Point2d.of(170.5F, 45.5F));
         MACRO_OCS.add(Point2d.of(184.5F, 38.5F));
         MACRO_OCS.add(Point2d.of(155.5F, 64.5F));
         MACRO_OCS.add(Point2d.of(156.5F, 69.5F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(162.0F, 87.0F));
         extraDepots.add(Point2d.of(186.0F, 48.0F));
         extraDepots.add(Point2d.of(186.0F, 50.0F));
         extraDepots.add(Point2d.of(187.0F, 52.0F));
         extraDepots.add(Point2d.of(185.0F, 52.0F));
         extraDepots.add(Point2d.of(186.0F, 54.0F));
         extraDepots.add(Point2d.of(184.0F, 54.0F));
         extraDepots.add(Point2d.of(175.0F, 42.0F));
         extraDepots.add(Point2d.of(175.0F, 44.0F));
         extraDepots.add(Point2d.of(174.0F, 47.0F));
         extraDepots.add(Point2d.of(167.0F, 44.0F));
         extraDepots.add(Point2d.of(167.0F, 42.0F));
         extraDepots.add(Point2d.of(176.0F, 35.0F));
         extraDepots.add(Point2d.of(172.0F, 53.0F));
         extraDepots.add(Point2d.of(184.0F, 64.0F));
         extraDepots.add(Point2d.of(167.0F, 44.0F));
      }

   }

   private static void setLocationsForIceAndChrome(boolean isTopPos) {
      numReaperWall = 5;
      if (isTopPos) {
         myMineralPos = Point2d.of(190.0F, 173.5F);
         enemyMineralPos = Point2d.of(66.0F, 62.5F);
         WALL_2x2 = Point2d.of(178.0F, 159.0F);
         MID_WALL_2x2 = Point2d.of(176.0F, 159.0F);
         MID_WALL_3x3 = Point2d.of(175.5F, 159.5F);
         WALL_3x3 = Point2d.of(174.5F, 156.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(175.5F, 179.5F));
         _3x3Structures.add(Point2d.of(175.5F, 182.5F));
         BUNKER_NATURAL = Point2d.of(177.5F, 138.5F);
         REAPER_JUMP2 = Point2d.of(166.5F, 163.5F);
         STARPORTS.add(Point2d.of(182.5F, 163.5F));
         STARPORTS.add(Point2d.of(189.5F, 165.5F));
         STARPORTS.add(Point2d.of(192.5F, 168.5F));
         STARPORTS.add(Point2d.of(192.5F, 172.5F));
         STARPORTS.add(Point2d.of(189.5F, 179.5F));
         STARPORTS.add(Point2d.of(183.5F, 181.5F));
         STARPORTS.add(Point2d.of(178.5F, 182.5F));
         STARPORTS.add(Point2d.of(170.5F, 162.5F));
         STARPORTS.add(Point2d.of(169.5F, 165.5F));
         STARPORTS.add(Point2d.of(167.5F, 168.5F));
         STARPORTS.add(Point2d.of(173.5F, 168.5F));
         STARPORTS.add(Point2d.of(175.5F, 165.5F));
         STARPORTS.add(Point2d.of(176.5F, 162.5F));
         STARPORTS.add(Point2d.of(193.5F, 153.5F));
         STARPORTS.add(Point2d.of(187.5F, 154.5F));
         STARPORTS.add(Point2d.of(182.5F, 160.5F));
         TURRETS.add(Point2d.of(187.0F, 172.0F));
         TURRETS.add(Point2d.of(178.0F, 177.0F));
         TURRETS.add(Point2d.of(181.0F, 177.0F));
         MACRO_OCS.add(Point2d.of(182.5F, 167.5F));
         MACRO_OCS.add(Point2d.of(176.5F, 172.5F));
         MACRO_OCS.add(Point2d.of(170.5F, 173.5F));
         MACRO_OCS.add(Point2d.of(170.5F, 178.5F));
         MACRO_OCS.add(Point2d.of(189.5F, 161.5F));
         extraDepots.add(Point2d.of(164.0F, 165.0F));
         extraDepots.add(Point2d.of(168.0F, 161.0F));
         extraDepots.add(Point2d.of(169.0F, 156.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(194.0F, 166.0F));
         extraDepots.add(Point2d.of(194.0F, 164.0F));
         extraDepots.add(Point2d.of(187.0F, 166.0F));
         extraDepots.add(Point2d.of(187.0F, 168.0F));
         extraDepots.add(Point2d.of(193.0F, 162.0F));
         extraDepots.add(Point2d.of(189.0F, 158.0F));
         extraDepots.add(Point2d.of(187.0F, 158.0F));
         extraDepots.add(Point2d.of(191.0F, 177.0F));
         extraDepots.add(Point2d.of(191.0F, 175.0F));
         extraDepots.add(Point2d.of(187.0F, 179.0F));
         extraDepots.add(Point2d.of(181.0F, 180.0F));
         extraDepots.add(Point2d.of(189.0F, 182.0F));
         extraDepots.add(Point2d.of(171.0F, 160.0F));
      } else {
         myMineralPos = Point2d.of(66.0F, 62.5F);
         enemyMineralPos = Point2d.of(190.0F, 173.5F);
         WALL_2x2 = Point2d.of(78.0F, 77.0F);
         WALL_3x3 = Point2d.of(81.0F, 80.0F);
         MID_WALL_2x2 = Point2d.of(153.0F, 50.0F);
         MID_WALL_3x3 = Point2d.of(80.5F, 76.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(80.5F, 56.5F));
         _3x3Structures.add(Point2d.of(63.5F, 62.5F));
         REAPER_JUMP2 = Point2d.of(89.5F, 72.5F);
         BUNKER_NATURAL = Point2d.of(78.5F, 97.5F);
         STARPORTS.add(Point2d.of(78.5F, 59.5F));
         STARPORTS.add(Point2d.of(78.5F, 53.5F));
         STARPORTS.add(Point2d.of(67.5F, 54.5F));
         STARPORTS.add(Point2d.of(72.5F, 54.5F));
         STARPORTS.add(Point2d.of(65.5F, 57.5F));
         STARPORTS.add(Point2d.of(60.5F, 66.5F));
         STARPORTS.add(Point2d.of(62.5F, 69.5F));
         STARPORTS.add(Point2d.of(67.5F, 69.5F));
         STARPORTS.add(Point2d.of(74.5F, 67.5F));
         STARPORTS.add(Point2d.of(80.5F, 67.5F));
         STARPORTS.add(Point2d.of(86.5F, 67.5F));
         STARPORTS.add(Point2d.of(89.5F, 69.5F));
         STARPORTS.add(Point2d.of(84.5F, 70.5F));
         STARPORTS.add(Point2d.of(83.5F, 73.5F));
         STARPORTS.add(Point2d.of(60.5F, 83.5F));
         STARPORTS.add(Point2d.of(74.5F, 73.5F));
         TURRETS.add(Point2d.of(75.0F, 59.0F));
         TURRETS.add(Point2d.of(69.0F, 64.0F));
         TURRETS.add(Point2d.of(71.0F, 59.0F));
         MACRO_OCS.add(Point2d.of(65.5F, 73.5F));
         MACRO_OCS.add(Point2d.of(70.5F, 73.5F));
         MACRO_OCS.add(Point2d.of(79.5F, 63.5F));
         MACRO_OCS.add(Point2d.of(85.5F, 63.5F));
         MACRO_OCS.add(Point2d.of(86.5F, 58.5F));
         extraDepots.add(Point2d.of(92.0F, 71.0F));
         extraDepots.add(Point2d.of(88.0F, 75.0F));
         extraDepots.add(Point2d.of(87.0F, 80.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(83.0F, 56.0F));
         extraDepots.add(Point2d.of(83.0F, 58.0F));
         extraDepots.add(Point2d.of(83.0F, 60.0F));
         extraDepots.add(Point2d.of(75.0F, 56.0F));
         extraDepots.add(Point2d.of(70.0F, 56.0F));
         extraDepots.add(Point2d.of(69.0F, 67.0F));
         extraDepots.add(Point2d.of(64.0F, 60.0F));
         extraDepots.add(Point2d.of(62.0F, 73.0F));
         extraDepots.add(Point2d.of(66.0F, 77.0F));
         extraDepots.add(Point2d.of(70.0F, 77.0F));
         extraDepots.add(Point2d.of(68.0F, 78.0F));
         extraDepots.add(Point2d.of(77.0F, 69.0F));
      }

   }

   private static void setLocationsForNightshade(boolean isTopPos) {
      numReaperWall = 3;
      if (isTopPos) {
         proxyBarracksPos = Point2d.of(122.5F, 91.5F);
         proxyBunkerPos = Point2d.of(140.5F, 59.5F);
         myMineralPos = Point2d.of(34.0F, 139.5F);
         enemyMineralPos = Point2d.of(158.0F, 32.5F);
         WALL_2x2 = Point2d.of(42.0F, 123.0F);
         MID_WALL_2x2 = Point2d.of(40.0F, 123.0F);
         MID_WALL_3x3 = Point2d.of(39.5F, 123.5F);
         WALL_3x3 = Point2d.of(38.5F, 120.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(32.5F, 125.5F));
         _3x3Structures.add(Point2d.of(49.5F, 146.5F));
         BUNKER_NATURAL = Point2d.of(52.5F, 113.5F);
         REAPER_JUMP2 = Point2d.of(54.5F, 128.5F);
         FACTORY = Point2d.of(44.5F, 124.5F);
         STARPORTS.add(Point2d.of(45.5F, 150.5F));
         STARPORTS.add(Point2d.of(42.5F, 148.5F));
         STARPORTS.add(Point2d.of(36.5F, 147.5F));
         STARPORTS.add(Point2d.of(33.5F, 145.5F));
         STARPORTS.add(Point2d.of(30.5F, 138.5F));
         STARPORTS.add(Point2d.of(30.5F, 132.5F));
         STARPORTS.add(Point2d.of(30.5F, 129.5F));
         STARPORTS.add(Point2d.of(53.5F, 131.5F));
         STARPORTS.add(Point2d.of(56.5F, 133.5F));
         STARPORTS.add(Point2d.of(47.5F, 126.5F));
         STARPORTS.add(Point2d.of(39.5F, 134.5F));
         STARPORTS.add(Point2d.of(36.5F, 131.5F));
         STARPORTS.add(Point2d.of(47.5F, 129.5F));
         STARPORTS.add(Point2d.of(33.5F, 113.5F));
         STARPORTS.add(Point2d.of(36.5F, 128.5F));
         TURRETS.add(Point2d.of(46.0F, 143.0F));
         TURRETS.add(Point2d.of(37.0F, 138.0F));
         TURRETS.add(Point2d.of(43.0F, 143.0F));
         MACRO_OCS.add(Point2d.of(46.5F, 138.5F));
         MACRO_OCS.add(Point2d.of(53.5F, 146.5F));
         MACRO_OCS.add(Point2d.of(52.5F, 141.5F));
         MACRO_OCS.add(Point2d.of(52.5F, 136.5F));
         MACRO_OCS.add(Point2d.of(58.5F, 143.5F));
         MACRO_OCS.add(Point2d.of(58.5F, 138.5F));
         MACRO_OCS.add(Point2d.of(42.5F, 128.5F));
         MACRO_OCS.add(Point2d.of(46.5F, 133.5F));
         extraDepots.add(Point2d.of(52.0F, 126.0F));
         extraDepots.add(Point2d.of(51.0F, 124.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(34.0F, 123.0F));
         extraDepots.add(Point2d.of(32.0F, 123.0F));
         extraDepots.add(Point2d.of(32.0F, 121.0F));
         extraDepots.add(Point2d.of(34.0F, 121.0F));
         extraDepots.add(Point2d.of(37.0F, 118.0F));
         extraDepots.add(Point2d.of(39.0F, 125.0F));
         extraDepots.add(Point2d.of(29.0F, 136.0F));
         extraDepots.add(Point2d.of(31.0F, 136.0F));
         extraDepots.add(Point2d.of(43.0F, 146.0F));
         extraDepots.add(Point2d.of(33.0F, 141.0F));
         extraDepots.add(Point2d.of(31.0F, 144.0F));
         extraDepots.add(Point2d.of(33.0F, 143.0F));
         extraDepots.add(Point2d.of(49.0F, 144.0F));
         extraDepots.add(Point2d.of(62.0F, 141.0F));
      } else {
         proxyBarracksPos = Point2d.of(65.5F, 80.5F);
         proxyBunkerPos = Point2d.of(51.5F, 112.5F);
         myMineralPos = Point2d.of(158.0F, 32.5F);
         enemyMineralPos = Point2d.of(34.0F, 139.5F);
         WALL_2x2 = Point2d.of(153.0F, 52.0F);
         WALL_3x3 = Point2d.of(150.5F, 48.5F);
         MID_WALL_2x2 = Point2d.of(153.0F, 50.0F);
         MID_WALL_3x3 = Point2d.of(153.5F, 49.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(161.5F, 34.5F));
         _3x3Structures.add(Point2d.of(142.5F, 26.5F));
         REAPER_JUMP2 = Point2d.of(137.5F, 43.5F);
         BUNKER_NATURAL = Point2d.of(138.5F, 58.5F);
         FACTORY = Point2d.of(156.5F, 44.5F);
         STARPORTS.add(Point2d.of(145.5F, 41.5F));
         STARPORTS.add(Point2d.of(160.5F, 38.5F));
         STARPORTS.add(Point2d.of(152.5F, 37.5F));
         STARPORTS.add(Point2d.of(155.5F, 39.5F));
         STARPORTS.add(Point2d.of(147.5F, 38.5F));
         STARPORTS.add(Point2d.of(150.5F, 40.5F));
         STARPORTS.add(Point2d.of(157.5F, 26.5F));
         STARPORTS.add(Point2d.of(139.5F, 39.5F));
         STARPORTS.add(Point2d.of(152.5F, 24.5F));
         STARPORTS.add(Point2d.of(147.5F, 22.5F));
         STARPORTS.add(Point2d.of(142.5F, 23.5F));
         STARPORTS.add(Point2d.of(156.5F, 58.5F));
         STARPORTS.add(Point2d.of(148.5F, 43.5F));
         STARPORTS.add(Point2d.of(153.5F, 42.5F));
         STARPORTS.add(Point2d.of(158.5F, 41.5F));
         TURRETS.add(Point2d.of(146.0F, 29.0F));
         TURRETS.add(Point2d.of(155.0F, 34.0F));
         TURRETS.add(Point2d.of(149.0F, 29.0F));
         MACRO_OCS.add(Point2d.of(145.5F, 33.5F));
         MACRO_OCS.add(Point2d.of(133.5F, 28.5F));
         MACRO_OCS.add(Point2d.of(133.5F, 33.5F));
         MACRO_OCS.add(Point2d.of(134.5F, 38.5F));
         MACRO_OCS.add(Point2d.of(138.5F, 25.5F));
         MACRO_OCS.add(Point2d.of(139.5F, 30.5F));
         MACRO_OCS.add(Point2d.of(139.5F, 35.5F));
         extraDepots.add(Point2d.of(140.0F, 46.0F));
         extraDepots.add(Point2d.of(141.0F, 48.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(130.0F, 31.0F));
         extraDepots.add(Point2d.of(137.0F, 22.0F));
         extraDepots.add(Point2d.of(143.0F, 29.0F));
         extraDepots.add(Point2d.of(161.0F, 32.0F));
         extraDepots.add(Point2d.of(160.0F, 30.0F));
         extraDepots.add(Point2d.of(161.0F, 28.0F));
         extraDepots.add(Point2d.of(146.0F, 44.0F));
         extraDepots.add(Point2d.of(144.0F, 44.0F));
         extraDepots.add(Point2d.of(145.0F, 39.0F));
         extraDepots.add(Point2d.of(159.0F, 34.0F));
         extraDepots.add(Point2d.of(149.0F, 26.0F));
         extraDepots.add(Point2d.of(155.0F, 27.0F));
         extraDepots.add(Point2d.of(130.0F, 29.0F));
      }

   }

   private static void setLocationsForPillarsOfGold(boolean isTopPos) {
      MID_WALL_3x3 = Point2d.of(36.5F, 120.5F);
      if (isTopPos) {
         myMineralPos = Point2d.of(145.0F, 132.5F);
         enemyMineralPos = Point2d.of(23.0F, 37.5F);
      } else {
         myMineralPos = Point2d.of(23.0F, 37.5F);
         enemyMineralPos = Point2d.of(145.0F, 132.5F);
      }

   }

   private static void setLocationsForSimulacrum(boolean isTopPos) {
      numReaperWall = 3;
      if (isTopPos) {
         proxyBarracksPos = Point2d.of(129.5F, 87.5F);
         proxyBunkerPos = Point2d.of(158.5F, 77.5F);
         myMineralPos = Point2d.of(47.0F, 140.5F);
         enemyMineralPos = Point2d.of(169.0F, 43.5F);
         WALL_2x2 = Point2d.of(58.0F, 127.0F);
         MID_WALL_2x2 = Point2d.of(60.0F, 127.0F);
         MID_WALL_3x3 = Point2d.of(60.5F, 127.5F);
         WALL_3x3 = Point2d.of(61.5F, 124.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(43.5F, 142.5F));
         _3x3Structures.add(Point2d.of(66.5F, 137.5F));
         BUNKER_NATURAL = Point2d.of(58.5F, 105.5F);
         REAPER_JUMP2 = Point2d.of(76.5F, 132.5F);
         FACTORY = Point2d.of(54.5F, 132.5F);
         STARPORTS.add(Point2d.of(43.5F, 145.5F));
         STARPORTS.add(Point2d.of(43.5F, 139.5F));
         STARPORTS.add(Point2d.of(43.5F, 136.5F));
         STARPORTS.add(Point2d.of(43.5F, 133.5F));
         STARPORTS.add(Point2d.of(49.5F, 133.5F));
         STARPORTS.add(Point2d.of(49.5F, 136.5F));
         STARPORTS.add(Point2d.of(47.5F, 148.5F));
         STARPORTS.add(Point2d.of(50.5F, 150.5F));
         STARPORTS.add(Point2d.of(56.5F, 149.5F));
         STARPORTS.add(Point2d.of(61.5F, 148.5F));
         STARPORTS.add(Point2d.of(65.5F, 145.5F));
         STARPORTS.add(Point2d.of(65.5F, 141.5F));
         STARPORTS.add(Point2d.of(60.5F, 139.5F));
         STARPORTS.add(Point2d.of(60.5F, 136.5F));
         STARPORTS.add(Point2d.of(39.5F, 119.5F));
         TURRETS.add(Point2d.of(58.0F, 144.0F));
         TURRETS.add(Point2d.of(50.0F, 139.0F));
         TURRETS.add(Point2d.of(55.0F, 144.0F));
         MACRO_OCS.add(Point2d.of(49.5F, 128.5F));
         MACRO_OCS.add(Point2d.of(72.5F, 148.5F));
         MACRO_OCS.add(Point2d.of(71.5F, 142.5F));
         MACRO_OCS.add(Point2d.of(76.5F, 143.5F));
         MACRO_OCS.add(Point2d.of(70.5F, 137.5F));
         MACRO_OCS.add(Point2d.of(76.5F, 137.5F));
         MACRO_OCS.add(Point2d.of(70.5F, 132.5F));
         MACRO_OCS.add(Point2d.of(64.5F, 131.5F));
         MACRO_OCS.add(Point2d.of(65.5F, 125.5F));
         extraDepots.add(Point2d.of(79.0F, 134.0F));
         extraDepots.add(Point2d.of(80.0F, 136.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(55.0F, 147.0F));
         extraDepots.add(Point2d.of(53.0F, 148.0F));
         extraDepots.add(Point2d.of(46.0F, 142.0F));
         extraDepots.add(Point2d.of(48.0F, 146.0F));
         extraDepots.add(Point2d.of(44.0F, 131.0F));
         extraDepots.add(Point2d.of(46.0F, 129.0F));
         extraDepots.add(Point2d.of(46.0F, 131.0F));
         extraDepots.add(Point2d.of(68.0F, 143.0F));
         extraDepots.add(Point2d.of(69.0F, 148.0F));
         extraDepots.add(Point2d.of(67.0F, 148.0F));
         extraDepots.add(Point2d.of(61.0F, 133.0F));
         extraDepots.add(Point2d.of(67.0F, 135.0F));
         extraDepots.add(Point2d.of(65.0F, 135.0F));
         extraDepots.add(Point2d.of(80.0F, 139.0F));
      } else {
         proxyBarracksPos = Point2d.of(84.5F, 96.5F);
         proxyBunkerPos = Point2d.of(57.5F, 106.5F);
         myMineralPos = Point2d.of(169.0F, 43.5F);
         enemyMineralPos = Point2d.of(47.0F, 140.5F);
         WALL_2x2 = Point2d.of(158.0F, 57.0F);
         WALL_3x3 = Point2d.of(154.5F, 59.5F);
         MID_WALL_2x2 = Point2d.of(156.0F, 57.0F);
         MID_WALL_3x3 = Point2d.of(155.5F, 56.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(159.5F, 51.5F));
         _3x3Structures.add(Point2d.of(172.5F, 42.5F));
         REAPER_JUMP2 = Point2d.of(139.5F, 51.5F);
         BUNKER_NATURAL = Point2d.of(157.5F, 78.5F);
         FACTORY = Point2d.of(151.5F, 52.5F);
         STARPORTS.add(Point2d.of(170.5F, 38.5F));
         STARPORTS.add(Point2d.of(156.5F, 33.5F));
         STARPORTS.add(Point2d.of(161.5F, 33.5F));
         STARPORTS.add(Point2d.of(151.5F, 37.5F));
         STARPORTS.add(Point2d.of(164.5F, 35.5F));
         STARPORTS.add(Point2d.of(170.5F, 45.5F));
         STARPORTS.add(Point2d.of(164.5F, 48.5F));
         STARPORTS.add(Point2d.of(164.5F, 51.5F));
         STARPORTS.add(Point2d.of(159.5F, 48.5F));
         STARPORTS.add(Point2d.of(162.5F, 54.5F));
         STARPORTS.add(Point2d.of(167.5F, 54.5F));
         STARPORTS.add(Point2d.of(148.5F, 42.5F));
         STARPORTS.add(Point2d.of(149.5F, 46.5F));
         STARPORTS.add(Point2d.of(150.5F, 49.5F));
         STARPORTS.add(Point2d.of(174.5F, 64.5F));
         TURRETS.add(Point2d.of(158.0F, 40.0F));
         TURRETS.add(Point2d.of(166.0F, 45.0F));
         TURRETS.add(Point2d.of(161.0F, 40.0F));
         MACRO_OCS.add(Point2d.of(141.5F, 36.5F));
         MACRO_OCS.add(Point2d.of(147.5F, 37.5F));
         MACRO_OCS.add(Point2d.of(139.5F, 41.5F));
         MACRO_OCS.add(Point2d.of(144.5F, 42.5F));
         MACRO_OCS.add(Point2d.of(139.5F, 46.5F));
         MACRO_OCS.add(Point2d.of(145.5F, 47.5F));
         MACRO_OCS.add(Point2d.of(146.5F, 52.5F));
         MACRO_OCS.add(Point2d.of(150.5F, 58.5F));
         MACRO_OCS.add(Point2d.of(171.5F, 50.5F));
         extraDepots.add(Point2d.of(137.0F, 50.0F));
         extraDepots.add(Point2d.of(136.0F, 48.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(162.0F, 36.0F));
         extraDepots.add(Point2d.of(170.0F, 42.0F));
         extraDepots.add(Point2d.of(173.0F, 47.0F));
         extraDepots.add(Point2d.of(154.0F, 35.0F));
         extraDepots.add(Point2d.of(156.0F, 36.0F));
         extraDepots.add(Point2d.of(156.0F, 38.0F));
         extraDepots.add(Point2d.of(154.0F, 39.0F));
         extraDepots.add(Point2d.of(162.0F, 50.0F));
         extraDepots.add(Point2d.of(162.0F, 52.0F));
         extraDepots.add(Point2d.of(138.0F, 37.0F));
         extraDepots.add(Point2d.of(145.0F, 34.0F));
         extraDepots.add(Point2d.of(151.0F, 44.0F));
         extraDepots.add(Point2d.of(153.0F, 44.0F));
         extraDepots.add(Point2d.of(136.0F, 45.0F));
      }

   }

   private static void setLocationsForSubmarine(boolean isTopPos) {
      MID_WALL_3x3 = Point2d.of(36.5F, 120.5F);
      if (isTopPos) {
         myMineralPos = Point2d.of(25.0F, 128.5F);
         enemyMineralPos = Point2d.of(143.0F, 35.5F);
      } else {
         myMineralPos = Point2d.of(143.0F, 35.5F);
         enemyMineralPos = Point2d.of(25.0F, 128.5F);
      }

   }

   private static void setLocationsForThunderBird(boolean isTopPos) {
      numReaperWall = 1;
      if (isTopPos) {
         proxyBarracksPos = Point2d.of(129.5F, 82.5F);
         proxyBunkerPos = Point2d.of(141.5F, 51.5F);
         myMineralPos = Point2d.of(38.0F, 140.5F);
         enemyMineralPos = Point2d.of(154.0F, 15.5F);
         WALL_2x2 = Point2d.of(37.0F, 118.0F);
         MID_WALL_3x3 = Point2d.of(36.5F, 120.5F);
         WALL_3x3 = Point2d.of(39.5F, 121.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(42.5F, 128.5F));
         _3x3Structures.add(Point2d.of(28.5F, 136.5F));
         BUNKER_NATURAL = Point2d.of(52.5F, 106.5F);
         FACTORY = Point2d.of(28.5F, 125.5F);
         STARPORTS.add(Point2d.of(42.5F, 145.5F));
         STARPORTS.add(Point2d.of(45.5F, 137.5F));
         STARPORTS.add(Point2d.of(45.5F, 141.5F));
         STARPORTS.add(Point2d.of(39.5F, 126.5F));
         STARPORTS.add(Point2d.of(51.5F, 141.5F));
         STARPORTS.add(Point2d.of(28.5F, 128.5F));
         STARPORTS.add(Point2d.of(33.5F, 143.5F));
         STARPORTS.add(Point2d.of(39.5F, 143.5F));
         STARPORTS.add(Point2d.of(34.5F, 127.5F));
         STARPORTS.add(Point2d.of(34.5F, 124.5F));
         STARPORTS.add(Point2d.of(37.5F, 129.5F));
         STARPORTS.add(Point2d.of(32.5F, 110.5F));
         STARPORTS.add(Point2d.of(29.5F, 103.5F));
         TURRETS.add(Point2d.of(42.0F, 138.0F));
         TURRETS.add(Point2d.of(34.0F, 133.0F));
         TURRETS.add(Point2d.of(36.0F, 141.0F));
         MACRO_OCS.add(Point2d.of(43.5F, 133.5F));
         MACRO_OCS.add(Point2d.of(48.5F, 132.5F));
         MACRO_OCS.add(Point2d.of(47.5F, 127.5F));
         MACRO_OCS.add(Point2d.of(53.5F, 127.5F));
         MACRO_OCS.add(Point2d.of(54.5F, 132.5F));
         MACRO_OCS.add(Point2d.of(56.5F, 137.5F));
         MACRO_OCS.add(Point2d.of(51.5F, 137.5F));
         extraDepots.add(Point2d.of(54.0F, 123.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(58.0F, 132.0F));
         extraDepots.add(Point2d.of(56.0F, 141.0F));
         extraDepots.add(Point2d.of(51.0F, 144.0F));
         extraDepots.add(Point2d.of(49.0F, 144.0F));
         extraDepots.add(Point2d.of(47.0F, 144.0F));
         extraDepots.add(Point2d.of(48.0F, 139.0F));
         extraDepots.add(Point2d.of(40.0F, 141.0F));
         extraDepots.add(Point2d.of(27.0F, 131.0F));
         extraDepots.add(Point2d.of(29.0F, 131.0F));
         extraDepots.add(Point2d.of(28.0F, 133.0F));
         extraDepots.add(Point2d.of(30.0F, 133.0F));
         extraDepots.add(Point2d.of(40.0F, 146.0F));
         extraDepots.add(Point2d.of(30.0F, 119.0F));
         extraDepots.add(Point2d.of(58.0F, 134.0F));
      } else {
         proxyBarracksPos = Point2d.of(61.5F, 74.5F);
         proxyBunkerPos = Point2d.of(50.5F, 104.5F);
         myMineralPos = Point2d.of(154.0F, 15.5F);
         enemyMineralPos = Point2d.of(38.0F, 140.5F);
         WALL_2x2 = Point2d.of(155.0F, 38.0F);
         MID_WALL_3x3 = Point2d.of(155.5F, 35.5F);
         WALL_3x3 = Point2d.of(152.5F, 34.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(161.5F, 36.5F));
         _3x3Structures.add(Point2d.of(163.5F, 19.5F));
         BUNKER_NATURAL = Point2d.of(140.5F, 49.5F);
         FACTORY = Point2d.of(160.5F, 30.5F);
         STARPORTS.add(Point2d.of(159.5F, 15.5F));
         STARPORTS.add(Point2d.of(156.5F, 13.5F));
         STARPORTS.add(Point2d.of(151.5F, 12.5F));
         STARPORTS.add(Point2d.of(146.5F, 12.5F));
         STARPORTS.add(Point2d.of(154.5F, 26.5F));
         STARPORTS.add(Point2d.of(157.5F, 28.5F));
         STARPORTS.add(Point2d.of(142.5F, 25.5F));
         STARPORTS.add(Point2d.of(137.5F, 28.5F));
         STARPORTS.add(Point2d.of(137.5F, 31.5F));
         STARPORTS.add(Point2d.of(143.5F, 29.5F));
         STARPORTS.add(Point2d.of(146.5F, 31.5F));
         STARPORTS.add(Point2d.of(161.5F, 53.5F));
         STARPORTS.add(Point2d.of(157.5F, 45.5F));
         STARPORTS.add(Point2d.of(136.5F, 25.5F));
         STARPORTS.add(Point2d.of(155.5F, 31.5F));
         TURRETS.add(Point2d.of(149.0F, 18.0F));
         TURRETS.add(Point2d.of(158.0F, 23.0F));
         TURRETS.add(Point2d.of(154.0F, 14.0F));
         MACRO_OCS.add(Point2d.of(148.5F, 22.5F));
         MACRO_OCS.add(Point2d.of(149.5F, 27.5F));
         MACRO_OCS.add(Point2d.of(143.5F, 21.5F));
         MACRO_OCS.add(Point2d.of(137.5F, 16.5F));
         MACRO_OCS.add(Point2d.of(142.5F, 16.5F));
         MACRO_OCS.add(Point2d.of(137.5F, 21.5F));
         MACRO_OCS.add(Point2d.of(142.5F, 34.5F));
         extraDepots.add(Point2d.of(139.0F, 34.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(134.0F, 17.0F));
         extraDepots.add(Point2d.of(134.0F, 19.0F));
         extraDepots.add(Point2d.of(134.0F, 23.0F));
         extraDepots.add(Point2d.of(134.0F, 25.0F));
         extraDepots.add(Point2d.of(153.0F, 10.0F));
         extraDepots.add(Point2d.of(149.0F, 10.0F));
         extraDepots.add(Point2d.of(147.0F, 10.0F));
         extraDepots.add(Point2d.of(151.0F, 10.0F));
         extraDepots.add(Point2d.of(144.0F, 12.0F));
         extraDepots.add(Point2d.of(140.0F, 13.0F));
         extraDepots.add(Point2d.of(142.0F, 12.0F));
         extraDepots.add(Point2d.of(152.0F, 15.0F));
         extraDepots.add(Point2d.of(162.0F, 17.0F));
         extraDepots.add(Point2d.of(164.0F, 23.0F));
         extraDepots.add(Point2d.of(162.0F, 23.0F));
         extraDepots.add(Point2d.of(146.0F, 15.0F));
         extraDepots.add(Point2d.of(146.0F, 17.0F));
         extraDepots.add(Point2d.of(147.0F, 19.0F));
         extraDepots.add(Point2d.of(134.0F, 21.0F));
      }

   }

   private static void setLocationsForTriton(boolean isTopPos) {
      numReaperWall = 2;
      if (isTopPos) {
         myMineralPos = Point2d.of(48.0F, 158.5F);
         enemyMineralPos = Point2d.of(168.0F, 45.5F);
         REAPER_JUMP2 = Point2d.of(71.5F, 144.5F);
         BUNKER_NATURAL = Point2d.of(87.5F, 152.5F);
         WALL_2x2 = Point2d.of(71.0F, 154.0F);
         WALL_3x3 = Point2d.of(73.5F, 150.5F);
         MID_WALL_2x2 = Point2d.of(71.0F, 152.0F);
         MID_WALL_3x3 = Point2d.of(70.5F, 151.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(53.5F, 169.5F));
         _3x3Structures.add(Point2d.of(44.5F, 149.5F));
         STARPORTS.add(Point2d.of(67.5F, 163.5F));
         STARPORTS.add(Point2d.of(64.5F, 161.5F));
         STARPORTS.add(Point2d.of(62.5F, 164.5F));
         STARPORTS.add(Point2d.of(58.5F, 168.5F));
         STARPORTS.add(Point2d.of(53.5F, 166.5F));
         STARPORTS.add(Point2d.of(47.5F, 164.5F));
         STARPORTS.add(Point2d.of(47.5F, 167.5F));
         STARPORTS.add(Point2d.of(42.5F, 162.5F));
         STARPORTS.add(Point2d.of(44.5F, 157.5F));
         STARPORTS.add(Point2d.of(45.5F, 152.5F));
         STARPORTS.add(Point2d.of(47.5F, 149.5F));
         STARPORTS.add(Point2d.of(47.5F, 146.5F));
         STARPORTS.add(Point2d.of(47.5F, 143.5F));
         STARPORTS.add(Point2d.of(51.5F, 153.5F));
         STARPORTS.add(Point2d.of(53.5F, 150.5F));
         TURRETS.add(Point2d.of(51.0F, 154.0F));
         TURRETS.add(Point2d.of(57.0F, 162.0F));
         TURRETS.add(Point2d.of(51.0F, 157.0F));
         MACRO_OCS.add(Point2d.of(60.5F, 157.5F));
         MACRO_OCS.add(Point2d.of(59.5F, 152.5F));
         MACRO_OCS.add(Point2d.of(54.5F, 146.5F));
         MACRO_OCS.add(Point2d.of(54.5F, 141.5F));
         MACRO_OCS.add(Point2d.of(65.5F, 156.5F));
         MACRO_OCS.add(Point2d.of(65.5F, 151.5F));
         MACRO_OCS.add(Point2d.of(65.5F, 146.5F));
         MACRO_OCS.add(Point2d.of(59.5F, 147.5F));
         MACRO_OCS.add(Point2d.of(59.5F, 141.5F));
         extraDepots.add(Point2d.of(69.0F, 144.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(70.0F, 157.0F));
         extraDepots.add(Point2d.of(70.0F, 159.0F));
         extraDepots.add(Point2d.of(70.0F, 161.0F));
         extraDepots.add(Point2d.of(42.0F, 151.0F));
         extraDepots.add(Point2d.of(42.0F, 149.0F));
         extraDepots.add(Point2d.of(65.0F, 166.0F));
         extraDepots.add(Point2d.of(67.0F, 166.0F));
         extraDepots.add(Point2d.of(65.0F, 168.0F));
         extraDepots.add(Point2d.of(63.0F, 168.0F));
         extraDepots.add(Point2d.of(56.0F, 170.0F));
         extraDepots.add(Point2d.of(56.0F, 168.0F));
         extraDepots.add(Point2d.of(45.0F, 166.0F));
         extraDepots.add(Point2d.of(45.0F, 164.0F));
         extraDepots.add(Point2d.of(43.0F, 160.0F));
         extraDepots.add(Point2d.of(44.0F, 155.0F));
         extraDepots.add(Point2d.of(46.0F, 155.0F));
      } else {
         myMineralPos = Point2d.of(168.0F, 45.5F);
         enemyMineralPos = Point2d.of(48.0F, 158.5F);
         REAPER_JUMP2 = Point2d.of(145.5F, 59.5F);
         BUNKER_NATURAL = Point2d.of(128.5F, 51.5F);
         WALL_2x2 = Point2d.of(145.0F, 50.0F);
         WALL_3x3 = Point2d.of(142.5F, 53.5F);
         MID_WALL_2x2 = Point2d.of(145.0F, 52.0F);
         MID_WALL_3x3 = Point2d.of(145.5F, 52.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(171.5F, 48.5F));
         _3x3Structures.add(Point2d.of(160.5F, 51.5F));
         STARPORTS.add(Point2d.of(152.5F, 36.5F));
         STARPORTS.add(Point2d.of(159.5F, 37.5F));
         STARPORTS.add(Point2d.of(158.5F, 34.5F));
         STARPORTS.add(Point2d.of(165.5F, 36.5F));
         STARPORTS.add(Point2d.of(165.5F, 39.5F));
         STARPORTS.add(Point2d.of(170.5F, 44.5F));
         STARPORTS.add(Point2d.of(170.5F, 41.5F));
         STARPORTS.add(Point2d.of(170.5F, 52.5F));
         STARPORTS.add(Point2d.of(170.5F, 55.5F));
         STARPORTS.add(Point2d.of(164.5F, 52.5F));
         STARPORTS.add(Point2d.of(164.5F, 55.5F));
         STARPORTS.add(Point2d.of(164.5F, 58.5F));
         STARPORTS.add(Point2d.of(164.5F, 58.5F));
         STARPORTS.add(Point2d.of(164.5F, 61.5F));
         STARPORTS.add(Point2d.of(158.5F, 60.5F));
         STARPORTS.add(Point2d.of(158.5F, 63.5F));
         TURRETS.add(Point2d.of(165.0F, 50.0F));
         TURRETS.add(Point2d.of(159.0F, 42.0F));
         TURRETS.add(Point2d.of(163.0F, 42.0F));
         MACRO_OCS.add(Point2d.of(150.5F, 45.5F));
         MACRO_OCS.add(Point2d.of(149.5F, 40.5F));
         MACRO_OCS.add(Point2d.of(150.5F, 50.5F));
         MACRO_OCS.add(Point2d.of(155.5F, 46.5F));
         MACRO_OCS.add(Point2d.of(157.5F, 56.5F));
         MACRO_OCS.add(Point2d.of(150.5F, 55.5F));
         MACRO_OCS.add(Point2d.of(156.5F, 51.5F));
         MACRO_OCS.add(Point2d.of(152.5F, 60.5F));
         extraDepots.add(Point2d.of(143.0F, 60.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(153.0F, 39.0F));
         extraDepots.add(Point2d.of(153.0F, 41.0F));
         extraDepots.add(Point2d.of(146.0F, 47.0F));
         extraDepots.add(Point2d.of(146.0F, 45.0F));
         extraDepots.add(Point2d.of(146.0F, 43.0F));
         extraDepots.add(Point2d.of(146.0F, 41.0F));
         extraDepots.add(Point2d.of(150.0F, 37.0F));
         extraDepots.add(Point2d.of(157.0F, 37.0F));
         extraDepots.add(Point2d.of(167.0F, 63.0F));
         extraDepots.add(Point2d.of(170.0F, 61.0F));
         extraDepots.add(Point2d.of(170.0F, 59.0F));
         extraDepots.add(Point2d.of(161.0F, 57.0F));
      }

   }

   private static void setLocationsForWintersGate(boolean isTopPos) {
      numReaperWall = 2;
      if (isTopPos) {
         myMineralPos = Point2d.of(47.0F, 141.5F);
         enemyMineralPos = Point2d.of(145.0F, 22.5F);
         WALL_2x2 = Point2d.of(52.0F, 121.0F);
         MID_WALL_2x2 = Point2d.of(54.0F, 121.0F);
         MID_WALL_3x3 = Point2d.of(54.5F, 121.5F);
         WALL_3x3 = Point2d.of(55.5F, 118.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(43.5F, 129.5F));
         _3x3Structures.add(Point2d.of(55.5F, 140.5F));
         BUNKER_NATURAL = Point2d.of(49.5F, 103.5F);
         REAPER_JUMP2 = Point2d.of(65.5F, 124.5F);
         STARPORTS.add(Point2d.of(36.5F, 134.5F));
         STARPORTS.add(Point2d.of(36.5F, 139.5F));
         STARPORTS.add(Point2d.of(39.5F, 143.5F));
         STARPORTS.add(Point2d.of(45.5F, 143.5F));
         STARPORTS.add(Point2d.of(51.5F, 144.5F));
         STARPORTS.add(Point2d.of(38.5F, 123.5F));
         STARPORTS.add(Point2d.of(38.5F, 127.5F));
         STARPORTS.add(Point2d.of(52.5F, 136.5F));
         STARPORTS.add(Point2d.of(52.5F, 133.5F));
         STARPORTS.add(Point2d.of(55.5F, 130.5F));
         STARPORTS.add(Point2d.of(64.5F, 139.5F));
         STARPORTS.add(Point2d.of(65.5F, 136.5F));
         STARPORTS.add(Point2d.of(66.5F, 133.5F));
         STARPORTS.add(Point2d.of(66.5F, 130.5F));
         STARPORTS.add(Point2d.of(66.5F, 127.5F));
         STARPORTS.add(Point2d.of(52.5F, 128.5F));
         TURRETS.add(Point2d.of(52.0F, 139.0F));
         TURRETS.add(Point2d.of(43.0F, 134.0F));
         TURRETS.add(Point2d.of(45.0F, 139.0F));
         MACRO_OCS.add(Point2d.of(47.5F, 129.5F));
         MACRO_OCS.add(Point2d.of(44.5F, 124.5F));
         MACRO_OCS.add(Point2d.of(59.5F, 142.5F));
         MACRO_OCS.add(Point2d.of(59.5F, 137.5F));
         MACRO_OCS.add(Point2d.of(62.5F, 132.5F));
         MACRO_OCS.add(Point2d.of(61.5F, 127.5F));
         MACRO_OCS.add(Point2d.of(58.5F, 122.5F));
         extraDepots.add(Point2d.of(68.0F, 125.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(37.0F, 142.0F));
         extraDepots.add(Point2d.of(36.0F, 137.0F));
         extraDepots.add(Point2d.of(39.0F, 137.0F));
         extraDepots.add(Point2d.of(39.0F, 141.0F));
         extraDepots.add(Point2d.of(41.0F, 141.0F));
         extraDepots.add(Point2d.of(43.0F, 141.0F));
         extraDepots.add(Point2d.of(38.0F, 130.0F));
         extraDepots.add(Point2d.of(37.0F, 132.0F));
         extraDepots.add(Point2d.of(41.0F, 125.0F));
         extraDepots.add(Point2d.of(65.0F, 142.0F));
         extraDepots.add(Point2d.of(56.0F, 144.0F));
         extraDepots.add(Point2d.of(63.0F, 142.0F));
         extraDepots.add(Point2d.of(71.0F, 132.0F));
         extraDepots.add(Point2d.of(70.0F, 135.0F));
         extraDepots.add(Point2d.of(41.0F, 129.0F));
      } else {
         myMineralPos = Point2d.of(145.0F, 22.5F);
         enemyMineralPos = Point2d.of(47.0F, 141.5F);
         WALL_2x2 = Point2d.of(140.0F, 43.0F);
         MID_WALL_2x2 = Point2d.of(138.0F, 43.0F);
         MID_WALL_3x3 = Point2d.of(137.5F, 42.5F);
         WALL_3x3 = Point2d.of(136.5F, 45.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(137.5F, 23.5F));
         _3x3Structures.add(Point2d.of(129.5F, 32.5F));
         BUNKER_NATURAL = Point2d.of(141.5F, 60.5F);
         REAPER_JUMP2 = Point2d.of(126.5F, 39.5F);
         STARPORTS.add(Point2d.of(154.5F, 28.5F));
         STARPORTS.add(Point2d.of(153.5F, 24.5F));
         STARPORTS.add(Point2d.of(150.5F, 20.5F));
         STARPORTS.add(Point2d.of(144.5F, 20.5F));
         STARPORTS.add(Point2d.of(137.5F, 20.5F));
         STARPORTS.add(Point2d.of(125.5F, 24.5F));
         STARPORTS.add(Point2d.of(125.5F, 27.5F));
         STARPORTS.add(Point2d.of(123.5F, 33.5F));
         STARPORTS.add(Point2d.of(124.5F, 30.5F));
         STARPORTS.add(Point2d.of(123.5F, 36.5F));
         STARPORTS.add(Point2d.of(129.5F, 36.5F));
         STARPORTS.add(Point2d.of(130.5F, 39.5F));
         STARPORTS.add(Point2d.of(147.5F, 45.5F));
         STARPORTS.add(Point2d.of(153.5F, 47.5F));
         STARPORTS.add(Point2d.of(150.5F, 35.5F));
         STARPORTS.add(Point2d.of(136.5F, 38.5F));
         TURRETS.add(Point2d.of(140.0F, 25.0F));
         TURRETS.add(Point2d.of(149.0F, 30.0F));
         TURRETS.add(Point2d.of(147.0F, 22.0F));
         MACRO_OCS.add(Point2d.of(144.5F, 34.5F));
         MACRO_OCS.add(Point2d.of(139.5F, 29.5F));
         MACRO_OCS.add(Point2d.of(138.5F, 34.5F));
         MACRO_OCS.add(Point2d.of(147.5F, 39.5F));
         MACRO_OCS.add(Point2d.of(152.5F, 39.5F));
         MACRO_OCS.add(Point2d.of(132.5F, 21.5F));
         MACRO_OCS.add(Point2d.of(133.5F, 31.5F));
         MACRO_OCS.add(Point2d.of(133.5F, 26.5F));
         extraDepots.add(Point2d.of(124.0F, 39.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(155.0F, 22.0F));
         extraDepots.add(Point2d.of(141.0F, 18.0F));
         extraDepots.add(Point2d.of(153.0F, 22.0F));
         extraDepots.add(Point2d.of(142.0F, 20.0F));
         extraDepots.add(Point2d.of(151.0F, 23.0F));
         extraDepots.add(Point2d.of(156.0F, 31.0F));
         extraDepots.add(Point2d.of(149.0F, 23.0F));
         extraDepots.add(Point2d.of(154.0F, 33.0F));
         extraDepots.add(Point2d.of(154.0F, 31.0F));
         extraDepots.add(Point2d.of(121.0F, 32.0F));
         extraDepots.add(Point2d.of(122.0F, 30.0F));
         extraDepots.add(Point2d.of(123.0F, 28.0F));
         extraDepots.add(Point2d.of(127.0F, 22.0F));
         extraDepots.add(Point2d.of(129.0F, 22.0F));
         extraDepots.add(Point2d.of(130.0F, 25.0F));
         extraDepots.add(Point2d.of(130.0F, 27.0F));
         extraDepots.add(Point2d.of(130.0F, 29.0F));
      }

   }

   private static void setLocationsForWorldOfSleepers(boolean isTopPos) {
      numReaperWall = 2;
      if (isTopPos) {
         myMineralPos = Point2d.of(150.0F, 148.5F);
         enemyMineralPos = Point2d.of(34.0F, 19.5F);
         WALL_2x2 = Point2d.of(149.0F, 124.0F);
         MID_WALL_2x2 = Point2d.of(149.0F, 126.0F);
         MID_WALL_3x3 = Point2d.of(149.5F, 126.5F);
         WALL_3x3 = Point2d.of(146.5F, 127.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(159.5F, 139.5F));
         _3x3Structures.add(Point2d.of(139.5F, 153.5F));
         BUNKER_NATURAL = Point2d.of(137.5F, 106.5F);
         REAPER_JUMP2 = Point2d.of(135.5F, 133.5F);
         STARPORTS.add(Point2d.of(159.5F, 143.5F));
         STARPORTS.add(Point2d.of(159.5F, 135.5F));
         STARPORTS.add(Point2d.of(159.5F, 147.5F));
         STARPORTS.add(Point2d.of(154.5F, 148.5F));
         STARPORTS.add(Point2d.of(154.5F, 152.5F));
         STARPORTS.add(Point2d.of(148.5F, 151.5F));
         STARPORTS.add(Point2d.of(143.5F, 152.5F));
         STARPORTS.add(Point2d.of(136.5F, 150.5F));
         STARPORTS.add(Point2d.of(139.5F, 147.5F));
         STARPORTS.add(Point2d.of(140.5F, 144.5F));
         STARPORTS.add(Point2d.of(141.5F, 141.5F));
         STARPORTS.add(Point2d.of(142.5F, 138.5F));
         STARPORTS.add(Point2d.of(142.5F, 135.5F));
         STARPORTS.add(Point2d.of(136.5F, 136.5F));
         STARPORTS.add(Point2d.of(138.5F, 132.5F));
         STARPORTS.add(Point2d.of(141.5F, 128.5F));
         TURRETS.add(Point2d.of(149.0F, 146.0F));
         TURRETS.add(Point2d.of(154.0F, 141.0F));
         TURRETS.add(Point2d.of(151.0F, 153.0F));
         MACRO_OCS.add(Point2d.of(149.5F, 136.5F));
         MACRO_OCS.add(Point2d.of(134.5F, 146.5F));
         MACRO_OCS.add(Point2d.of(136.5F, 141.5F));
         MACRO_OCS.add(Point2d.of(154.5F, 124.5F));
         MACRO_OCS.add(Point2d.of(158.5F, 129.5F));
         MACRO_OCS.add(Point2d.of(153.5F, 131.5F));
         extraDepots.add(Point2d.of(136.0F, 131.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(162.0F, 145.0F));
         extraDepots.add(Point2d.of(159.0F, 150.0F));
         extraDepots.add(Point2d.of(157.0F, 150.0F));
         extraDepots.add(Point2d.of(152.0F, 149.0F));
         extraDepots.add(Point2d.of(148.0F, 149.0F));
         extraDepots.add(Point2d.of(141.0F, 150.0F));
         extraDepots.add(Point2d.of(143.0F, 150.0F));
         extraDepots.add(Point2d.of(162.0F, 133.0F));
         extraDepots.add(Point2d.of(158.0F, 133.0F));
         extraDepots.add(Point2d.of(160.0F, 133.0F));
         extraDepots.add(Point2d.of(133.0F, 142.0F));
         extraDepots.add(Point2d.of(138.0F, 145.0F));
         extraDepots.add(Point2d.of(157.0F, 136.0F));
         extraDepots.add(Point2d.of(155.0F, 136.0F));
      } else {
         myMineralPos = Point2d.of(34.0F, 19.5F);
         enemyMineralPos = Point2d.of(150.0F, 148.5F);
         WALL_2x2 = Point2d.of(35.0F, 44.0F);
         MID_WALL_2x2 = Point2d.of(35.0F, 42.0F);
         MID_WALL_3x3 = Point2d.of(34.5F, 41.5F);
         WALL_3x3 = Point2d.of(37.5F, 40.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(45.5F, 34.5F));
         _3x3Structures.add(Point2d.of(24.5F, 29.5F));
         BUNKER_NATURAL = Point2d.of(46.5F, 61.5F);
         REAPER_JUMP2 = Point2d.of(48.5F, 34.5F);
         STARPORTS.add(Point2d.of(22.5F, 33.5F));
         STARPORTS.add(Point2d.of(22.5F, 36.5F));
         STARPORTS.add(Point2d.of(24.5F, 40.5F));
         STARPORTS.add(Point2d.of(28.5F, 32.5F));
         STARPORTS.add(Point2d.of(28.5F, 35.5F));
         STARPORTS.add(Point2d.of(30.5F, 38.5F));
         STARPORTS.add(Point2d.of(22.5F, 24.5F));
         STARPORTS.add(Point2d.of(22.5F, 21.5F));
         STARPORTS.add(Point2d.of(29.5F, 19.5F));
         STARPORTS.add(Point2d.of(26.5F, 17.5F));
         STARPORTS.add(Point2d.of(39.5F, 16.5F));
         STARPORTS.add(Point2d.of(34.5F, 16.5F));
         STARPORTS.add(Point2d.of(43.5F, 24.5F));
         STARPORTS.add(Point2d.of(43.5F, 21.5F));
         STARPORTS.add(Point2d.of(49.5F, 21.5F));
         STARPORTS.add(Point2d.of(49.5F, 25.5F));
         TURRETS.add(Point2d.of(39.0F, 22.0F));
         TURRETS.add(Point2d.of(30.0F, 27.0F));
         TURRETS.add(Point2d.of(31.0F, 16.0F));
         MACRO_OCS.add(Point2d.of(39.5F, 26.5F));
         MACRO_OCS.add(Point2d.of(35.5F, 31.5F));
         MACRO_OCS.add(Point2d.of(41.5F, 32.5F));
         MACRO_OCS.add(Point2d.of(42.5F, 38.5F));
         MACRO_OCS.add(Point2d.of(46.5F, 29.5F));
         MACRO_OCS.add(Point2d.of(45.5F, 17.5F));
         MACRO_OCS.add(Point2d.of(29.5F, 43.5F));
         extraDepots.add(Point2d.of(48.0F, 37.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(24.0F, 19.0F));
         extraDepots.add(Point2d.of(33.0F, 14.0F));
         extraDepots.add(Point2d.of(31.0F, 14.0F));
         extraDepots.add(Point2d.of(29.0F, 14.0F));
         extraDepots.add(Point2d.of(43.0F, 14.0F));
         extraDepots.add(Point2d.of(41.0F, 14.0F));
         extraDepots.add(Point2d.of(45.0F, 14.0F));
         extraDepots.add(Point2d.of(49.0F, 18.0F));
         extraDepots.add(Point2d.of(42.0F, 18.0F));
         extraDepots.add(Point2d.of(41.0F, 20.0F));
         extraDepots.add(Point2d.of(24.0F, 27.0F));
         extraDepots.add(Point2d.of(26.0F, 27.0F));
         extraDepots.add(Point2d.of(27.0F, 21.0F));
         extraDepots.add(Point2d.of(36.0F, 19.0F));
      }

   }

   private static void setLocationsForZen(boolean isTopPos) {
      numReaperWall = 3;
      if (isTopPos) {
         proxyBarracksPos = Point2d.of(71.5F, 87.5F);
         proxyBunkerPos = Point2d.of(66.5F, 50.5F);
         myMineralPos = Point2d.of(150.0F, 140.5F);
         enemyMineralPos = Point2d.of(42.0F, 31.5F);
         WALL_2x2 = Point2d.of(135.0F, 130.0F);
         MID_WALL_2x2 = Point2d.of(135.0F, 132.0F);
         MID_WALL_3x3 = Point2d.of(135.5F, 132.5F);
         WALL_3x3 = Point2d.of(132.5F, 133.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(136.5F, 146.5F));
         _3x3Structures.add(Point2d.of(128.5F, 135.5F));
         BUNKER_NATURAL = Point2d.of(129.5F, 112.5F);
         REAPER_JUMP2 = Point2d.of(126.5F, 144.5F);
         FACTORY = Point2d.of(144.5F, 134.5F);
         STARPORTS.add(Point2d.of(146.5F, 131.5F));
         STARPORTS.add(Point2d.of(153.5F, 138.5F));
         STARPORTS.add(Point2d.of(152.5F, 135.5F));
         STARPORTS.add(Point2d.of(128.5F, 148.5F));
         STARPORTS.add(Point2d.of(149.5F, 133.5F));
         STARPORTS.add(Point2d.of(151.5F, 130.5F));
         STARPORTS.add(Point2d.of(148.5F, 128.5F));
         STARPORTS.add(Point2d.of(145.5F, 126.5F));
         STARPORTS.add(Point2d.of(145.5F, 123.5F));
         STARPORTS.add(Point2d.of(152.5F, 142.5F));
         STARPORTS.add(Point2d.of(149.5F, 146.5F));
         STARPORTS.add(Point2d.of(142.5F, 148.5F));
         STARPORTS.add(Point2d.of(145.5F, 150.5F));
         STARPORTS.add(Point2d.of(137.5F, 149.5F));
         STARPORTS.add(Point2d.of(136.5F, 152.5F));
         TURRETS.add(Point2d.of(139.0F, 144.0F));
         TURRETS.add(Point2d.of(147.0F, 139.0F));
         TURRETS.add(Point2d.of(142.0F, 144.0F));
         MACRO_OCS.add(Point2d.of(137.5F, 139.5F));
         MACRO_OCS.add(Point2d.of(132.5F, 143.5F));
         MACRO_OCS.add(Point2d.of(127.5F, 140.5F));
         MACRO_OCS.add(Point2d.of(132.5F, 138.5F));
         MACRO_OCS.add(Point2d.of(132.5F, 151.5F));
         MACRO_OCS.add(Point2d.of(141.5F, 129.5F));
         MACRO_OCS.add(Point2d.of(125.5F, 129.5F));
         MACRO_OCS.add(Point2d.of(120.5F, 126.5F));
         extraDepots.add(Point2d.of(144.0F, 121.0F));
         extraDepots.add(Point2d.of(126.0F, 147.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(143.0F, 151.0F));
         extraDepots.add(Point2d.of(141.0F, 151.0F));
         extraDepots.add(Point2d.of(147.0F, 147.0F));
         extraDepots.add(Point2d.of(151.0F, 139.0F));
         extraDepots.add(Point2d.of(155.0F, 133.0F));
         extraDepots.add(Point2d.of(154.0F, 128.0F));
         extraDepots.add(Point2d.of(152.0F, 126.0F));
         extraDepots.add(Point2d.of(150.0F, 126.0F));
         extraDepots.add(Point2d.of(150.0F, 124.0F));
         extraDepots.add(Point2d.of(129.0F, 151.0F));
         extraDepots.add(Point2d.of(126.0F, 149.0F));
         extraDepots.add(Point2d.of(129.0F, 144.0F));
         extraDepots.add(Point2d.of(129.0F, 146.0F));
         extraDepots.add(Point2d.of(133.0F, 148.0F));
      } else {
         proxyBarracksPos = Point2d.of(119.5F, 85.5F);
         proxyBunkerPos = Point2d.of(125.5F, 121.5F);
         myMineralPos = Point2d.of(42.0F, 31.5F);
         enemyMineralPos = Point2d.of(150.0F, 140.5F);
         WALL_2x2 = Point2d.of(60.0F, 39.0F);
         WALL_3x3 = Point2d.of(56.5F, 41.5F);
         MID_WALL_2x2 = Point2d.of(58.0F, 39.0F);
         MID_WALL_3x3 = Point2d.of(57.5F, 38.5F);
         _3x3Structures.add(MID_WALL_3x3);
         _3x3Structures.add(WALL_3x3);
         _3x3Structures.add(Point2d.of(65.5F, 29.5F));
         _3x3Structures.add(Point2d.of(54.5F, 18.5F));
         REAPER_JUMP2 = Point2d.of(65.5F, 24.5F);
         BUNKER_NATURAL = Point2d.of(61.5F, 58.5F);
         FACTORY = Point2d.of(49.5F, 36.5F);
         STARPORTS.add(Point2d.of(36.5F, 37.5F));
         STARPORTS.add(Point2d.of(36.5F, 34.5F));
         STARPORTS.add(Point2d.of(37.5F, 40.5F));
         STARPORTS.add(Point2d.of(42.5F, 38.5F));
         STARPORTS.add(Point2d.of(40.5F, 43.5F));
         STARPORTS.add(Point2d.of(41.5F, 46.5F));
         STARPORTS.add(Point2d.of(47.5F, 46.5F));
         STARPORTS.add(Point2d.of(44.5F, 49.5F));
         STARPORTS.add(Point2d.of(37.5F, 31.5F));
         STARPORTS.add(Point2d.of(39.5F, 27.5F));
         STARPORTS.add(Point2d.of(46.5F, 20.5F));
         STARPORTS.add(Point2d.of(49.5F, 22.5F));
         STARPORTS.add(Point2d.of(44.5F, 23.5F));
         STARPORTS.add(Point2d.of(55.5F, 24.5F));
         TURRETS.add(Point2d.of(53.0F, 28.0F));
         TURRETS.add(Point2d.of(45.0F, 33.0F));
         TURRETS.add(Point2d.of(50.0F, 28.0F));
         MACRO_OCS.add(Point2d.of(58.5F, 19.5F));
         MACRO_OCS.add(Point2d.of(61.5F, 24.5F));
         MACRO_OCS.add(Point2d.of(59.5F, 29.5F));
         MACRO_OCS.add(Point2d.of(62.5F, 34.5F));
         MACRO_OCS.add(Point2d.of(54.5F, 32.5F));
         MACRO_OCS.add(Point2d.of(47.5F, 42.5F));
         MACRO_OCS.add(Point2d.of(66.5F, 42.5F));
         MACRO_OCS.add(Point2d.of(71.5F, 45.5F));
         extraDepots.add(Point2d.of(48.0F, 51.0F));
         extraDepots.add(Point2d.of(66.0F, 27.0F));
         extraDepots.add(WALL_2x2);
         extraDepots.add(Point2d.of(44.0F, 41.0F));
         extraDepots.add(Point2d.of(42.0F, 41.0F));
         extraDepots.add(Point2d.of(37.0F, 43.0F));
         extraDepots.add(Point2d.of(46.0F, 52.0F));
         extraDepots.add(Point2d.of(41.0F, 33.0F));
         extraDepots.add(Point2d.of(66.0F, 33.0F));
         extraDepots.add(Point2d.of(63.0F, 28.0F));
         extraDepots.add(Point2d.of(63.0F, 30.0F));
         extraDepots.add(Point2d.of(65.0F, 22.0F));
         extraDepots.add(Point2d.of(55.0F, 21.0F));
         extraDepots.add(Point2d.of(59.0F, 33.0F));
         extraDepots.add(Point2d.of(56.0F, 29.0F));
         extraDepots.add(Point2d.of(63.0F, 21.0F));
      }

   }

   public static boolean isWallStructure(Unit structure) {
      float x1 = WALL_2x2.getX();
      float x2 = WALL_3x3.getX();
      float y1 = WALL_2x2.getY();
      float y2 = WALL_3x3.getY();
      float xMin = Math.min(x1, x2) - 1.0F;
      float xMax = Math.max(x1, x2) + 1.0F;
      float yMin = Math.min(y1, y2) - 1.0F;
      float yMax = Math.max(y1, y2) + 1.0F;
      float xStructure = structure.getPosition().getX();
      float yStructure = structure.getPosition().getY();
      return xStructure >= xMin && xStructure <= xMax && yStructure >= yMin && yStructure <= yMax;
   }

   public static void setBaseLocations() {
      String var0 = MAP;

      System.out.println("SetBaseLocations: MAP = " + MAP);
      System.out.println("SetBaseLocations: MAP Hash = " + MAP.hashCode());

      byte var1 = -1;
      switch(var0.hashCode()) {
      case -1794024318:
         if (var0.equals("Deathaura LE")) {
            var1 = 1;
         }
         break;
      case -1625047871:
         if (var0.equals("Ice and Chrome LE")) {
            var1 = 8;
         }
         break;
      case -1621757898:
         if (var0.equals("Zen LE")) {
            var1 = 17;
         }
         break;
      case -1571483726:
         if (var0.equals("Disco Bloodbath LE")) {
            var1 = 2;
         }
         break;
      case -1473241860:
         if (var0.equals("Thunderbird LE")) {
            var1 = 13;
         }
         break;
      case -1364344577:
         if (var0.equals("Simulacrum LE")) {
            var1 = 11;
         }
         break;
      case -1148468991:
         if (var0.equals("Acropolis LE")) {
            var1 = 0;
         }
         break;
      case -1125614728:
         if (var0.equals("Ever Dream LE")) {
            var1 = 6;
         }
         break;
      case -869678110:
         if (var0.equals("Eternal Empire LE")) {
            var1 = 5;
         }
         break;
      case -738120668:
         if (var0.equals("[TLMC12] Ephemeron")) {
            var1 = 3;
         }
         break;
      case -727670153:
         if (var0.equals("Submarine LE")) {
            var1 = 12;
         }
         break;
      case -699257656:
         if (var0.equals("Ephemeron LE")) {
            var1 = 4;
         }
         break;
      case -590794265:
         if (var0.equals("Pillars of Gold LE")) {
            var1 = 10;
         }
         break;
      case -74849212:
         if (var0.equals("Nightshade LE")) {
            var1 = 9;
         }
         break;
      case 556774015:
         if (var0.equals("Winter's Gate LE")) {
            var1 = 15;
         }
         break;
      case 805896056:
         if (var0.equals("Golden Wall LE")) {
            var1 = 7;
         }
         break;
      case 1661143439:
         if (var0.equals("World of Sleepers LE")) {
            var1 = 16;
         }
         break;
      case 1734971569:
         if (var0.equals("Triton LE")) {
            var1 = 14;
         }
      }

      switch(var1) {
      case 0:
         baseLocations.add(Point2d.of(33.5F, 138.5F));
         baseLocations.add(Point2d.of(31.5F, 113.5F));
         baseLocations.add(Point2d.of(58.5F, 111.5F));
         baseLocations.add(Point2d.of(32.5F, 85.5F));
         baseLocations.add(Point2d.of(29.5F, 53.5F));
         baseLocations.add(Point2d.of(73.5F, 138.5F));
         baseLocations.add(Point2d.of(47.5F, 28.5F));
         baseLocations.add(Point2d.of(107.5F, 129.5F));
         baseLocations.add(Point2d.of(68.5F, 42.5F));
         baseLocations.add(Point2d.of(128.5F, 143.5F));
         baseLocations.add(Point2d.of(102.5F, 33.5F));
         baseLocations.add(Point2d.of(146.5F, 118.5F));
         baseLocations.add(Point2d.of(143.5F, 86.5F));
         baseLocations.add(Point2d.of(117.5F, 60.5F));
         baseLocations.add(Point2d.of(144.5F, 58.5F));
         baseLocations.add(Point2d.of(142.5F, 33.5F));
         break;
      case 1:
         baseLocations.add(Point2d.of(37.5F, 139.5F));
         baseLocations.add(Point2d.of(57.5F, 148.5F));
         baseLocations.add(Point2d.of(54.5F, 118.5F));
         baseLocations.add(Point2d.of(84.5F, 149.5F));
         baseLocations.add(Point2d.of(116.5F, 150.5F));
         baseLocations.add(Point2d.of(38.5F, 85.5F));
         baseLocations.add(Point2d.of(126.5F, 120.5F));
         baseLocations.add(Point2d.of(65.5F, 67.5F));
         baseLocations.add(Point2d.of(153.5F, 102.5F));
         baseLocations.add(Point2d.of(75.5F, 37.5F));
         baseLocations.add(Point2d.of(107.5F, 38.5F));
         baseLocations.add(Point2d.of(137.5F, 69.5F));
         baseLocations.add(Point2d.of(134.5F, 39.5F));
         baseLocations.add(Point2d.of(154.5F, 48.5F));
         break;
      case 2:
         baseLocations.add(Point2d.of(39.5F, 115.5F));
         baseLocations.add(Point2d.of(48.5F, 142.5F));
         baseLocations.add(Point2d.of(64.5F, 113.5F));
         baseLocations.add(Point2d.of(83.5F, 144.5F));
         baseLocations.add(Point2d.of(117.5F, 143.5F));
         baseLocations.add(Point2d.of(38.5F, 81.5F));
         baseLocations.add(Point2d.of(159.5F, 141.5F));
         baseLocations.add(Point2d.of(143.5F, 115.5F));
         baseLocations.add(Point2d.of(56.5F, 64.5F));
         baseLocations.add(Point2d.of(40.5F, 38.5F));
         baseLocations.add(Point2d.of(161.5F, 98.5F));
         baseLocations.add(Point2d.of(82.5F, 36.5F));
         baseLocations.add(Point2d.of(116.5F, 35.5F));
         baseLocations.add(Point2d.of(135.5F, 66.5F));
         baseLocations.add(Point2d.of(151.5F, 37.5F));
         baseLocations.add(Point2d.of(160.5F, 64.5F));
         break;
      case 3:
      case 4:
         baseLocations.add(Point2d.of(29.5F, 138.5F));
         baseLocations.add(Point2d.of(29.5F, 111.5F));
         baseLocations.add(Point2d.of(61.5F, 134.5F));
         baseLocations.add(Point2d.of(94.5F, 141.5F));
         baseLocations.add(Point2d.of(131.5F, 131.5F));
         baseLocations.add(Point2d.of(28.5F, 73.5F));
         baseLocations.add(Point2d.of(91.5F, 111.5F));
         baseLocations.add(Point2d.of(68.5F, 48.5F));
         baseLocations.add(Point2d.of(131.5F, 86.5F));
         baseLocations.add(Point2d.of(28.5F, 28.5F));
         baseLocations.add(Point2d.of(65.5F, 18.5F));
         baseLocations.add(Point2d.of(98.5F, 25.5F));
         baseLocations.add(Point2d.of(130.5F, 48.5F));
         baseLocations.add(Point2d.of(130.5F, 21.5F));
         break;
      case 5:
         baseLocations.add(Point2d.of(142.5F, 140.5F));
         baseLocations.add(Point2d.of(142.5F, 110.5F));
         baseLocations.add(Point2d.of(115.5F, 134.5F));
         baseLocations.add(Point2d.of(79.5F, 146.5F));
         baseLocations.add(Point2d.of(131.5F, 83.5F));
         baseLocations.add(Point2d.of(86.5F, 126.5F));
         baseLocations.add(Point2d.of(126.5F, 55.5F));
         baseLocations.add(Point2d.of(33.5F, 139.5F));
         baseLocations.add(Point2d.of(49.5F, 116.5F));
         baseLocations.add(Point2d.of(142.5F, 32.5F));
         baseLocations.add(Point2d.of(89.5F, 45.5F));
         baseLocations.add(Point2d.of(44.5F, 88.5F));
         baseLocations.add(Point2d.of(96.5F, 25.5F));
         baseLocations.add(Point2d.of(60.5F, 37.5F));
         baseLocations.add(Point2d.of(33.5F, 61.5F));
         baseLocations.add(Point2d.of(33.5F, 31.5F));
         break;
      case 6:
         baseLocations.add(Point2d.of(139.5F, 163.5F));
         baseLocations.add(Point2d.of(154.5F, 147.5F));
         baseLocations.add(Point2d.of(153.5F, 113.5F));
         baseLocations.add(Point2d.of(118.5F, 145.5F));
         baseLocations.add(Point2d.of(94.5F, 161.5F));
         baseLocations.add(Point2d.of(156.5F, 84.5F));
         baseLocations.add(Point2d.of(131.5F, 114.5F));
         baseLocations.add(Point2d.of(154.5F, 51.5F));
         baseLocations.add(Point2d.of(45.5F, 160.5F));
         baseLocations.add(Point2d.of(68.5F, 97.5F));
         baseLocations.add(Point2d.of(105.5F, 50.5F));
         baseLocations.add(Point2d.of(43.5F, 127.5F));
         baseLocations.add(Point2d.of(81.5F, 66.5F));
         baseLocations.add(Point2d.of(46.5F, 98.5F));
         baseLocations.add(Point2d.of(45.5F, 64.5F));
         baseLocations.add(Point2d.of(60.5F, 48.5F));
         break;
      case 7:
         baseLocations.add(Point2d.of(32.5F, 50.5F));
         baseLocations.add(Point2d.of(40.5F, 77.5F));
         baseLocations.add(Point2d.of(42.5F, 109.5F));
         baseLocations.add(Point2d.of(71.5F, 71.5F));
         baseLocations.add(Point2d.of(39.5F, 141.5F));
         baseLocations.add(Point2d.of(75.5F, 130.5F));
         baseLocations.add(Point2d.of(51.5F, 30.5F));
         baseLocations.add(Point2d.of(79.5F, 29.5F));
         baseLocations.add(Point2d.of(128.5F, 29.5F));
         baseLocations.add(Point2d.of(156.5F, 30.5F));
         baseLocations.add(Point2d.of(132.5F, 130.5F));
         baseLocations.add(Point2d.of(168.5F, 141.5F));
         baseLocations.add(Point2d.of(136.5F, 71.5F));
         baseLocations.add(Point2d.of(165.5F, 109.5F));
         baseLocations.add(Point2d.of(167.5F, 77.5F));
         baseLocations.add(Point2d.of(175.5F, 50.5F));
         break;
      case 8:
         baseLocations.add(Point2d.of(182.5F, 172.5F));
         baseLocations.add(Point2d.of(186.5F, 145.5F));
         baseLocations.add(Point2d.of(157.5F, 152.5F));
         baseLocations.add(Point2d.of(182.5F, 116.5F));
         baseLocations.add(Point2d.of(144.5F, 174.5F));
         baseLocations.add(Point2d.of(185.5F, 88.5F));
         baseLocations.add(Point2d.of(110.5F, 175.5F));
         baseLocations.add(Point2d.of(124.5F, 152.5F));
         baseLocations.add(Point2d.of(131.5F, 83.5F));
         baseLocations.add(Point2d.of(145.5F, 60.5F));
         baseLocations.add(Point2d.of(70.5F, 147.5F));
         baseLocations.add(Point2d.of(111.5F, 61.5F));
         baseLocations.add(Point2d.of(73.5F, 119.5F));
         baseLocations.add(Point2d.of(98.5F, 83.5F));
         baseLocations.add(Point2d.of(69.5F, 90.5F));
         baseLocations.add(Point2d.of(73.5F, 63.5F));
         break;
      case 9:
         baseLocations.add(Point2d.of(41.5F, 138.5F));
         baseLocations.add(Point2d.of(42.5F, 107.5F));
         baseLocations.add(Point2d.of(73.5F, 133.5F));
         baseLocations.add(Point2d.of(105.5F, 141.5F));
         baseLocations.add(Point2d.of(60.5F, 94.5F));
         baseLocations.add(Point2d.of(150.5F, 140.5F));
         baseLocations.add(Point2d.of(127.5F, 121.5F));
         baseLocations.add(Point2d.of(39.5F, 69.5F));
         baseLocations.add(Point2d.of(152.5F, 102.5F));
         baseLocations.add(Point2d.of(64.5F, 50.5F));
         baseLocations.add(Point2d.of(41.5F, 31.5F));
         baseLocations.add(Point2d.of(131.5F, 77.5F));
         baseLocations.add(Point2d.of(86.5F, 30.5F));
         baseLocations.add(Point2d.of(118.5F, 38.5F));
         baseLocations.add(Point2d.of(149.5F, 64.5F));
         baseLocations.add(Point2d.of(150.5F, 33.5F));
         break;
      case 10:
         baseLocations.add(Point2d.of(137.5F, 133.5F));
         baseLocations.add(Point2d.of(110.5F, 142.5F));
         baseLocations.add(Point2d.of(116.5F, 112.5F));
         baseLocations.add(Point2d.of(81.5F, 141.5F));
         baseLocations.add(Point2d.of(136.5F, 95.5F));
         baseLocations.add(Point2d.of(36.5F, 139.5F));
         baseLocations.add(Point2d.of(51.5F, 122.5F));
         baseLocations.add(Point2d.of(116.5F, 49.5F));
         baseLocations.add(Point2d.of(131.5F, 32.5F));
         baseLocations.add(Point2d.of(31.5F, 76.5F));
         baseLocations.add(Point2d.of(86.5F, 30.5F));
         baseLocations.add(Point2d.of(51.5F, 59.5F));
         baseLocations.add(Point2d.of(57.5F, 29.5F));
         baseLocations.add(Point2d.of(30.5F, 38.5F));
         break;
      case 11:
         baseLocations.add(Point2d.of(54.5F, 139.5F));
         baseLocations.add(Point2d.of(50.5F, 113.5F));
         baseLocations.add(Point2d.of(78.5F, 118.5F));
         baseLocations.add(Point2d.of(99.5F, 141.5F));
         baseLocations.add(Point2d.of(49.5F, 80.5F));
         baseLocations.add(Point2d.of(159.5F, 140.5F));
         baseLocations.add(Point2d.of(130.5F, 128.5F));
         baseLocations.add(Point2d.of(85.5F, 55.5F));
         baseLocations.add(Point2d.of(56.5F, 43.5F));
         baseLocations.add(Point2d.of(116.5F, 42.5F));
         baseLocations.add(Point2d.of(166.5F, 103.5F));
         baseLocations.add(Point2d.of(137.5F, 65.5F));
         baseLocations.add(Point2d.of(165.5F, 70.5F));
         baseLocations.add(Point2d.of(161.5F, 44.5F));
         break;
      case 12:
         baseLocations.add(Point2d.of(32.5F, 127.5F));
         baseLocations.add(Point2d.of(33.5F, 104.5F));
         baseLocations.add(Point2d.of(62.5F, 122.5F));
         baseLocations.add(Point2d.of(36.5F, 79.5F));
         baseLocations.add(Point2d.of(33.5F, 49.5F));
         baseLocations.add(Point2d.of(97.5F, 129.5F));
         baseLocations.add(Point2d.of(70.5F, 34.5F));
         baseLocations.add(Point2d.of(134.5F, 114.5F));
         baseLocations.add(Point2d.of(131.5F, 84.5F));
         baseLocations.add(Point2d.of(105.5F, 41.5F));
         baseLocations.add(Point2d.of(134.5F, 59.5F));
         baseLocations.add(Point2d.of(135.5F, 36.5F));
         break;
      case 13:
         baseLocations.add(Point2d.of(38.5F, 133.5F));
         baseLocations.add(Point2d.of(40.5F, 103.5F));
         baseLocations.add(Point2d.of(67.5F, 126.5F));
         baseLocations.add(Point2d.of(57.5F, 87.5F));
         baseLocations.add(Point2d.of(98.5F, 130.5F));
         baseLocations.add(Point2d.of(37.5F, 62.5F));
         baseLocations.add(Point2d.of(131.5F, 136.5F));
         baseLocations.add(Point2d.of(111.5F, 100.5F));
         baseLocations.add(Point2d.of(80.5F, 55.5F));
         baseLocations.add(Point2d.of(60.5F, 19.5F));
         baseLocations.add(Point2d.of(154.5F, 93.5F));
         baseLocations.add(Point2d.of(93.5F, 25.5F));
         baseLocations.add(Point2d.of(134.5F, 68.5F));
         baseLocations.add(Point2d.of(124.5F, 29.5F));
         baseLocations.add(Point2d.of(151.5F, 52.5F));
         baseLocations.add(Point2d.of(153.5F, 22.5F));
         break;
      case 14:
         baseLocations.add(Point2d.of(55.5F, 157.5F));
         baseLocations.add(Point2d.of(82.5F, 160.5F));
         baseLocations.add(Point2d.of(71.5F, 132.5F));
         baseLocations.add(Point2d.of(112.5F, 159.5F));
         baseLocations.add(Point2d.of(49.5F, 114.5F));
         baseLocations.add(Point2d.of(154.5F, 157.5F));
         baseLocations.add(Point2d.of(120.5F, 132.5F));
         baseLocations.add(Point2d.of(150.5F, 131.5F));
         baseLocations.add(Point2d.of(65.5F, 72.5F));
         baseLocations.add(Point2d.of(95.5F, 71.5F));
         baseLocations.add(Point2d.of(61.5F, 46.5F));
         baseLocations.add(Point2d.of(166.5F, 89.5F));
         baseLocations.add(Point2d.of(103.5F, 44.5F));
         baseLocations.add(Point2d.of(144.5F, 71.5F));
         baseLocations.add(Point2d.of(133.5F, 43.5F));
         baseLocations.add(Point2d.of(160.5F, 46.5F));
         break;
      case 15:
         baseLocations.add(Point2d.of(47.5F, 134.5F));
         baseLocations.add(Point2d.of(44.5F, 109.5F));
         baseLocations.add(Point2d.of(70.5F, 112.5F));
         baseLocations.add(Point2d.of(43.5F, 81.5F));
         baseLocations.add(Point2d.of(92.5F, 135.5F));
         baseLocations.add(Point2d.of(44.5F, 53.5F));
         baseLocations.add(Point2d.of(44.5F, 28.5F));
         baseLocations.add(Point2d.of(147.5F, 135.5F));
         baseLocations.add(Point2d.of(147.5F, 110.5F));
         baseLocations.add(Point2d.of(148.5F, 82.5F));
         baseLocations.add(Point2d.of(99.5F, 28.5F));
         baseLocations.add(Point2d.of(121.5F, 51.5F));
         baseLocations.add(Point2d.of(147.5F, 54.5F));
         baseLocations.add(Point2d.of(144.5F, 29.5F));
         break;
      case 16:
         baseLocations.add(Point2d.of(149.5F, 141.5F));
         baseLocations.add(Point2d.of(147.5F, 112.5F));
         baseLocations.add(Point2d.of(120.5F, 140.5F));
         baseLocations.add(Point2d.of(148.5F, 83.5F));
         baseLocations.add(Point2d.of(118.5F, 115.5F));
         baseLocations.add(Point2d.of(149.5F, 56.5F));
         baseLocations.add(Point2d.of(84.5F, 142.5F));
         baseLocations.add(Point2d.of(147.5F, 25.5F));
         baseLocations.add(Point2d.of(36.5F, 142.5F));
         baseLocations.add(Point2d.of(99.5F, 25.5F));
         baseLocations.add(Point2d.of(65.5F, 52.5F));
         baseLocations.add(Point2d.of(34.5F, 111.5F));
         baseLocations.add(Point2d.of(63.5F, 27.5F));
         baseLocations.add(Point2d.of(35.5F, 84.5F));
         baseLocations.add(Point2d.of(36.5F, 55.5F));
         baseLocations.add(Point2d.of(34.5F, 26.5F));
         break;
      case 17:
         baseLocations.add(Point2d.of(142.5F, 139.5F));
         baseLocations.add(Point2d.of(134.5F, 117.5F));
         baseLocations.add(Point2d.of(149.5F, 95.5F));
         baseLocations.add(Point2d.of(149.5F, 63.5F));
         baseLocations.add(Point2d.of(111.5F, 146.5F));
         baseLocations.add(Point2d.of(104.5F, 121.5F));
         baseLocations.add(Point2d.of(150.5F, 27.5F));
         baseLocations.add(Point2d.of(71.5F, 134.5F));
         baseLocations.add(Point2d.of(120.5F, 37.5F));
         baseLocations.add(Point2d.of(41.5F, 144.5F));
         baseLocations.add(Point2d.of(42.5F, 108.5F));
         baseLocations.add(Point2d.of(80.5F, 25.5F));
         baseLocations.add(Point2d.of(87.5F, 50.5F));
         baseLocations.add(Point2d.of(42.5F, 76.5F));
         baseLocations.add(Point2d.of(57.5F, 54.5F));
         baseLocations.add(Point2d.of(49.5F, 32.5F));
      }

      if (!isTopSpawn) {
         Collections.reverse(baseLocations);
      }

   }

   public static void setClockBaseLists() {
      Map<Double, Point2d> basesByAngle = new TreeMap();
      float midX = (float)MAX_X / 2.0F;
      float midY = (float)MAX_Y / 2.0F;

      System.out.println("baseLocations = " + baseLocations);

      Point2d homeBasePos = (Point2d)baseLocations.get(0);



      double homeBaseAngle = Math.toDegrees(Math.atan2((double)(homeBasePos.getX() - midX), (double)(homeBasePos.getY() - midY)));
      Point2d enemyBasePos = (Point2d)baseLocations.get(baseLocations.size() - 1);
      double enemyBaseAngle = Math.toDegrees(Math.atan2((double)(enemyBasePos.getX() - midX), (double)(enemyBasePos.getY() - midY))) - homeBaseAngle;

      Point2d basePos;
      double angle;
      for(Iterator var9 = baseLocations.iterator(); var9.hasNext(); basesByAngle.put(angle, basePos)) {
         basePos = (Point2d)var9.next();
         angle = Math.toDegrees(Math.atan2((double)(basePos.getX() - midX), (double)(basePos.getY() - midY))) - homeBaseAngle;
         if (enemyBaseAngle < 0.0D && angle < enemyBaseAngle) {
            angle += 360.0D;
         }

         if (enemyBaseAngle > 0.0D && angle > enemyBaseAngle) {
            angle -= 360.0D;
         }
      }

      basesByAngle.forEach((anglex, basePosx) -> {
         if (anglex <= 0.0D) {
            counterClockBasePositions.add(0, basePosx);
         }

         if (anglex >= 0.0D) {
            clockBasePositions.add(basePosx);
         }

      });
      if (enemyBaseAngle < 0.0D) {
         clockBasePositions.add(enemyBasePos);
      } else {
         counterClockBasePositions.add(enemyBasePos);
      }

   }

   public static Point2d getMainBaseMidPoint(boolean isEnemyMain) {
      boolean[][] pointInBase = isEnemyMain ? InfluenceMaps.pointInEnemyMainBase : InfluenceMaps.pointInMainBase;
      int xMin = 0;
      int xMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getX());
      int yMin = 0;
      int yMax = InfluenceMaps.toMapCoord(SCREEN_TOP_RIGHT.getY());
      int xBaseLeft = Integer.MAX_VALUE;
      int xBaseRight = 0;
      int yBaseTop = 0;
      int yBaseBottom = Integer.MAX_VALUE;

      for(int x = xMin; x <= xMax; ++x) {
         for(int y = yMin; y <= yMax; ++y) {
            if (pointInBase[x][y]) {
               xBaseLeft = Math.min(xBaseLeft, x);
               xBaseRight = Math.max(xBaseRight, x);
               yBaseTop = Math.max(yBaseTop, y);
               yBaseBottom = Math.min(yBaseBottom, y);
            }
         }
      }

      float avgX = (float)(xBaseLeft + xBaseRight) / 2.0F;
      float avgY = (float)(yBaseTop + yBaseBottom) / 2.0F;
      return Point2d.of(avgX / 2.0F, avgY / 2.0F);
   }

   public static void prepareReaperWallLocations() {
      if (numReaperWall == 2) {
         _3x3Structures.set(0, REAPER_JUMP2);
         extraDepots.add(2, MID_WALL_2x2);
      } else if (numReaperWall == 3) {
         _3x3Structures.set(0, REAPER_JUMP2);
         extraDepots.add(3, MID_WALL_2x2);
      }

   }

   public static Point2d getFactoryPos() {
      if (UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_STARPORT, FACTORY, 1.0F).isEmpty()) {
         return FACTORY;
      } else {
         Strategy.DO_INCLUDE_TANKS = false;
         return (Point2d)STARPORTS.remove(0);
      }
   }

   static {
      SCREEN_BOTTOM_LEFT = ((StartRaw)Bot.OBS.getGameInfo().getStartRaw().get()).getPlayableArea().getP0().toPoint2d();
      SCREEN_TOP_RIGHT = ((StartRaw)Bot.OBS.getGameInfo().getStartRaw().get()).getPlayableArea().getP1().toPoint2d();
      MAX_X = (int)SCREEN_TOP_RIGHT.getX();
      MAX_Y = (int)SCREEN_TOP_RIGHT.getY();
      _3x3Structures = new ArrayList();
      extraDepots = new ArrayList();
      STARPORTS = new ArrayList();
      TURRETS = new ArrayList();
      MACRO_OCS = new ArrayList();
      baseLocations = new ArrayList();
      clockBasePositions = new ArrayList();
      counterClockBasePositions = new ArrayList();
      baseAttackIndex = 2;
   }
}

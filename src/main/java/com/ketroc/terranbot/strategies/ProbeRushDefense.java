package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat.Channel;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.StructureScv;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ProbeRushDefense {
   public static int clusterMyNodeStep;
   public static int clusterEnemyNodeStep;
   public static int defenseStep;
   public static List<UnitInPool> scvList;
   public static boolean isAttackingTownHall;
   public static Units townHallType;

   public static boolean onStep() {
      try {
         if (townHallType == null && UnitUtils.enemyCommandStructures != null) {
            townHallType = (Units)UnitUtils.enemyCommandStructures.iterator().next();
         }

         int i;
         if (defenseStep >= 2) {
            LocationConstants.enemyMineralTriangle.updateNodes();
            UnitUtils.removeDeadUnits(scvList);
            Unit cc = (Unit)GameCache.ccList.get(0);
            i = UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, cc.getPosition().toPoint2d(), 12.0F).size();
            if (i > 0 && cc.getType() == Units.TERRAN_COMMAND_CENTER) {
               if (!cc.getOrders().isEmpty() && ((UnitOrder)cc.getOrders().get(0)).getAbility() == Abilities.TRAIN_SCV) {
                  Bot.ACTION.unitCommand(cc, Abilities.CANCEL_LAST, false);
               } else {
                  Bot.ACTION.unitCommand((Unit)GameCache.ccList.get(0), Abilities.LIFT_COMMAND_CENTER, false);
               }
            } else if (i == 0 && ((Unit)GameCache.ccList.get(0)).getType() == Units.TERRAN_COMMAND_CENTER_FLYING) {
               Bot.ACTION.unitCommand((Unit)GameCache.ccList.get(0), Abilities.LAND_COMMAND_CENTER, (Point2d)LocationConstants.baseLocations.get(0), false);
            }
         }

         switch(defenseStep) {
         case 0:
            if (Bot.OBS.getGameLoop() < 3200L && UnitUtils.getEnemyUnitsOfType(UnitUtils.enemyWorkerType).size() > 5 && UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, (Point2d)LocationConstants.baseLocations.get(0), 50.0F).size() > 5) {
               ++defenseStep;
               Bot.ACTION.sendChat("Okay!  I can do that too.", Channel.BROADCAST);
            }
         case 1:
            List<UnitInPool> producingStructures = Bot.OBS.getUnits(Alliance.SELF, (u) -> {
               return u.unit().getBuildProgress() != 1.0F;
            });

            for(i = 0; i < StructureScv.scvBuildingList.size(); ++i) {
               StructureScv scv = (StructureScv)StructureScv.scvBuildingList.get(i);
               Iterator var3 = producingStructures.iterator();

               while(var3.hasNext()) {
                  UnitInPool structure = (UnitInPool)var3.next();
                  if (UnitUtils.getDistance(structure.unit(), scv.structurePos) < 1.0F) {
                     Bot.ACTION.unitCommand(structure.unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
                     break;
                  }
               }

               Bot.ACTION.unitCommand(scv.scv.unit(), Abilities.SMART, ((Base)GameCache.baseList.get(0)).getRallyNode(), false);
               if (scv.structureType == Units.TERRAN_SUPPLY_DEPOT) {
                  LocationConstants.extraDepots.add(0, scv.structurePos);
               }

               StructureScv.remove(scv);
               --i;
            }

            ++defenseStep;
         case 2:
            if (UnitUtils.getUnitsNearbyOfType(Alliance.ENEMY, UnitUtils.enemyWorkerType, ((Unit)GameCache.ccList.get(0)).getPosition().toPoint2d(), 12.0F).isEmpty()) {
               break;
            }

            if (scvList == null) {
               scvList = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SCV, ((Unit)GameCache.ccList.get(0)).getPosition().toPoint2d(), 20.0F);
            }

            if (clusterMyNode()) {
               ++defenseStep;
            }
         case 3:
            if (clusterEnemyNode()) {
               ++defenseStep;
            }
         case 4:
            attackingTownHall();
         case 5:
            if (((List)GameCache.allFriendliesMap.getOrDefault(Units.TERRAN_COMMAND_CENTER_FLYING, Collections.emptyList())).isEmpty()) {
               defenseStep = 0;
            }
         }
      } catch (Exception var8) {
         var8.printStackTrace();
      } finally {
         return defenseStep != 0;
      }
   }

   private static void attackingTownHall() {
      if (((List)GameCache.allEnemiesMap.get(townHallType)).isEmpty()) {
         Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.ATTACK, Position.towards(LocationConstants.myMineralPos, (Point2d)LocationConstants.baseLocations.get(0), 2.0F), false).unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralPos, false);
         ++defenseStep;
      } else {
         Unit townHall = ((UnitInPool)((List)GameCache.allEnemiesMap.get(townHallType)).get(0)).unit();
         int numWorkers = Bot.OBS.getUnits(Alliance.ENEMY, (worker) -> {
            return worker.unit().getType() == UnitUtils.enemyWorkerType && UnitUtils.getDistance(worker.unit(), townHall) < 10.0F;
         }).size();
         if (scvList.size() > numWorkers * 2) {
            Iterator var2 = scvList.iterator();

            while(true) {
               while(var2.hasNext()) {
                  UnitInPool scv = (UnitInPool)var2.next();
                  if (!Bot.OBS.getUnits(Alliance.ENEMY, (worker) -> {
                     return worker.unit().getType() == UnitUtils.enemyWorkerType && UnitUtils.getDistance(worker.unit(), scv.unit()) < 1.0F;
                  }).isEmpty()) {
                     Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.enemyMineralPos, false);
                  } else {
                     List<UnitInPool> nearbyWeakScvs = Bot.OBS.getUnits(Alliance.SELF, (weakScv) -> {
                        return weakScv.unit().getType() == Units.TERRAN_SCV && UnitUtils.getDistance(weakScv.unit(), scv.unit()) < 1.0F && (Float)weakScv.unit().getHealth().get() < (Float)weakScv.unit().getHealthMax().get();
                     });
                     if (GameCache.mineralBank > 0 && !nearbyWeakScvs.isEmpty()) {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.EFFECT_REPAIR, ((UnitInPool)nearbyWeakScvs.get(0)).unit(), false);
                     } else {
                        Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, townHall, false);
                     }
                  }
               }

               isAttackingTownHall = true;
               break;
            }
         } else if (isAttackingTownHall) {
            if (clusterEnemyNode()) {
               isAttackingTownHall = false;
            }
         } else {
            Unit closestWorker = UnitUtils.getClosestEnemyOfType(UnitUtils.enemyWorkerType, ((UnitInPool)scvList.get(0)).unit().getPosition().toPoint2d());
            if (UnitUtils.getDistance(closestWorker, ((UnitInPool)scvList.get(0)).unit()) > 1.0F) {
               Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
            } else {
               Iterator var6 = scvList.iterator();

               while(var6.hasNext()) {
                  UnitInPool scv = (UnitInPool)var6.next();
                  if ((Float)scv.unit().getWeaponCooldown().get() == 0.0F) {
                     Bot.ACTION.unitCommand(scv.unit(), Abilities.ATTACK, LocationConstants.myMineralPos, false);
                  } else {
                     Bot.ACTION.unitCommand(scv.unit(), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
                  }
               }
            }
         }

      }
   }

   public static boolean clusterMyNode() {
      switch(clusterMyNodeStep) {
      case 0:
         if (scvList.stream().anyMatch((scv) -> {
            return UnitUtils.getDistance(scv.unit(), LocationConstants.myMineralTriangle.getInner().unit()) > 2.0F;
         })) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getInner().unit(), false);
         } else {
            ++clusterMyNodeStep;
         }
         break;
      case 1:
         if (scvList.stream().anyMatch((scv) -> {
            return UnitUtils.getDistance(scv.unit(), LocationConstants.myMineralTriangle.getOuter().unit()) > 2.0F;
         })) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getOuter().unit(), false);
         } else {
            ++clusterMyNodeStep;
         }
         break;
      case 2:
         if (!scvList.stream().anyMatch((scv) -> {
            return UnitUtils.getDistance(scv.unit(), LocationConstants.myMineralTriangle.getMiddle().unit()) > 2.0F;
         })) {
            clusterMyNodeStep = 0;
            return true;
         }

         Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.myMineralTriangle.getMiddle().unit(), false);
      }

      return false;
   }

   public static boolean clusterEnemyNode() {
      switch(clusterEnemyNodeStep) {
      case 0:
         if (scvList.stream().anyMatch((scv) -> {
            return UnitUtils.getDistance(scv.unit(), LocationConstants.enemyMineralTriangle.getInner().unit()) > 2.0F;
         })) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getInner().unit(), false);
         } else {
            ++clusterEnemyNodeStep;
         }
         break;
      case 1:
         if (scvList.stream().anyMatch((scv) -> {
            return UnitUtils.getDistance(scv.unit(), LocationConstants.enemyMineralTriangle.getOuter().unit()) > 2.0F;
         })) {
            Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getOuter().unit(), false);
         } else {
            ++clusterEnemyNodeStep;
         }
         break;
      case 2:
         if (!scvList.stream().anyMatch((scv) -> {
            return UnitUtils.getDistance(scv.unit(), LocationConstants.enemyMineralTriangle.getMiddle().unit()) > 2.0F;
         })) {
            clusterEnemyNodeStep = 0;
            return true;
         }

         Bot.ACTION.unitCommand(UnitUtils.toUnitList(scvList), Abilities.SMART, LocationConstants.enemyMineralTriangle.getMiddle().unit(), false);
      }

      return false;
   }
}

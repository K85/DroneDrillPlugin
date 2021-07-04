package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.DebugInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class CannonRushDefense {
   public static int cannonRushStep;
   public static boolean isSafe = true;

   public static void onStep() {
      switch(cannonRushStep) {
      case 0:
         if (Bot.OBS.getGameLoop() < 2500L && !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_PYLON).stream().anyMatch((pylon) -> {
            return UnitUtils.getDistance(pylon.unit(), (Point2d)LocationConstants.baseLocations.get(0)) < 40.0F;
         })) {
            ++cannonRushStep;
         }
         break;
      case 1:
         ScvTarget.removeDeadTargets();
         ScvTarget.targets.stream().forEach((scvTargetx) -> {
            scvTargetx.scvs.removeIf((u) -> {
               return !u.isAlive();
            });
         });
         addNewTarget(Units.PROTOSS_PHOTON_CANNON);
         addNewTarget(Units.PROTOSS_PROBE);
         addNewTarget(Units.PROTOSS_PYLON);
         List<Unit> marines = UnitUtils.getFriendlyUnitsOfType(Units.TERRAN_MARINE);
         List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs((Point2d)LocationConstants.baseLocations.get(0), 50);
         Iterator var2 = ScvTarget.targets.iterator();

         while(var2.hasNext()) {
            ScvTarget scvTarget = (ScvTarget)var2.next();
            if (scvTarget.targetUnit.unit().getType() == Units.PROTOSS_PROBE) {
               System.out.println("scvTarget.numScvs = " + scvTarget.numScvs);
               System.out.println("scvTarget.scvs.size() = " + scvTarget.scvs.size());
            }

            for(int i = 0; i < scvTarget.numScvs - scvTarget.scvs.size() && !availableScvs.isEmpty(); ++i) {
               UnitInPool newScv = (UnitInPool)availableScvs.remove(0);
               Bot.ACTION.unitCommand(newScv.unit(), Abilities.ATTACK, scvTarget.targetUnit.unit(), false);
               scvTarget.addScv(newScv);
            }

            if (!marines.isEmpty() && scvTarget.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON) {
               Bot.ACTION.unitCommand(marines, Abilities.ATTACK, scvTarget.targetUnit.unit(), false);
            }
         }

         if (!marines.isEmpty() && !ScvTarget.targets.isEmpty()) {
            Unit cleanUp = (Unit)ScvTarget.targets.stream().map((scvTargetx) -> {
               return scvTargetx.targetUnit.unit();
            }).sorted(Comparator.comparing((targetUnit) -> {
               return UnitUtils.getDistance(targetUnit, ((Base)GameCache.baseList.get(1)).getCcPos());
            })).findFirst().get();
            Bot.ACTION.unitCommand(marines, Abilities.ATTACK, cleanUp, false);
         }

         if (BansheeBot.isDebugOn) {
            DebugInterface var10000 = Bot.DEBUG;
            String var10001 = "targets list size: " + ScvTarget.targets.size();
            Point2d var10002 = Point2d.of(0.1F, 0.2037037F);
            Color var10003 = Color.WHITE;
            var10000.debugTextOut(var10001, var10002, Color.WHITE, 12);
         }

         int i = 1;
         Iterator var8 = ScvTarget.targets.iterator();

         while(var8.hasNext()) {
            ScvTarget target = (ScvTarget)var8.next();
            if (BansheeBot.isDebugOn) {
               Bot.DEBUG.debugTextOut("scvs: " + target.scvs.size() + " on: " + (Units)target.targetUnit.unit().getType(), Point2d.of(0.1F, (float)((100.0D + 20.0D * (double)(6 + i++)) / 1080.0D)), Color.WHITE, 12);
            }
         }

         isSafe = !ScvTarget.targets.stream().anyMatch((t) -> {
            return t.targetUnit.unit().getType() == Units.PROTOSS_PROBE || t.targetUnit.unit().getType() == Units.PROTOSS_PHOTON_CANNON;
         });
         if (ScvTarget.targets.isEmpty()) {
            cannonRushStep = 0;
         }
      case 2:
      }

   }

   private static void addNewTarget(Units unitType) {
      Iterator var1 = UnitUtils.getEnemyUnitsOfType(unitType).iterator();

      while(var1.hasNext()) {
         UnitInPool newTarget = (UnitInPool)var1.next();
         if (UnitUtils.isVisible(newTarget) && UnitUtils.getDistance(newTarget.unit(), (Point2d)LocationConstants.baseLocations.get(0)) < 50.0F && !ScvTarget.targets.stream().anyMatch((t) -> {
            return t.targetUnit.getTag().equals(newTarget.getTag());
         })) {
            ScvTarget.targets.add(new ScvTarget(newTarget));
         }
      }

   }
}

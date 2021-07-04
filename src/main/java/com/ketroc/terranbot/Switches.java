package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.purchases.PurchaseUpgrade;
import com.ketroc.terranbot.strategies.Strategy;
import java.util.List;

public class Switches {
   public static UnitInPool bansheeDiveTarget;
   public static UnitInPool vikingDiveTarget;
   public static boolean isDivingTempests;
   public static boolean tvtFastStart;
   public static boolean finishHim;
   public static boolean isExpectingEnemyBCs;
   public static boolean firstObserverSpotted;
   public static boolean scvRushComplete = true;
   public static boolean enemyCanProduceAir;
   public static boolean enemyHasCloakThreat;
   public static boolean phoenixAreReal;
   public static boolean hotkey8;
   public static boolean includeTanks;
   public static boolean scoutScanComplete;
   public static boolean doBuildMainBaseTurrets = true;

   public static void onStep() {
      if (!isExpectingEnemyBCs && Bot.OBS.getGameLoop() < 10752L && LocationConstants.opponentRace == Race.TERRAN && (!UnitUtils.getEnemyUnitsOfType(Units.TERRAN_BATTLECRUISER).isEmpty() || !UnitUtils.getEnemyUnitsOfType(Units.TERRAN_FUSION_CORE).isEmpty())) {
         BansheeBot.purchaseQueue.addFirst(new PurchaseUpgrade(Upgrades.TERRAN_BUILDING_ARMOR, Bot.OBS.getUnit(((Unit)((List)GameCache.allFriendliesMap.get(Units.TERRAN_ENGINEERING_BAY)).get(0)).getTag())));
         BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_MISSILE_TURRET, (Point2d)LocationConstants.TURRETS.get(2)));
         isExpectingEnemyBCs = true;
      }

      if (LocationConstants.opponentRace == Race.PROTOSS && !firstObserverSpotted && !UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_OBSERVER).isEmpty()) {
         Strategy.energyToMuleAt = 100;
         firstObserverSpotted = true;
      }

   }
}

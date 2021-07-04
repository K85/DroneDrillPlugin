package com.ketroc.terranbot.managers;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.Switches;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.PurchaseStructure;
import com.ketroc.terranbot.strategies.BunkerContain;

public class BuildOrder {
    public static UnitInPool proxyScv;

    public static void onGameStart() {
        if (BunkerContain.proxyBunkerLevel != 0) {
            BunkerContain.addAnotherRepairScv();
        }

        switch (LocationConstants.opponentRace) {
            case TERRAN:
                LocationConstants.prepareReaperWallLocations();
                if (BunkerContain.proxyBunkerLevel == 2) {
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvList.get(0).unit(), Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
                    Point2d factoryPos = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 3);
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_FACTORY, factoryPos));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                } else {
                    Switches.tvtFastStart = true;
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BUNKER, LocationConstants.BUNKER_NATURAL));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                }
                break;
            case PROTOSS:
                if (BunkerContain.proxyBunkerLevel != 0) {
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2));
                    LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(BunkerContain.repairScvList.get(0).unit(), Units.TERRAN_BARRACKS, LocationConstants.proxyBarracksPos));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                } else {
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2));
                    LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                    BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
                }
                break;
            case RANDOM:
            case ZERG:
                BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.WALL_2x2));
                LocationConstants.extraDepots.remove(LocationConstants.WALL_2x2);
                BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
                BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_COMMAND_CENTER));
                BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_BARRACKS));
                BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_ENGINEERING_BAY));
        }

    }

    private static Point2d getBunkerContainPosition() {
        Point2d natCCPos = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 2);
        Point2d bunkerPos = Position.towards(natCCPos, LocationConstants.baseLocations.get(1), 11.0F);
        if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, bunkerPos)) {
            return bunkerPos;
        } else if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(bunkerPos, natCCPos, 1.0F))) {
            return Position.towards(bunkerPos, natCCPos, 1.0F);
        } else {
            Point2d enemyMainCCPos = LocationConstants.baseLocations.get(LocationConstants.baseLocations.size() - 1);
            boolean clockwiseFirst = Position.rotate(bunkerPos, natCCPos, 10.0D).distance(enemyMainCCPos) > Position.rotate(bunkerPos, natCCPos, -10.0D).distance(enemyMainCCPos);

            int i;
            int rotation;
            Point2d rotatedPos;
            for (i = 10; i < 90; i += 10) {
                rotation = clockwiseFirst ? i : i * -1;
                rotatedPos = Position.rotate(bunkerPos, natCCPos, rotation);
                if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, rotatedPos)) {
                    return rotatedPos;
                }

                if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(rotatedPos, natCCPos, 1.0F))) {
                    return Position.towards(rotatedPos, natCCPos, 1.0F);
                }
            }

            for (i = 10; i < 90; i += 10) {
                rotation = !clockwiseFirst ? i : i * -1;
                rotatedPos = Position.rotate(bunkerPos, natCCPos, rotation);
                if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, rotatedPos)) {
                    return rotatedPos;
                }

                if (Bot.QUERY.placement(Abilities.BUILD_BUNKER, Position.towards(rotatedPos, natCCPos, 1.0F))) {
                    return Position.towards(rotatedPos, natCCPos, 1.0F);
                }
            }

            return natCCPos;
        }
    }
}

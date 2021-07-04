package com.ketroc.terranbot.strategies;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.action.ActionChat.Channel;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.Switches;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.purchases.Purchase;
import com.ketroc.terranbot.purchases.PurchaseStructure;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class Strategy {
    public static final boolean ANTI_DROP_TURRET = false;
    public static final int NUM_TANKS_PER_EXPANSION = 2;
    public static final int MAX_TANKS = 12;
    public static final int NUM_LIBS_PER_EXPANSION = 2;
    public static final int MAX_LIBS = 12;
    public static final int FUNGAL_FRAMES = 16;
    public static final int MAX_VIKINGS_TO_DIVE_TEMPESTS = 20;
    public static final float DISTANCE_RAISE_DEPOT = 9.0F;
    public static final int MIN_STRUCTURE_HEALTH = 40;
    public static final float KITING_BUFFER = 2.5F;
    public static final int RETREAT_HEALTH = 40;
    public static final int NUM_DONT_EXPAND = 2;
    public static final float ENERGY_BEFORE_CLOAKING = 80.0F;
    public static final int NUM_SCVS_REPAIR_STATION = 7;
    public static final float BANSHEE_RANGE = 6.1F;
    public static final float VIKING_RANGE = 9.1F;
    public static final int MIN_GAS_FOR_REFINERY = 1;
    public static final int TEMPEST_DIVE_RANGE = 23;
    public static final float RAVEN_DISTANCING_BUFFER = 2.0F;
    public static final int CAST_SEEKER_RANGE = 15;
    public static final float SEEKER_RADIUS = 3.0F;
    public static final float MIN_SUPPLY_TO_SEEKER = 22.0F;
    public static final int MAP_ENEMIES_IN_FOG_DURATION = 112;
    public static int selectedStrategy;
    public static int SKIP_FRAMES;
    public static boolean ANTI_NYDUS_BUILD;
    public static boolean DO_INCLUDE_TANKS;
    public static boolean DO_INCLUDE_LIBS;
    public static float VIKING_BANSHEE_RATIO = 0.2F;
    public static int maxScvs = 80;
    public static boolean enemyHasAirThreat;
    public static int DIVE_RANGE = 12;
    public static int energyToMuleAt = 50;
    public static boolean techBuilt;
    public static boolean diveRavensVsVikings;
    public static int step_TvtFastStart = 1;
    public static UnitInPool scv_TvtFastStart;
    public static int floatBaseAt = 50;

    public static void onGameStart() {
        SKIP_FRAMES = BansheeBot.isRealTime ? 6 : 2;
        getGameStrategyChoice();
        if (ANTI_NYDUS_BUILD) {
            antiNydusBuild();
        }

    }

    private static void getGameStrategyChoice() {
        setStrategyNumber();
        switch (LocationConstants.opponentRace) {
            case TERRAN:
                chooseTvTStrategy();
                break;
            case PROTOSS:
                chooseTvPStrategy();
                break;
            case ZERG:
                chooseTvZStrategy();
        }

    }

    private static void chooseTvTStrategy() {
        boolean numStrategies = true;
        selectedStrategy = 1;
        switch (selectedStrategy) {
            case 0:
                Bot.ACTION.sendChat("Standard Strategy", Channel.BROADCAST);
                break;
            case 1:
                Bot.ACTION.sendChat("Bunker Contain Strategy", Channel.BROADCAST);
                BunkerContain.proxyBunkerLevel = 2;
                break;
            case 2:
                Bot.ACTION.sendChat("SCV Rush Strategy", Channel.BROADCAST);
                Switches.scvRushComplete = false;
        }

    }

    private static void chooseTvPStrategy() {
        boolean numStrategies = true;
        selectedStrategy = 1;
        switch (selectedStrategy) {
            case 0:
                Bot.ACTION.sendChat("Standard Strategy", Channel.BROADCAST);
                break;
            case 1:
                Bot.ACTION.sendChat("Bunker Contain Strategy", Channel.BROADCAST);
                BunkerContain.proxyBunkerLevel = 1;
                break;
            case 2:
                Bot.ACTION.sendChat("SCV Rush Strategy", Channel.BROADCAST);
                Switches.scvRushComplete = false;
        }

    }

    private static void chooseTvZStrategy() {
        int numStrategies = 2;
        selectedStrategy %= numStrategies;
        switch (selectedStrategy) {
            case 0:
                Bot.ACTION.sendChat("Standard Strategy", Channel.BROADCAST);
                break;
            case 1:
                Bot.ACTION.sendChat("SCV Rush Strategy", Channel.BROADCAST);
                Switches.scvRushComplete = false;
        }

    }

    private static void setStrategyNumber() {


        String[] fileText = new String[0];
        // Fix
        fileText = "5f0cdd49b351856~1".split("~");

        String lastOpponentId = fileText[0];
        int opponentStrategy = Integer.valueOf(fileText[1]);
        if (lastOpponentId.equals(BansheeBot.opponentId) && LocationConstants.opponentRace != Race.RANDOM) {
            selectedStrategy = opponentStrategy;
        } else {
            selectedStrategy = 0;
        }

    }

    public static void onStep() {
    }

    public static void onStep_TvtFaststart() {
        switch (step_TvtFastStart) {
            case 1:
                Bot.ACTION.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, LocationConstants.extraDepots.get(0), false);
                ++step_TvtFastStart;
                break;
            case 2:
                if (Bot.OBS.getFoodWorkers() == 13) {
                    Bot.ACTION.unitCommand(GameCache.ccList.get(0), Abilities.RALLY_COMMAND_CENTER, GameCache.baseList.get(0).getMineralPatches().get(0), false);
                    ++step_TvtFastStart;
                }
                break;
            case 3:
                if (GameCache.mineralBank >= 100) {
                    List<UnitInPool> scvNearDepot = WorkerManager.getAllScvs(LocationConstants.extraDepots.get(0), 6);
                    if (!scvNearDepot.isEmpty()) {
                        scv_TvtFastStart = scvNearDepot.get(0);
                        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
                        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(scv_TvtFastStart.unit(), Units.TERRAN_SUPPLY_DEPOT));
                    } else {
                        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_BARRACKS, LocationConstants._3x3Structures.remove(0)));
                        BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT));
                    }

                    Switches.tvtFastStart = false;
                }
        }

    }

    public static void setMaxScvs() {
        if (GameCache.defaultRallyNode == null) {
            maxScvs = 5;
        } else if (LocationConstants.MACRO_OCS.isEmpty() && GameCache.mineralBank > 3000) {
            maxScvs = 50;
        }

    }

    public static void antiNydusBuild() {
        BansheeBot.purchaseQueue.add(1, BansheeBot.purchaseQueue.remove(3));
        BansheeBot.purchaseQueue.add(4, new PurchaseStructure(Units.TERRAN_SUPPLY_DEPOT, LocationConstants.extraDepots.remove(LocationConstants.extraDepots.size() - 1)));
        BansheeBot.purchaseQueue.add(new PurchaseStructure(Units.TERRAN_REFINERY));
        Point2d closestStarportPos = LocationConstants.STARPORTS.stream().min(Comparator.comparing((starportPos) -> {
            return starportPos.distance(LocationConstants.MID_WALL_3x3);
        })).get();
        ((PurchaseStructure) BansheeBot.purchaseQueue.get(1)).setPosition(closestStarportPos);
        LocationConstants._3x3Structures.remove(0);
    }
}

package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.gateway.ActionInterface;
import com.github.ocraft.s2client.bot.gateway.DebugInterface;
import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.QueryInterface;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;

import java.util.HashMap;
import java.util.Map;

public class Bot extends S2Agent {
    public static ActionInterface ACTION;
    public static ObservationInterface OBS;
    public static QueryInterface QUERY;
    public static DebugInterface DEBUG;
    public static boolean isDebugOn;
    public static boolean isRealTime;
    public static String opponentId;
    public static Map<Abilities, Units> abilityToUnitType = new HashMap();
    public static Map<Abilities, Upgrades> abilityToUpgrade = new HashMap();

    public Bot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        Bot.isDebugOn = isDebugOn;
        Bot.opponentId = opponentId;
        Bot.isRealTime = isRealTime;
    }

    public void onGameStart() {
        OBS = this.observation();
        ACTION = this.actions();
        QUERY = this.query();
        DEBUG = this.debug();
    }
}

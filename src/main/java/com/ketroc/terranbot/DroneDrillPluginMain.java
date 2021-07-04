package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.syntax.SettingsSyntax;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.terranbot.bots._12PoolBot;
import com.sakurawald.plugin.PluginBase;

public class DroneDrillPluginMain extends PluginBase {

    @Override
    public String getPluginName() {
        return "DroneDrill";
    }

    @Override
    public SettingsSyntax generateSettingsSyntax() {
        return S2Coordinator.setup().setTimeoutMS(300000).setRawAffectsSelection(false).setShowCloaked(true).setShowBurrowed(true).setRealtime(true);
    }

    @Override
    public S2Agent generateS2Agent() {
        return new _12PoolBot();
    }

    @Override
    public void beforeLaunch() {
        // Check Bot Race.
        if (this.getChooseBotRace() != Race.ZERG) {
            throw new RuntimeException("The Bot Only Support Race: ZERG");
        }
    }

}

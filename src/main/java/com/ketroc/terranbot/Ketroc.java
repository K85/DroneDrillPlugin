package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.setting.PlayerSettings;
import com.github.ocraft.s2client.protocol.game.Difficulty;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.terranbot.bots.DroneDrill;
import java.nio.file.Paths;

public class Ketroc {
   public static void main(String[] args) {
      S2Coordinator s2Coordinator = S2Coordinator.setup().loadSettings(args).setWindowLocation(900, 0).setNeedsSupportDir(true).setShowCloaked(true).setShowBurrowed(true).setRawAffectsSelection(false).setTimeoutMS(600000).setParticipants(new PlayerSettings[]{S2Coordinator.createParticipant(Race.ZERG, new DroneDrill(true, (String)null, false)), S2Coordinator.createComputer(Race.TERRAN, Difficulty.VERY_HARD)}).launchStarcraft().startGame(LocalMap.of(Paths.get("DeathAuraLE.SC2Map")));

      while(s2Coordinator.update()) {
      }

      s2Coordinator.quit();
   }
}

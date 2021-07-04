package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.setting.PlayerSettings;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.bots.DroneDrill;

public class Ladder {
   public static void main(String[] args) {
      boolean realTime = false;
      String opponentId = null;

      for(int i = 0; i < args.length; ++i) {
         String arg = args[i];
         if (arg.equals("--RealTime")) {
            realTime = true;
            break;
         }

         if (arg.contains("--OpponentId")) {
            opponentId = args[i + 1];
         }
      }

      Bot bot = new DroneDrill(false, opponentId, realTime);
      S2Coordinator s2Coordinator = S2Coordinator.setup().setTimeoutMS(300000).setRawAffectsSelection(false).loadLadderSettings(args).setShowCloaked(true).setShowBurrowed(true).setParticipants(new PlayerSettings[]{S2Coordinator.createParticipant(Race.ZERG, bot)}).connectToLadder().joinGame();

      while(s2Coordinator.update()) {
      }

      s2Coordinator.quit();
   }
}

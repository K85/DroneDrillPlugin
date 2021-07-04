
import com.github.ocraft.s2client.bot.S2Agent;
import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.gateway.ActionInterface;
import com.github.ocraft.s2client.bot.syntax.SettingsSyntax;
import com.github.ocraft.s2client.protocol.game.LocalMap;
import com.github.ocraft.s2client.protocol.game.Race;
import com.ketroc.terranbot.Ladder;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.DroneDrill;
import com.ketroc.terranbot.strategies.MapNameTransfer;
import com.sakurawald.plugin.PluginBase;
import com.sakurawald.ui.App;

import java.nio.file.Paths;

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
        return new DroneDrill(false, "NONE_OPPONENT_ID", true);
    }

    @Override
    public void beforeLaunch() {
        // Check Bot Race.
        if (this.getChooseBotRace() != Race.ZERG) {
            throw new RuntimeException("The Bot Only Support Race: ZERG");
        }
    }

    public static void main(String[] args) {


        PluginBase pluginBase = new DroneDrillPluginMain();
        S2Coordinator s2Coordinator = pluginBase.generateSettingsSyntax().setParticipants(S2Coordinator.createParticipant(Race.TERRAN, new S2Agent(){}, "Player")
        , S2Coordinator.createParticipant(Race.ZERG, new BansheeBot(true, "NONE_OPPONENT_ID", true), "Opponent")).launchStarcraft().startGame(LocalMap.of(Paths.get("C:\\Users\\31729\\Desktop\\package\\Titanium\\Maps\\TritonLE.SC2Map")));

        while (s2Coordinator.update()) {
            // Do nothing.
        }
        s2Coordinator.quit();


    }

}

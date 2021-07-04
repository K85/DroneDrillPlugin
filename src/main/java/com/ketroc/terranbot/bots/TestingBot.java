package com.ketroc.terranbot.bots;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.Tester;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

public class TestingBot extends Bot {
    public static UnitInPool bunker;
    public static UnitInPool marine;
    public float z;
    public Unit commandCenter;

    public TestingBot(boolean isDebugOn, String opponentId, boolean isRealTime) {
        super(isDebugOn, opponentId, isRealTime);
    }

    public void onGameStart() {
        super.onGameStart();
        this.debug().debugGiveAllTech().debugGodMode().debugFastBuild().debugIgnoreFood().debugIgnoreMineral().debugIgnoreResourceCost();
        int playerId = this.observation().getPlayerId();
        this.debug().debugCreateUnit(Units.TERRAN_SCV, Point2d.of(114.0F, 120.0F), playerId, 10);
        this.debug().sendDebug();
    }

    public void onUnitCreated(UnitInPool unitInPool) {
    }

    public void onBuildingConstructionComplete(UnitInPool unitInPool) {
        Unit unit = unitInPool.unit();
        PrintStream var10000 = System.out;
        float var10001 = unit.getPosition().getX();
        var10000.println("baseLocations.add(Point2d.of(" + var10001 + "f, " + unit.getPosition().getY() + "f));");
    }

    public void onStep() {
        List<Unit> scvs = this.observation().getUnits(Alliance.SELF, (scv) -> {
            return scv.unit().getType() == Units.TERRAN_SCV;
        }).stream().map(UnitInPool::unit).collect(Collectors.toList());
        this.actions().unitCommand(scvs.remove(0), Abilities.BUILD_COMMAND_CENTER, Point2d.of(1.0F, 250.0F), false);
        if (OBS.getGameLoop() == 100L) {
            List<Point2d> expansions = Tester.calculateExpansionLocations(OBS);
            List<Unit> scvss = this.observation().getUnits(Alliance.SELF, (scv) -> {
                return scv.unit().getType() == Units.TERRAN_SCV;
            }).stream().map(UnitInPool::unit).collect(Collectors.toList());
            expansions.forEach((p) -> {
                this.actions().unitCommand(scvss.remove(0), Abilities.BUILD_COMMAND_CENTER, p, false);
            });
        }

        ACTION.sendActions();
        DEBUG.sendDebug();
        boolean q = true;
    }

    public void onUnitIdle(UnitInPool unitInPool) {
    }
}

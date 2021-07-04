package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Infestor {
    public static List<Infestor> infestorList = new ArrayList();
    public static List<IncomingFungal> incomingFungalList = new ArrayList();
    private final UnitInPool infestor;
    private float prevEnergy;

    public Infestor(UnitInPool infestor) {
        this.infestor = infestor;
        this.prevEnergy = infestor.unit().getEnergy().orElse(0.0F);
    }

    public static void onStep() {
        Infestor inf;
        for (Iterator var0 = infestorList.iterator(); var0.hasNext(); inf.prevEnergy = inf.infestor.unit().getEnergy().orElse(inf.prevEnergy)) {
            inf = (Infestor) var0.next();
            if (inf.prevEnergy - inf.infestor.unit().getEnergy().orElse(inf.prevEnergy) > 73.0F && inf.prevEnergy - inf.infestor.unit().getEnergy().get() < 76.0F) {
                dodgefungal(inf.infestor.unit());
            }
        }

        List<Unit> visibleInfestors = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.INFESTOR_TYPE);
        visibleInfestors.stream().filter((infestor) -> {
            return !isInfestorInList(infestor);
        }).forEach((unit) -> {
            infestorList.add(new Infestor(Bot.OBS.getUnit(unit.getTag())));
        });
        incomingFungalList = incomingFungalList.stream().filter((fungal) -> {
            return !fungal.isExpired();
        }).collect(Collectors.toList());
        incomingFungalList.stream().forEach((fungal) -> {
            GameCache.enemyMappingList.add(new EnemyUnit(fungal.position, true));
        });
    }

    private static void dodgefungal(Unit infestor) {
        Set<Units> airUnitTypes = Set.of(Units.TERRAN_BANSHEE, Units.TERRAN_VIKING_FIGHTER, Units.TERRAN_RAVEN);
        List<UnitInPool> airUnitsInFungalRange = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, airUnitTypes, infestor.getPosition().toPoint2d(), 15.0F);
        airUnitsInFungalRange.stream().forEach((unit) -> {
            Ignored.add(new IgnoredFungalDodger(unit.getTag()));
            Point2d dodgePoint = Position.towards(unit.unit().getPosition().toPoint2d(), infestor.getPosition().toPoint2d(), -3.0F);
            Bot.ACTION.unitCommand(unit.unit(), Abilities.MOVE, dodgePoint, false);
            switch ((Units) unit.unit().getType()) {
                case TERRAN_BANSHEE:
                    GameCache.bansheeList.remove(unit.unit());
                    break;
                case TERRAN_VIKING_FIGHTER:
                    GameCache.vikingList.remove(unit.unit());
                    break;
                case TERRAN_RAVEN:
                    GameCache.ravenList.remove(unit.unit());
            }

        });
    }

    private static Point2d calcFungalPos(Unit infestor) {
        Point2d infestorPos = infestor.getPosition().toPoint2d();
        double direction = Math.toDegrees(infestor.getFacing());
        float distance = 10.0F;
        Point2d originPoint = Point2d.of(infestorPos.getX() + distance, infestorPos.getY());
        return Position.rotate(originPoint, infestorPos, direction);
    }

    public static boolean isInfestorInList(Unit newInfestor) {
        return infestorList.stream().anyMatch((infestor) -> {
            return infestor.infestor.unit().getTag().equals(newInfestor.getTag());
        });
    }
}

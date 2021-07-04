package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.DisplayType;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.Position;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

import java.util.List;

public class TriangleOfNodes {
    private final Point2d midPos;
    private UnitInPool middle;
    private UnitInPool inner;
    private UnitInPool outer;
    private Point2d clusterPos;

    public TriangleOfNodes(Point2d midPos) {
        this.midPos = midPos;
    }

    public Point2d getClusterPos() {
        if (this.clusterPos == null) {
            this.updateNodes();
        }

        return this.clusterPos;
    }

    public void setClusterPos(Point2d clusterPos) {
        this.clusterPos = clusterPos;
    }

    public UnitInPool getMiddle() {
        if (this.middle == null) {
            this.updateNodes();
        }

        return this.middle;
    }

    public void setMiddle(UnitInPool middle) {
        this.middle = middle;
    }

    public UnitInPool getInner() {
        if (this.inner == null) {
            this.updateNodes();
        }

        return this.inner;
    }

    public void setInner(UnitInPool inner) {
        this.inner = inner;
    }

    public UnitInPool getOuter() {
        if (this.outer == null) {
            this.updateNodes();
        }

        return this.outer;
    }

    public void setOuter(UnitInPool outer) {
        this.outer = outer;
    }

    public boolean hasSnapshots() {
        return this.getMiddle().unit().getDisplayType() == DisplayType.SNAPSHOT || this.getInner().unit().getDisplayType() == DisplayType.SNAPSHOT || this.getOuter().unit().getDisplayType() == DisplayType.SNAPSHOT;
    }

    public boolean requiresUpdate() {
        return this.middle == null || this.inner == null || this.outer == null || !UnitUtils.isVisible(this.getMiddle()) || !UnitUtils.isVisible(this.getInner()) || !UnitUtils.isVisible(this.getOuter());
    }

    public void updateNodes() {
        if (this.requiresUpdate()) {
            this.middle = null;
            this.inner = null;
            this.outer = null;
            this.clusterPos = null;
            List<UnitInPool> mineralNodes = Bot.OBS.getUnits(Alliance.NEUTRAL, (node) -> {
                return UnitUtils.MINERAL_NODE_TYPE.contains(node.unit().getType()) && node.unit().getPosition().toPoint2d().distance(this.midPos) < 2.5D;
            });

            for (int i = 0; i < mineralNodes.size(); ++i) {
                if ((double) UnitUtils.getDistance(mineralNodes.get(i).unit(), this.midPos) < 0.5D) {
                    this.setMiddle(mineralNodes.remove(i));
                    break;
                }
            }

            UnitInPool node1 = mineralNodes.get(0);
            UnitInPool node2 = mineralNodes.get(1);
            Point2d midMineralLine = UnitUtils.getDistance(node1.unit(), GameCache.baseList.get(0).getResourceMidPoint()) < 10.0F ? GameCache.baseList.get(0).getResourceMidPoint() : GameCache.baseList.get(GameCache.baseList.size() - 1).getResourceMidPoint();
            if (UnitUtils.getDistance(node1.unit(), midMineralLine) < UnitUtils.getDistance(node2.unit(), midMineralLine)) {
                this.setInner(node1);
                this.setOuter(node2);
            } else {
                this.setInner(node2);
                this.setOuter(node1);
            }

            this.clusterPos = Position.midPoint(this.inner.unit().getPosition().toPoint2d(), this.outer.unit().getPosition().toPoint2d());
        }

    }
}

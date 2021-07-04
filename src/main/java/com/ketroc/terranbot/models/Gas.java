package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;

public class Gas {
    private Unit geyser;
    private Unit refinery;
    private Point2d location;

    public Gas(Unit geyser) {
        this.geyser = geyser;
        this.location = geyser.getPosition().toPoint2d();
    }

    public Unit getGeyser() {
        return this.geyser;
    }

    public void setGeyser(Unit geyser) {
        this.geyser = geyser;
    }

    public Unit getRefinery() {
        return this.refinery;
    }

    public void setRefinery(Unit refinery) {
        this.refinery = refinery;
    }

    public Point2d getLocation() {
        return this.location;
    }

    public void setLocation(Point2d location) {
        this.location = location;
    }
}

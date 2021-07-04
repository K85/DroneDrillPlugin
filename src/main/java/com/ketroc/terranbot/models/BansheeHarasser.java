package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.CloakState;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.Bot;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class BansheeHarasser {
    public static final float RETREAT_HEALTH = 50.0F;
    public UnitInPool banshee;
    public boolean retreatForRepairs;
    private List<Point2d> baseList;
    private boolean isDodgeClockwise;
    private int baseIndex = 1;

    public BansheeHarasser(UnitInPool banshee, boolean isBaseTravelClockwise) {
        this.banshee = banshee;
        this.baseList = isBaseTravelClockwise ? LocationConstants.clockBasePositions : LocationConstants.counterClockBasePositions;
        this.baseList = this.baseList.subList(1, this.baseList.size());
        this.isDodgeClockwise = isBaseTravelClockwise;
    }

    public boolean isDodgeClockwise() {
        return this.isDodgeClockwise;
    }

    public void toggleDodgeClockwise() {
        this.isDodgeClockwise = !this.isDodgeClockwise;
    }

    private void nextBase() {
        this.baseIndex = (this.baseIndex + 1) % this.baseList.size();
    }

    private Point2d getThisBase() {
        return this.baseList.get(this.baseIndex);
    }

    public void bansheeMicro() {
        if (!this.retreatForRepairs && this.isLowHealth()) {
            this.retreatForRepairs = true;
        }

        if (this.shouldCloak()) {
            Bot.ACTION.unitCommand(this.banshee.unit(), Abilities.BEHAVIOR_CLOAK_ON_BANSHEE, false);
        } else {
            if (this.banshee.unit().getWeaponCooldown().orElse(1.0F) == 0.0F) {
                BansheeHarasser.Target target = this.selectHarassTarget();
                if (target.unit != null) {
                    if (this.isSafe() || !this.retreatForRepairs && target.hp <= 24.0F) {
                        Bot.ACTION.unitCommand(this.banshee.unit(), Abilities.ATTACK, target.unit.unit(), false);
                        return;
                    }
                } else if (UnitUtils.getDistance(this.banshee.unit(), this.getThisBase()) < 2.0F && UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType).stream().noneMatch((enemyWorker) -> {
                    return UnitUtils.getDistance(this.banshee.unit(), enemyWorker) < 10.0F;
                })) {
                    this.nextBase();
                }
            }

            Point2d headedTo = this.retreatForRepairs ? LocationConstants.REPAIR_BAY : this.getTargetLocation();
            this.giveMovementCommand(headedTo);
        }
    }

    private Point2d getTargetLocation() {
        if (this.retreatForRepairs) {
            return LocationConstants.REPAIR_BAY;
        } else {
            Unit closestWorker = UnitUtils.getVisibleEnemyUnitsOfType(UnitUtils.enemyWorkerType).stream().min(Comparator.comparing((worker) -> {
                return UnitUtils.getDistance(this.banshee.unit(), worker);
            })).orElse(null);
            return closestWorker != null && UnitUtils.getDistance(this.banshee.unit(), closestWorker) < 10.0F ? closestWorker.getPosition().toPoint2d() : this.getThisBase();
        }
    }

    private void giveMovementCommand(Point2d targetPos) {
        Point2d safePos = this.getSafePos(targetPos);
        Bot.ACTION.unitCommand(this.banshee.unit(), Abilities.MOVE, safePos, false);
        DebugHelper.drawBox(safePos, Color.GREEN, 0.22F);
        DebugHelper.drawBox(safePos, Color.GREEN, 0.2F);
        DebugHelper.drawBox(safePos, Color.GREEN, 0.18F);
    }

    private Point2d getSafePos(Point2d targetPos) {
        return this.getSafePos(targetPos, 3.5F);
    }

    private Point2d getSafePos(Point2d targetPos, float rangeCheck) {
        Point2d towardsTarget = Position.towards(this.banshee.unit().getPosition().toPoint2d(), targetPos, rangeCheck);
        Point2d safestPos = null;
        int safestThreatValue = Integer.MAX_VALUE;

        for (int i = 0; i < 360; i += 20) {
            int angle = this.isDodgeClockwise ? i : i * -1;
            Point2d detourPos = Position.rotate(towardsTarget, this.banshee.unit().getPosition().toPoint2d(), angle, true);
            if (detourPos != null) {
                int threatValue = InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, detourPos);
                if (rangeCheck > 7.0F && threatValue < safestThreatValue) {
                    safestThreatValue = threatValue;
                    safestPos = detourPos;
                }

                if (this.isSafe(detourPos)) {
                    if (i > 200 || this.isLeavingWorkers(detourPos)) {
                        this.toggleDodgeClockwise();
                    }

                    i += 20;
                    angle = this.isDodgeClockwise ? i : i * -1;
                    detourPos = Position.rotate(towardsTarget, this.banshee.unit().getPosition().toPoint2d(), angle);
                    return detourPos;
                }
            }
        }

        if (safestPos == null) {
            return this.getSafePos(targetPos, rangeCheck + 2.0F);
        } else {
            return safestPos;
        }
    }

    private boolean isLeavingWorkers(Point2d targetPos) {
        return this.isWorkerInRange(this.banshee.unit().getPosition().toPoint2d()) && !this.isWorkerInRange(targetPos);
    }

    private boolean isWorkerInRange(Point2d pos) {
        return !Bot.OBS.getUnits(Alliance.ENEMY, (enemy) -> {
            return enemy.unit().getType() == UnitUtils.enemyWorkerType && UnitUtils.getDistance(enemy.unit(), pos) < 6.0F;
        }).isEmpty();
    }

    public boolean canCloak() {
        float energyToCloak = this.banshee.unit().getHealth().get() > 24.0F ? 50.0F : 27.0F;
        return Bot.OBS.getUpgrades().contains(Upgrades.BANSHEE_CLOAK) && this.banshee.unit().getEnergy().orElse(0.0F) > energyToCloak;
    }

    private boolean isCloaked() {
        return this.banshee.unit().getCloakState().orElse(CloakState.NOT_CLOAKED) == CloakState.CLOAKED_ALLIED;
    }

    private boolean isSafe() {
        return this.isSafe(this.banshee.unit().getPosition().toPoint2d());
    }

    private boolean isLowHealth() {
        return this.banshee.unit().getHealth().orElse(1.0F) < 50.0F;
    }

    private boolean isSafe(Point2d p) {
        float threatValue = (float) InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, p);
        if (threatValue >= 50.0F) {
            return false;
        } else {
            boolean safe = threatValue <= 2.0F;
            if (this.retreatForRepairs) {
                return safe;
            } else {
                boolean cloakAvailable = this.canCloak() || this.isCloaked() && this.banshee.unit().getEnergy().orElse(0.0F) > 5.0F;
                return safe || cloakAvailable && !this.isDetected(p);
            }
        }
    }

    private boolean shouldCloak() {
        if (!this.isCloaked() && this.canCloak()) {
            Point2d p = this.banshee.unit().getPosition().toPoint2d();
            return !this.isDetected(p) && (float) InfluenceMaps.getValue(InfluenceMaps.pointThreatToAir, p) > this.banshee.unit().getHealth().get() / 30.0F;
        } else {
            return false;
        }
    }

    private boolean isDetected(Point2d p) {
        return InfluenceMaps.getValue(InfluenceMaps.pointDetected, p);
    }

    public BansheeHarasser.Target selectHarassTarget() {
        List<UnitInPool> enemiesInRange = Bot.OBS.getUnits(Alliance.ENEMY, (enemyx) -> {
            return !(Boolean) enemyx.unit().getFlying().orElse(true) && (double) UnitUtils.getDistance(enemyx.unit(), this.banshee.unit()) <= 5.8D && !UnitUtils.IGNORED_TARGETS.contains(enemyx.unit().getType());
        });
        BansheeHarasser.Target bestTarget = new BansheeHarasser.Target(null, Float.MAX_VALUE, Float.MAX_VALUE);
        Iterator var3 = enemiesInRange.iterator();

        while (var3.hasNext()) {
            UnitInPool enemy = (UnitInPool) var3.next();
            float enemyHP = enemy.unit().getHealth().orElse(0.0F) + enemy.unit().getShield().orElse(0.0F);
            UnitTypeData enemyData = Bot.OBS.getUnitTypeData(false).get(enemy.unit().getType());
            float enemyCost;
            if (enemy.unit().getType() == UnitUtils.enemyWorkerType) {
                enemyCost = 75.0F;
            } else {
                enemyCost = (float) enemyData.getMineralCost().orElse(1) + (float) enemyData.getVespeneCost().orElse(1) * 1.2F;
            }

            float enemyValue = enemyHP / enemyCost;
            if (enemyValue < bestTarget.value && !enemy.unit().getBuffs().contains(Buffs.PROTECTIVE_BARRIER)) {
                bestTarget.update(enemy, enemyValue, enemyHP);
            }
        }

        return bestTarget;
    }

    public static class Target {
        public UnitInPool unit;
        public float value;
        public float hp;

        public Target(UnitInPool unit, float value, float hp) {
            this.update(unit, value, hp);
        }

        public void update(UnitInPool unit, float value, float hp) {
            this.unit = unit;
            this.value = value;
            this.hp = hp;
        }
    }
}

package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.Buffs;
import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;

public class EnemyUnit {
    public float x;
    public float y;
    public float supply;
    public boolean isAir;
    public boolean isDetector;
    public boolean isEffect;
    public boolean isArmy;
    public boolean isSeekered;
    public boolean isTempest;
    public float detectRange;
    public float groundAttackRange;
    public float airAttackRange;
    public short threatLevel;
    public byte pfTargetLevel;
    public float maxRange;

    public EnemyUnit(Unit friendly, boolean isParasitic) {
        this.x = friendly.getPosition().getX();
        this.y = friendly.getPosition().getY();
        this.isDetector = true;
        this.detectRange = 5.5F;
        this.airAttackRange = 5.5F;
        this.threatLevel = 200;
        this.calcMaxRange();
    }

    public EnemyUnit(Point2d pos, boolean isFungal) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.isDetector = true;
        this.isEffect = true;
        this.detectRange = 3.5F;
        this.groundAttackRange = 3.5F;
        this.airAttackRange = 3.5F;
        this.maxRange = 3.5F;
        this.threatLevel = 200;
    }

    public EnemyUnit(Unit enemy) {
        float kitingBuffer = this.getKitingBuffer(enemy);
        this.x = enemy.getPosition().getX();
        this.y = enemy.getPosition().getY();
        this.supply = Bot.OBS.getUnitTypeData(false).get(enemy.getType()).getFoodRequired().orElse(0.0F);
        this.isAir = enemy.getFlying().orElse(false);
        this.threatLevel = getThreatValue((Units) enemy.getType());
        this.pfTargetLevel = getPFTargetValue((Units) enemy.getType());
        this.detectRange = this.getDetectionRange(enemy);
        this.isDetector = this.detectRange > 0.0F;
        this.detectRange += kitingBuffer;
        this.isArmy = this.supply > 0.0F && !UnitUtils.WORKER_TYPE.contains(enemy.getType());
        this.isSeekered = enemy.getBuffs().contains(Buffs.RAVEN_SHREDDER_MISSILE_TINT);
        this.airAttackRange = UnitUtils.getAirAttackRange(enemy);
        if (this.airAttackRange != 0.0F) {
            this.airAttackRange += kitingBuffer;
        }

        this.groundAttackRange = UnitUtils.getGroundAttackRange(enemy);
        if (this.groundAttackRange != 0.0F) {
            this.groundAttackRange += kitingBuffer;
        }

        switch ((Units) enemy.getType()) {
            case PROTOSS_PHOENIX:
                this.airAttackRange += 2.0F;
                break;
            case TERRAN_MISSILE_TURRET:
            case TERRAN_AUTO_TURRET:
            case ZERG_HYDRALISK:
            case ZERG_MUTALISK:
                ++this.airAttackRange;
                break;
            case TERRAN_MARINE:
            case PROTOSS_SENTRY:
            case PROTOSS_HIGH_TEMPLAR:
                this.airAttackRange -= 0.5F;
                break;
            case PROTOSS_TEMPEST:
                this.isTempest = true;
        }

        this.calcMaxRange();
    }

    public EnemyUnit(EffectLocations effect) {
        float kitingBuffer = 2.0F;
        this.isEffect = true;
        Point2d position = effect.getPositions().iterator().next();
        this.x = position.getX();
        this.y = position.getY();
        switch ((Effects) effect.getEffect()) {
            case SCANNER_SWEEP:
                this.isDetector = true;
                this.detectRange = 13.0F;
                break;
            case RAVAGER_CORROSIVE_BILE_CP:
                this.isDetector = true;
                this.detectRange = 5.0F + kitingBuffer;
                this.threatLevel = 200;
                this.airAttackRange = 5.0F + kitingBuffer;
                this.groundAttackRange = this.airAttackRange;
                break;
            case PSI_STORM_PERSISTENT:
                this.isDetector = true;
                this.detectRange = effect.getRadius().get() + kitingBuffer;
                this.threatLevel = 200;
                this.airAttackRange = effect.getRadius().get() + kitingBuffer;
                this.groundAttackRange = this.airAttackRange;
        }

        this.calcMaxRange();
    }

    public static byte getThreatValue(Units unitType) {
        switch (unitType) {
            case PROTOSS_PHOENIX:
                return 5;
            case TERRAN_MISSILE_TURRET:
                return 8;
            case TERRAN_AUTO_TURRET:
                return 6;
            case ZERG_HYDRALISK:
                return 3;
            case ZERG_MUTALISK:
                return 3;
            case TERRAN_MARINE:
                return 2;
            case PROTOSS_SENTRY:
                return 2;
            case PROTOSS_HIGH_TEMPLAR:
                return 2;
            case PROTOSS_TEMPEST:
                return 2;
            case PROTOSS_PHOTON_CANNON:
                return 5;
            case ZERG_SPORE_CRAWLER:
                return 5;
            case PROTOSS_OBSERVER:
            default:
                return 0;
            case TERRAN_BUNKER:
                return 12;
            case TERRAN_VIKING_FIGHTER:
                return 3;
            case TERRAN_LIBERATOR:
                return 6;
            case TERRAN_GHOST:
                return 4;
            case TERRAN_CYCLONE:
                return 5;
            case TERRAN_THOR:
                return 14;
            case TERRAN_THOR_AP:
                return 6;
            case TERRAN_WIDOWMINE_BURROWED:
                return 0;
            case TERRAN_BATTLECRUISER:
                return 8;
            case PROTOSS_ARCHON:
                return 10;
            case PROTOSS_INTERCEPTOR:
                return 1;
            case PROTOSS_MOTHERSHIP:
                return 5;
            case PROTOSS_VOIDRAY:
                return 4;
            case PROTOSS_STALKER:
                return 3;
            case ZERG_QUEEN:
                return 4;
            case ZERG_CORRUPTOR:
                return 3;
        }
    }

    public static byte getPFTargetValue(Units unitType) {
        switch (unitType) {
            case TERRAN_AUTO_TURRET:
                return 1;
            case ZERG_HYDRALISK:
                return 6;
            case ZERG_MUTALISK:
            case PROTOSS_TEMPEST:
            case PROTOSS_PHOTON_CANNON:
            case ZERG_SPORE_CRAWLER:
            case PROTOSS_OBSERVER:
            case TERRAN_BUNKER:
            case TERRAN_VIKING_FIGHTER:
            case TERRAN_LIBERATOR:
            case TERRAN_WIDOWMINE_BURROWED:
            case TERRAN_BATTLECRUISER:
            case PROTOSS_INTERCEPTOR:
            case PROTOSS_MOTHERSHIP:
            case PROTOSS_VOIDRAY:
            case ZERG_CORRUPTOR:
            default:
                return 0;
            case TERRAN_MARINE:
                return 5;
            case PROTOSS_SENTRY:
                return 3;
            case PROTOSS_HIGH_TEMPLAR:
                return 15;
            case TERRAN_GHOST:
                return 9;
            case TERRAN_CYCLONE:
                return 6;
            case TERRAN_THOR:
                return 4;
            case TERRAN_THOR_AP:
                return 4;
            case PROTOSS_ARCHON:
                return 5;
            case PROTOSS_STALKER:
                return 4;
            case ZERG_QUEEN:
                return 3;
            case TERRAN_MARAUDER:
                return 6;
            case TERRAN_SIEGE_TANK:
                return 5;
            case TERRAN_SIEGE_TANK_SIEGED:
                return 4;
            case TERRAN_HELLION:
                return 2;
            case TERRAN_HELLION_TANK:
                return 5;
            case PROTOSS_ZEALOT:
                return 5;
            case PROTOSS_ADEPT:
                return 3;
            case PROTOSS_COLOSSUS:
                return 3;
            case PROTOSS_IMMORTAL:
                return 9;
            case PROTOSS_DARK_TEMPLAR:
                return 9;
            case ZERG_HYDRALISK_BURROWED:
                return 5;
            case ZERG_QUEEN_BURROWED:
                return 2;
            case ZERG_INFESTOR:
                return 7;
            case ZERG_INFESTOR_BURROWED:
                return 7;
            case ZERG_LURKER_MP:
                return 8;
            case ZERG_LURKER_MP_BURROWED:
                return 8;
            case ZERG_ZERGLING:
                return 5;
            case ZERG_ZERGLING_BURROWED:
                return 5;
            case ZERG_BANELING:
                return 20;
            case ZERG_BANELING_BURROWED:
                return 8;
            case ZERG_BANELING_COCOON:
                return 1;
            case ZERG_RAVAGER:
                return 6;
            case ZERG_RAVAGER_COCOON:
                return 1;
            case ZERG_ULTRALISK:
                return 3;
            case ZERG_SWARM_HOST_MP:
                return 4;
            case ZERG_SWARM_HOST_BURROWED_MP:
                return 3;
            case ZERG_LOCUS_TMP:
                return 1;
        }
    }

    private float getKitingBuffer(Unit enemy) {
        return !UnitUtils.canMove(enemy.getType()) ? 0.5F : 2.5F;
    }

    private float getDetectionRange(Unit enemy) {
        float range = enemy.getDetectRange().orElse(0.0F);
        if (range == 0.0F) {
            switch ((Units) enemy.getType()) {
                case TERRAN_MISSILE_TURRET:
                case PROTOSS_PHOTON_CANNON:
                case ZERG_SPORE_CRAWLER:
                    range = 11.0F;
                    break;
                case PROTOSS_OBSERVER:
                    range = 11.0F;
            }
        }

        return range + enemy.getRadius();
    }

    private void calcMaxRange() {
        if (!UnitUtils.getEnemyUnitsOfType(Units.PROTOSS_TEMPEST).isEmpty()) {
            this.maxRange = 17.5F;
        } else {
            this.maxRange = Math.max(this.airAttackRange + 2.0F, this.groundAttackRange);
            this.maxRange = Math.max(this.maxRange, this.detectRange);
            if (this.isAir) {
                this.maxRange = Math.max(this.maxRange, 9.1F);
            } else if (this.isDetector) {
                this.maxRange = Math.max(this.maxRange, 6.1F);
            } else {
                this.maxRange = Math.max(this.maxRange, 13.0F);
            }

        }
    }
}

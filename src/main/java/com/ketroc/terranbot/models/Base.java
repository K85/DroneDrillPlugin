package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.*;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.purchases.PurchaseStructure;

import java.util.*;
import java.util.stream.Collectors;

public class Base {
    private static float libDistanceFromCC = -1.0F;
    public long lastScoutedFrame;
    public boolean isEnemyBase;
    private boolean isDryedUp;
    private Point2d ccPos;
    private UnitInPool cc;
    private List<Gas> gases = new ArrayList();
    private List<Unit> mineralPatches = new ArrayList();
    private Unit rallyNode;
    private int extraScvs;
    private Point2d resourceMidPoint = null;
    private final List<DefenseUnitPositions> turrets = new ArrayList();
    private List<DefenseUnitPositions> liberators = new ArrayList();
    private List<DefenseUnitPositions> tanks = new ArrayList();
    private boolean continueUnsieging;
    private boolean onMyBaseDeath;

    public Base(Point2d ccPos) {
        this.ccPos = ccPos;
    }

    public static float getLibDistanceFromCC() {
        if (libDistanceFromCC == -1.0F || LocationConstants.opponentRace == Race.RANDOM) {
            libDistanceFromCC = LocationConstants.opponentRace == Race.PROTOSS ? 1.0F : 2.5F;
        }

        return libDistanceFromCC;
    }

    public static int totalMineralPatchesForMyBases() {
        return GameCache.baseList.stream().filter((base) -> {
            return base.isMyBase();
        }).mapToInt(Base::numActiveMineralPatches).sum();
    }

    public static int totalActiveRefineriesForMyBases() {
        return GameCache.baseList.stream().filter((base) -> {
            return base.isMyBase();
        }).mapToInt(Base::numActiveRefineries).sum();
    }

    public static int totalScvsRequiredForMyBases() {
        return totalActiveRefineriesForMyBases() * 3 + totalMineralPatchesForMyBases() * 2;
    }

    public static int numMyBases() {
        return (int) GameCache.baseList.stream().filter((base) -> {
            return base.isMyBase();
        }).count();
    }

    public List<Unit> getMineralPatches() {
        return this.mineralPatches;
    }

    public void setMineralPatches(List<Unit> mineralPatches) {
        this.mineralPatches = mineralPatches;
    }

    public Optional<UnitInPool> getCc() {
        return Optional.ofNullable(this.cc);
    }

    public void setCc(UnitInPool cc) {
        if (this.cc != null && cc == null) {
            this.continueUnsieging = true;
            this.onMyBaseDeath = true;
        } else if (this.cc == null && cc != null) {
            this.continueUnsieging = false;
        }

        this.cc = cc;
    }

    public Point2d getCcPos() {
        return this.ccPos;
    }

    public void setCcPos(Point2d ccPos) {
        this.ccPos = ccPos;
    }

    public List<Gas> getGases() {
        return this.gases;
    }

    public void setGases(List<Gas> gases) {
        this.gases = gases;
    }

    public Unit getRallyNode() {
        return this.rallyNode;
    }

    public void setRallyNode(Unit rallyNode) {
        this.rallyNode = rallyNode;
    }

    public List<DefenseUnitPositions> getTurrets() {
        if (this.turrets.isEmpty() && !this.isMyMainBase()) {
            this.turrets.add(new DefenseUnitPositions(Position.moveClearExactly(this.getResourceMidPoint(), this.ccPos, 3.5F), null));
            this.turrets.add(new DefenseUnitPositions(Position.moveClearExactly(Position.rotate(this.getResourceMidPoint(), this.ccPos, 110.0D), this.ccPos, 3.5F), null));
            this.turrets.add(new DefenseUnitPositions(Position.moveClearExactly(Position.rotate(this.getResourceMidPoint(), this.ccPos, -110.0D), this.ccPos, 3.5F), null));
        }

        return this.turrets;
    }

    public int getExtraScvs() {
        return this.extraScvs;
    }

    public void setExtraScvs(int extraScvs) {
        this.extraScvs = extraScvs;
    }

    public boolean isDryedUp() {
        return this.isDryedUp;
    }

    public void setDryedUp(boolean dryedUp) {
        if (dryedUp && !this.isDryedUp) {
            this.continueUnsieging = true;
        }

        this.isDryedUp = dryedUp;
    }

    public List<DefenseUnitPositions> getLiberators() {
        if (this.liberators.isEmpty()) {
            Point2d resourceMidPoint = this.getResourceMidPoint();
            if (resourceMidPoint != null) {
                Point2d midPoint = Position.towards(this.ccPos, resourceMidPoint, getLibDistanceFromCC());
                this.liberators.add(new DefenseUnitPositions(Position.rotate(midPoint, this.ccPos, 32.5D), null));
                this.liberators.add(new DefenseUnitPositions(Position.rotate(midPoint, this.ccPos, -32.5D), null));
            }
        }

        return this.liberators;
    }

    public void setLiberators(List<DefenseUnitPositions> liberators) {
        this.liberators = liberators;
    }

    public List<DefenseUnitPositions> getTanks() {
        if (this.tanks.isEmpty()) {
            Point2d resourceMidPoint = this.getResourceMidPoint();
            if (resourceMidPoint != null) {
                Point2d midPoint = Position.towards(this.ccPos, resourceMidPoint, 4.3F);
                this.tanks.add(new DefenseUnitPositions(Position.rotate(midPoint, this.ccPos, 45.0D), null));
                this.tanks.add(new DefenseUnitPositions(Position.rotate(midPoint, this.ccPos, -45.0D), null));
            }
        }

        return this.tanks;
    }

    public void setTanks(List<DefenseUnitPositions> tanks) {
        this.tanks = tanks;
    }

    public void onStep() {
        if (this.onMyBaseDeath) {
            this.onMyBaseDeath();
            this.onMyBaseDeath = false;
        }

        if (this.continueUnsieging && !InfluenceMaps.getValue(InfluenceMaps.pointGroundUnitWithin13, this.ccPos)) {
            this.unsiegeBase();
            this.continueUnsieging = false;
        }

    }

    public List<UnitInPool> getAvailableGeysers() {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, (u) -> {
            return this.ccPos.distance(u.unit().getPosition().toPoint2d()) < 10.0D && UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType());
        });
    }

    public boolean isComplete() {
        return this.isComplete(1.0F);
    }

    public boolean isComplete(float percentageDone) {
        return this.cc != null && this.cc.unit().getType() != Units.TERRAN_COMMAND_CENTER_FLYING && this.cc.unit().getBuildProgress() >= percentageDone && UnitUtils.getDistance(this.cc.unit(), this.ccPos) < 1.0F;
    }

    public boolean isMyBase() {
        return this.cc != null && this.cc.unit().getAlliance() == Alliance.SELF;
    }

    public boolean isUntakenBase() {
        return this.cc == null && !this.isEnemyBase && StructureScv.scvBuildingList.stream().noneMatch((scv) -> {
            return scv.structurePos.distance(this.ccPos) < 1.0D;
        });
    }

    public int numActiveRefineries() {
        return (int) this.gases.stream().filter((gas) -> {
            return gas.getRefinery() != null && gas.getRefinery().getVespeneContents().orElse(0) > 80;
        }).count();
    }

    public int numActiveMineralPatches() {
        return (int) this.mineralPatches.stream().filter((patch) -> {
            return patch.getMineralContents().orElse(0) > 100;
        }).count();
    }

    public List<UnitInPool> getAllTanks() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.SIEGE_TANK_TYPE, this.ccPos, 10.0F);
    }

    public List<UnitInPool> getUnsiegedTanks() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SIEGE_TANK, this.ccPos, 10.0F);
    }

    public List<UnitInPool> getSiegedTanks() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_SIEGE_TANK_SIEGED, this.ccPos, 10.0F);
    }

    public List<UnitInPool> getAllLibs() {
        return UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.LIBERATOR_TYPE, this.ccPos, 10.0F);
    }

    public List<Unit> getUnsiegedLibs() {
        List<UnitInPool> idleLibs = Bot.OBS.getUnits(Alliance.SELF, (u) -> {
            return u.unit().getType() == Units.TERRAN_LIBERATOR && u.unit().getPosition().toPoint2d().distance(this.ccPos) < 4.0D && (u.unit().getOrders().isEmpty() || u.unit().getOrders().get(0).getAbility() != Abilities.ATTACK);
        });
        return UnitUtils.toUnitList(idleLibs);
    }

    public List<Unit> getSiegedLibs() {
        return UnitUtils.toUnitList(UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Units.TERRAN_LIBERATOR_AG, this.ccPos, 10.0F));
    }

    public List<Unit> getLargeMinerals() {
        return this.mineralPatches.stream().filter((node) -> {
            return UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType());
        }).collect(Collectors.toList());
    }

    public List<Point2d> initTankPositions() {
        return null;
    }

    public List<Unit> getOuterBigPatches() {
        List<Unit> minMaxNodes = new ArrayList(List.of(this.mineralPatches.stream().max(Comparator.comparing((node) -> {
            return node.getPosition().getX();
        })).get(), this.mineralPatches.stream().max(Comparator.comparing((node) -> {
            return node.getPosition().getY();
        })).get(), this.mineralPatches.stream().min(Comparator.comparing((node) -> {
            return node.getPosition().getX();
        })).get(), this.mineralPatches.stream().min(Comparator.comparing((node) -> {
            return node.getPosition().getY();
        })).get()));
        minMaxNodes.removeIf((node) -> {
            return !UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType());
        });
        if (minMaxNodes.size() != 2) {
            System.out.println("found more than 2 outer patches");
            minMaxNodes.stream().forEach((unit) -> {
                System.out.println(unit.getPosition().toPoint2d());
            });
        }

        return minMaxNodes;
    }

    public List<Unit> getInnerBigPatches() {
        List<Unit> bigPatches = new ArrayList(this.mineralPatches);
        bigPatches.removeIf((node) -> {
            return !UnitUtils.MINERAL_NODE_TYPE_LARGE.contains(node.getType());
        });
        Point2d p = Point2d.of(0.0F, 0.0F);

        Unit patch;
        for (Iterator var3 = bigPatches.iterator(); var3.hasNext(); p = p.add(patch.getPosition().toPoint2d())) {
            patch = (Unit) var3.next();
        }

        Point2d midPoint = p.div((float) bigPatches.size());
        bigPatches.sort(Comparator.comparing((node) -> {
            return UnitUtils.getDistance(node, midPoint);
        }));
        return bigPatches.subList(0, 2);
    }

    public Point2d getResourceMidPoint() {
        if (this.resourceMidPoint == null) {
            List<Unit> resourceNodes = new ArrayList();
            resourceNodes.addAll(this.mineralPatches);
            resourceNodes.addAll(this.gases.stream().map((gas) -> {
                return gas.getGeyser();
            }).collect(Collectors.toList()));
            if (resourceNodes.isEmpty()) {
                return null;
            }

            this.resourceMidPoint = Position.towards(this.ccPos, Position.midPointUnitsWeighted(resourceNodes), 4.25F);
        }

        return this.resourceMidPoint;
    }

    public UnitInPool getUpdatedUnit(Units unitType, Optional<UnitInPool> unit, Point2d pos) {
        if (unit.isEmpty()) {
            UnitInPool newUnit;
            if (UnitUtils.COMMAND_CENTER_TYPE.contains(unitType)) {
                newUnit = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, UnitUtils.COMMAND_CENTER_TYPE, pos, 1.0F).stream().filter((cc) -> {
                    return !(Boolean) cc.unit().getFlying().orElse(true);
                }).findFirst().orElse(null);
            } else {
                newUnit = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, unitType, pos, 1.0F).stream().findFirst().orElse(null);
            }

            return newUnit;
        } else {
            return !unit.get().isAlive() ? null : unit.get();
        }
    }

    public boolean isMyMainBase() {
        return this.equals(GameCache.baseList.get(0));
    }

    public void unsiegeBase() {
        this.freeUpLiberators();
        this.freeUpTanks();
    }

    public void onMyBaseDeath() {
        List<Unit> baseScvs = UnitUtils.toUnitList(UnitUtils.getUnitsNearbyOfType(Alliance.SELF, Set.of(Units.TERRAN_SCV, Units.TERRAN_MULE), this.ccPos, 7.0F));
        Unit mineralPatch = UnitUtils.getSafestMineralPatch();
        List<Unit> scvsCarrying = baseScvs.stream().filter((unit) -> {
            return UnitUtils.isCarryingResources(unit);
        }).collect(Collectors.toList());
        if (!scvsCarrying.isEmpty()) {
            Bot.ACTION.unitCommand(scvsCarrying, Abilities.HARVEST_RETURN, false);
            if (mineralPatch == null) {
                Bot.ACTION.unitCommand(scvsCarrying, Abilities.STOP, true);
            } else {
                Bot.ACTION.unitCommand(scvsCarrying, Abilities.SMART, mineralPatch, true);
            }
        }

        List<Unit> scvsNotCarrying = baseScvs.stream().filter((unit) -> {
            return !UnitUtils.isCarryingResources(unit);
        }).collect(Collectors.toList());
        if (!scvsNotCarrying.isEmpty()) {
            if (mineralPatch == null) {
                Bot.ACTION.unitCommand(scvsNotCarrying, Abilities.STOP, false);
            } else {
                Bot.ACTION.unitCommand(scvsNotCarrying, Abilities.SMART, mineralPatch, false);
            }
        }

        for (int i = 0; i < StructureScv.scvBuildingList.size(); ++i) {
            StructureScv scv = StructureScv.scvBuildingList.get(i);
            if ((scv.structureType == Units.TERRAN_MISSILE_TURRET || scv.structureType == Units.TERRAN_REFINERY) && scv.structurePos.distance(this.ccPos) < 10.0D) {
                scv.cancelProduction();
                StructureScv.remove(scv);
                --i;
            }
        }

        BansheeBot.purchaseQueue.removeIf((p) -> {
            return p instanceof PurchaseStructure && ((PurchaseStructure) p).getStructureType() == Units.TERRAN_MISSILE_TURRET && ((PurchaseStructure) p).getPosition().distance(this.ccPos) < 10.0D;
        });
    }

    private void freeUpLiberators() {
        Iterator var1 = this.getLiberators().iterator();

        while (var1.hasNext()) {
            DefenseUnitPositions libPos = (DefenseUnitPositions) var1.next();
            if (libPos.getUnit().isPresent()) {
                Unit baseLib = libPos.getUnit().get().unit();
                if (baseLib.getType() == Units.TERRAN_LIBERATOR_AG) {
                    Bot.ACTION.unitCommand(baseLib, Abilities.MORPH_LIBERATOR_AA_MODE, false);
                } else {
                    Bot.ACTION.unitCommand(baseLib, Abilities.STOP, false);
                }

                libPos.setUnit(null);
            }
        }

    }

    private void freeUpTanks() {
        Iterator var1 = this.getTanks().iterator();

        while (var1.hasNext()) {
            DefenseUnitPositions tankPos = (DefenseUnitPositions) var1.next();
            if (tankPos.getUnit().isPresent()) {
                Unit tank = tankPos.getUnit().get().unit();
                if (tank.getType() == Units.TERRAN_SIEGE_TANK_SIEGED) {
                    Bot.ACTION.unitCommand(tank, Abilities.MORPH_UNSIEGE, false);
                } else {
                    Bot.ACTION.unitCommand(tank, Abilities.STOP, false);
                }

                tankPos.setUnit(null);
            }
        }

    }
}

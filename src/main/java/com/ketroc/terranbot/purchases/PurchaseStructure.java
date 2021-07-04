package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.InfluenceMaps;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.BuildManager;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.models.Base;
import com.ketroc.terranbot.models.Cost;
import com.ketroc.terranbot.models.Gas;
import com.ketroc.terranbot.models.StructureScv;

import java.io.PrintStream;
import java.util.*;

public class PurchaseStructure implements Purchase {
    public static final Map<Units, Abilities> structureToActionMap;

    static {
        Hashtable<Units, Abilities> tmp = new Hashtable();
        tmp.put(Units.TERRAN_BARRACKS, Abilities.BUILD_BARRACKS);
        tmp.put(Units.TERRAN_COMMAND_CENTER, Abilities.BUILD_COMMAND_CENTER);
        tmp.put(Units.TERRAN_SUPPLY_DEPOT, Abilities.BUILD_SUPPLY_DEPOT);
        tmp.put(Units.TERRAN_BUNKER, Abilities.BUILD_BUNKER);
        tmp.put(Units.TERRAN_ARMORY, Abilities.BUILD_ARMORY);
        tmp.put(Units.TERRAN_ENGINEERING_BAY, Abilities.BUILD_ENGINEERING_BAY);
        tmp.put(Units.TERRAN_FACTORY, Abilities.BUILD_FACTORY);
        tmp.put(Units.TERRAN_FUSION_CORE, Abilities.BUILD_FUSION_CORE);
        tmp.put(Units.TERRAN_GHOST_ACADEMY, Abilities.BUILD_GHOST_ACADEMY);
        tmp.put(Units.TERRAN_MISSILE_TURRET, Abilities.BUILD_MISSILE_TURRET);
        tmp.put(Units.TERRAN_REFINERY, Abilities.BUILD_REFINERY);
        tmp.put(Units.TERRAN_SENSOR_TOWER, Abilities.BUILD_SENSOR_TOWER);
        tmp.put(Units.TERRAN_STARPORT, Abilities.BUILD_STARPORT);
        structureToActionMap = Collections.unmodifiableMap(tmp);
    }

    private Unit scv;
    private Units structureType;
    private Cost cost;
    private final UnitTypeData structureData;
    private Unit rallyUnit;
    private Point2d position;
    private boolean isPositionImportant;
    private Point2d rallyPosition;

    public PurchaseStructure(Units structureType, Unit rallyUnit, Point2d position) {
        this(null, structureType, rallyUnit, position, true);
    }

    public PurchaseStructure(Units structureType, Point2d position) {
        this(null, structureType, null, position, true);
    }

    public PurchaseStructure(Units structureType, Unit rallyUnit) {
        this(null, structureType, rallyUnit, null, false);
    }

    public PurchaseStructure(Units structureType) {
        this(null, structureType, null, null, false);
    }

    public PurchaseStructure(Unit scv, Units structureType, Point2d position) {
        this(scv, structureType, null, position, true);
    }

    public PurchaseStructure(Unit scv, Units structureType, Unit rallyUnit) {
        this(scv, structureType, rallyUnit, null, false);
    }

    public PurchaseStructure(Unit scv, Units structureType) {
        this(scv, structureType, null, null, false);
    }

    public PurchaseStructure(Unit scv, Units structureType, Unit rallyUnit, Point2d position) {
        this(scv, structureType, rallyUnit, position, true);
    }

    public PurchaseStructure(Unit scv, Units structureType, Unit rallyUnit, Point2d position, boolean isPositionImportant) {
        this.scv = scv;
        this.structureType = structureType;
        this.rallyUnit = rallyUnit;
        this.position = position;
        this.isPositionImportant = isPositionImportant;
        this.structureData = Bot.OBS.getUnitTypeData(false).get(this.structureType);
        this.setCost();
        System.out.println("Added to queue: " + this.structureType);
    }

    public static int countUnitType(Units unitType) {
        int numUnitType = UnitUtils.getNumUnits(unitType, false);
        switch (unitType) {
            case TERRAN_SUPPLY_DEPOT:
                numUnitType += UnitUtils.getNumUnits(Units.TERRAN_SUPPLY_DEPOT_LOWERED, false);
                break;
            case TERRAN_STARPORT:
                numUnitType += UnitUtils.getNumUnits(Units.TERRAN_STARPORT_FLYING, false);
                break;
            case TERRAN_COMMAND_CENTER:
                numUnitType += UnitUtils.getNumUnits(Units.TERRAN_ORBITAL_COMMAND, false);
                numUnitType += UnitUtils.getNumUnits(Units.TERRAN_PLANETARY_FORTRESS, false);
                break;
            case TERRAN_BARRACKS:
                numUnitType += UnitUtils.getNumUnits(Units.TERRAN_BARRACKS_FLYING, false);
            case TERRAN_ENGINEERING_BAY:
            case TERRAN_ARMORY:
            default:
                break;
            case TERRAN_FACTORY:
                numUnitType += UnitUtils.getNumUnits(Units.TERRAN_FACTORY_FLYING, false);
        }

        return numUnitType;
    }

    public static Unit getGeyserUnitAtPosition(Point2d location) {
        return Bot.OBS.getUnits(Alliance.NEUTRAL, (u) -> {
            return UnitUtils.GAS_GEYSER_TYPE.contains(u.unit().getType()) && u.unit().getPosition().toPoint2d().distance(location) < 1.0D;
        }).get(0).unit();
    }

    public Unit getScv() {
        return this.scv;
    }

    public void setScv(Unit scv) {
        this.scv = scv;
    }

    public Units getStructureType() {
        return this.structureType;
    }

    public void setStructureType(Units structureType) {
        this.structureType = structureType;
    }

    public Unit getRallyUnit() {
        return this.rallyUnit;
    }

    public void setRallyUnit(Units rallyUnit) {
    }

    public void setRallyUnit(Unit rallyUnit) {
        this.rallyUnit = rallyUnit;
    }

    public Point2d getPosition() {
        return this.position;
    }

    public void setPosition(Point2d position) {
        this.position = position;
    }

    public boolean isPositionImportant() {
        return this.isPositionImportant;
    }

    public void setPositionImportant(boolean positionImportant) {
        this.isPositionImportant = positionImportant;
    }

    public Cost getCost() {
        return this.cost;
    }

    public PurchaseResult build() {
        if (!this.canAfford()) {
            Cost.updateBank(this.cost);
            return PurchaseResult.WAITING;
        } else if (this.structureData.getTechRequirement().isPresent() && countUnitType((Units) this.structureData.getTechRequirement().get()) <= 0) {
            Cost.updateBank(this.cost);
            return PurchaseResult.WAITING;
        } else {
            return this.structureData.getAbility().get() == Abilities.BUILD_REFINERY ? this.buildRefinery() : this.buildOther();
        }
    }

    public PurchaseResult buildOther() {
        if (this.position == null && !this.selectStructurePosition()) {
            System.out.println("cancelled " + this.structureType + " because no position set");
            return PurchaseResult.CANCEL;
        } else if (!UnitUtils.getUnitsNearbyOfType(Alliance.SELF, this.structureType, this.position, 1.0F).isEmpty()) {
            return PurchaseResult.SUCCESS;
        } else if (this.structureType == Units.TERRAN_STARPORT && Bot.OBS.getFoodUsed() <= 197 && GameCache.starportList.stream().anyMatch((u) -> {
            return u.unit().getOrders().isEmpty();
        })) {
            this.makePositionAvailableAgain(this.position);
            return PurchaseResult.CANCEL;
        } else {
            Abilities buildAction = (Abilities) this.structureData.getAbility().get();
            if (!BuildManager.isPlaceable(this.position, buildAction)) {
                if (Bot.OBS.getUnits((u) -> {
                    return this.position.distance(u.unit().getPosition().toPoint2d()) < (double) BuildManager.getStructureRadius(buildAction) && !UnitUtils.canMove(u.unit().getType());
                }).isEmpty() && !Bot.OBS.hasCreep(this.position)) {
                    return PurchaseResult.WAITING;
                } else {
                    this.makePositionAvailableAgain(this.position);
                    return PurchaseResult.CANCEL;
                }
            } else {
                if (this.scv == null) {
                    List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(this.position);
                    if (availableScvs.isEmpty()) {
                        System.out.println("cancelled " + this.structureType + " because no scv available");
                        this.makePositionAvailableAgain(this.position);
                        return PurchaseResult.CANCEL;
                    }

                    this.scv = availableScvs.get(0).unit();
                }

                System.out.println("sending action @" + Bot.OBS.getGameLoop() + buildAction + " at " + this.position.toString());
                Bot.ACTION.unitCommand(this.scv, buildAction, this.position, false);
                StructureScv.add(new StructureScv(Bot.OBS.getUnit(this.scv.getTag()), buildAction, this.position));
                Cost.updateBank(this.structureType);
                return PurchaseResult.SUCCESS;
            }
        }
    }

    public PurchaseResult buildRefinery() {
        Abilities buildAction = (Abilities) this.structureData.getAbility().get();
        Iterator var2 = GameCache.baseList.iterator();

        while (true) {
            Base base;
            do {
                do {
                    if (!var2.hasNext()) {
                        return PurchaseResult.CANCEL;
                    }

                    base = (Base) var2.next();
                } while (!base.isMyBase());
            } while (!base.isComplete(0.6F));

            Iterator var4 = base.getGases().iterator();

            while (var4.hasNext()) {
                Gas gas = (Gas) var4.next();
                if (gas.getRefinery() == null && gas.getGeyser().getVespeneContents().orElse(0) > 1 && StructureScv.scvBuildingList.stream().noneMatch((scv) -> {
                    return scv.buildAbility == Abilities.BUILD_REFINERY && scv.structurePos.distance(gas.getLocation()) < 1.0D;
                })) {
                    this.position = gas.getLocation();
                    List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(this.position);
                    if (availableScvs.isEmpty()) {
                        return PurchaseResult.WAITING;
                    }

                    this.scv = availableScvs.get(0).unit();
                    gas.setGeyser(getGeyserUnitAtPosition(gas.getLocation()));
                    PrintStream var10000 = System.out;
                    long var10001 = Bot.OBS.getGameLoop();
                    var10000.println("sending action @" + var10001 + Abilities.BUILD_REFINERY);
                    Bot.ACTION.unitCommand(this.scv, Abilities.BUILD_REFINERY, gas.getGeyser(), false);
                    StructureScv.add(new StructureScv(Bot.OBS.getUnit(this.scv.getTag()), buildAction, Bot.OBS.getUnit(gas.getGeyser().getTag())));
                    Cost.updateBank(Units.TERRAN_REFINERY);
                    return PurchaseResult.SUCCESS;
                }
            }
        }
    }

    private void selectARallyUnit() {
        if (this.rallyUnit == null) {
            if (this.scv.getOrders().isEmpty()) {
                this.rallyUnit = GameCache.defaultRallyNode;
            } else {
                this.rallyUnit = Bot.OBS.getUnit(this.scv.getOrders().get(0).getTargetedUnitTag().get()).unit();
            }
        }

    }

    private void makePositionAvailableAgain(Point2d pos) {
        switch (this.structureType) {
            case TERRAN_SUPPLY_DEPOT:
                LocationConstants.extraDepots.add(pos);
                break;
            case TERRAN_STARPORT:
                LocationConstants.STARPORTS.add(pos);
                break;
            case TERRAN_COMMAND_CENTER:
                if (LocationConstants.baseLocations.contains(pos)) {
                    return;
                }

                LocationConstants.MACRO_OCS.add(pos);
                break;
            case TERRAN_BARRACKS:
            case TERRAN_ENGINEERING_BAY:
            case TERRAN_ARMORY:
                LocationConstants._3x3Structures.add(pos);
        }

    }

    private boolean selectStructurePosition() {
        switch (this.structureType) {
            case TERRAN_SUPPLY_DEPOT:
                if (!LocationConstants.extraDepots.isEmpty()) {
                    this.position = LocationConstants.extraDepots.stream().filter((p) -> {
                        return this.isLocationSafeAndAvailable(p, Abilities.BUILD_SUPPLY_DEPOT);
                    }).findFirst().orElse(null);
                    if (this.position != null) {
                        LocationConstants.extraDepots.remove(this.position);
                        return true;
                    }
                }

                return false;
            case TERRAN_STARPORT:
                if (!LocationConstants.STARPORTS.isEmpty()) {
                    this.position = LocationConstants.STARPORTS.stream().filter((p) -> {
                        return this.isLocationSafeAndAvailable(p, Abilities.BUILD_STARPORT);
                    }).findFirst().orElse(null);
                    if (this.position != null) {
                        LocationConstants.STARPORTS.remove(this.position);
                        return true;
                    }
                }

                return false;
            case TERRAN_COMMAND_CENTER:
                Iterator var1 = GameCache.baseList.iterator();

                Base base;
                do {
                    if (!var1.hasNext()) {
                        return false;
                    }

                    base = (Base) var1.next();
                } while (!base.isUntakenBase() || !this.isLocationSafeAndAvailable(base.getCcPos(), Abilities.BUILD_COMMAND_CENTER));

                this.position = base.getCcPos();
                return true;
            case TERRAN_BARRACKS:
            case TERRAN_ENGINEERING_BAY:
            case TERRAN_ARMORY:
                this.position = LocationConstants._3x3Structures.stream().filter((p) -> {
                    return this.isLocationSafeAndAvailable(p, Bot.OBS.getUnitTypeData(false).get(this.structureType).getAbility().get());
                }).findFirst().orElse(null);
                if (this.position != null) {
                    LocationConstants._3x3Structures.remove(this.position);
                    return true;
                }

                return false;
            default:
                return false;
        }
    }

    private boolean isLocationSafeAndAvailable(Point2d p, Ability buildAbility) {
        return InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, p) == 0.0F && Bot.QUERY.placement(buildAbility, p);
    }

    public void setCost() {
        this.cost = Cost.getUnitCost(this.structureType);
    }

    public boolean canAfford() {
        return GameCache.mineralBank >= this.cost.minerals && GameCache.gasBank >= this.cost.gas;
    }

    public String getType() {
        return this.structureType.toString();
    }
}

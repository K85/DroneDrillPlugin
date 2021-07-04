package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.Race;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Alliance;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.InfluenceMaps;
import com.ketroc.terranbot.LocationConstants;
import com.ketroc.terranbot.UnitUtils;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.managers.WorkerManager;
import com.ketroc.terranbot.purchases.PurchaseStructure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StructureScv {
    public static final List<StructureScv> scvBuildingList = new ArrayList();
    public Point2d structurePos;
    public boolean isGas;
    public UnitInPool gasGeyser;
    public Abilities buildAbility;
    public Units structureType;
    public UnitInPool scv;
    private UnitInPool structureUnit;

    public StructureScv(UnitInPool scv, Abilities buildAbility, Point2d structurePos) {
        this.scv = scv;
        this.buildAbility = buildAbility;
        this.structureType = Bot.abilityToUnitType.get(buildAbility);
        this.structurePos = structurePos;
    }

    public StructureScv(UnitInPool scv, Abilities buildAbility, UnitInPool gasGeyser) {
        this.scv = scv;
        this.buildAbility = buildAbility;
        this.structureType = Bot.abilityToUnitType.get(buildAbility);
        this.isGas = true;
        this.gasGeyser = gasGeyser;
        this.structurePos = gasGeyser.unit().getPosition().toPoint2d();
    }

    public static boolean removeScvFromList(Unit structure) {
        for (int i = 0; i < scvBuildingList.size(); ++i) {
            StructureScv scv = scvBuildingList.get(i);
            if (scv.structureType == structure.getType() && scv.structurePos.distance(structure.getPosition().toPoint2d()) < 1.0D) {
                remove(scv);
                return true;
            }
        }

        return false;
    }

    public static boolean cancelProduction(Units type, Point2d pos) {
        for (int i = 0; i < scvBuildingList.size(); ++i) {
            StructureScv scv = scvBuildingList.get(i);
            if (scv.structureType == type && scv.structurePos.distance(pos) < 1.0D) {
                scv.cancelProduction();
                remove(scv);
                return true;
            }
        }

        return false;
    }

    public static void checkScvsActivelyBuilding() {
        for (int i = 0; i < scvBuildingList.size(); ++i) {
            StructureScv structureScv = scvBuildingList.get(i);
            if (!structureScv.scv.isAlive()) {
                List<UnitInPool> availableScvs = WorkerManager.getAvailableScvs(structureScv.structurePos);
                if (!availableScvs.isEmpty()) {
                    structureScv.addScv(availableScvs.get(0));
                }
            }

            if (structureScv.scv.unit().getOrders().isEmpty() || !structureScv.scv.unit().getOrders().stream().anyMatch((order) -> {
                return order.getAbility() == structureScv.buildAbility;
            })) {
                UnitInPool structure = structureScv.getStructureUnit();
                if (structure != null && structure.isAlive()) {
                    if (structure.unit().getBuildProgress() < 1.0F) {
                        if (isDuplicateStructureScv(structureScv)) {
                            scvBuildingList.remove(i--);
                        } else {
                            Bot.ACTION.unitCommand(structureScv.scv.unit(), Abilities.SMART, structure.unit(), false);
                        }
                    } else if (structure.unit().getBuildProgress() == 1.0F) {
                        remove(structureScv);
                        --i;
                    }
                } else {
                    if (LocationConstants.opponentRace == Race.ZERG && structureScv.scv.isAlive() && structureScv.structureType == Units.TERRAN_COMMAND_CENTER && UnitUtils.getDistance(structureScv.scv.unit(), structureScv.structurePos) < 5.0F) {
                        int blockedBaseIndex = LocationConstants.baseLocations.indexOf(structureScv.structurePos);
                        if (blockedBaseIndex > 0) {
                            LocationConstants.baseAttackIndex = blockedBaseIndex;
                            System.out.println("blocked base.  set baseIndex to " + blockedBaseIndex);
                        }
                    }

                    if (!(InfluenceMaps.getValue(InfluenceMaps.pointThreatToGround, structureScv.structurePos) > 0.0F) && !UnitUtils.wallUnderAttack()) {
                        Cost.updateBank(structureScv.structureType);
                        if (Bot.QUERY.placement(structureScv.buildAbility, structureScv.structurePos)) {
                            Bot.ACTION.unitCommand(structureScv.scv.unit(), structureScv.buildAbility, structureScv.structurePos, false);
                        }
                    } else {
                        requeueCancelledStructure(structureScv);
                        remove(structureScv);
                        --i;
                    }
                }
            }
        }

    }

    private static void requeueCancelledStructure(StructureScv structureScv) {
        switch (structureScv.structureType) {
            case TERRAN_COMMAND_CENTER:
            case TERRAN_REFINERY:
            case TERRAN_REFINERY_RICH:
            case TERRAN_BUNKER:
                break;
            case TERRAN_SUPPLY_DEPOT:
                LocationConstants.extraDepots.add(structureScv.structurePos);
                break;
            case TERRAN_STARPORT:
                LocationConstants.STARPORTS.add(structureScv.structurePos);
                break;
            case TERRAN_BARRACKS:
                if (structureScv.structurePos.distance(LocationConstants.proxyBarracksPos) > 10.0D) {
                    LocationConstants._3x3Structures.add(structureScv.structurePos);
                }

                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType));
                break;
            case TERRAN_ARMORY:
            case TERRAN_ENGINEERING_BAY:
            case TERRAN_GHOST_ACADEMY:
                LocationConstants._3x3Structures.add(structureScv.structurePos);
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType));
                break;
            default:
                BansheeBot.purchaseQueue.addFirst(new PurchaseStructure(structureScv.structureType, structureScv.structurePos));
        }

    }

    public static boolean isAlreadyInProductionAt(Units type, Point2d pos) {
        return scvBuildingList.stream().anyMatch((scv) -> {
            return scv.structureType == type && scv.structurePos == pos;
        });
    }

    public static boolean isAlreadyInProduction(Units type) {
        return scvBuildingList.stream().anyMatch((scv) -> {
            return scv.structureType == type;
        });
    }

    public static boolean isScvProducing(Unit scv) {
        return scvBuildingList.stream().anyMatch((structureScv) -> {
            return structureScv.scv.getTag().equals(scv.getTag());
        });
    }

    private static boolean isDuplicateStructureScv(StructureScv structureScv) {
        Iterator var1 = scvBuildingList.iterator();

        StructureScv otherScv;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            otherScv = (StructureScv) var1.next();
        } while (structureScv.equals(otherScv) || otherScv.buildAbility != structureScv.buildAbility || !(UnitUtils.getDistance(otherScv.scv.unit(), structureScv.getStructureUnit().unit()) < 3.0F));

        return true;
    }

    public static void add(StructureScv structureScv) {
        scvBuildingList.add(structureScv);
        if (structureScv.scv != null) {
            Ignored.add(new IgnoredUnit(structureScv.scv.getTag()));
        }

    }

    public static void remove(StructureScv structureScv) {
        scvBuildingList.remove(structureScv);
        if (structureScv.scv != null) {
            Ignored.remove(structureScv.scv.getTag());
        }

    }

    public UnitInPool getStructureUnit() {
        if (this.structureUnit == null) {
            List<UnitInPool> structure = UnitUtils.getUnitsNearbyOfType(Alliance.SELF, this.structureType, this.structurePos, 1.0F);
            if (!structure.isEmpty()) {
                this.structureUnit = structure.get(0);
            }
        }

        return this.structureUnit;
    }

    public void setStructureUnit(UnitInPool structureUnit) {
        this.structureUnit = structureUnit;
    }

    public void cancelProduction() {
        if (this.getStructureUnit() != null && this.getStructureUnit().isAlive()) {
            Bot.ACTION.unitCommand(this.getStructureUnit().unit(), Abilities.CANCEL_BUILD_IN_PROGRESS, false);
        }

        if (this.scv.isAlive()) {
            Unit mineralPatch = UnitUtils.getSafestMineralPatch();
            if (mineralPatch == null) {
                Bot.ACTION.unitCommand(this.scv.unit(), Abilities.STOP, false);
            } else {
                Bot.ACTION.unitCommand(this.scv.unit(), Abilities.SMART, mineralPatch, false);
            }
        }

    }

    public void addScv(UnitInPool scv) {
        this.scv = scv;
        Ignored.add(new IgnoredUnit(scv.getTag()));
    }
}

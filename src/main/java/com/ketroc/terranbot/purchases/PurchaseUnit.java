package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Cost;

public class PurchaseUnit implements Purchase {
    private Cost cost;
    private Units unitType;
    private UnitInPool productionStructure;

    public PurchaseUnit(Units unitType, Unit productionStructure) {
        this(unitType, Bot.OBS.getUnit(productionStructure.getTag()));
    }

    public PurchaseUnit(Units unitType, UnitInPool productionStructure) {
        this.unitType = unitType;
        this.productionStructure = productionStructure;
        this.setCost();
    }

    public Cost getCost() {
        return this.cost;
    }

    public void setCost(Cost cost) {
        this.cost = cost;
    }

    public Units getUnitType() {
        return this.unitType;
    }

    public void setUnitType(Units unitType) {
        this.unitType = unitType;
    }

    public UnitInPool getProductionStructure() {
        return this.productionStructure;
    }

    public void setProductionStructure(UnitInPool productionStructure) {
        this.productionStructure = productionStructure;
    }

    public PurchaseResult build() {
        if (!this.productionStructure.isAlive()) {
            return PurchaseResult.CANCEL;
        } else if (this.canAfford()) {
            Bot.ACTION.unitCommand(this.productionStructure.unit(), Bot.OBS.getUnitTypeData(false).get(this.unitType).getAbility().get(), false);
            Cost.updateBank(this.cost);
            return PurchaseResult.SUCCESS;
        } else {
            Cost.updateBank(this.cost);
            return PurchaseResult.WAITING;
        }
    }

    public boolean canAfford() {
        return GameCache.mineralBank >= this.cost.minerals && GameCache.gasBank >= this.cost.gas;
    }

    public void setCost() {
        this.cost = Cost.getUnitCost(this.unitType);
    }

    public String getType() {
        return this.unitType.toString();
    }
}

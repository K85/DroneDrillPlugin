package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Ability;
import com.github.ocraft.s2client.protocol.data.UnitTypeData;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitOrder;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Cost;

import java.io.PrintStream;

public class PurchaseStructureMorph implements Purchase {
    private static final float CANCEL_THRESHOLD = 0.4F;
    private Ability morphOrAddOn;
    private UnitInPool structure;
    private final UnitTypeData structureData;
    private final UnitTypeData morphData;
    private Cost cost;

    public PurchaseStructureMorph(Ability morphOrAddOn, Unit structure) {
        this(morphOrAddOn, Bot.OBS.getUnit(structure.getTag()));
    }

    public PurchaseStructureMorph(Ability morphOrAddOn, UnitInPool structure) {
        this.morphOrAddOn = morphOrAddOn;
        this.structure = structure;
        this.structureData = Bot.OBS.getUnitTypeData(false).get(structure.unit().getType());
        this.morphData = Bot.OBS.getUnitTypeData(false).get(Bot.abilityToUnitType.get(morphOrAddOn));
        this.setCost();
        System.out.println("Added to queue: " + this.morphOrAddOn);
    }

    public Ability getMorphOrAddOn() {
        return this.morphOrAddOn;
    }

    public void setMorphOrAddOn(Ability morphOrAddOn) {
        this.morphOrAddOn = morphOrAddOn;
    }

    public UnitInPool getStructure() {
        return this.structure;
    }

    public void setStructure(UnitInPool structure) {
        this.structure = structure;
    }

    public Cost getCost() {
        return this.cost;
    }

    public PurchaseResult build() {
        if (!this.structure.isAlive()) {
            return PurchaseResult.CANCEL;
        } else if (this.shouldCancelPreviousOrder()) {
            System.out.println("cancelled unit");
            Bot.ACTION.unitCommand(this.structure.unit(), Abilities.CANCEL_LAST, false);
            GameCache.mineralBank += 50;
            Cost.updateBank(this.cost);
            return PurchaseResult.WAITING;
        } else if (!this.canAfford()) {
            Cost.updateBank(this.cost);
            return PurchaseResult.WAITING;
        } else if (this.structure.unit().getOrders().isEmpty()) {
            System.out.println("start building " + this.morphOrAddOn.toString());
            PrintStream var10000 = System.out;
            long var10001 = Bot.OBS.getGameLoop();
            var10000.println("sending action @" + var10001 + this.morphOrAddOn);
            Bot.ACTION.unitCommand(this.structure.unit(), this.morphOrAddOn, false);
            Cost.updateBank(this.cost);
            return PurchaseResult.SUCCESS;
        } else {
            Cost.updateBank(this.cost);
            return PurchaseResult.WAITING;
        }
    }

    private boolean shouldCancelPreviousOrder() {
        if (!this.structure.unit().getOrders().isEmpty() && this.structure.unit().getOrders().get(0).getAbility() == Abilities.TRAIN_SCV) {
            int minerals = GameCache.mineralBank;
            int gas = GameCache.gasBank;
            UnitOrder order = this.structure.unit().getOrders().get(0);
            UnitTypeData producingUnitData = Bot.OBS.getUnitTypeData(false).get(Bot.abilityToUnitType.get(order.getAbility()));
            return minerals + producingUnitData.getMineralCost().get() >= this.cost.minerals && gas + producingUnitData.getVespeneCost().get() >= this.cost.gas && order.getProgress().get() < 0.4F;
        }

        return false;
    }

    public void setCost() {
        this.cost = Cost.getUnitCost(Bot.abilityToUnitType.get(this.morphOrAddOn));
    }

    public boolean canAfford() {
        return GameCache.mineralBank >= this.cost.minerals && GameCache.gasBank >= this.cost.gas;
    }

    public String getType() {
        return this.morphOrAddOn.toString();
    }
}

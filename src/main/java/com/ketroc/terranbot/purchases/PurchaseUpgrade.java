package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.UpgradeData;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.ketroc.terranbot.GameCache;
import com.ketroc.terranbot.bots.Bot;
import com.ketroc.terranbot.models.Cost;

import java.io.PrintStream;

public class PurchaseUpgrade implements Purchase {
    private UnitInPool structure;
    private Upgrades upgrade;
    private Cost cost;

    public PurchaseUpgrade(Upgrades upgrade, UnitInPool structure) {
        this.upgrade = upgrade;
        this.structure = structure;
        this.setCost();
    }

    public UnitInPool getStructure() {
        return this.structure;
    }

    public void setStructure(UnitInPool structure) {
        this.structure = structure;
    }

    public Upgrades getUpgrade() {
        return this.upgrade;
    }

    public void setUpgrade(Upgrades upgrade) {
        this.upgrade = upgrade;
    }

    public Cost getCost() {
        return this.cost;
    }

    public PurchaseResult build() {
        if (!this.structure.isAlive()) {
            return PurchaseResult.CANCEL;
        } else if (!this.canAfford()) {
            Cost.updateBank(this.cost);
            return PurchaseResult.WAITING;
        } else if (this.structure.unit().getOrders().isEmpty()) {
            PrintStream var10000 = System.out;
            long var10001 = Bot.OBS.getGameLoop();
            var10000.println("sending action @" + var10001 + this.upgrade);
            Abilities upgradeAbility = (Abilities) Bot.OBS.getUpgradeData(false).get(this.upgrade).getAbility().orElse(Abilities.INVALID);
            switch (upgradeAbility) {
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL1_V2:
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL2_V2:
                case RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING_LEVEL3_V2:
                    upgradeAbility = Abilities.RESEARCH_TERRAN_VEHICLE_AND_SHIP_PLATING;
                default:
                    Bot.ACTION.unitCommand(this.structure.unit(), upgradeAbility, false);
                    Cost.updateBank(this.cost);
                    return PurchaseResult.SUCCESS;
            }
        } else {
            return PurchaseResult.CANCEL;
        }
    }

    public boolean canAfford() {
        return GameCache.mineralBank >= this.cost.minerals && GameCache.gasBank >= this.cost.gas;
    }

    public void setCost() {
        this.cost = Cost.getUpgradeCost(this.upgrade);
    }

    public String getType() {
        return this.upgrade.toString();
    }
}

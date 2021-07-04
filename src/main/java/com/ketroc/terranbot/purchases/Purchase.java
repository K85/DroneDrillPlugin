package com.ketroc.terranbot.purchases;

import com.github.ocraft.s2client.protocol.data.Abilities;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.data.Upgrades;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Tag;
import com.ketroc.terranbot.bots.BansheeBot;
import com.ketroc.terranbot.models.Cost;

import java.util.Iterator;

public interface Purchase {
    static boolean isMorphQueued(Abilities morphType) {
        Iterator var1 = BansheeBot.purchaseQueue.iterator();

        Purchase p;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            p = (Purchase) var1.next();
        } while (!(p instanceof PurchaseStructureMorph) || ((PurchaseStructureMorph) p).getMorphOrAddOn() != morphType);

        return true;
    }

    static boolean isUpgradeQueued(Upgrades upgrade) {
        Iterator var1 = BansheeBot.purchaseQueue.iterator();

        Purchase p;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            p = (Purchase) var1.next();
        } while (!(p instanceof PurchaseUpgrade) || ((PurchaseUpgrade) p).getUpgrade() != upgrade);

        return true;
    }

    static boolean isUpgradeQueued(Tag structureTag) {
        Iterator var1 = BansheeBot.purchaseQueue.iterator();

        Purchase p;
        do {
            if (!var1.hasNext()) {
                return false;
            }

            p = (Purchase) var1.next();
        } while (!(p instanceof PurchaseUpgrade) || ((PurchaseUpgrade) p).getStructure().unit().getTag() != structureTag);

        return true;
    }

    static boolean isStructureQueued(Units unitType) {
        return isStructureQueued(unitType, null);
    }

    static boolean isStructureQueued(Units unitType, Point2d pos) {
        Iterator var2 = BansheeBot.purchaseQueue.iterator();

        Purchase p;
        do {
            do {
                do {
                    if (!var2.hasNext()) {
                        return false;
                    }

                    p = (Purchase) var2.next();
                } while (!(p instanceof PurchaseStructure));
            } while (((PurchaseStructure) p).getStructureType() != unitType);
        } while (pos != null && !(((PurchaseStructure) p).getPosition().distance(pos) < 1.0D));

        return true;
    }

    static Point2d getPositionOfQueuedStructure(Units unitType) {
        Iterator var1 = BansheeBot.purchaseQueue.iterator();

        Purchase p;
        do {
            if (!var1.hasNext()) {
                return null;
            }

            p = (Purchase) var1.next();
        } while (!(p instanceof PurchaseStructure) || ((PurchaseStructure) p).getStructureType() != unitType);

        return ((PurchaseStructure) p).getPosition();
    }

    PurchaseResult build();

    Cost getCost();

    void setCost();

    String getType();

    boolean canAfford();
}

package com.ketroc.terranbot.strategies;

import java.util.Arrays;
import java.util.concurrent.SubmissionPublisher;

public enum MapNameTransfer {
    DEATH_AURA("死亡光环-天梯版", "Deathaura LE"),
    ICE_AND_CHROME("冰雪合金-天梯版", "Ice and Chrome LE"),
    DISCO_BLOODBATH("浴血迪斯科 - 天梯版","Disco Bloodbath LE"),
    THUNDER_BIRD("雷鸟 - 天梯版","Thunderbird LE"),
    ACROPOLIS("滨海卫城 - 天梯版","Acropolis LE"),
    EVER_DREAM("永恒梦境-天梯版","Ever Dream LE"),
    ETERNAL_EMPIRE("永恒帝国-天梯版","Eternal Empire LE"),
    TLMC12_EPHEMERON("伊菲莫隆 - 天梯版","[TLMC12] Ephemeron"),
    SUBMARINE("潜水艇 - 天梯版","Submarine LE"),
    PILLARS_OF_GOLD("黄金之柱-天梯版","Pillars of Gold LE"),
    WINTERS_GATE("黑冬隘口 - 天梯版","Winter's Gate LE"),
    GOLDEN_WALL("黄金墙-天梯版","Golden Wall LE"),
    WORLD_OF_SLEEPERS("梦境世界 - 天梯版","World of Sleepers LE"),
    TRITON("特里同 - 天梯版"," Triton LE")
    ;

    public final String fromMapName;
    public final String toMapName;


    MapNameTransfer(String fromMapName, String toMapName) {
        this.fromMapName = fromMapName;
        this.toMapName = toMapName;
    }

    @Override
    public String toString() {
        return toMapName;
    }

    public static String getTransferredMapName(String originalMapName) {
        for (MapNameTransfer mapNameTransfer : MapNameTransfer.values()) {
            if (mapNameTransfer.fromMapName.equals(originalMapName)) return mapNameTransfer.toMapName;
        }

        throw new RuntimeException("Unsupport Map [" + originalMapName +"]! The Bot Only Support Maps: " + Arrays.asList(MapNameTransfer.values()));
    }
}

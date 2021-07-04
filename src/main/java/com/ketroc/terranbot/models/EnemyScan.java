package com.ketroc.terranbot.models;

import com.github.ocraft.s2client.protocol.data.Effects;
import com.github.ocraft.s2client.protocol.observation.raw.EffectLocations;
import com.github.ocraft.s2client.protocol.observation.raw.Visibility;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.ketroc.terranbot.bots.Bot;

import java.util.HashSet;
import java.util.Set;

public class EnemyScan {
    public static final int SCAN_DURATION = 275;
    public static Set<EnemyScan> enemyScanSet = new HashSet();
    public long endTime;
    public EffectLocations scanEffect;
    public Point2d position;

    public EnemyScan(EffectLocations scanEffect) {
        this.scanEffect = scanEffect;
        this.position = scanEffect.getPositions().iterator().next();
        this.endTime = Bot.OBS.getGameLoop() + 275L;
    }

    public static void onStep() {
        enemyScanSet.removeIf((enemyScan) -> {
            return Bot.OBS.getVisibility(enemyScan.position) == Visibility.VISIBLE && Bot.OBS.getEffects().stream().noneMatch((effect) -> {
                return effect.getEffect() == Effects.SCANNER_SWEEP && enemyScan.position.distance(effect.getPositions().iterator().next()) < 1.0D;
            }) || Bot.OBS.getGameLoop() >= enemyScan.endTime;
        });
    }

    public static boolean contains(EffectLocations scanEffect) {
        return enemyScanSet.stream().anyMatch((enemyScan) -> {
            return enemyScan.scanEffect.equals(scanEffect);
        });
    }

    public static void add(EffectLocations scanEffect) {
        enemyScanSet.add(new EnemyScan(scanEffect));
    }

    public static void remove(Point2d p) {
        enemyScanSet.removeIf((enemyScan) -> {
            return enemyScan.position.equals(p);
        });
    }
}

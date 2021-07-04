package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.ObservationInterface;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.debug.Color;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class Tester {
   public static Map<Point2d, List<UnitInPool>> cluster(ObservationInterface observation, List<UnitInPool> units, double distanceApart) {
      return cluster(observation, units, distanceApart, true);
   }

   public static Map<Point2d, List<UnitInPool>> cluster(ObservationInterface observation, List<UnitInPool> units, double distanceApart, boolean isElevationResticted) {
      Map<Point2d, List<UnitInPool>> clusters = new LinkedHashMap();
      Iterator var6 = units.iterator();

      while(true) {
         label44:
         while(var6.hasNext()) {
            UnitInPool u = (UnitInPool)var6.next();
            double distance = Double.MAX_VALUE;
            Point2d unitPos = u.unit().getPosition().toPoint2d();
            Entry<Point2d, List<UnitInPool>> targetCluster = null;
            Iterator var12 = clusters.entrySet().iterator();

            while(true) {
               Entry cluster;
               double d;
               boolean isSameElevation;
               do {
                  do {
                     if (!var12.hasNext()) {
                        if (targetCluster != null && !(distance > distanceApart)) {
                           if (targetCluster.getValue() == null) {
                              targetCluster.setValue(new ArrayList());
                           }

                           ((List)targetCluster.getValue()).add(u);
                           int size = ((List)targetCluster.getValue()).size();
                           Point2d centerOfMass = ((Point2d)targetCluster.getKey()).mul((float)(size - 1)).add(unitPos).div((float)size);
                           clusters.put(centerOfMass, (List)clusters.remove(targetCluster.getKey()));
                           continue label44;
                        }

                        ArrayList<UnitInPool> unitsInCluster = new ArrayList();
                        unitsInCluster.add(u);
                        clusters.put(unitPos, unitsInCluster);
                        continue label44;
                     }

                     cluster = (Entry)var12.next();
                     d = unitPos.distance((Point2d)cluster.getKey());
                     isSameElevation = isSameElevation(observation, unitPos, (Point2d)cluster.getKey());
                  } while(!(d < distance));
               } while(isElevationResticted && !isSameElevation);

               distance = d;
               targetCluster = cluster;
            }
         }

         return clusters;
      }
   }

   public static List<Point2d> calculateExpansionLocations(ObservationInterface observation) {
      List<UnitInPool> resources = observation.getUnits((unitInPool) -> {
         Set<UnitType> nodes = new HashSet(Arrays.asList(Units.NEUTRAL_MINERAL_FIELD, Units.NEUTRAL_MINERAL_FIELD750, Units.NEUTRAL_RICH_MINERAL_FIELD, Units.NEUTRAL_RICH_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_MINERAL_FIELD750, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD, Units.NEUTRAL_PURIFIER_RICH_MINERAL_FIELD750, Units.NEUTRAL_LAB_MINERAL_FIELD, Units.NEUTRAL_LAB_MINERAL_FIELD750, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD, Units.NEUTRAL_BATTLE_STATION_MINERAL_FIELD750, Units.NEUTRAL_VESPENE_GEYSER, Units.NEUTRAL_PROTOSS_VESPENE_GEYSER, Units.NEUTRAL_SPACE_PLATFORM_GEYSER, Units.NEUTRAL_PURIFIER_VESPENE_GEYSER, Units.NEUTRAL_SHAKURAS_VESPENE_GEYSER, Units.NEUTRAL_RICH_VESPENE_GEYSER));
         return nodes.contains(unitInPool.unit().getType());
      });
      List<Point2d> expansionLocations = new ArrayList();
      Map<Point2d, List<UnitInPool>> clusters = cluster(observation, resources, 15.0D);
      Iterator var4 = clusters.entrySet().iterator();

      label96:
      while(var4.hasNext()) {
         Entry<Point2d, List<UnitInPool>> cluster = (Entry)var4.next();
         Point2d nodeMidPoint = (Point2d)cluster.getKey();
         Point2d basePos = nodeMidPoint;
         List<UnitInPool> nodes = (List)cluster.getValue();
         DebugHelper.drawBox(nodeMidPoint, Color.RED, 0.25F);
         boolean moveUp = (float)nodes.stream().filter((u) -> {
            return u.unit().getPosition().toPoint2d().getY() < nodeMidPoint.getY();
         }).count() > (float)nodes.size() / 2.0F;
         boolean moveRight = (float)nodes.stream().filter((u) -> {
            return u.unit().getPosition().toPoint2d().getX() < nodeMidPoint.getX();
         }).count() > (float)nodes.size() / 2.0F;
         Iterator var11 = ((List)cluster.getValue()).iterator();

         while(true) {
            Point2d nodePos;
            float xDistance;
            label91:
            do {
               while(var11.hasNext()) {
                  UnitInPool node = (UnitInPool)var11.next();
                  nodePos = nearestHalfPoint(node.unit().getPosition().toPoint2d());
                  if (isMineralNode(node)) {
                     if ((moveUp && nodePos.getY() < basePos.getY() || !moveUp && nodePos.getY() > basePos.getY()) && nodePos.getX() - basePos.getX() < 6.0F) {
                        xDistance = Math.abs(nodePos.getY() - basePos.getY());
                        if ((double)xDistance < 5.5D) {
                           basePos = bufferPoint(basePos, nodePos, false, 6.0F);
                        } else if ((double)xDistance < 6.5D) {
                           basePos = bufferPoint(basePos, nodePos, false, 5.0F);
                        }
                     }
                     continue label91;
                  }

                  if (Math.abs(nodePos.getX() - basePos.getX()) < 7.0F) {
                     xDistance = Math.abs(nodePos.getY() - basePos.getY());
                     if (xDistance < 6.0F) {
                        basePos = bufferPoint(basePos, nodePos, false, 7.0F);
                     } else if (xDistance < 7.0F) {
                        basePos = bufferPoint(basePos, nodePos, false, 6.0F);
                     }
                  }

                  if (Math.abs(nodePos.getY() - basePos.getY()) < 7.0F) {
                     xDistance = Math.abs(nodePos.getX() - basePos.getX());
                     if (xDistance < 6.0F) {
                        basePos = bufferPoint(basePos, nodePos, true, 7.0F);
                     } else if (xDistance < 7.0F) {
                        basePos = bufferPoint(basePos, nodePos, true, 6.0F);
                     }
                  }
               }

               expansionLocations.add(basePos);
               DebugHelper.drawBox(basePos, Color.GREEN, 2.5F);
               continue label96;
            } while((!moveRight || !(nodePos.getX() < basePos.getX())) && (moveRight || !(nodePos.getX() > basePos.getX())));

            if ((double)Math.abs(nodePos.getY() - basePos.getY()) < 6.5D) {
               xDistance = Math.abs(nodePos.getX() - basePos.getX());
               if (xDistance < 5.0F) {
                  basePos = bufferPoint(basePos, nodePos, true, 6.5F);
               } else if (xDistance < 6.0F) {
                  basePos = bufferPoint(basePos, nodePos, true, 5.5F);
               }
            }
         }
      }

      return expansionLocations;
   }

   private static Point2d bufferPoint(Point2d origin, Point2d awayFrom, boolean onXAxis, float distanceBuffer) {
      float newY;
      if (onXAxis) {
         newY = awayFrom.getX() < origin.getX() ? awayFrom.getX() + distanceBuffer : awayFrom.getX() - distanceBuffer;
         return Point2d.of(newY, origin.getY());
      } else {
         newY = awayFrom.getY() < origin.getY() ? awayFrom.getY() + distanceBuffer : awayFrom.getY() - distanceBuffer;
         return Point2d.of(origin.getX(), newY);
      }
   }

   private static Point2d nearestHalfPoint(Point2d point) {
      return Point2d.of(roundToNearestHalf(point.getX()), roundToNearestHalf(point.getY()));
   }

   private static float roundToNearestHalf(float number) {
      return (float)(Math.round(number * 2.0F) / 2);
   }

   private static boolean isMineralNode(UnitInPool node) {
      return node.unit().toString().contains("MINERAL_FIELD");
   }

   private static boolean isGeyserNode(UnitInPool node) {
      return node.unit().getType().toString().contains("VESPENE_GEYSER");
   }

   private static boolean isSameElevation(ObservationInterface observation, Point2d p1, Point2d p2) {
      return Math.abs(observation.terrainHeight(p1) - observation.terrainHeight(p2)) < 1.0F;
   }
}

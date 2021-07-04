package com.ketroc.terranbot;

import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.spatial.Point;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.github.ocraft.s2client.protocol.unit.UnitSnapshot;
import com.ketroc.terranbot.bots.Bot;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class Position {
   public static Point2d towards(Point2d origin, Point2d target, float distance) {
      if (origin.equals(target)) {
         return inBounds(origin.getX() + distance, origin.getY());
      } else {
         Point2d vector = unitVector(origin, target);
         return inBounds(origin.add(vector.mul(distance)));
      }
   }

   private static Point2d inBounds(Point2d p) {
      return Point2d.of(inBoundsX(p.getX()), inBoundsY(p.getY()));
   }

   private static Point2d inBounds(float x, float y) {
      return Point2d.of(inBoundsX(x), inBoundsY(y));
   }

   private static float inBoundsX(float x) {
      x = Math.min(x, (float)LocationConstants.MAX_X);
      x = Math.max(x, 0.0F);
      return x;
   }

   private static float inBoundsY(float y) {
      y = Math.min(y, (float)LocationConstants.MAX_Y);
      y = Math.max(y, 0.0F);
      return y;
   }

   private static boolean isOutOfBoundsX(float x) {
      return x > (float)LocationConstants.MAX_X || x < 0.0F;
   }

   private static boolean isOutOfBoundsY(float y) {
      return y > (float)LocationConstants.MAX_Y || y < 0.0F;
   }

   public static boolean atEdgeOfMap(Point2d p) {
      return p.getX() == 0.0F || p.getX() == (float)LocationConstants.MAX_X || p.getY() == 0.0F || p.getY() == (float)LocationConstants.MAX_Y;
   }

   public static Point2d towards(Point2d origin, Point2d target, float xDistance, float yDistance) {
      if (target.getX() < origin.getX()) {
         xDistance *= -1.0F;
      }

      if (target.getY() < origin.getY()) {
         yDistance *= -1.0F;
      }

      float x = origin.getX() + xDistance;
      float y = origin.getY() + yDistance;
      return inBounds(x, y);
   }

   public static Point2d midPoint(Point2d p1, Point2d p2) {
      return p1.add(p2).div(2.0F);
   }

   public static Point2d midPoint(List<Point2d> pointList) {
      Point2d midPoint = Point2d.of(0.0F, 0.0F);
      Iterator var2 = pointList.iterator();

      while(var2.hasNext()) {
         Point2d p = (Point2d)var2.next();
         midPoint.add(p);
      }

      return midPoint.div((float)pointList.size());
   }

   public static Point2d midPointUnitsWeighted(List<Unit> unitList) {
      Point2d midPoint = Point2d.of(0.0F, 0.0F);

      Unit u;
      for(Iterator var2 = unitList.iterator(); var2.hasNext(); midPoint = midPoint.add(u.getPosition().toPoint2d())) {
         u = (Unit)var2.next();
      }

      return midPoint.div((float)unitList.size());
   }

   public static Point2d midPointUnitsMedian(List<Unit> unitList) {
      if (unitList.isEmpty()) {
         return null;
      } else {
         List<Float> xCoords = (List)unitList.stream().map(UnitSnapshot::getPosition).map(Point::getX).sorted().collect(Collectors.toList());
         List<Float> yCoords = (List)unitList.stream().map(UnitSnapshot::getPosition).map(Point::getY).sorted().collect(Collectors.toList());
         return Point2d.of((Float)xCoords.get(xCoords.size() / 2), (Float)yCoords.get(yCoords.size() / 2));
      }
   }

   public static Point2d midPointUnits(List<Unit> unitList) {
      float minY = Float.MAX_VALUE;
      float minX = Float.MAX_VALUE;
      float maxY = 0.0F;
      float maxX = 0.0F;

      Point2d p;
      for(Iterator var5 = unitList.iterator(); var5.hasNext(); maxY = Math.max(p.getY(), maxY)) {
         Unit u = (Unit)var5.next();
         p = u.getPosition().toPoint2d();
         minX = Math.min(p.getX(), minX);
         maxX = Math.max(p.getX(), maxX);
         minY = Math.min(p.getY(), minY);
      }

      return Point2d.of((minX + maxX) / 2.0F, (minY + maxY) / 2.0F);
   }

   public static Point2d midPointUnitInPools(List<UnitInPool> unitList) {
      Point2d midPoint = Point2d.of(0.0F, 0.0F);
      Iterator var2 = unitList.iterator();

      while(var2.hasNext()) {
         UnitInPool u = (UnitInPool)var2.next();
         midPoint.add(u.unit().getPosition().toPoint2d());
      }

      return midPoint.div((float)unitList.size());
   }

   public static Point2d unitVector(Point2d from, Point2d to) {
      return normalize(to.sub(from));
   }

   public static Point2d rotate(Point2d origin, Point2d pivotPoint, double angle) {
      return rotate(origin, pivotPoint, angle, false);
   }

   public static Point2d rotate(Point2d origin, Point2d pivotPoint, double angle, boolean nullOutOfBounds) {
      double rads = Math.toRadians(angle);
      double sin = Math.sin(rads);
      double cos = Math.cos(rads);
      origin = origin.sub(pivotPoint);
      float xnew = (float)((double)origin.getX() * cos - (double)origin.getY() * sin);
      float ynew = (float)((double)origin.getX() * sin + (double)origin.getY() * cos);
      float x = xnew + pivotPoint.getX();
      float y = ynew + pivotPoint.getY();
      return !nullOutOfBounds || !isOutOfBoundsX(x) && !isOutOfBoundsY(y) ? inBounds(x, y) : null;
   }

   public static Point2d normalize(Point2d vector) {
      double length = Math.sqrt((double)(vector.getX() * vector.getX() + vector.getY() * vector.getY()));
      return Point2d.of((float)((double)vector.getX() / length), (float)((double)vector.getY() / length));
   }

   public static Point2d nearestHalfPoint(Point2d point) {
      return Point2d.of(roundToNearestHalf(point.getX()), roundToNearestHalf(point.getY()));
   }

   public static Point2d nearestWholePoint(Point2d point) {
      return Point2d.of((float)Math.round(point.getX()), (float)Math.round(point.getY()));
   }

   public static float roundToNearestHalf(float number) {
      return (float)(Math.round(number * 2.0F) / 2);
   }

   public static Point2d moveClear(Point2d pointToMove, Unit obstacle, float minDistance) {
      Point2d obstaclePoint = nearestHalfPoint(obstacle.getPosition().toPoint2d());
      return moveClear(pointToMove, obstaclePoint, minDistance);
   }

   public static Point2d moveClear(Point2d pointToMove, Point2d obstacle, float minDistance) {
      return moveClear(pointToMove, obstacle, minDistance, Float.MAX_VALUE);
   }

   public static Point2d moveClearExactly(Point2d pointToMove, Point2d obstacle, float distance) {
      return moveClear(pointToMove, obstacle, distance, distance);
   }

   public static Point2d moveClear(Point2d pointToMove, Point2d obstacle, float minDistance, float maxDistance) {
      float xDistance = Math.abs(obstacle.getX() - pointToMove.getX());
      float yDistance = Math.abs(obstacle.getY() - pointToMove.getY());
      Point2d newPoint;
      float distance;
      if (xDistance > yDistance) {
         distance = Math.min(maxDistance, Math.max(minDistance, xDistance));
         newPoint = inBounds(moveNumberExactly(pointToMove.getX(), obstacle.getX(), distance), pointToMove.getY());
      } else {
         distance = Math.min(maxDistance, Math.max(minDistance, yDistance));
         newPoint = inBounds(pointToMove.getX(), moveNumberExactly(pointToMove.getY(), obstacle.getY(), distance));
      }

      return newPoint;
   }

   public static Point point2dToPoint(Point2d p) {
      return Point.of(p.getX(), p.getY(), Bot.OBS.terrainHeight(p));
   }

   public static boolean isSameElevation(Point p1, Point p2) {
      return (double)Math.abs(p1.getZ() - p2.getZ()) < 1.2D;
   }

   private static float moveNumberAtLeast(float number, float blocker, float stayClearBy) {
      if (blocker >= number && blocker - number < stayClearBy) {
         number = blocker - stayClearBy;
      } else if (number >= blocker && number - blocker < stayClearBy) {
         number = blocker + stayClearBy;
      }

      return number;
   }

   private static float moveNumberExactly(float number, float blocker, float stayClearBy) {
      if (blocker >= number) {
         number = blocker - stayClearBy;
      } else if (number >= blocker) {
         number = blocker + stayClearBy;
      }

      return number;
   }

   public static float getZ(float x, float y) {
      return getZ(Point2d.of(x, y));
   }

   public static float getZ(Point2d p) {
      return Bot.OBS.terrainHeight(p) + 0.3F;
   }

   public static float distance(float x1, float y1, float x2, float y2) {
      float width = Math.abs(x2 - x1);
      float height = Math.abs(y2 - y1);
      return (float)Math.sqrt((double)(width * width + height * height));
   }
}

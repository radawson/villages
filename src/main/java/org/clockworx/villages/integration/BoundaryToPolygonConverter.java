package org.clockworx.villages.integration;

import org.clockworx.villages.model.VillageBoundary;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to convert VillageBoundary (axis-aligned bounding box) to polygon coordinates.
 * 
 * Since village boundaries are AABBs, they form rectangular polygons when viewed from above.
 * This converter creates a 4-corner polygon representing the boundary outline.
 * 
 * @author Clockworx
 * @since 0.3.0
 */
public class BoundaryToPolygonConverter {
    
    /**
     * Represents a 2D point in the polygon (X and Z coordinates).
     */
    public static class Point2D {
        private final double x;
        private final double z;
        
        public Point2D(double x, double z) {
            this.x = x;
            this.z = z;
        }
        
        public double getX() {
            return x;
        }
        
        public double getZ() {
            return z;
        }
        
        @Override
        public String toString() {
            return "Point2D{x=" + x + ", z=" + z + "}";
        }
    }
    
    /**
     * Converts a VillageBoundary to a list of polygon points.
     * Creates a rectangle with four corners: bottom-left, bottom-right, top-right, top-left.
     * 
     * @param boundary The village boundary to convert
     * @return List of 2D points representing the polygon outline
     */
    public static List<Point2D> toPolygon(VillageBoundary boundary) {
        if (boundary == null) {
            return new ArrayList<>();
        }
        
        List<Point2D> points = new ArrayList<>(4);
        
        // Create rectangle polygon: bottom-left -> bottom-right -> top-right -> top-left
        // Using block centers for cleaner appearance
        double minX = boundary.getMinX() + 0.5;
        double minZ = boundary.getMinZ() + 0.5;
        double maxX = boundary.getMaxX() + 0.5;
        double maxZ = boundary.getMaxZ() + 0.5;
        
        // Bottom-left corner
        points.add(new Point2D(minX, minZ));
        // Bottom-right corner
        points.add(new Point2D(maxX, minZ));
        // Top-right corner
        points.add(new Point2D(maxX, maxZ));
        // Top-left corner
        points.add(new Point2D(minX, maxZ));
        
        return points;
    }
    
    /**
     * Converts a VillageBoundary to a list of coordinate arrays [x, z].
     * This format is commonly used by mapping APIs.
     * 
     * @param boundary The village boundary to convert
     * @return List of double arrays, each containing [x, z] coordinates
     */
    public static List<double[]> toCoordinateArrays(VillageBoundary boundary) {
        List<Point2D> points = toPolygon(boundary);
        List<double[]> coordinates = new ArrayList<>(points.size());
        
        for (Point2D point : points) {
            coordinates.add(new double[]{point.getX(), point.getZ()});
        }
        
        return coordinates;
    }
    
    /**
     * Gets the center point of the boundary.
     * 
     * @param boundary The village boundary
     * @return The center point, or null if boundary is null
     */
    public static Point2D getCenter(VillageBoundary boundary) {
        if (boundary == null) {
            return null;
        }
        
        return new Point2D(boundary.getCenterX() + 0.5, boundary.getCenterZ() + 0.5);
    }
    
    /**
     * Gets the Y coordinate (height) for the boundary.
     * Uses the center Y coordinate.
     * 
     * @param boundary The village boundary
     * @return The Y coordinate, or 64 (default) if boundary is null
     */
    public static int getY(VillageBoundary boundary) {
        if (boundary == null) {
            return 64; // Default sea level
        }
        
        return boundary.getCenterY();
    }
    
    /**
     * Gets the depth (height range) of the boundary.
     * 
     * @param boundary The village boundary
     * @return The depth in blocks, or 0 if boundary is null
     */
    public static int getDepth(VillageBoundary boundary) {
        if (boundary == null) {
            return 0;
        }
        
        return boundary.getHeight();
    }
}

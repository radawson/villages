package org.clockworx.villages.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Represents the calculated boundary of a Minecraft village.
 * 
 * Village boundaries are determined by the Points of Interest (POIs) claimed by
 * villagers. According to Minecraft mechanics:
 * - Initial boundary: 32 blocks horizontally, 12 blocks vertically from first bed
 * - Expansion: Villages grow to include POIs within 32H/52V of existing boundary
 * - Center: Shifts to geometric center of all claimed POIs
 * 
 * This class stores an axis-aligned bounding box (AABB) that encompasses all
 * village POIs, plus the calculated center point used for iron golem and cat spawning.
 * 
 * @author Clockworx
 * @since 0.2.0
 * @see <a href="https://minecraft.wiki/w/Village_mechanics">Minecraft Wiki - Village Mechanics</a>
 */
public class VillageBoundary {
    
    /** Minimum X coordinate of the boundary */
    private final int minX;
    
    /** Minimum Y coordinate of the boundary */
    private final int minY;
    
    /** Minimum Z coordinate of the boundary */
    private final int minZ;
    
    /** Maximum X coordinate of the boundary */
    private final int maxX;
    
    /** Maximum Y coordinate of the boundary */
    private final int maxY;
    
    /** Maximum Z coordinate of the boundary */
    private final int maxZ;
    
    /** X coordinate of the village center */
    private final int centerX;
    
    /** Y coordinate of the village center */
    private final int centerY;
    
    /** Z coordinate of the village center */
    private final int centerZ;
    
    /**
     * Creates a new VillageBoundary with the specified bounds.
     * The center is automatically calculated as the geometric center of the bounds.
     * 
     * @param minX Minimum X coordinate
     * @param minY Minimum Y coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxY Maximum Y coordinate
     * @param maxZ Maximum Z coordinate
     */
    public VillageBoundary(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // Ensure min <= max for each axis
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        
        // Calculate center as geometric center
        this.centerX = (this.minX + this.maxX) / 2;
        this.centerY = (this.minY + this.maxY) / 2;
        this.centerZ = (this.minZ + this.maxZ) / 2;
    }
    
    /**
     * Creates a new VillageBoundary with explicit center coordinates.
     * Useful when loading from storage where center may have been customized.
     * 
     * @param minX Minimum X coordinate
     * @param minY Minimum Y coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxY Maximum Y coordinate
     * @param maxZ Maximum Z coordinate
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     */
    public VillageBoundary(int minX, int minY, int minZ, 
                          int maxX, int maxY, int maxZ,
                          int centerX, int centerY, int centerZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
    }
    
    /**
     * Creates a boundary from a center point using Minecraft's default village dimensions.
     * Default: 32 blocks horizontal, 12 blocks vertical from center.
     * 
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     * @return A new VillageBoundary with default dimensions
     */
    public static VillageBoundary fromCenter(int centerX, int centerY, int centerZ) {
        return fromCenter(centerX, centerY, centerZ, 32, 12);
    }
    
    /**
     * Creates a boundary from a center point with custom dimensions.
     * 
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     * @param horizontalRadius Horizontal radius (X and Z)
     * @param verticalRadius Vertical radius (Y)
     * @return A new VillageBoundary with specified dimensions
     */
    public static VillageBoundary fromCenter(int centerX, int centerY, int centerZ,
                                             int horizontalRadius, int verticalRadius) {
        return new VillageBoundary(
                centerX - horizontalRadius,
                centerY - verticalRadius,
                centerZ - horizontalRadius,
                centerX + horizontalRadius,
                centerY + verticalRadius,
                centerZ + horizontalRadius,
                centerX,
                centerY,
                centerZ
        );
    }
    
    /**
     * Creates a boundary that encompasses two locations.
     * 
     * @param loc1 First corner location
     * @param loc2 Second corner location
     * @return A new VillageBoundary encompassing both locations
     */
    public static VillageBoundary fromLocations(Location loc1, Location loc2) {
        return new VillageBoundary(
                loc1.getBlockX(), loc1.getBlockY(), loc1.getBlockZ(),
                loc2.getBlockX(), loc2.getBlockY(), loc2.getBlockZ()
        );
    }
    
    // ==================== Getters ====================
    
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }
    public int getCenterZ() { return centerZ; }
    
    /**
     * Gets the center as a Bukkit Location.
     * 
     * @param world The world for the location
     * @return Center location
     */
    public Location getCenter(World world) {
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }
    
    /**
     * Gets the minimum corner as a Bukkit Location.
     * 
     * @param world The world for the location
     * @return Minimum corner location
     */
    public Location getMinCorner(World world) {
        return new Location(world, minX, minY, minZ);
    }
    
    /**
     * Gets the maximum corner as a Bukkit Location.
     * 
     * @param world The world for the location
     * @return Maximum corner location
     */
    public Location getMaxCorner(World world) {
        return new Location(world, maxX, maxY, maxZ);
    }
    
    // ==================== Dimension Calculations ====================
    
    /**
     * Gets the width of the boundary (X axis).
     * 
     * @return Width in blocks
     */
    public int getWidth() {
        return maxX - minX + 1;
    }
    
    /**
     * Gets the height of the boundary (Y axis).
     * 
     * @return Height in blocks
     */
    public int getHeight() {
        return maxY - minY + 1;
    }
    
    /**
     * Gets the depth of the boundary (Z axis).
     * 
     * @return Depth in blocks
     */
    public int getDepth() {
        return maxZ - minZ + 1;
    }
    
    /**
     * Gets the total volume of the boundary in blocks.
     * 
     * @return Volume in blocks cubed
     */
    public long getVolume() {
        return (long) getWidth() * getHeight() * getDepth();
    }
    
    /**
     * Gets the perimeter length at ground level (for entrance detection).
     * 
     * @return Perimeter length in blocks
     */
    public int getPerimeter() {
        return 2 * (getWidth() + getDepth());
    }
    
    // ==================== Containment Checks ====================
    
    /**
     * Checks if a point is within this boundary.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if the point is within the boundary
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Checks if a location is within this boundary.
     * 
     * @param location The location to check
     * @return true if the location is within the boundary
     */
    public boolean contains(Location location) {
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    
    /**
     * Checks if a point is within the horizontal bounds (ignoring Y).
     * Useful for entrance detection at any height.
     * 
     * @param x X coordinate
     * @param z Z coordinate
     * @return true if the point is within horizontal bounds
     */
    public boolean containsHorizontal(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
    
    /**
     * Checks if a point is on the boundary edge (within 1 block).
     * 
     * @param x X coordinate
     * @param z Z coordinate
     * @return true if the point is on the boundary edge
     */
    public boolean isOnEdge(int x, int z) {
        boolean onXEdge = (x == minX || x == maxX) && z >= minZ && z <= maxZ;
        boolean onZEdge = (z == minZ || z == maxZ) && x >= minX && x <= maxX;
        return onXEdge || onZEdge;
    }
    
    /**
     * Checks if this boundary overlaps with another.
     * 
     * @param other The other boundary
     * @return true if the boundaries overlap
     */
    public boolean overlaps(VillageBoundary other) {
        return this.minX <= other.maxX && this.maxX >= other.minX &&
               this.minY <= other.maxY && this.maxY >= other.minY &&
               this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }
    
    // ==================== Expansion ====================
    
    /**
     * Creates a new boundary expanded by the specified amount in all directions.
     * 
     * @param horizontal Horizontal expansion (X and Z)
     * @param vertical Vertical expansion (Y)
     * @return A new expanded boundary
     */
    public VillageBoundary expand(int horizontal, int vertical) {
        return new VillageBoundary(
                minX - horizontal,
                minY - vertical,
                minZ - horizontal,
                maxX + horizontal,
                maxY + vertical,
                maxZ + horizontal,
                centerX,
                centerY,
                centerZ
        );
    }
    
    /**
     * Creates a new boundary that includes a point.
     * If the point is already inside, returns the same boundary.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return A new boundary that includes the point
     */
    public VillageBoundary expandToInclude(int x, int y, int z) {
        if (contains(x, y, z)) {
            return this;
        }
        
        int newMinX = Math.min(minX, x);
        int newMinY = Math.min(minY, y);
        int newMinZ = Math.min(minZ, z);
        int newMaxX = Math.max(maxX, x);
        int newMaxY = Math.max(maxY, y);
        int newMaxZ = Math.max(maxZ, z);
        
        return new VillageBoundary(newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);
    }
    
    /**
     * Creates a new boundary that includes a location.
     * 
     * @param location The location to include
     * @return A new boundary that includes the location
     */
    public VillageBoundary expandToInclude(Location location) {
        return expandToInclude(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    
    // ==================== Distance Calculations ====================
    
    /**
     * Calculates the distance from a point to the boundary center.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Distance to center
     */
    public double distanceToCenter(int x, int y, int z) {
        double dx = x - centerX;
        double dy = y - centerY;
        double dz = z - centerZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Calculates the horizontal distance from a point to the boundary center.
     * 
     * @param x X coordinate
     * @param z Z coordinate
     * @return Horizontal distance to center
     */
    public double horizontalDistanceToCenter(int x, int z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Calculates the distance from a point to the nearest boundary edge.
     * 
     * @param x X coordinate
     * @param z Z coordinate
     * @return Distance to nearest edge (negative if inside, positive if outside)
     */
    public double distanceToEdge(int x, int z) {
        // Distance to each edge
        int distToMinX = x - minX;
        int distToMaxX = maxX - x;
        int distToMinZ = z - minZ;
        int distToMaxZ = maxZ - z;
        
        // If inside, return negative of distance to nearest edge
        if (containsHorizontal(x, z)) {
            return -Math.min(Math.min(distToMinX, distToMaxX), Math.min(distToMinZ, distToMaxZ));
        }
        
        // If outside, calculate distance to nearest edge/corner
        int dx = Math.max(0, Math.max(minX - x, x - maxX));
        int dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VillageBoundary that = (VillageBoundary) o;
        return minX == that.minX && minY == that.minY && minZ == that.minZ &&
               maxX == that.maxX && maxY == that.maxY && maxZ == that.maxZ;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    @Override
    public String toString() {
        return "VillageBoundary{" +
                "min=(" + minX + ", " + minY + ", " + minZ + "), " +
                "max=(" + maxX + ", " + maxY + ", " + maxZ + "), " +
                "center=(" + centerX + ", " + centerY + ", " + centerZ + "), " +
                "size=" + getWidth() + "x" + getHeight() + "x" + getDepth() +
                '}';
    }
}

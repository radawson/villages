package org.clockworx.villages.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.Objects;

/**
 * Represents an entrance point to a village.
 * 
 * Entrances are locations at the village boundary where roads or paths enter.
 * They can be detected automatically by scanning for path blocks at the boundary,
 * or marked manually by players using commands.
 * 
 * Each entrance has:
 * - Location (x, y, z coordinates)
 * - Facing direction (the direction a player would face when entering the village)
 * - Auto-detection flag (true if detected automatically, false if manually marked)
 * 
 * Entrances are used for placing "Welcome to [Village Name]" signs.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class VillageEntrance {
    
    /** X coordinate of the entrance */
    private final int x;
    
    /** Y coordinate of the entrance */
    private final int y;
    
    /** Z coordinate of the entrance */
    private final int z;
    
    /** Direction facing into the village (the direction a welcome sign should face) */
    private final BlockFace facing;
    
    /** Whether this entrance was automatically detected or manually marked */
    private final boolean autoDetected;
    
    /**
     * Creates a new VillageEntrance.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param facing Direction facing into the village
     * @param autoDetected true if auto-detected, false if manually marked
     */
    public VillageEntrance(int x, int y, int z, BlockFace facing, boolean autoDetected) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing != null ? facing : BlockFace.NORTH;
        this.autoDetected = autoDetected;
    }
    
    /**
     * Creates a new VillageEntrance from a Location.
     * 
     * @param location The entrance location
     * @param facing Direction facing into the village
     * @param autoDetected true if auto-detected, false if manually marked
     */
    public VillageEntrance(Location location, BlockFace facing, boolean autoDetected) {
        this(location.getBlockX(), location.getBlockY(), location.getBlockZ(), facing, autoDetected);
    }
    
    /**
     * Creates a manually marked entrance.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param facing Direction facing into the village
     * @return A new manually marked entrance
     */
    public static VillageEntrance manual(int x, int y, int z, BlockFace facing) {
        return new VillageEntrance(x, y, z, facing, false);
    }
    
    /**
     * Creates a manually marked entrance from a Location.
     * 
     * @param location The entrance location
     * @param facing Direction facing into the village
     * @return A new manually marked entrance
     */
    public static VillageEntrance manual(Location location, BlockFace facing) {
        return new VillageEntrance(location, facing, false);
    }
    
    /**
     * Creates an auto-detected entrance.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param facing Direction facing into the village
     * @return A new auto-detected entrance
     */
    public static VillageEntrance autoDetected(int x, int y, int z, BlockFace facing) {
        return new VillageEntrance(x, y, z, facing, true);
    }
    
    /**
     * Creates an auto-detected entrance from a Location.
     * 
     * @param location The entrance location
     * @param facing Direction facing into the village
     * @return A new auto-detected entrance
     */
    public static VillageEntrance autoDetected(Location location, BlockFace facing) {
        return new VillageEntrance(location, facing, true);
    }
    
    // ==================== Getters ====================
    
    /**
     * Gets the X coordinate.
     * 
     * @return X coordinate
     */
    public int getX() {
        return x;
    }
    
    /**
     * Gets the Y coordinate.
     * 
     * @return Y coordinate
     */
    public int getY() {
        return y;
    }
    
    /**
     * Gets the Z coordinate.
     * 
     * @return Z coordinate
     */
    public int getZ() {
        return z;
    }
    
    /**
     * Gets the facing direction.
     * This is the direction that faces INTO the village - the direction
     * a welcome sign should face to greet incoming players.
     * 
     * @return Facing direction
     */
    public BlockFace getFacing() {
        return facing;
    }
    
    /**
     * Gets the facing direction as a string.
     * Useful for storage and display.
     * 
     * @return Facing direction name (e.g., "NORTH", "SOUTH")
     */
    public String getFacingName() {
        return facing.name();
    }
    
    /**
     * Checks if this entrance was automatically detected.
     * 
     * @return true if auto-detected, false if manually marked
     */
    public boolean isAutoDetected() {
        return autoDetected;
    }
    
    /**
     * Checks if this entrance was manually marked.
     * 
     * @return true if manually marked, false if auto-detected
     */
    public boolean isManual() {
        return !autoDetected;
    }
    
    /**
     * Gets the entrance as a Bukkit Location.
     * 
     * @param world The world for the location
     * @return The entrance location
     */
    public Location toLocation(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }
    
    /**
     * Gets the location where a welcome sign should be placed.
     * This is typically 1 block in the facing direction from the entrance.
     * 
     * @param world The world for the location
     * @return The sign placement location
     */
    public Location getSignLocation(World world) {
        Location loc = toLocation(world);
        // Place sign facing the entrance, so offset by opposite of facing
        return loc.clone().add(facing.getOppositeFace().getModX(), 0, facing.getOppositeFace().getModZ());
    }
    
    /**
     * Calculates the distance from this entrance to a location.
     * 
     * @param location The location to measure to
     * @return Distance in blocks
     */
    public double distanceTo(Location location) {
        double dx = x - location.getBlockX();
        double dy = y - location.getBlockY();
        double dz = z - location.getBlockZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Calculates the horizontal distance from this entrance to a location.
     * 
     * @param location The location to measure to
     * @return Horizontal distance in blocks
     */
    public double horizontalDistanceTo(Location location) {
        double dx = x - location.getBlockX();
        double dz = z - location.getBlockZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Determines the BlockFace from a string name.
     * Used when loading from storage.
     * 
     * @param name The face name (e.g., "NORTH", "SOUTH")
     * @return The BlockFace, or NORTH if invalid
     */
    public static BlockFace faceFromString(String name) {
        if (name == null || name.isEmpty()) {
            return BlockFace.NORTH;
        }
        try {
            return BlockFace.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BlockFace.NORTH;
        }
    }
    
    /**
     * Calculates the facing direction from inside the village to outside.
     * Based on position relative to village center.
     * 
     * @param entranceX Entrance X coordinate
     * @param entranceZ Entrance Z coordinate
     * @param centerX Village center X coordinate
     * @param centerZ Village center Z coordinate
     * @return The BlockFace pointing outward from the village
     */
    public static BlockFace calculateFacing(int entranceX, int entranceZ, int centerX, int centerZ) {
        int dx = entranceX - centerX;
        int dz = entranceZ - centerZ;
        
        // Determine primary direction based on which axis has greater distance
        if (Math.abs(dx) > Math.abs(dz)) {
            // More east/west than north/south
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            // More north/south than east/west
            return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VillageEntrance that = (VillageEntrance) o;
        return x == that.x && y == that.y && z == that.z;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
    
    @Override
    public String toString() {
        return "VillageEntrance{" +
                "pos=(" + x + ", " + y + ", " + z + "), " +
                "facing=" + facing + ", " +
                "autoDetected=" + autoDetected +
                '}';
    }
}

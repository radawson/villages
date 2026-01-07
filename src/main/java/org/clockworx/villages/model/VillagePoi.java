package org.clockworx.villages.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Represents a Point of Interest (POI) within a village.
 * 
 * In Minecraft, POIs are special blocks that villagers can interact with:
 * - MEETING: Bell blocks - used as gathering points
 * - BED: Beds - claimed for sleeping
 * - JOB_SITE: Workstation blocks - claimed for professions
 *   - ARMORER: Blast Furnace
 *   - BUTCHER: Smoker
 *   - CARTOGRAPHER: Cartography Table
 *   - CLERIC: Brewing Stand
 *   - FARMER: Composter
 *   - FISHERMAN: Barrel
 *   - FLETCHER: Fletching Table
 *   - LEATHERWORKER: Cauldron
 *   - LIBRARIAN: Lectern
 *   - MASON: Stonecutter
 *   - SHEPHERD: Loom
 *   - TOOLSMITH: Smithing Table
 *   - WEAPONSMITH: Grindstone
 * 
 * POIs determine village boundaries - a village expands to include claimed POIs.
 * 
 * @author Clockworx
 * @since 0.2.0
 * @see <a href="https://minecraft.wiki/w/Village_mechanics">Minecraft Wiki - Village Mechanics</a>
 */
public class VillagePoi {
    
    /**
     * Enumeration of POI types relevant to villages.
     */
    public enum PoiType {
        /** Bell block - village gathering point */
        MEETING("meeting"),
        
        /** Bed - villager sleeping spot */
        BED("bed"),
        
        /** Blast Furnace - Armorer workstation */
        ARMORER("armorer"),
        
        /** Smoker - Butcher workstation */
        BUTCHER("butcher"),
        
        /** Cartography Table - Cartographer workstation */
        CARTOGRAPHER("cartographer"),
        
        /** Brewing Stand - Cleric workstation */
        CLERIC("cleric"),
        
        /** Composter - Farmer workstation */
        FARMER("farmer"),
        
        /** Barrel - Fisherman workstation */
        FISHERMAN("fisherman"),
        
        /** Fletching Table - Fletcher workstation */
        FLETCHER("fletcher"),
        
        /** Cauldron - Leatherworker workstation */
        LEATHERWORKER("leatherworker"),
        
        /** Lectern - Librarian workstation */
        LIBRARIAN("librarian"),
        
        /** Stonecutter - Mason workstation */
        MASON("mason"),
        
        /** Loom - Shepherd workstation */
        SHEPHERD("shepherd"),
        
        /** Smithing Table - Toolsmith workstation */
        TOOLSMITH("toolsmith"),
        
        /** Grindstone - Weaponsmith workstation */
        WEAPONSMITH("weaponsmith"),
        
        /** Unknown or unrecognized POI type */
        UNKNOWN("unknown");
        
        private final String id;
        
        PoiType(String id) {
            this.id = id;
        }
        
        /**
         * Gets the string identifier for this POI type.
         * 
         * @return The POI type ID
         */
        public String getId() {
            return id;
        }
        
        /**
         * Checks if this POI type is a job site (workstation).
         * 
         * @return true if this is a job site
         */
        public boolean isJobSite() {
            return this != MEETING && this != BED && this != UNKNOWN;
        }
        
        /**
         * Gets a PoiType from its string ID.
         * 
         * @param id The POI type ID
         * @return The matching PoiType, or UNKNOWN if not found
         */
        public static PoiType fromId(String id) {
            if (id == null || id.isEmpty()) {
                return UNKNOWN;
            }
            String normalized = id.toLowerCase().replace("minecraft:", "");
            for (PoiType type : values()) {
                if (type.id.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
        
        /**
         * Gets a PoiType from an NMS POI type name.
         * Handles the full Minecraft registry names.
         * 
         * @param nmsName The NMS POI type name (e.g., "minecraft:meeting")
         * @return The matching PoiType
         */
        public static PoiType fromNmsName(String nmsName) {
            return fromId(nmsName);
        }
    }
    
    /** The type of this POI */
    private final PoiType type;
    
    /** X coordinate */
    private final int x;
    
    /** Y coordinate */
    private final int y;
    
    /** Z coordinate */
    private final int z;
    
    /**
     * Creates a new VillagePoi.
     * 
     * @param type The POI type
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public VillagePoi(PoiType type, int x, int y, int z) {
        this.type = type != null ? type : PoiType.UNKNOWN;
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * Creates a new VillagePoi from a Location.
     * 
     * @param type The POI type
     * @param location The POI location
     */
    public VillagePoi(PoiType type, Location location) {
        this(type, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    
    /**
     * Creates a new VillagePoi from type string and coordinates.
     * 
     * @param typeId The POI type ID string
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public VillagePoi(String typeId, int x, int y, int z) {
        this(PoiType.fromId(typeId), x, y, z);
    }
    
    // ==================== Getters ====================
    
    /**
     * Gets the POI type.
     * 
     * @return The POI type
     */
    public PoiType getType() {
        return type;
    }
    
    /**
     * Gets the POI type as a string ID.
     * 
     * @return The POI type ID
     */
    public String getTypeId() {
        return type.getId();
    }
    
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
     * Gets this POI as a Bukkit Location.
     * 
     * @param world The world for the location
     * @return The POI location
     */
    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }
    
    // ==================== Type Checks ====================
    
    /**
     * Checks if this is a bell (MEETING) POI.
     * 
     * @return true if this is a bell
     */
    public boolean isBell() {
        return type == PoiType.MEETING;
    }
    
    /**
     * Checks if this is a bed POI.
     * 
     * @return true if this is a bed
     */
    public boolean isBed() {
        return type == PoiType.BED;
    }
    
    /**
     * Checks if this is a job site (workstation) POI.
     * 
     * @return true if this is a job site
     */
    public boolean isJobSite() {
        return type.isJobSite();
    }
    
    // ==================== Distance Calculations ====================
    
    /**
     * Calculates the distance from this POI to a location.
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
     * Calculates the horizontal distance from this POI to a location.
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
     * Calculates the distance from this POI to another.
     * 
     * @param other The other POI
     * @return Distance in blocks
     */
    public double distanceTo(VillagePoi other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Calculates the horizontal distance from this POI to another.
     * 
     * @param other The other POI
     * @return Horizontal distance in blocks
     */
    public double horizontalDistanceTo(VillagePoi other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Checks if this POI is within the specified range of another POI.
     * Uses Minecraft's village expansion rules: 32 blocks horizontal, 52 blocks vertical.
     * 
     * @param other The other POI
     * @return true if within village expansion range
     */
    public boolean isWithinVillageRange(VillagePoi other) {
        int dx = Math.abs(x - other.x);
        int dy = Math.abs(y - other.y);
        int dz = Math.abs(z - other.z);
        return dx <= 32 && dz <= 32 && dy <= 52;
    }
    
    /**
     * Checks if this POI is within the initial village boundary range of another POI.
     * Uses Minecraft's initial village rules: 32 blocks horizontal, 12 blocks vertical.
     * 
     * @param other The other POI
     * @return true if within initial village range
     */
    public boolean isWithinInitialRange(VillagePoi other) {
        int dx = Math.abs(x - other.x);
        int dy = Math.abs(y - other.y);
        int dz = Math.abs(z - other.z);
        return dx <= 32 && dz <= 32 && dy <= 12;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VillagePoi that = (VillagePoi) o;
        return x == that.x && y == that.y && z == that.z && type == that.type;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, x, y, z);
    }
    
    @Override
    public String toString() {
        return "VillagePoi{" +
                "type=" + type +
                ", pos=(" + x + ", " + y + ", " + z + ")" +
                '}';
    }
}

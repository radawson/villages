package org.clockworx.villages.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a Minecraft village with its associated data.
 * 
 * A village is identified by a unique UUID and contains:
 * - Location of the village bell (the primary POI)
 * - Optional name assigned by players
 * - Calculated boundary information
 * - List of POIs (beds, job sites, etc.) within the village
 * - Entrance points for welcome sign placement
 * - Optional region ID if registered with WorldGuard/RegionGuard
 * 
 * This class serves as the primary data model for village persistence
 * and is used by all storage providers (YAML, SQLite, MySQL).
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class Village {
    
    /** Unique identifier for this village */
    private final UUID id;
    
    /** Name of the world containing this village */
    private final String worldName;
    
    /** Optional player-assigned name for the village */
    private String name;
    
    /** X coordinate of the village bell */
    private final int bellX;
    
    /** Y coordinate of the village bell */
    private final int bellY;
    
    /** Z coordinate of the village bell */
    private final int bellZ;
    
    /** Calculated boundary of the village */
    private VillageBoundary boundary;
    
    /** List of POIs within this village */
    private final List<VillagePoi> pois;
    
    /** List of entrance points for welcome signs */
    private final List<VillageEntrance> entrances;
    
    /** Region ID if registered with WorldGuard/RegionGuard */
    private String regionId;
    
    /** Timestamp when the village was first detected */
    private final Instant createdAt;
    
    /** Timestamp when the village was last updated */
    private Instant updatedAt;
    
    /**
     * Creates a new Village with the specified properties.
     * 
     * @param id Unique identifier for the village
     * @param worldName Name of the world containing the village
     * @param bellX X coordinate of the bell
     * @param bellY Y coordinate of the bell
     * @param bellZ Z coordinate of the bell
     */
    public Village(UUID id, String worldName, int bellX, int bellY, int bellZ) {
        this.id = Objects.requireNonNull(id, "Village ID cannot be null");
        this.worldName = Objects.requireNonNull(worldName, "World name cannot be null");
        this.bellX = bellX;
        this.bellY = bellY;
        this.bellZ = bellZ;
        this.pois = new ArrayList<>();
        this.entrances = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    /**
     * Creates a new Village from a bell location.
     * Convenience constructor that extracts coordinates from a Location.
     * 
     * @param id Unique identifier for the village
     * @param bellLocation Location of the village bell
     */
    public Village(UUID id, Location bellLocation) {
        this(id, 
             bellLocation.getWorld().getName(),
             bellLocation.getBlockX(),
             bellLocation.getBlockY(),
             bellLocation.getBlockZ());
    }
    
    /**
     * Full constructor for loading from storage.
     * 
     * @param id Unique identifier
     * @param worldName World name
     * @param name Village name (may be null)
     * @param bellX Bell X coordinate
     * @param bellY Bell Y coordinate
     * @param bellZ Bell Z coordinate
     * @param boundary Village boundary (may be null)
     * @param regionId Region ID (may be null)
     * @param createdAt Creation timestamp
     * @param updatedAt Last update timestamp
     */
    public Village(UUID id, String worldName, String name, 
                   int bellX, int bellY, int bellZ,
                   VillageBoundary boundary, String regionId,
                   Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Village ID cannot be null");
        this.worldName = Objects.requireNonNull(worldName, "World name cannot be null");
        this.name = name;
        this.bellX = bellX;
        this.bellY = bellY;
        this.bellZ = bellZ;
        this.boundary = boundary;
        this.regionId = regionId;
        this.pois = new ArrayList<>();
        this.entrances = new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
    
    // ==================== Getters ====================
    
    /**
     * Gets the unique identifier for this village.
     * 
     * @return The village UUID
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * Gets the name of the world containing this village.
     * 
     * @return The world name
     */
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Gets the Bukkit World object for this village.
     * 
     * @return The World, or null if the world is not loaded
     */
    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }
    
    /**
     * Gets the player-assigned name for this village.
     * 
     * @return The village name, or null if not named
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the display name for this village.
     * Returns the name if set, otherwise returns a shortened UUID.
     * 
     * @return A display-friendly name for the village
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        // Return first 8 characters of UUID as fallback
        return id.toString().substring(0, 8);
    }
    
    /**
     * Checks if this village has a player-assigned name.
     * 
     * @return true if the village has a name
     */
    public boolean hasName() {
        return name != null && !name.isEmpty();
    }
    
    /**
     * Gets the X coordinate of the village bell.
     * 
     * @return Bell X coordinate
     */
    public int getBellX() {
        return bellX;
    }
    
    /**
     * Gets the Y coordinate of the village bell.
     * 
     * @return Bell Y coordinate
     */
    public int getBellY() {
        return bellY;
    }
    
    /**
     * Gets the Z coordinate of the village bell.
     * 
     * @return Bell Z coordinate
     */
    public int getBellZ() {
        return bellZ;
    }
    
    /**
     * Gets the bell location as a Bukkit Location.
     * 
     * @return The bell location, or null if the world is not loaded
     */
    public Location getBellLocation() {
        World world = getWorld();
        if (world == null) {
            return null;
        }
        return new Location(world, bellX, bellY, bellZ);
    }
    
    /**
     * Gets the calculated boundary of this village.
     * 
     * @return The village boundary, or null if not calculated
     */
    public VillageBoundary getBoundary() {
        return boundary;
    }
    
    /**
     * Checks if this village has a calculated boundary.
     * 
     * @return true if boundary has been calculated
     */
    public boolean hasBoundary() {
        return boundary != null;
    }
    
    /**
     * Gets the list of POIs within this village.
     * 
     * @return Unmodifiable list of POIs
     */
    public List<VillagePoi> getPois() {
        return List.copyOf(pois);
    }
    
    /**
     * Gets the list of entrance points for this village.
     * 
     * @return Unmodifiable list of entrances
     */
    public List<VillageEntrance> getEntrances() {
        return List.copyOf(entrances);
    }
    
    /**
     * Gets the region ID if registered with WorldGuard/RegionGuard.
     * 
     * @return The region ID, or null if not registered
     */
    public String getRegionId() {
        return regionId;
    }
    
    /**
     * Checks if this village has a registered region.
     * 
     * @return true if a region is registered
     */
    public boolean hasRegion() {
        return regionId != null && !regionId.isEmpty();
    }
    
    /**
     * Gets the timestamp when this village was first detected.
     * 
     * @return Creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Gets the timestamp when this village was last updated.
     * 
     * @return Last update timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    // ==================== Setters ====================
    
    /**
     * Sets the player-assigned name for this village.
     * 
     * @param name The name to set, or null to clear
     */
    public void setName(String name) {
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
        touch();
    }
    
    /**
     * Sets the calculated boundary for this village.
     * 
     * @param boundary The boundary to set
     */
    public void setBoundary(VillageBoundary boundary) {
        this.boundary = boundary;
        touch();
    }
    
    /**
     * Sets the region ID for this village.
     * 
     * @param regionId The region ID to set, or null to clear
     */
    public void setRegionId(String regionId) {
        this.regionId = regionId;
        touch();
    }
    
    // ==================== POI Management ====================
    
    /**
     * Adds a POI to this village.
     * 
     * @param poi The POI to add
     */
    public void addPoi(VillagePoi poi) {
        if (poi != null && !pois.contains(poi)) {
            pois.add(poi);
            touch();
        }
    }
    
    /**
     * Removes a POI from this village.
     * 
     * @param poi The POI to remove
     * @return true if the POI was removed
     */
    public boolean removePoi(VillagePoi poi) {
        boolean removed = pois.remove(poi);
        if (removed) {
            touch();
        }
        return removed;
    }
    
    /**
     * Clears all POIs from this village.
     */
    public void clearPois() {
        if (!pois.isEmpty()) {
            pois.clear();
            touch();
        }
    }
    
    /**
     * Sets all POIs for this village, replacing existing ones.
     * 
     * @param newPois The POIs to set
     */
    public void setPois(List<VillagePoi> newPois) {
        pois.clear();
        if (newPois != null) {
            pois.addAll(newPois);
        }
        touch();
    }
    
    // ==================== Entrance Management ====================
    
    /**
     * Adds an entrance to this village.
     * 
     * @param entrance The entrance to add
     */
    public void addEntrance(VillageEntrance entrance) {
        if (entrance != null && !entrances.contains(entrance)) {
            entrances.add(entrance);
            touch();
        }
    }
    
    /**
     * Removes an entrance from this village.
     * 
     * @param entrance The entrance to remove
     * @return true if the entrance was removed
     */
    public boolean removeEntrance(VillageEntrance entrance) {
        boolean removed = entrances.remove(entrance);
        if (removed) {
            touch();
        }
        return removed;
    }
    
    /**
     * Clears all entrances from this village.
     */
    public void clearEntrances() {
        if (!entrances.isEmpty()) {
            entrances.clear();
            touch();
        }
    }
    
    /**
     * Sets all entrances for this village, replacing existing ones.
     * 
     * @param newEntrances The entrances to set
     */
    public void setEntrances(List<VillageEntrance> newEntrances) {
        entrances.clear();
        if (newEntrances != null) {
            entrances.addAll(newEntrances);
        }
        touch();
    }
    
    /**
     * Gets only the auto-detected entrances.
     * 
     * @return List of auto-detected entrances
     */
    public List<VillageEntrance> getAutoDetectedEntrances() {
        return entrances.stream()
                .filter(VillageEntrance::isAutoDetected)
                .toList();
    }
    
    /**
     * Gets only the manually marked entrances.
     * 
     * @return List of manually marked entrances
     */
    public List<VillageEntrance> getManualEntrances() {
        return entrances.stream()
                .filter(e -> !e.isAutoDetected())
                .toList();
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Updates the updatedAt timestamp to the current time.
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * Sets the updatedAt timestamp to a specific time.
     * Used when loading from storage.
     * 
     * @param timestamp The timestamp to set
     */
    public void setUpdatedAt(Instant timestamp) {
        this.updatedAt = timestamp != null ? timestamp : Instant.now();
    }
    
    /**
     * Gets the chunk X coordinate containing the bell.
     * 
     * @return Chunk X coordinate
     */
    public int getChunkX() {
        return bellX >> 4;
    }
    
    /**
     * Gets the chunk Z coordinate containing the bell.
     * 
     * @return Chunk Z coordinate
     */
    public int getChunkZ() {
        return bellZ >> 4;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Village village = (Village) o;
        return id.equals(village.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Village{" +
                "id=" + id +
                ", worldName='" + worldName + '\'' +
                ", name='" + name + '\'' +
                ", bell=(" + bellX + ", " + bellY + ", " + bellZ + ")" +
                ", hasBoundary=" + hasBoundary() +
                ", pois=" + pois.size() +
                ", entrances=" + entrances.size() +
                ", regionId='" + regionId + '\'' +
                '}';
    }
}

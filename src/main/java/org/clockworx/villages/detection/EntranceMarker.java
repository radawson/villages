package org.clockworx.villages.detection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageEntrance;

import java.util.List;
import java.util.Optional;

/**
 * Handles manual entrance marking for villages.
 * 
 * Players can mark entrances using:
 * - Commands (/village entrance add)
 * - Placing specific marker blocks (optional feature)
 * 
 * Manual entrances are preserved when auto-detection runs.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class EntranceMarker {
    
    private final VillagesPlugin plugin;
    
    /** Material used as entrance marker block (if block-based marking is enabled) */
    private Material markerMaterial;
    
    /** Whether block-based marking is enabled */
    private boolean blockMarkingEnabled;
    
    /**
     * Creates a new EntranceMarker.
     * 
     * @param plugin The plugin instance
     */
    public EntranceMarker(VillagesPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }
    
    /**
     * Loads marker configuration.
     */
    private void loadConfiguration() {
        // Block-based marking could be configured here
        // For now, primarily using command-based marking
        blockMarkingEnabled = false;
        markerMaterial = Material.LODESTONE; // Default marker block
    }
    
    /**
     * Marks an entrance at the player's current location.
     * 
     * @param player The player marking the entrance
     * @param village The village to add the entrance to
     * @return The created entrance, or empty if marking failed
     */
    public Optional<VillageEntrance> markEntrance(Player player, Village village) {
        Location playerLoc = player.getLocation();
        
        // Validate player is near the village boundary
        if (!isNearBoundary(playerLoc, village)) {
            return Optional.empty();
        }
        
        // Determine facing direction based on player's facing and village center
        BlockFace facing = calculateFacing(playerLoc, village);
        
        // Create manual entrance
        VillageEntrance entrance = VillageEntrance.manual(
            playerLoc.getBlockX(),
            playerLoc.getBlockY(),
            playerLoc.getBlockZ(),
            facing
        );
        
        // Add to village
        village.addEntrance(entrance);
        
        plugin.getLogger().info("Player " + player.getName() + " marked entrance at " + 
            entrance.getX() + ", " + entrance.getY() + ", " + entrance.getZ() +
            " facing " + facing);
        
        return Optional.of(entrance);
    }
    
    /**
     * Marks an entrance at a specific location.
     * 
     * @param location The entrance location
     * @param facing The direction facing into the village
     * @param village The village to add the entrance to
     * @return The created entrance
     */
    public VillageEntrance markEntranceAt(Location location, BlockFace facing, Village village) {
        VillageEntrance entrance = VillageEntrance.manual(location, facing);
        village.addEntrance(entrance);
        return entrance;
    }
    
    /**
     * Removes the nearest entrance to a player's location.
     * 
     * @param player The player
     * @param village The village
     * @param maxDistance Maximum distance to search
     * @return The removed entrance, or empty if none found
     */
    public Optional<VillageEntrance> removeNearestEntrance(Player player, Village village, double maxDistance) {
        Location playerLoc = player.getLocation();
        
        VillageEntrance nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (VillageEntrance entrance : village.getEntrances()) {
            double dist = entrance.distanceTo(playerLoc);
            if (dist < nearestDist && dist <= maxDistance) {
                nearestDist = dist;
                nearest = entrance;
            }
        }
        
        if (nearest != null) {
            village.removeEntrance(nearest);
            plugin.getLogger().info("Removed entrance at " + 
                nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ());
            return Optional.of(nearest);
        }
        
        return Optional.empty();
    }
    
    /**
     * Removes a specific entrance from a village.
     * 
     * @param entrance The entrance to remove
     * @param village The village
     * @return true if removed
     */
    public boolean removeEntrance(VillageEntrance entrance, Village village) {
        return village.removeEntrance(entrance);
    }
    
    /**
     * Clears all manual entrances from a village.
     * Auto-detected entrances are preserved.
     * 
     * @param village The village
     * @return Number of entrances removed
     */
    public int clearManualEntrances(Village village) {
        List<VillageEntrance> manualEntrances = village.getManualEntrances();
        int count = manualEntrances.size();
        
        for (VillageEntrance entrance : manualEntrances) {
            village.removeEntrance(entrance);
        }
        
        return count;
    }
    
    /**
     * Clears all entrances (both manual and auto-detected) from a village.
     * 
     * @param village The village
     * @return Number of entrances removed
     */
    public int clearAllEntrances(Village village) {
        int count = village.getEntrances().size();
        village.clearEntrances();
        return count;
    }
    
    /**
     * Checks if a location is near the village boundary.
     * 
     * @param location The location to check
     * @param village The village
     * @return true if near boundary (within 10 blocks)
     */
    private boolean isNearBoundary(Location location, Village village) {
        if (!village.hasBoundary()) {
            // If no boundary, allow marking anywhere in the chunk
            return village.getChunkX() == (location.getBlockX() >> 4) &&
                   village.getChunkZ() == (location.getBlockZ() >> 4);
        }
        
        double distToEdge = village.getBoundary().distanceToEdge(
            location.getBlockX(), 
            location.getBlockZ()
        );
        
        return Math.abs(distToEdge) <= 10; // Within 10 blocks of edge
    }
    
    /**
     * Calculates the facing direction for an entrance based on location and village center.
     * 
     * @param location The entrance location
     * @param village The village
     * @return The facing direction (into the village)
     */
    private BlockFace calculateFacing(Location location, Village village) {
        int centerX, centerZ;
        
        if (village.hasBoundary()) {
            centerX = village.getBoundary().getCenterX();
            centerZ = village.getBoundary().getCenterZ();
        } else {
            centerX = village.getBellX();
            centerZ = village.getBellZ();
        }
        
        return VillageEntrance.calculateFacing(
            location.getBlockX(),
            location.getBlockZ(),
            centerX,
            centerZ
        ).getOppositeFace(); // Opposite because we want facing INTO the village
    }
    
    /**
     * Handles a block being placed that might be an entrance marker.
     * Only relevant if block-based marking is enabled.
     * 
     * @param block The placed block
     * @param player The player who placed it
     * @param village The village (if placement is near one)
     */
    public void onBlockPlace(Block block, Player player, Village village) {
        if (!blockMarkingEnabled) {
            return;
        }
        
        if (block.getType() != markerMaterial) {
            return;
        }
        
        if (village == null) {
            return;
        }
        
        // Mark entrance at this location
        BlockFace facing = calculateFacing(block.getLocation(), village);
        VillageEntrance entrance = VillageEntrance.manual(block.getLocation(), facing);
        village.addEntrance(entrance);
        
        plugin.getLogger().info("Block-based entrance marker placed at " + 
            block.getX() + ", " + block.getY() + ", " + block.getZ());
    }
    
    /**
     * Handles a block being broken that might be an entrance marker.
     * 
     * @param block The broken block
     * @param village The village
     */
    public void onBlockBreak(Block block, Village village) {
        if (!blockMarkingEnabled) {
            return;
        }
        
        if (village == null) {
            return;
        }
        
        // Check if there's an entrance at this location
        for (VillageEntrance entrance : village.getEntrances()) {
            if (entrance.getX() == block.getX() &&
                entrance.getY() == block.getY() &&
                entrance.getZ() == block.getZ() &&
                !entrance.isAutoDetected()) {
                
                village.removeEntrance(entrance);
                plugin.getLogger().info("Entrance marker removed at " + 
                    block.getX() + ", " + block.getY() + ", " + block.getZ());
                break;
            }
        }
    }
    
    /**
     * Reloads configuration.
     */
    public void reload() {
        loadConfiguration();
    }
}

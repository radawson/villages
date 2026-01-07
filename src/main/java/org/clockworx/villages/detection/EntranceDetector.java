package org.clockworx.villages.detection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.model.VillageEntrance;
import org.clockworx.villages.util.PluginLogger;

import java.util.*;

/**
 * Detects road entrances to villages by scanning the boundary perimeter.
 * 
 * Entrance detection works by:
 * 1. Scanning the village boundary perimeter at ground level
 * 2. Looking for path blocks (dirt paths, cobblestone, etc.)
 * 3. Checking if paths lead outward from the village
 * 4. Grouping adjacent path blocks into single entrances
 * 
 * Configurable via config.yml:
 * - path-blocks: List of materials considered as paths
 * - min-path-width: Minimum width to be considered an entrance
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class EntranceDetector {
    
    private final VillagesPlugin plugin;
    private PluginLogger logger;
    private Set<Material> pathMaterials;
    private int minPathWidth;
    
    // Default path block types
    private static final Set<Material> DEFAULT_PATH_MATERIALS = Set.of(
        Material.DIRT_PATH,
        Material.COBBLESTONE,
        Material.STONE,
        Material.STONE_BRICKS,
        Material.GRAVEL,
        Material.COBBLESTONE_SLAB,
        Material.STONE_SLAB,
        Material.STONE_BRICK_SLAB
    );
    
    /**
     * Creates a new EntranceDetector.
     * 
     * @param plugin The plugin instance
     */
    public EntranceDetector(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        loadConfiguration();
    }
    
    /**
     * Loads entrance detection configuration.
     */
    private void loadConfiguration() {
        // Load path materials from config
        List<String> pathBlocks = plugin.getConfig().getStringList("entrances.path-blocks");
        if (pathBlocks.isEmpty()) {
            pathMaterials = new HashSet<>(DEFAULT_PATH_MATERIALS);
        } else {
            pathMaterials = new HashSet<>();
            for (String name : pathBlocks) {
                try {
                    Material mat = Material.valueOf(name.toUpperCase());
                    pathMaterials.add(mat);
                } catch (IllegalArgumentException e) {
                    logWarning("Invalid path block material: " + name);
                }
            }
        }
        
        // Load minimum path width
        minPathWidth = plugin.getConfig().getInt("entrances.min-path-width", 2);
    }
    
    /**
     * Detects all road entrances for a village.
     * 
     * @param village The village to detect entrances for
     * @return List of detected entrance points
     */
    public List<VillageEntrance> detectEntrances(Village village) {
        if (!village.hasBoundary()) {
            return Collections.emptyList();
        }
        
        VillageBoundary boundary = village.getBoundary();
        World world = village.getWorld();
        
        if (world == null) {
            return Collections.emptyList();
        }
        
        List<VillageEntrance> entrances = new ArrayList<>();
        
        try {
            // Scan all four edges of the boundary
            entrances.addAll(scanEdge(world, boundary, BlockFace.NORTH));
            entrances.addAll(scanEdge(world, boundary, BlockFace.SOUTH));
            entrances.addAll(scanEdge(world, boundary, BlockFace.EAST));
            entrances.addAll(scanEdge(world, boundary, BlockFace.WEST));
            
            // Merge nearby entrances
            entrances = mergeNearbyEntrances(entrances);
            
            logDebug("Detected " + entrances.size() + " entrances for village: " + 
                village.getDisplayName());
            
        } catch (Exception e) {
            logWarning("Failed to detect entrances: " + e.getMessage());
        }
        
        return entrances;
    }
    
    /**
     * Scans one edge of the boundary for path entrances.
     * 
     * @param world The world
     * @param boundary The village boundary
     * @param edge Which edge to scan (NORTH, SOUTH, EAST, WEST)
     * @return List of entrances found on this edge
     */
    private List<VillageEntrance> scanEdge(World world, VillageBoundary boundary, BlockFace edge) {
        List<VillageEntrance> entrances = new ArrayList<>();
        List<Location> currentPath = new ArrayList<>();
        
        int startX, endX, startZ, endZ, edgeX, edgeZ;
        
        switch (edge) {
            case NORTH -> {
                // North edge: z = minZ, scan x from minX to maxX
                startX = boundary.getMinX();
                endX = boundary.getMaxX();
                startZ = edgeZ = boundary.getMinZ();
                endZ = startZ;
            }
            case SOUTH -> {
                // South edge: z = maxZ, scan x from minX to maxX
                startX = boundary.getMinX();
                endX = boundary.getMaxX();
                startZ = edgeZ = boundary.getMaxZ();
                endZ = startZ;
            }
            case EAST -> {
                // East edge: x = maxX, scan z from minZ to maxZ
                startX = edgeX = boundary.getMaxX();
                endX = startX;
                startZ = boundary.getMinZ();
                endZ = boundary.getMaxZ();
            }
            case WEST -> {
                // West edge: x = minX, scan z from minZ to maxZ
                startX = edgeX = boundary.getMinX();
                endX = startX;
                startZ = boundary.getMinZ();
                endZ = boundary.getMaxZ();
            }
            default -> {
                return entrances;
            }
        }
        
        // Scan along the edge
        if (edge == BlockFace.NORTH || edge == BlockFace.SOUTH) {
            for (int x = startX; x <= endX; x++) {
                Location loc = findPathAtPosition(world, x, startZ);
                if (loc != null && isPathLeadingOutward(world, loc, edge)) {
                    currentPath.add(loc);
                } else {
                    // End of current path segment
                    if (currentPath.size() >= minPathWidth) {
                        entrances.add(createEntranceFromPath(currentPath, edge));
                    }
                    currentPath.clear();
                }
            }
        } else {
            for (int z = startZ; z <= endZ; z++) {
                Location loc = findPathAtPosition(world, startX, z);
                if (loc != null && isPathLeadingOutward(world, loc, edge)) {
                    currentPath.add(loc);
                } else {
                    if (currentPath.size() >= minPathWidth) {
                        entrances.add(createEntranceFromPath(currentPath, edge));
                    }
                    currentPath.clear();
                }
            }
        }
        
        // Handle last path segment
        if (currentPath.size() >= minPathWidth) {
            entrances.add(createEntranceFromPath(currentPath, edge));
        }
        
        return entrances;
    }
    
    /**
     * Finds a path block at a given X,Z position.
     * Searches vertically around the assumed ground level.
     * 
     * @param world The world
     * @param x X coordinate
     * @param z Z coordinate
     * @return Location of the path block, or null if not found
     */
    private Location findPathAtPosition(World world, int x, int z) {
        // Get the highest block at this position
        int highestY = world.getHighestBlockYAt(x, z);
        
        // Search a few blocks around ground level
        for (int y = highestY; y >= highestY - 5 && y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (isPathBlock(block)) {
                return block.getLocation();
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a block is a path block.
     * 
     * @param block The block to check
     * @return true if it's a path block
     */
    private boolean isPathBlock(Block block) {
        return pathMaterials.contains(block.getType());
    }
    
    /**
     * Checks if a path leads outward from the village (not just along the edge).
     * 
     * @param world The world
     * @param pathLoc Location of the path block
     * @param edge The edge direction
     * @return true if the path leads outward
     */
    private boolean isPathLeadingOutward(World world, Location pathLoc, BlockFace edge) {
        // Check if there's a path block outside the boundary in the edge direction
        Block outsideBlock = pathLoc.getBlock().getRelative(edge);
        
        // Also check one block up/down for slopes
        if (isPathBlock(outsideBlock)) {
            return true;
        }
        if (isPathBlock(outsideBlock.getRelative(BlockFace.UP))) {
            return true;
        }
        if (isPathBlock(outsideBlock.getRelative(BlockFace.DOWN))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Creates an entrance from a path segment.
     * Uses the center of the path segment as the entrance location.
     * 
     * @param pathLocations Locations making up the path
     * @param edge The edge direction (determines facing)
     * @return The created entrance
     */
    private VillageEntrance createEntranceFromPath(List<Location> pathLocations, BlockFace edge) {
        // Use the center of the path as the entrance location
        Location center = pathLocations.get(pathLocations.size() / 2);
        
        // Entrance faces into the village (opposite of edge direction)
        BlockFace facing = edge.getOppositeFace();
        
        return VillageEntrance.autoDetected(center, facing);
    }
    
    /**
     * Merges nearby entrances into single entrances.
     * Prevents multiple entrances being created for wide roads.
     * 
     * @param entrances List of entrances to merge
     * @return Merged list of entrances
     */
    private List<VillageEntrance> mergeNearbyEntrances(List<VillageEntrance> entrances) {
        if (entrances.size() <= 1) {
            return entrances;
        }
        
        List<VillageEntrance> merged = new ArrayList<>();
        Set<Integer> usedIndices = new HashSet<>();
        
        for (int i = 0; i < entrances.size(); i++) {
            if (usedIndices.contains(i)) continue;
            
            VillageEntrance entrance = entrances.get(i);
            usedIndices.add(i);
            
            // Check if any other entrances are nearby with same facing
            List<VillageEntrance> group = new ArrayList<>();
            group.add(entrance);
            
            for (int j = i + 1; j < entrances.size(); j++) {
                if (usedIndices.contains(j)) continue;
                
                VillageEntrance other = entrances.get(j);
                if (entrance.getFacing() == other.getFacing()) {
                    // Check distance
                    double dist = Math.sqrt(
                        Math.pow(entrance.getX() - other.getX(), 2) +
                        Math.pow(entrance.getZ() - other.getZ(), 2)
                    );
                    
                    if (dist < 10) { // Merge entrances within 10 blocks
                        group.add(other);
                        usedIndices.add(j);
                    }
                }
            }
            
            // Use the center entrance from the group
            if (group.size() == 1) {
                merged.add(entrance);
            } else {
                // Calculate center of group
                int avgX = 0, avgY = 0, avgZ = 0;
                for (VillageEntrance e : group) {
                    avgX += e.getX();
                    avgY += e.getY();
                    avgZ += e.getZ();
                }
                avgX /= group.size();
                avgY /= group.size();
                avgZ /= group.size();
                
                merged.add(VillageEntrance.autoDetected(avgX, avgY, avgZ, entrance.getFacing()));
            }
        }
        
        return merged;
    }
    
    /**
     * Detects entrances and updates the village.
     * Existing auto-detected entrances are replaced, manual ones are kept.
     * 
     * @param village The village to update
     * @return The updated list of entrances
     */
    public List<VillageEntrance> detectAndUpdate(Village village) {
        // Get existing manual entrances
        List<VillageEntrance> manualEntrances = village.getManualEntrances();
        
        // Detect new auto entrances
        List<VillageEntrance> autoEntrances = detectEntrances(village);
        
        // Combine
        List<VillageEntrance> allEntrances = new ArrayList<>(manualEntrances);
        allEntrances.addAll(autoEntrances);
        
        // Update village
        village.setEntrances(allEntrances);
        
        return allEntrances;
    }
    
    /**
     * Reloads configuration.
     */
    public void reload() {
        this.logger = plugin.getPluginLogger();
        loadConfiguration();
    }
    
    // ==================== Logging Helpers ====================
    
    private void logWarning(String message) {
        if (logger != null) {
            logger.warning(message);
        } else {
            plugin.getLogger().warning(message);
        }
    }
    
    private void logDebug(String message) {
        if (logger != null) {
            logger.debugEntrance(message);
        }
    }
}

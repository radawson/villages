package org.clockworx.villages.naming;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.util.EnumSet;
import java.util.Set;

/**
 * Detects terrain features around villages for enhanced naming.
 * 
 * Currently supports:
 * - Coastal detection (boundary scan + radius check)
 * 
 * Future support planned:
 * - River detection
 * - Mountain/hill detection
 * - Forest detection
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class TerrainFeatureDetector {
    
    private final PluginLogger logger;
    
    /** Water block types to check for coastal detection */
    private static final Set<Material> WATER_MATERIALS = EnumSet.of(
        Material.WATER,
        Material.LAVA  // Lava can also indicate coastal areas in some cases
    );
    
    /** Default radius for coastal detection around bell (blocks) */
    private static final int COASTAL_RADIUS = 50;
    
    /** Minimum number of water blocks found to consider village coastal */
    private static final int MIN_WATER_BLOCKS = 3;
    
    /**
     * Creates a new TerrainFeatureDetector.
     * 
     * @param logger The plugin logger
     */
    public TerrainFeatureDetector(PluginLogger logger) {
        this.logger = logger;
    }
    
    /**
     * Detects if a village is coastal (near water).
     * Uses both boundary scanning and radius checking around the bell.
     * 
     * @param village The village to check
     * @return true if the village is coastal
     */
    public boolean isCoastal(Village village) {
        World world = village.getWorld();
        if (world == null) {
            logger.debug(LogCategory.GENERAL, "Cannot detect coastal: village world not loaded for " + village.getId());
            return false;
        }
        
        if (!village.hasBoundary()) {
            // If no boundary, just check radius around bell
            return checkRadiusForWater(world, village.getBellX(), village.getBellY(), village.getBellZ());
        }
        
        // Check both boundary perimeter and radius around bell
        boolean boundaryHasWater = scanBoundaryForWater(world, village.getBoundary());
        boolean radiusHasWater = checkRadiusForWater(world, village.getBellX(), village.getBellY(), village.getBellZ());
        
        boolean isCoastal = boundaryHasWater || radiusHasWater;
        
        if (isCoastal) {
            logger.debug(LogCategory.GENERAL, "Village " + village.getId() + " detected as coastal");
        }
        
        return isCoastal;
    }
    
    /**
     * Scans the village boundary perimeter for water blocks.
     * 
     * @param world The world
     * @param boundary The village boundary
     * @return true if water blocks found along boundary
     */
    private boolean scanBoundaryForWater(World world, VillageBoundary boundary) {
        int waterBlocksFound = 0;
        
        // Scan the perimeter of the boundary at ground level
        int minX = boundary.getMinX();
        int maxX = boundary.getMaxX();
        int minZ = boundary.getMinZ();
        int maxZ = boundary.getMaxZ();
        
        // Scan top and bottom edges
        for (int x = minX; x <= maxX; x++) {
            int groundY = world.getHighestBlockYAt(x, minZ);
            if (checkBlockForWater(world, x, groundY, minZ)) {
                waterBlocksFound++;
            }
            groundY = world.getHighestBlockYAt(x, maxZ);
            if (checkBlockForWater(world, x, groundY, maxZ)) {
                waterBlocksFound++;
            }
        }
        
        // Scan left and right edges
        for (int z = minZ; z <= maxZ; z++) {
            int groundY = world.getHighestBlockYAt(minX, z);
            if (checkBlockForWater(world, minX, groundY, z)) {
                waterBlocksFound++;
            }
            groundY = world.getHighestBlockYAt(maxX, z);
            if (checkBlockForWater(world, maxX, groundY, z)) {
                waterBlocksFound++;
            }
        }
        
        return waterBlocksFound >= MIN_WATER_BLOCKS;
    }
    
    /**
     * Checks a radius around the bell location for water blocks.
     * 
     * @param world The world
     * @param bellX Bell X coordinate
     * @param bellY Bell Y coordinate
     * @param bellZ Bell Z coordinate
     * @return true if water blocks found within radius
     */
    private boolean checkRadiusForWater(World world, int bellX, int bellY, int bellZ) {
        int waterBlocksFound = 0;
        
        // Check in a radius around the bell
        for (int dx = -COASTAL_RADIUS; dx <= COASTAL_RADIUS; dx += 5) { // Sample every 5 blocks for performance
            for (int dz = -COASTAL_RADIUS; dz <= COASTAL_RADIUS; dz += 5) {
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq > COASTAL_RADIUS * COASTAL_RADIUS) {
                    continue; // Outside radius
                }
                
                int x = bellX + dx;
                int z = bellZ + dz;
                int groundY = world.getHighestBlockYAt(x, z);
                
                // Check ground level and a few blocks around it
                for (int dy = -2; dy <= 2; dy++) {
                    int y = groundY + dy;
                    if (checkBlockForWater(world, x, y, z)) {
                        waterBlocksFound++;
                        if (waterBlocksFound >= MIN_WATER_BLOCKS) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return waterBlocksFound >= MIN_WATER_BLOCKS;
    }
    
    /**
     * Checks if a block is water.
     * 
     * @param world The world
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return true if the block is water
     */
    private boolean checkBlockForWater(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y > world.getMaxHeight()) {
            return false;
        }
        
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        
        return WATER_MATERIALS.contains(type) || 
               type == Material.KELP ||
               type == Material.KELP_PLANT ||
               type == Material.SEAGRASS ||
               type == Material.TALL_SEAGRASS;
    }
}

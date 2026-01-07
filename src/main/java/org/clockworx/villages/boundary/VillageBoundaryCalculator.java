package org.clockworx.villages.boundary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.model.VillagePoi;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Calculates village boundaries using Minecraft's native POI (Point of Interest) system.
 * 
 * This class uses NMS (net.minecraft.server) classes via paperweight-userdev to access
 * the same POI data that Minecraft uses internally for village mechanics.
 * 
 * Village Boundary Mechanics (from Minecraft Wiki):
 * - Initial boundary: 32 blocks horizontal, 12 blocks vertical from first POI
 * - Expansion: Villages grow to include POIs within 32H/52V of existing boundary
 * - Center: Geometric center of all claimed POIs
 * 
 * Key NMS Classes:
 * - PoiManager: Manages all POIs in a world
 * - PoiType: Defines POI types (MEETING for bells, beds, job sites)
 * - PoiRecord: Individual POI instance with position
 * 
 * @author Clockworx
 * @since 0.2.0
 * @see <a href="https://minecraft.wiki/w/Village_mechanics">Minecraft Wiki - Village Mechanics</a>
 */
public class VillageBoundaryCalculator {
    
    private final VillagesPlugin plugin;
    
    // Minecraft village boundary constants
    /** Initial horizontal radius from first POI (blocks) */
    public static final int INITIAL_HORIZONTAL_RADIUS = 32;
    
    /** Initial vertical radius from first POI (blocks) */
    public static final int INITIAL_VERTICAL_RADIUS = 12;
    
    /** Expansion horizontal range - how far beyond boundary to check for new POIs */
    public static final int EXPANSION_HORIZONTAL_RANGE = 32;
    
    /** Expansion vertical range - how far beyond boundary to check for new POIs */
    public static final int EXPANSION_VERTICAL_RANGE = 52;
    
    /** Minimum distance between village centers for separate villages */
    public static final int MIN_VILLAGE_SEPARATION = 97;
    
    /**
     * Creates a new VillageBoundaryCalculator.
     * 
     * @param plugin The plugin instance
     */
    public VillageBoundaryCalculator(VillagesPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Calculates the boundary for a village starting from its bell location.
     * 
     * This method:
     * 1. Gets the NMS ServerLevel and PoiManager for the world
     * 2. Starts from the bell (MEETING POI) location
     * 3. Scans for all POIs within village range
     * 4. Groups connected POIs into a single village
     * 5. Calculates the bounding box and center
     * 
     * @param bellBlock The bell block that anchors this village
     * @return The calculated boundary, or null if calculation fails
     */
    public VillageBoundary calculateBoundary(Block bellBlock) {
        World world = bellBlock.getWorld();
        Location bellLoc = bellBlock.getLocation();
        
        try {
            // Get NMS world and POI manager
            ServerLevel serverLevel = ((CraftWorld) world).getHandle();
            PoiManager poiManager = serverLevel.getPoiManager();
            
            // Collect all POIs that belong to this village
            List<BlockPos> villagePois = collectVillagePois(poiManager, bellLoc);
            
            if (villagePois.isEmpty()) {
                // No POIs found - use default boundary around bell
                return VillageBoundary.fromCenter(
                    bellLoc.getBlockX(),
                    bellLoc.getBlockY(),
                    bellLoc.getBlockZ(),
                    INITIAL_HORIZONTAL_RADIUS,
                    INITIAL_VERTICAL_RADIUS
                );
            }
            
            // Calculate bounding box from all POIs
            return calculateBoundingBox(villagePois);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to calculate village boundary", e);
            // Fallback to default boundary
            return VillageBoundary.fromCenter(
                bellLoc.getBlockX(),
                bellLoc.getBlockY(),
                bellLoc.getBlockZ(),
                INITIAL_HORIZONTAL_RADIUS,
                INITIAL_VERTICAL_RADIUS
            );
        }
    }
    
    /**
     * Calculates the boundary for a village and populates it with POI data.
     * 
     * @param village The village to calculate boundary for
     * @return The calculated boundary with POIs added to the village
     */
    public VillageBoundary calculateAndPopulate(Village village) {
        Location bellLoc = village.getBellLocation();
        if (bellLoc == null || bellLoc.getWorld() == null) {
            return null;
        }
        
        World world = bellLoc.getWorld();
        
        try {
            ServerLevel serverLevel = ((CraftWorld) world).getHandle();
            PoiManager poiManager = serverLevel.getPoiManager();
            
            // Collect POIs and their types
            List<PoiData> poiDataList = collectVillagePoisWithTypes(poiManager, bellLoc);
            
            if (poiDataList.isEmpty()) {
                return VillageBoundary.fromCenter(
                    bellLoc.getBlockX(),
                    bellLoc.getBlockY(),
                    bellLoc.getBlockZ()
                );
            }
            
            // Convert to VillagePoi objects and add to village
            List<VillagePoi> villagePois = new ArrayList<>();
            List<BlockPos> positions = new ArrayList<>();
            
            for (PoiData data : poiDataList) {
                positions.add(data.pos);
                villagePois.add(new VillagePoi(
                    VillagePoi.PoiType.fromNmsName(data.type),
                    data.pos.getX(),
                    data.pos.getY(),
                    data.pos.getZ()
                ));
            }
            
            village.setPois(villagePois);
            
            // Calculate bounding box
            return calculateBoundingBox(positions);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to calculate village boundary", e);
            return VillageBoundary.fromCenter(
                bellLoc.getBlockX(),
                bellLoc.getBlockY(),
                bellLoc.getBlockZ()
            );
        }
    }
    
    /**
     * Collects all POIs that belong to a village starting from a bell location.
     * Uses flood-fill algorithm based on Minecraft's village expansion rules.
     * 
     * @param poiManager The NMS POI manager
     * @param bellLoc The bell location to start from
     * @return List of BlockPos for all POIs in the village
     */
    private List<BlockPos> collectVillagePois(PoiManager poiManager, Location bellLoc) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toVisit = new LinkedList<>();
        
        BlockPos bellPos = new BlockPos(bellLoc.getBlockX(), bellLoc.getBlockY(), bellLoc.getBlockZ());
        toVisit.add(bellPos);
        result.add(bellPos);
        visited.add(bellPos);
        
        // Flood-fill to find all connected POIs
        while (!toVisit.isEmpty()) {
            BlockPos current = toVisit.poll();
            
            // Search for POIs within expansion range of current position
            int searchRadius = EXPANSION_HORIZONTAL_RANGE + INITIAL_HORIZONTAL_RADIUS;
            
            // Use getInRange to find nearby POIs
            Stream<PoiRecord> nearbyPois = poiManager.getInRange(
                holder -> isVillagePoiType(holder),
                current,
                searchRadius,
                PoiManager.Occupancy.ANY
            );
            
            nearbyPois.forEach(record -> {
                BlockPos pos = record.getPos();
                if (!visited.contains(pos)) {
                    // Check if this POI is within village expansion range
                    if (isWithinExpansionRange(current, pos)) {
                        visited.add(pos);
                        result.add(pos);
                        toVisit.add(pos);
                    }
                }
            });
        }
        
        return result;
    }
    
    /**
     * Collects POIs with their type information.
     */
    private List<PoiData> collectVillagePoisWithTypes(PoiManager poiManager, Location bellLoc) {
        List<PoiData> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toVisit = new LinkedList<>();
        
        BlockPos bellPos = new BlockPos(bellLoc.getBlockX(), bellLoc.getBlockY(), bellLoc.getBlockZ());
        toVisit.add(bellPos);
        result.add(new PoiData(bellPos, "meeting"));
        visited.add(bellPos);
        
        while (!toVisit.isEmpty()) {
            BlockPos current = toVisit.poll();
            int searchRadius = EXPANSION_HORIZONTAL_RANGE + INITIAL_HORIZONTAL_RADIUS;
            
            Stream<PoiRecord> nearbyPois = poiManager.getInRange(
                holder -> isVillagePoiType(holder),
                current,
                searchRadius,
                PoiManager.Occupancy.ANY
            );
            
            nearbyPois.forEach(record -> {
                BlockPos pos = record.getPos();
                if (!visited.contains(pos)) {
                    if (isWithinExpansionRange(current, pos)) {
                        visited.add(pos);
                        String typeName = getPoiTypeName(record);
                        result.add(new PoiData(pos, typeName));
                        toVisit.add(pos);
                    }
                }
            });
        }
        
        return result;
    }
    
    /**
     * Checks if a POI type is relevant to villages.
     * Includes: bells (MEETING), beds, and all job site blocks.
     */
    private boolean isVillagePoiType(Holder<PoiType> holder) {
        if (holder == null) return false;
        
        // Get the POI type key
        var key = holder.unwrapKey();
        if (key.isEmpty()) return false;
        
        String typeName = key.get().registry().getPath();
        
        // Village POI types
        return typeName.equals("meeting") ||           // Bell
               typeName.equals("home") ||              // Bed
               typeName.equals("armorer") ||
               typeName.equals("butcher") ||
               typeName.equals("cartographer") ||
               typeName.equals("cleric") ||
               typeName.equals("farmer") ||
               typeName.equals("fisherman") ||
               typeName.equals("fletcher") ||
               typeName.equals("leatherworker") ||
               typeName.equals("librarian") ||
               typeName.equals("mason") ||
               typeName.equals("shepherd") ||
               typeName.equals("toolsmith") ||
               typeName.equals("weaponsmith");
    }
    
    /**
     * Gets the type name from a POI record.
     */
    private String getPoiTypeName(PoiRecord record) {
        try {
            var key = record.getPoiType().unwrapKey();
            if (key.isPresent()) {
                return key.get().registry().getPath();
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Could not get POI type name: " + e.getMessage());
        }
        return "unknown";
    }
    
    /**
     * Checks if a position is within village expansion range of another.
     * Based on Minecraft rules: 32 blocks horizontal, 52 blocks vertical.
     */
    private boolean isWithinExpansionRange(BlockPos from, BlockPos to) {
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        int dz = Math.abs(to.getZ() - from.getZ());
        
        return dx <= EXPANSION_HORIZONTAL_RANGE &&
               dz <= EXPANSION_HORIZONTAL_RANGE &&
               dy <= EXPANSION_VERTICAL_RANGE;
    }
    
    /**
     * Calculates a bounding box from a list of positions.
     */
    private VillageBoundary calculateBoundingBox(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return null;
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        // Expand bounds by initial radius to get actual village boundary
        return new VillageBoundary(
            minX - INITIAL_HORIZONTAL_RADIUS,
            minY - INITIAL_VERTICAL_RADIUS,
            minZ - INITIAL_HORIZONTAL_RADIUS,
            maxX + INITIAL_HORIZONTAL_RADIUS,
            maxY + INITIAL_VERTICAL_RADIUS,
            maxZ + INITIAL_HORIZONTAL_RADIUS
        );
    }
    
    /**
     * Finds all bells in a world that could be village centers.
     * 
     * @param world The world to search
     * @param centerX Search center X
     * @param centerZ Search center Z
     * @param radius Search radius
     * @return List of bell positions
     */
    public List<BlockPos> findBellsNear(World world, int centerX, int centerZ, int radius) {
        List<BlockPos> bells = new ArrayList<>();
        
        try {
            ServerLevel serverLevel = ((CraftWorld) world).getHandle();
            PoiManager poiManager = serverLevel.getPoiManager();
            
            BlockPos center = new BlockPos(centerX, 64, centerZ);
            
            poiManager.getInRange(
                holder -> {
                    var key = holder.unwrapKey();
                    return key.isPresent() && key.get().registry().getPath().equals("meeting");
                },
                center,
                radius,
                PoiManager.Occupancy.ANY
            ).forEach(record -> bells.add(record.getPos()));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to find bells", e);
        }
        
        return bells;
    }
    
    /**
     * Checks if a location is within any village boundary in the world.
     * 
     * @param location The location to check
     * @param villages List of villages to check against
     * @return The village containing this location, or empty if none
     */
    public Optional<Village> findVillageAt(Location location, List<Village> villages) {
        for (Village village : villages) {
            if (village.hasBoundary()) {
                if (village.getBoundary().contains(location)) {
                    return Optional.of(village);
                }
            }
        }
        return Optional.empty();
    }
    
    /**
     * Helper class to hold POI position and type data.
     */
    private record PoiData(BlockPos pos, String type) {}
}

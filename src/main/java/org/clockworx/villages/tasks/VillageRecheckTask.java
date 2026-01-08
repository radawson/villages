package org.clockworx.villages.tasks;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.managers.VillageManager;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.storage.StorageManager;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.util.List;

/**
 * Periodic task that rechecks villages for bell merging and boundary recalculation.
 * 
 * This task runs at the configured interval (default: 72000 ticks = 1 hour) and:
 * 1. Scans all loaded chunks for bells
 * 2. Checks if bells should be merged into existing villages
 * 3. Recalculates boundaries for existing villages to catch POI changes
 * 
 * Only processes loaded chunks to avoid performance issues.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class VillageRecheckTask extends BukkitRunnable {
    
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final SignManager signManager;
    private final StorageManager storageManager;
    private final PluginLogger logger;
    
    /**
     * Creates a new VillageRecheckTask.
     * 
     * @param plugin The plugin instance
     * @param villageManager The village manager
     * @param signManager The sign manager
     * @param storageManager The storage manager
     */
    public VillageRecheckTask(VillagesPlugin plugin, VillageManager villageManager, 
                              SignManager signManager, StorageManager storageManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.signManager = signManager;
        this.storageManager = storageManager;
        this.logger = plugin.getPluginLogger();
    }
    
    @Override
    public void run() {
        logger.debug(LogCategory.GENERAL, "Starting periodic village recheck");
        
        int totalBellsFound = 0;
        int bellsMerged = 0;
        int villagesRecalculated = 0;
        
        // Process all loaded worlds
        for (World world : plugin.getServer().getWorlds()) {
            logger.debug(LogCategory.GENERAL, "Rechecking villages in world: " + world.getName());
            
            // Get all loaded chunks in this world
            Chunk[] loadedChunks = world.getLoadedChunks();
            logger.debug(LogCategory.GENERAL, "Found " + loadedChunks.length + " loaded chunks in " + world.getName());
            
            // Scan each loaded chunk for bells
            for (Chunk chunk : loadedChunks) {
                List<Block> bells = findBellsInChunk(chunk);
                totalBellsFound += bells.size();
                
                // Process each bell found
                for (Block bellBlock : bells) {
                    // Check if bell already has a UUID in PDC (already processed)
                    java.util.UUID existingUuid = villageManager.getUuidFromPdc(bellBlock);
                    
                    if (existingUuid == null) {
                        // Bell doesn't have a UUID yet - process it (may merge into existing village)
                        Village village = villageManager.getOrCreateVillage(bellBlock);
                        if (village != null) {
                            // Update signs around the bell
                            signManager.placeSignsAroundBell(bellBlock, village.getId(), village.getName());
                            bellsMerged++;
                            logger.debug(LogCategory.GENERAL, "Processed new bell at " + bellBlock.getLocation() + 
                                " for village " + village.getId());
                        }
                    } else {
                        // Bell already processed - just ensure signs are up to date
                        java.util.Optional<Village> village = villageManager.getVillage(existingUuid);
                        if (village.isPresent()) {
                            signManager.placeSignsAroundBell(bellBlock, village.get().getId(), village.get().getName());
                        }
                    }
                }
            }
            
            // Recalculate boundaries for existing villages in this world
            List<Village> villages = storageManager.loadVillagesInWorld(world).join();
            for (Village village : villages) {
                if (village.hasBoundary() && village.getBellLocation() != null) {
                    // Check if bell chunk is loaded before recalculating
                    World villageWorld = village.getWorld();
                    if (villageWorld != null) {
                        int bellX = village.getBellX();
                        int bellZ = village.getBellZ();
                        Chunk bellChunk = villageWorld.getChunkAt(bellX >> 4, bellZ >> 4);
                        
                        if (bellChunk.isLoaded()) {
                            logger.debug(LogCategory.GENERAL, "Recalculating boundary for village " + village.getId());
                            villageManager.recalculateBoundary(village);
                            villagesRecalculated++;
                        }
                    }
                }
            }
        }
        
        logger.info(LogCategory.GENERAL, "Village recheck completed: " + totalBellsFound + " bells found, " + 
            bellsMerged + " processed, " + villagesRecalculated + " boundaries recalculated");
    }
    
    /**
     * Finds all bell blocks in a chunk.
     * 
     * @param chunk The chunk to scan
     * @return List of bell blocks found
     */
    private List<Block> findBellsInChunk(Chunk chunk) {
        List<Block> bells = new java.util.ArrayList<>();
        
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();
        
        // Iterate through all blocks in the chunk
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minHeight; y < maxHeight; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.BELL) {
                        bells.add(block);
                    }
                }
            }
        }
        
        return bells;
    }
}

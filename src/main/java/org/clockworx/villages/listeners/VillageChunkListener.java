package org.clockworx.villages.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.managers.VillageManager;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

/**
 * Listens for chunk load events and detects village bells.
 * 
 * This listener is registered in the main plugin class and handles the ChunkLoadEvent.
 * When a chunk loads, it iterates through all blocks in the chunk to find bell blocks.
 * 
 * Key concepts:
 * - ChunkLoadEvent: Fired when a chunk is loaded into memory
 * - Chunk blocks: A chunk is 16x16 blocks horizontally and 384 blocks tall (in 1.21)
 * - Block iteration: We check each block's material to find bells
 * - Event priority: We use NORMAL priority (default) to process after world generation
 * 
 * When a bell is found:
 * 1. VillageManager creates/loads the full Village object with calculated boundary
 * 2. The Village is saved to the configured storage backend
 * 3. Signs are placed around the bell with the village name
 * 
 * Performance note: Iterating through all blocks in a chunk (16x16x384 = 98,304 blocks)
 * can be expensive, but chunk load events are relatively infrequent, so this is acceptable.
 * In the future, we could optimize by using structure data or other detection methods.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class VillageChunkListener implements Listener {
    
    private final VillageManager villageManager;
    private final SignManager signManager;
    private final PluginLogger logger;
    
    /**
     * Creates a new VillageChunkListener.
     * 
     * @param villageManager The manager for village lifecycle operations
     * @param signManager The manager for placing signs around bells
     * @param plugin The plugin instance for logger access
     */
    public VillageChunkListener(VillageManager villageManager, SignManager signManager, VillagesPlugin plugin) {
        this.villageManager = villageManager;
        this.signManager = signManager;
        this.logger = plugin.getPluginLogger();
    }
    
    /**
     * Handles chunk load events to detect village bells.
     * 
     * This method:
     * 1. Gets the chunk from the event
     * 2. Iterates through all blocks in the chunk (16x16x384)
     * 3. Checks if each block is a BELL material
     * 4. For each bell found:
     *    - Creates or loads the Village using VillageManager
     *    - Places signs around the bell using SignManager
     * 
     * @param event The ChunkLoadEvent
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (logger != null) {
            logger.debug(LogCategory.GENERAL, "Chunk load event: " + event.getChunk().getX() + ", " + event.getChunk().getZ() + 
                " in world " + event.getWorld().getName());
        }
        
        // Get the chunk that was loaded
        var chunk = event.getChunk();

        // Find bells via the POI index rather than scanning all ~98k blocks in the column.
        var bells = villageManager.findBellsInChunk(chunk);
        for (Block bell : bells) {
            processBell(bell);
        }

        if (logger != null && !bells.isEmpty()) {
            logger.info(LogCategory.GENERAL, "Found " + bells.size() + " bell(s) in chunk " + chunk.getX() + ", " + chunk.getZ());
        }
    }
    
    /**
     * Processes a bell block: creates/loads village and places signs.
     * 
     * This method:
     * 1. Gets or creates a full Village object using VillageManager
     *    - Calculates boundaries if new
     *    - Saves to storage
     *    - Caches UUID in bell's PDC
     * 2. Places signs around the bell using SignManager
     * 
     * @param bellBlock The bell block to process
     */
    private void processBell(Block bellBlock) {
        if (logger != null) {
            logger.debug(LogCategory.GENERAL, "Processing bell at " + bellBlock.getLocation());
        }

        // Resolve/create the village off the main thread (storage I/O runs on the storage
        // executor); the returned future completes on the main thread, so the sign placement
        // in thenAccept runs on the main thread and may safely touch the Bukkit API.
        villageManager.getOrCreateVillageAsync(bellBlock)
            .thenAccept(village ->
                signManager.placeSignsAroundBell(bellBlock, village.getId(), village.getName()))
            .exceptionally(ex -> {
                if (logger != null) {
                    logger.warning(LogCategory.GENERAL, "Failed to process bell at "
                        + bellBlock.getLocation() + ": " + ex.getMessage());
                }
                return null;
            });
    }
    
    /**
     * Optional: Handle chunk unload events if needed in the future.
     * Currently not used, but kept for potential future enhancements.
     * 
     * @param event The ChunkUnloadEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // This could be used in the future to clean up temporary data
        // or to save village data to a file when chunks unload
    }
}

package org.clockworx.villages.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.managers.VillageManager;

import java.util.UUID;

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
 * Performance note: Iterating through all blocks in a chunk (16x16x384 = 98,304 blocks)
 * can be expensive, but chunk load events are relatively infrequent, so this is acceptable.
 * In the future, we could optimize by using structure data or other detection methods.
 */
public class VillageChunkListener implements Listener {
    
    private final VillageManager villageManager;
    private final SignManager signManager;
    
    /**
     * Creates a new VillageChunkListener.
     * 
     * @param villageManager The manager for handling UUID generation and PDC storage
     * @param signManager The manager for placing signs around bells
     */
    public VillageChunkListener(VillageManager villageManager, SignManager signManager) {
        this.villageManager = villageManager;
        this.signManager = signManager;
    }
    
    /**
     * Handles chunk load events to detect village bells.
     * 
     * This method:
     * 1. Gets the chunk from the event
     * 2. Iterates through all blocks in the chunk (16x16x384)
     * 3. Checks if each block is a BELL material
     * 4. For each bell found:
     *    - Gets or creates a UUID using VillageManager
     *    - Places signs around the bell using SignManager
     * 
     * @param event The ChunkLoadEvent
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkLoadEvent event) {
        // Get the chunk that was loaded
        var chunk = event.getChunk();
        
        // Get the world's min and max height for iteration
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();
        
        // Iterate through all blocks in the chunk
        // A chunk is 16x16 blocks horizontally
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Iterate through all Y levels (height) in the chunk
                for (int y = minHeight; y < maxHeight; y++) {
                    // Get the block at this position
                    Block block = chunk.getBlock(x, y, z);
                    
                    // Check if this block is a bell
                    if (block.getType() == Material.BELL) {
                        // Found a bell! Process it
                        processBell(block);
                    }
                }
            }
        }
    }
    
    /**
     * Processes a bell block: assigns UUID and places signs.
     * 
     * This method:
     * 1. Gets or creates a UUID for the bell using VillageManager
     * 2. Gets the village name (if it exists) from VillageManager
     * 3. Places signs around the bell using SignManager (with name if available, UUID otherwise)
     * 
     * @param bellBlock The bell block to process
     */
    private void processBell(Block bellBlock) {
        // Get or create the UUID for this village bell
        UUID villageUuid = villageManager.getOrCreateVillageUuid(bellBlock);
        
        // Get the village name if it exists
        String villageName = villageManager.getVillageName(bellBlock);
        
        // Place signs around the bell with the name (or UUID if no name)
        signManager.placeSignsAroundBell(bellBlock, villageUuid, villageName);
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

package org.clockworx.villages.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.storage.VillageStorage;

import java.util.UUID;

/**
 * Manages village UUID generation and storage using Persistent Data Container (PDC).
 * 
 * PDC (Persistent Data Container) is Paper's way of storing custom data on blocks
 * and entities that persists across server restarts. We use a NamespacedKey to create
 * a unique identifier for our custom data, then store the UUID as a string.
 * 
 * Key concepts:
 * - NamespacedKey: A unique identifier for custom data (format: namespace:key)
 * - PersistentDataContainer: The container that holds our custom data
 * - PersistentDataType: Defines the data type (STRING for UUIDs stored as text)
 */
public class VillageManager {
    
    private final VillagesPlugin plugin;
    private final VillageStorage villageStorage;
    private final NamespacedKey villageUuidKey;
    private final NamespacedKey villageNameKey;
    
    /**
     * Creates a new VillageManager.
     * 
     * @param plugin The plugin instance
     * @param villageStorage The storage system for persistent UUIDs
     */
    public VillageManager(VillagesPlugin plugin, VillageStorage villageStorage) {
        this.plugin = plugin;
        this.villageStorage = villageStorage;
        // Create a unique NamespacedKey for storing village UUIDs
        // Format: plugin_name:key_name
        this.villageUuidKey = new NamespacedKey(plugin, "village_uuid");
        // Create a unique NamespacedKey for storing village names
        this.villageNameKey = new NamespacedKey(plugin, "village_name");
    }
    
    /**
     * Gets the UUID for a bell block, generating a new one if it doesn't exist.
     * 
     * This method:
     * 1. Gets the block's state (BlockState) which provides access to PDC
     * 2. Gets the PersistentDataContainer from the block state
     * 3. Checks if a UUID already exists in the PDC
     * 4. If not found in PDC, checks file storage for the chunk
     * 5. If found in file storage, restores it to the bell's PDC
     * 6. If not found anywhere, generates a new UUID and stores it in both PDC and file storage
     * 7. Returns the UUID (either existing, restored, or newly generated)
     * 
     * @param bellBlock The bell block to get/assign a UUID for
     * @return The UUID associated with this bell block
     */
    public UUID getOrCreateVillageUuid(Block bellBlock) {
        // Get the block state - this is required to access PDC on blocks
        // BlockState implements PersistentDataHolder, so we can access PDC
        BlockState blockState = bellBlock.getState();
        if (!(blockState instanceof PersistentDataHolder holder)) {
            plugin.getLogger().warning("BlockState does not support PDC at " + bellBlock.getLocation());
            // Fallback: check file storage and generate if not found
            return getOrCreateUuidFromStorage(bellBlock);
        }
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        
        // Check if a UUID already exists in the PDC
        String existingUuid = pdc.get(villageUuidKey, PersistentDataType.STRING);
        
        if (existingUuid != null) {
            // UUID already exists in PDC, return it
            UUID uuid = UUID.fromString(existingUuid);
            
            // Also ensure it's stored in file storage (in case it wasn't before)
            var chunk = bellBlock.getChunk();
            villageStorage.setVillageUuid(bellBlock.getWorld(), chunk.getX(), chunk.getZ(), uuid);
            
            return uuid;
        }
        
        // UUID not found in PDC, check file storage for the chunk
        var chunk = bellBlock.getChunk();
        UUID storedUuid = villageStorage.getVillageUuid(bellBlock.getWorld(), chunk.getX(), chunk.getZ());
        
        if (storedUuid != null) {
            // Found UUID in file storage, restore it to the bell's PDC
            pdc.set(villageUuidKey, PersistentDataType.STRING, storedUuid.toString());
            blockState.update();
            
            plugin.getLogger().info("Restored village UUID: " + storedUuid + " to bell at " + 
                bellBlock.getLocation() + " from file storage");
            
            return storedUuid;
        }
        
        // No UUID found anywhere, generate a new one
        UUID newUuid = UUID.randomUUID();
        
        // Store the UUID in the PDC as a string
        pdc.set(villageUuidKey, PersistentDataType.STRING, newUuid.toString());
        
        // IMPORTANT: We must apply the changes to save the PDC data
        blockState.update();
        
        // Also store in file storage for persistence across bell removal
        villageStorage.setVillageUuid(bellBlock.getWorld(), chunk.getX(), chunk.getZ(), newUuid);
        
        plugin.getLogger().info("Assigned new village UUID: " + newUuid + " to bell at " + bellBlock.getLocation());
        
        return newUuid;
    }
    
    /**
     * Fallback method to get or create UUID from storage when PDC is not available.
     * 
     * @param bellBlock The bell block
     * @return The UUID for this bell block
     */
    private UUID getOrCreateUuidFromStorage(Block bellBlock) {
        var chunk = bellBlock.getChunk();
        UUID storedUuid = villageStorage.getVillageUuid(bellBlock.getWorld(), chunk.getX(), chunk.getZ());
        
        if (storedUuid != null) {
            return storedUuid;
        }
        
        // Generate new UUID and store it
        UUID newUuid = UUID.randomUUID();
        villageStorage.setVillageUuid(bellBlock.getWorld(), chunk.getX(), chunk.getZ(), newUuid);
        
        return newUuid;
    }
    
    /**
     * Gets the existing UUID for a bell block, if one exists.
     * 
     * @param bellBlock The bell block to check
     * @return The UUID if it exists, null otherwise
     */
    public UUID getVillageUuid(Block bellBlock) {
        BlockState blockState = bellBlock.getState();
        if (!(blockState instanceof PersistentDataHolder holder)) {
            return null;
        }
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        
        String uuidString = pdc.get(villageUuidKey, PersistentDataType.STRING);
        
        if (uuidString != null) {
            return UUID.fromString(uuidString);
        }
        
        return null;
    }
    
    /**
     * Gets the NamespacedKey used for storing village UUIDs.
     * 
     * @return The NamespacedKey
     */
    public NamespacedKey getVillageUuidKey() {
        return villageUuidKey;
    }
    
    /**
     * Sets the name for a village bell block.
     * 
     * This method:
     * 1. Gets the block's state (BlockState) which provides access to PDC
     * 2. Gets the PersistentDataContainer from the block state
     * 3. Stores the name as a string in the PDC
     * 4. Updates the block state to persist the changes
     * 
     * @param bellBlock The bell block to set the name for
     * @param name The name to assign to the village (can be null to remove the name)
     */
    public void setVillageName(Block bellBlock, String name) {
        BlockState blockState = bellBlock.getState();
        if (!(blockState instanceof PersistentDataHolder holder)) {
            plugin.getLogger().warning("BlockState does not support PDC at " + bellBlock.getLocation());
            return;
        }
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        
        if (name == null || name.trim().isEmpty()) {
            // Remove the name if null or empty
            pdc.remove(villageNameKey);
        } else {
            // Store the name in the PDC as a string
            pdc.set(villageNameKey, PersistentDataType.STRING, name.trim());
        }
        
        // IMPORTANT: We must apply the changes to save the PDC data
        blockState.update();
        
        plugin.getLogger().info("Set village name to '" + name + "' for bell at " + bellBlock.getLocation());
    }
    
    /**
     * Gets the name for a village bell block, if one exists.
     * 
     * @param bellBlock The bell block to check
     * @return The village name if it exists, null otherwise
     */
    public String getVillageName(Block bellBlock) {
        BlockState blockState = bellBlock.getState();
        if (!(blockState instanceof PersistentDataHolder holder)) {
            return null;
        }
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        
        String name = pdc.get(villageNameKey, PersistentDataType.STRING);
        
        return name;
    }
}

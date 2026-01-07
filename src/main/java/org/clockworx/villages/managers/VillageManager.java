package org.clockworx.villages.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.boundary.VillageBoundaryCalculator;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.storage.StorageManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages village lifecycle including detection, creation, and persistence.
 * 
 * This manager is the central hub for all village operations:
 * - Detecting and creating villages when bells are found
 * - Calculating village boundaries using Minecraft's POI system
 * - Persisting villages to storage (SQLite, MySQL, or YAML)
 * - Quick lookups using bell PDC (Persistent Data Container)
 * 
 * The manager stores a UUID in each bell's PDC for fast lookups, while the
 * full Village object is stored in the configured storage backend.
 * 
 * @author Clockworx
 * @since 0.2.1
 */
public class VillageManager {
    
    private final VillagesPlugin plugin;
    private final StorageManager storageManager;
    private final VillageBoundaryCalculator boundaryCalculator;
    private final NamespacedKey villageUuidKey;
    private final NamespacedKey villageNameKey;
    
    /**
     * Creates a new VillageManager.
     * 
     * @param plugin The plugin instance
     * @param storageManager The storage manager for persisting villages
     * @param boundaryCalculator The calculator for village boundaries
     */
    public VillageManager(VillagesPlugin plugin, StorageManager storageManager, 
                          VillageBoundaryCalculator boundaryCalculator) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.boundaryCalculator = boundaryCalculator;
        this.villageUuidKey = new NamespacedKey(plugin, "village_uuid");
        this.villageNameKey = new NamespacedKey(plugin, "village_name");
    }
    
    /**
     * Gets or creates a Village for a bell block.
     * 
     * This method:
     * 1. Checks the bell's PDC for an existing UUID
     * 2. If found, loads the Village from storage
     * 3. If not found, checks storage by bell location
     * 4. If still not found, creates a new Village with calculated boundary
     * 5. Saves the Village to storage and caches UUID in PDC
     * 
     * @param bellBlock The bell block that anchors the village
     * @return The Village associated with this bell
     */
    public Village getOrCreateVillage(Block bellBlock) {
        // Try to get existing UUID from PDC
        UUID existingUuid = getUuidFromPdc(bellBlock);
        
        if (existingUuid != null) {
            // Try to load from storage
            Optional<Village> existing = storageManager.loadVillage(existingUuid).join();
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        
        // Check storage by bell location
        String worldName = bellBlock.getWorld().getName();
        int x = bellBlock.getX();
        int y = bellBlock.getY();
        int z = bellBlock.getZ();
        
        Optional<Village> byBell = storageManager.loadVillageByBell(worldName, x, y, z).join();
        if (byBell.isPresent()) {
            Village village = byBell.get();
            // Ensure PDC is updated
            setUuidInPdc(bellBlock, village.getId());
            return village;
        }
        
        // Create new village
        UUID newUuid = existingUuid != null ? existingUuid : UUID.randomUUID();
        Village village = new Village(newUuid, bellBlock.getLocation());
        
        // Calculate boundary
        VillageBoundary boundary = boundaryCalculator.calculateAndPopulate(village);
        village.setBoundary(boundary);
        
        // Save to storage
        storageManager.saveVillage(village).join();
        
        // Cache UUID in PDC
        setUuidInPdc(bellBlock, newUuid);
        
        plugin.getLogger().info("Created new village " + newUuid + " at " + bellBlock.getLocation());
        
        return village;
    }
    
    /**
     * Gets a Village by its UUID.
     * 
     * @param id The village UUID
     * @return The Village if found, empty otherwise
     */
    public Optional<Village> getVillage(UUID id) {
        return storageManager.loadVillage(id).join();
    }
    
    /**
     * Gets a Village from a bell block, if it exists.
     * Does not create a new village if one doesn't exist.
     * 
     * @param bellBlock The bell block to check
     * @return The Village if found, empty otherwise
     */
    public Optional<Village> getVillageFromBell(Block bellBlock) {
        UUID uuid = getUuidFromPdc(bellBlock);
        if (uuid != null) {
            return storageManager.loadVillage(uuid).join();
        }
        
        // Check by location
        return storageManager.loadVillageByBell(
            bellBlock.getWorld().getName(),
            bellBlock.getX(),
            bellBlock.getY(),
            bellBlock.getZ()
        ).join();
    }
    
    /**
     * Saves a Village to storage.
     * 
     * @param village The village to save
     */
    public void saveVillage(Village village) {
        storageManager.saveVillage(village).join();
    }
    
    /**
     * Saves a Village asynchronously.
     * 
     * @param village The village to save
     */
    public void saveVillageAsync(Village village) {
        storageManager.saveVillage(village);
    }
    
    /**
     * Recalculates the boundary for a village.
     * 
     * @param village The village to recalculate
     * @return The updated village with new boundary
     */
    public Village recalculateBoundary(Village village) {
        VillageBoundary boundary = boundaryCalculator.calculateAndPopulate(village);
        village.setBoundary(boundary);
        saveVillageAsync(village);
        return village;
    }
    
    /**
     * Sets the name for a village.
     * 
     * @param village The village to name
     * @param name The new name
     */
    public void setVillageName(Village village, String name) {
        village.setName(name);
        saveVillageAsync(village);
        plugin.getLogger().info("Named village " + village.getId() + " to: " + name);
    }
    
    /**
     * Gets the UUID from a bell block's PDC.
     * 
     * @param bellBlock The bell block
     * @return The UUID if found, null otherwise
     */
    public UUID getUuidFromPdc(Block bellBlock) {
        BlockState blockState = bellBlock.getState();
        if (!(blockState instanceof PersistentDataHolder holder)) {
            return null;
        }
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        String uuidString = pdc.get(villageUuidKey, PersistentDataType.STRING);
        
        if (uuidString != null && !uuidString.isEmpty()) {
            try {
                return UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in bell PDC: " + uuidString);
            }
        }
        return null;
    }
    
    /**
     * Sets the UUID in a bell block's PDC.
     * 
     * @param bellBlock The bell block
     * @param uuid The UUID to store
     */
    private void setUuidInPdc(Block bellBlock, UUID uuid) {
        BlockState blockState = bellBlock.getState();
        if (!(blockState instanceof PersistentDataHolder holder)) {
            plugin.getLogger().warning("Bell at " + bellBlock.getLocation() + " does not support PDC");
            return;
        }
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        pdc.set(villageUuidKey, PersistentDataType.STRING, uuid.toString());
        blockState.update();
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
     * Gets the NamespacedKey used for storing village names.
     * 
     * @return The NamespacedKey
     */
    public NamespacedKey getVillageNameKey() {
        return villageNameKey;
    }
    
    // ==================== Legacy Compatibility Methods ====================
    // These methods maintain backward compatibility during migration
    
    /**
     * Gets or creates a UUID for a bell block.
     * This is a compatibility wrapper around getOrCreateVillage.
     * 
     * @param bellBlock The bell block
     * @return The village UUID
     * @deprecated Use {@link #getOrCreateVillage(Block)} instead
     */
    @Deprecated
    public UUID getOrCreateVillageUuid(Block bellBlock) {
        return getOrCreateVillage(bellBlock).getId();
    }
    
    /**
     * Gets the UUID for a bell block if it exists.
     * 
     * @param bellBlock The bell block
     * @return The UUID if found, null otherwise
     * @deprecated Use {@link #getVillageFromBell(Block)} instead
     */
    @Deprecated
    public UUID getVillageUuid(Block bellBlock) {
        return getUuidFromPdc(bellBlock);
    }
    
    /**
     * Sets the name for a village by bell block.
     * 
     * @param bellBlock The bell block
     * @param name The name to set
     * @deprecated Use {@link #setVillageName(Village, String)} instead
     */
    @Deprecated
    public void setVillageName(Block bellBlock, String name) {
        Optional<Village> village = getVillageFromBell(bellBlock);
        if (village.isPresent()) {
            setVillageName(village.get(), name);
        } else {
            plugin.getLogger().warning("Cannot set name: No village found for bell at " + bellBlock.getLocation());
        }
    }
    
    /**
     * Gets the name for a village by bell block.
     * 
     * @param bellBlock The bell block
     * @return The name if found, null otherwise
     * @deprecated Use {@link #getVillageFromBell(Block)} and {@link Village#getName()} instead
     */
    @Deprecated
    public String getVillageName(Block bellBlock) {
        return getVillageFromBell(bellBlock)
            .map(Village::getName)
            .orElse(null);
    }
}

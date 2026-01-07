package org.clockworx.villages.storage;

import org.bukkit.World;
import org.clockworx.villages.model.Village;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining storage operations for village data.
 * 
 * This abstraction layer allows the plugin to support multiple storage backends:
 * - YAML: Simple file-based storage (human-readable, good for small servers)
 * - SQLite: Embedded database (recommended default, no server required)
 * - MySQL: Network database (for multi-server deployments)
 * 
 * All operations that may be slow (I/O, network) return CompletableFuture
 * to allow async execution and prevent blocking the main server thread.
 * 
 * Implementations must be thread-safe as operations may be called from
 * multiple threads (main thread, async scheduler, etc.).
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public interface StorageProvider {
    
    /**
     * Gets the name of this storage provider.
     * Used for logging and configuration.
     * 
     * @return The provider name (e.g., "yaml", "sqlite", "mysql")
     */
    String getName();
    
    /**
     * Initializes the storage provider.
     * This is called once when the plugin enables.
     * 
     * For database providers, this should:
     * - Establish connection(s)
     * - Create tables if they don't exist
     * - Run any necessary migrations
     * 
     * @return CompletableFuture that completes when initialization is done
     * @throws StorageException if initialization fails
     */
    CompletableFuture<Void> initialize();
    
    /**
     * Shuts down the storage provider.
     * This is called when the plugin disables.
     * 
     * For database providers, this should:
     * - Flush any pending writes
     * - Close connection pools
     * - Clean up resources
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    CompletableFuture<Void> shutdown();
    
    /**
     * Checks if the storage provider is currently connected/available.
     * 
     * @return true if the provider is ready to accept operations
     */
    boolean isAvailable();
    
    // ==================== Village CRUD Operations ====================
    
    /**
     * Saves a village to storage.
     * If the village already exists, it will be updated.
     * 
     * This saves all village data including:
     * - Basic info (UUID, name, bell location)
     * - Boundary data
     * - POIs
     * - Entrances
     * - Region ID
     * 
     * @param village The village to save
     * @return CompletableFuture that completes when the save is done
     * @throws StorageException if the save fails
     */
    CompletableFuture<Void> saveVillage(Village village);
    
    /**
     * Loads a village by its UUID.
     * 
     * @param id The village UUID
     * @return CompletableFuture containing the village if found, empty otherwise
     */
    CompletableFuture<Optional<Village>> loadVillage(UUID id);
    
    /**
     * Loads a village by its bell location.
     * Useful when processing bell blocks.
     * 
     * @param worldName The world name
     * @param x Bell X coordinate
     * @param y Bell Y coordinate
     * @param z Bell Z coordinate
     * @return CompletableFuture containing the village if found, empty otherwise
     */
    CompletableFuture<Optional<Village>> loadVillageByBell(String worldName, int x, int y, int z);
    
    /**
     * Loads a village by chunk coordinates.
     * Used for backward compatibility with chunk-based storage.
     * 
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return CompletableFuture containing the village if found, empty otherwise
     */
    CompletableFuture<Optional<Village>> loadVillageByChunk(String worldName, int chunkX, int chunkZ);
    
    /**
     * Loads all villages in a specific world.
     * 
     * @param world The world to load villages from
     * @return CompletableFuture containing list of villages in the world
     */
    CompletableFuture<List<Village>> loadVillagesInWorld(World world);
    
    /**
     * Loads all villages in a specific world by name.
     * 
     * @param worldName The world name
     * @return CompletableFuture containing list of villages in the world
     */
    CompletableFuture<List<Village>> loadVillagesInWorld(String worldName);
    
    /**
     * Loads all villages from storage.
     * Use with caution on large datasets.
     * 
     * @return CompletableFuture containing list of all villages
     */
    CompletableFuture<List<Village>> loadAllVillages();
    
    /**
     * Deletes a village from storage.
     * 
     * @param id The UUID of the village to delete
     * @return CompletableFuture that completes with true if deleted, false if not found
     */
    CompletableFuture<Boolean> deleteVillage(UUID id);
    
    /**
     * Checks if a village exists in storage.
     * 
     * @param id The village UUID
     * @return CompletableFuture that completes with true if exists
     */
    CompletableFuture<Boolean> villageExists(UUID id);
    
    // ==================== Utility Operations ====================
    
    /**
     * Gets the total count of villages in storage.
     * 
     * @return CompletableFuture containing the village count
     */
    CompletableFuture<Integer> getVillageCount();
    
    /**
     * Gets the count of villages in a specific world.
     * 
     * @param worldName The world name
     * @return CompletableFuture containing the village count for that world
     */
    CompletableFuture<Integer> getVillageCount(String worldName);
    
    /**
     * Finds villages within a radius of a location.
     * Useful for preventing overlapping villages.
     * 
     * @param worldName The world name
     * @param x Center X coordinate
     * @param z Center Z coordinate
     * @param radius Search radius in blocks
     * @return CompletableFuture containing list of villages within radius
     */
    CompletableFuture<List<Village>> findVillagesNear(String worldName, int x, int z, int radius);
    
    /**
     * Finds a village containing a specific location.
     * Uses boundary data to determine containment.
     * 
     * @param worldName The world name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return CompletableFuture containing the village if location is inside one
     */
    CompletableFuture<Optional<Village>> findVillageAt(String worldName, int x, int y, int z);
    
    // ==================== Backup and Migration ====================
    
    /**
     * Creates a backup of all village data.
     * 
     * @param backupPath Path for the backup file/directory
     * @return CompletableFuture that completes when backup is done
     */
    CompletableFuture<Void> backup(String backupPath);
    
    /**
     * Exports all villages for migration to another provider.
     * 
     * @return CompletableFuture containing all villages ready for import
     */
    CompletableFuture<List<Village>> exportAll();
    
    /**
     * Imports villages from another provider.
     * 
     * @param villages The villages to import
     * @param overwrite If true, existing villages will be overwritten
     * @return CompletableFuture containing count of imported villages
     */
    CompletableFuture<Integer> importAll(List<Village> villages, boolean overwrite);
}

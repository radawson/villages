package org.clockworx.villages.storage;

import org.bukkit.World;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages storage provider lifecycle and operations.
 * 
 * The StorageManager is responsible for:
 * - Selecting and initializing the appropriate storage provider based on config
 * - Providing a unified interface for storage operations
 * - Handling provider switching and data migration
 * - Managing async operation scheduling
 * 
 * Usage:
 * <pre>
 * StorageManager storage = new StorageManager(plugin);
 * storage.initialize().join(); // Wait for init
 * 
 * Save a village
 * storage.saveVillage(village);
 * 
 * Load a village
 * Village v = storage.loadVillage(uuid).join().orElse(null);
 * </pre>
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class StorageManager {
    
    private final VillagesPlugin plugin;
    private StorageProvider activeProvider;
    private StorageType activeType;
    
    /**
     * Enumeration of available storage types.
     */
    public enum StorageType {
        /** YAML file-based storage */
        YAML("yaml"),
        /** SQLite embedded database */
        SQLITE("sqlite"),
        /** MySQL/MariaDB network database */
        MYSQL("mysql");
        
        private final String id;
        
        StorageType(String id) {
            this.id = id;
        }
        
        public String getId() {
            return id;
        }
        
        public static StorageType fromId(String id) {
            if (id == null) return SQLITE; // Default
            for (StorageType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return SQLITE; // Default fallback
        }
    }
    
    /**
     * Creates a new StorageManager.
     * 
     * @param plugin The plugin instance
     */
    public StorageManager(VillagesPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initializes the storage manager with the configured provider.
     * Reads the storage type from config and initializes the appropriate provider.
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    public CompletableFuture<Void> initialize() {
        // Read storage type from config (default to SQLite)
        String typeId = plugin.getConfig().getString("storage.type", "sqlite");
        this.activeType = StorageType.fromId(typeId);
        
        plugin.getLogger().info("Initializing storage provider: " + activeType.name());
        
        // Create the appropriate provider
        this.activeProvider = createProvider(activeType);
        
        // Initialize the provider
        return activeProvider.initialize()
            .thenRun(() -> {
                plugin.getLogger().info("Storage provider initialized: " + activeProvider.getName());
            })
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize storage provider", ex);
                throw new StorageException("Storage initialization failed", ex);
            });
    }
    
    /**
     * Shuts down the storage manager and active provider.
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    public CompletableFuture<Void> shutdown() {
        if (activeProvider != null) {
            plugin.getLogger().info("Shutting down storage provider: " + activeProvider.getName());
            return activeProvider.shutdown()
                .thenRun(() -> {
                    plugin.getLogger().info("Storage provider shut down successfully");
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Error during storage shutdown", ex);
                    return null;
                });
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Creates a storage provider of the specified type.
     * 
     * @param type The storage type
     * @return The created provider
     */
    private StorageProvider createProvider(StorageType type) {
        return switch (type) {
            case YAML -> new YamlStorageProvider(plugin);
            case SQLITE -> new SQLiteStorageProvider(plugin);
            case MYSQL -> new MySQLStorageProvider(plugin);
        };
    }
    
    /**
     * Gets the active storage provider.
     * 
     * @return The active provider
     * @throws IllegalStateException if no provider is initialized
     */
    public StorageProvider getProvider() {
        if (activeProvider == null) {
            throw new IllegalStateException("Storage provider not initialized");
        }
        return activeProvider;
    }
    
    /**
     * Gets the active storage type.
     * 
     * @return The active type
     */
    public StorageType getActiveType() {
        return activeType;
    }
    
    /**
     * Checks if the storage is available and ready.
     * 
     * @return true if ready for operations
     */
    public boolean isAvailable() {
        return activeProvider != null && activeProvider.isAvailable();
    }
    
    // ==================== Delegated Operations ====================
    // These methods delegate to the active provider for convenience
    
    /**
     * Saves a village to storage.
     * 
     * @param village The village to save
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> saveVillage(Village village) {
        return getProvider().saveVillage(village);
    }
    
    /**
     * Loads a village by UUID.
     * 
     * @param id The village UUID
     * @return CompletableFuture with the village if found
     */
    public CompletableFuture<Optional<Village>> loadVillage(UUID id) {
        return getProvider().loadVillage(id);
    }
    
    /**
     * Loads a village by bell location.
     * 
     * @param worldName World name
     * @param x Bell X coordinate
     * @param y Bell Y coordinate
     * @param z Bell Z coordinate
     * @return CompletableFuture with the village if found
     */
    public CompletableFuture<Optional<Village>> loadVillageByBell(String worldName, int x, int y, int z) {
        return getProvider().loadVillageByBell(worldName, x, y, z);
    }
    
    /**
     * Loads a village by chunk coordinates.
     * 
     * @param worldName World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return CompletableFuture with the village if found
     */
    public CompletableFuture<Optional<Village>> loadVillageByChunk(String worldName, int chunkX, int chunkZ) {
        return getProvider().loadVillageByChunk(worldName, chunkX, chunkZ);
    }
    
    /**
     * Loads all villages in a world.
     * 
     * @param world The world
     * @return CompletableFuture with list of villages
     */
    public CompletableFuture<List<Village>> loadVillagesInWorld(World world) {
        return getProvider().loadVillagesInWorld(world);
    }
    
    /**
     * Loads all villages.
     * 
     * @return CompletableFuture with list of all villages
     */
    public CompletableFuture<List<Village>> loadAllVillages() {
        return getProvider().loadAllVillages();
    }
    
    /**
     * Deletes a village.
     * 
     * @param id The village UUID
     * @return CompletableFuture with true if deleted
     */
    public CompletableFuture<Boolean> deleteVillage(UUID id) {
        return getProvider().deleteVillage(id);
    }
    
    /**
     * Gets the total village count.
     * 
     * @return CompletableFuture with the count
     */
    public CompletableFuture<Integer> getVillageCount() {
        return getProvider().getVillageCount();
    }
    
    /**
     * Finds a village at a location.
     * 
     * @param worldName World name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return CompletableFuture with the village if found
     */
    public CompletableFuture<Optional<Village>> findVillageAt(String worldName, int x, int y, int z) {
        return getProvider().findVillageAt(worldName, x, y, z);
    }
    
    // ==================== Migration Operations ====================
    
    /**
     * Migrates data from one storage provider to another.
     * 
     * @param fromType Source storage type
     * @param toType Target storage type
     * @return CompletableFuture with the count of migrated villages
     */
    public CompletableFuture<Integer> migrateData(StorageType fromType, StorageType toType) {
        if (fromType == toType) {
            return CompletableFuture.completedFuture(0);
        }
        
        plugin.getLogger().info("Starting migration from " + fromType + " to " + toType);
        
        StorageProvider source = createProvider(fromType);
        StorageProvider target = createProvider(toType);
        
        return source.initialize()
            .thenCompose(v -> target.initialize())
            .thenCompose(v -> source.exportAll())
            .thenCompose(villages -> {
                plugin.getLogger().info("Exporting " + villages.size() + " villages for migration");
                return target.importAll(villages, true);
            })
            .thenCompose(count -> {
                plugin.getLogger().info("Migration complete: " + count + " villages migrated");
                return source.shutdown()
                    .thenCompose(v -> target.shutdown())
                    .thenApply(v -> count);
            })
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Migration failed", ex);
                throw new StorageException("Migration failed", ex);
            });
    }
    
    /**
     * Creates a backup of the current storage.
     * 
     * @param backupPath Path for the backup
     * @return CompletableFuture that completes when backup is done
     */
    public CompletableFuture<Void> backup(String backupPath) {
        return getProvider().backup(backupPath);
    }
}

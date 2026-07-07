package org.clockworx.villages.storage;

import org.bukkit.World;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.util.PluginLogger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private PluginLogger logger;
    private StorageProvider activeProvider;
    private StorageType activeType;

    /**
     * Dedicated, bounded executor for all storage I/O. Using a named pool (instead of
     * the shared common ForkJoinPool) lets us cap concurrency to the DB connection pool
     * and, crucially, drain in-flight writes on shutdown so no save is lost.
     */
    private ExecutorService storageExecutor;
    
    /**
     * Enumeration of available storage types.
     */
    public enum StorageType {
        /** YAML file-based storage */
        YAML("yaml"),
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
            if (id == null) return MYSQL; // Default
            for (StorageType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return MYSQL; // Default fallback (SQLite was removed in favour of MySQL)
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
        // Get logger (may be null during early init)
        this.logger = plugin.getPluginLogger();

        // Read storage type from config (default to MySQL)
        String typeId = plugin.getConfig().getString("storage.type", "mysql");
        if ("sqlite".equalsIgnoreCase(typeId)) {
            logWarning("storage.type 'sqlite' is no longer supported and was removed; " +
                "falling back to MySQL. Set storage.type to 'mysql' or 'yaml' in config.yml.");
        }
        this.activeType = StorageType.fromId(typeId);

        logInfo("Initializing storage provider: " + activeType.name());

        // Create the dedicated storage executor before any provider work runs on it.
        // Sized to the MySQL pool so we never queue more concurrent DB tasks than connections.
        int threads = Math.max(2, plugin.getConfig().getInt("storage.mysql.pool-size", 8));
        this.storageExecutor = createStorageExecutor(threads);

        // Create the appropriate provider
        this.activeProvider = createProvider(activeType);
        
        // Initialize the provider
        return activeProvider.initialize()
            .thenRun(() -> {
                logInfo("Storage provider initialized: " + activeProvider.getName());
            })
            .exceptionally(ex -> {
                logSevere("Failed to initialize storage provider", ex);
                throw new StorageException("Storage initialization failed", ex);
            });
    }
    
    /**
     * Shuts down the storage manager and active provider.
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    public CompletableFuture<Void> shutdown() {
        // Stop accepting new storage tasks and drain in-flight writes BEFORE the provider
        // closes its connection pool, so no queued save is lost on shutdown.
        if (storageExecutor != null) {
            storageExecutor.shutdown();
            try {
                if (!storageExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logWarning("Storage executor did not drain within 30s; forcing shutdown " +
                        "(pending writes may be lost)");
                    storageExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                storageExecutor.shutdownNow();
            }
        }

        if (activeProvider != null) {
            logInfo("Shutting down storage provider: " + activeProvider.getName());
            return activeProvider.shutdown()
                .thenRun(() -> {
                    logInfo("Storage provider shut down successfully");
                })
                .exceptionally(ex -> {
                    logWarning("Error during storage shutdown: " + ex.getMessage());
                    return null;
                });
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Creates the dedicated storage executor: a fixed pool of daemon threads named
     * "Villages-Storage-N". Daemon so an abnormal exit never hangs the JVM; we still
     * {@code awaitTermination} on normal shutdown to flush pending writes.
     */
    private ExecutorService createStorageExecutor(int threads) {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "Villages-Storage-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(threads, factory);
    }
    
    /**
     * Creates a storage provider of the specified type.
     * 
     * @param type The storage type
     * @return The created provider
     */
    private StorageProvider createProvider(StorageType type) {
        return switch (type) {
            case YAML -> new YamlStorageProvider(plugin, storageExecutor);
            case MYSQL -> new MySQLStorageProvider(plugin, storageExecutor);
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
        // Snapshot on the calling (main) thread so the async write reads a stable copy even
        // if the live village is mutated concurrently (prevents CME / torn writes).
        return getProvider().saveVillage(village.snapshot());
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
        
        logInfo("Starting migration from " + fromType + " to " + toType);
        
        StorageProvider source = createProvider(fromType);
        StorageProvider target = createProvider(toType);
        
        return source.initialize()
            .thenCompose(v -> target.initialize())
            .thenCompose(v -> source.exportAll())
            .thenCompose(villages -> {
                logInfo("Exporting " + villages.size() + " villages for migration");
                return target.importAll(villages, true);
            })
            .thenCompose(count -> {
                logInfo("Migration complete: " + count + " villages migrated");
                return source.shutdown()
                    .thenCompose(v -> target.shutdown())
                    .thenApply(v -> count);
            })
            .exceptionally(ex -> {
                logSevere("Migration failed", ex);
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
    
    // ==================== Logging Helpers ====================
    
    private void logInfo(String message) {
        if (logger != null) {
            logger.info(message);
        } else {
            plugin.getLogger().info(message);
        }
    }
    
    private void logWarning(String message) {
        if (logger != null) {
            logger.warning(message);
        } else {
            plugin.getLogger().warning(message);
        }
    }
    
    private void logSevere(String message, Throwable ex) {
        if (logger != null) {
            logger.severe(message, ex);
        } else {
            plugin.getLogger().severe(message + ": " + ex.getMessage());
        }
    }
    
    /**
     * Logs a debug message for storage operations.
     * 
     * @param message The message to log
     */
    public void logDebug(String message) {
        if (logger != null) {
            logger.debugStorage(message);
        }
    }
}

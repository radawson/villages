package org.clockworx.villages.storage;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.model.VillageEntrance;
import org.clockworx.villages.model.VillagePoi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * YAML file-based storage provider for village data.
 * 
 * This provider stores all village data in a single YAML file, making it
 * human-readable and easy to edit manually. It's best suited for small
 * servers with fewer than 100 villages.
 * 
 * Storage format:
 * <pre>
 * villages:
 *   [uuid]:
 *     world: world_name
 *     name: Village Name
 *     bell:
 *       x: 100
 *       y: 64
 *       z: 200
 *     boundary:
 *       minX: 68
 *       minY: 52
 *       minZ: 168
 *       maxX: 132
 *       maxY: 76
 *       maxZ: 232
 *     pois:
 *       - type: meeting
 *         x: 100
 *         y: 64
 *         z: 200
 *     entrances:
 *       - x: 68
 *         y: 64
 *         z: 200
 *         facing: WEST
 *         autoDetected: true
 *     regionId: village_myvillage
 *     createdAt: 2024-01-01T00:00:00Z
 *     updatedAt: 2024-01-01T00:00:00Z
 * </pre>
 * 
 * Thread safety is ensured using a read-write lock to allow concurrent reads
 * while serializing writes.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class YamlStorageProvider implements StorageProvider {
    
    private final VillagesPlugin plugin;
    private final File storageFile;
    private final ReentrantReadWriteLock lock;
    private YamlConfiguration config;
    private boolean available;
    
    // Cache for faster lookups
    private final Map<UUID, Village> villageCache;
    private boolean cacheValid;
    
    /**
     * Creates a new YamlStorageProvider.
     * 
     * @param plugin The plugin instance
     */
    public YamlStorageProvider(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "villages.yml");
        this.lock = new ReentrantReadWriteLock();
        this.villageCache = new HashMap<>();
        this.cacheValid = false;
        this.available = false;
    }
    
    @Override
    public String getName() {
        return "yaml";
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                // Ensure data folder exists
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                
                // Create file if it doesn't exist
                if (!storageFile.exists()) {
                    storageFile.createNewFile();
                    plugin.getLogger().info("Created new villages.yml storage file");
                }
                
                // Load the configuration
                config = YamlConfiguration.loadConfiguration(storageFile);
                
                // Ensure villages section exists
                if (!config.isConfigurationSection("villages")) {
                    config.createSection("villages");
                    save();
                }
                
                // Load into cache
                loadCache();
                
                available = true;
                plugin.getLogger().info("YAML storage initialized with " + villageCache.size() + " villages");
                
            } catch (IOException e) {
                throw new StorageException("Failed to initialize YAML storage", e);
            } finally {
                lock.writeLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                if (config != null) {
                    save();
                }
                available = false;
                villageCache.clear();
                cacheValid = false;
            } finally {
                lock.writeLock().unlock();
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    // ==================== Village CRUD Operations ====================
    
    @Override
    public CompletableFuture<Void> saveVillage(Village village) {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                String path = "villages." + village.getId().toString();
                ConfigurationSection section = config.createSection(path);
                
                // Basic info
                section.set("world", village.getWorldName());
                if (village.getName() != null) {
                    section.set("name", village.getName());
                }
                
                // Bell location
                ConfigurationSection bellSection = section.createSection("bell");
                bellSection.set("x", village.getBellX());
                bellSection.set("y", village.getBellY());
                bellSection.set("z", village.getBellZ());
                
                // Boundary
                VillageBoundary boundary = village.getBoundary();
                if (boundary != null) {
                    ConfigurationSection boundarySection = section.createSection("boundary");
                    boundarySection.set("minX", boundary.getMinX());
                    boundarySection.set("minY", boundary.getMinY());
                    boundarySection.set("minZ", boundary.getMinZ());
                    boundarySection.set("maxX", boundary.getMaxX());
                    boundarySection.set("maxY", boundary.getMaxY());
                    boundarySection.set("maxZ", boundary.getMaxZ());
                    boundarySection.set("centerX", boundary.getCenterX());
                    boundarySection.set("centerY", boundary.getCenterY());
                    boundarySection.set("centerZ", boundary.getCenterZ());
                }
                
                // POIs
                List<Map<String, Object>> poisList = new ArrayList<>();
                for (VillagePoi poi : village.getPois()) {
                    Map<String, Object> poiMap = new LinkedHashMap<>();
                    poiMap.put("type", poi.getTypeId());
                    poiMap.put("x", poi.getX());
                    poiMap.put("y", poi.getY());
                    poiMap.put("z", poi.getZ());
                    poisList.add(poiMap);
                }
                section.set("pois", poisList);
                
                // Entrances
                List<Map<String, Object>> entrancesList = new ArrayList<>();
                for (VillageEntrance entrance : village.getEntrances()) {
                    Map<String, Object> entranceMap = new LinkedHashMap<>();
                    entranceMap.put("x", entrance.getX());
                    entranceMap.put("y", entrance.getY());
                    entranceMap.put("z", entrance.getZ());
                    entranceMap.put("facing", entrance.getFacingName());
                    entranceMap.put("autoDetected", entrance.isAutoDetected());
                    entrancesList.add(entranceMap);
                }
                section.set("entrances", entrancesList);
                
                // Region ID
                if (village.getRegionId() != null) {
                    section.set("regionId", village.getRegionId());
                }
                
                // Timestamps
                section.set("createdAt", village.getCreatedAt().toString());
                section.set("updatedAt", village.getUpdatedAt().toString());
                
                // Save to disk
                save();
                
                // Update cache
                villageCache.put(village.getId(), village);
                
            } finally {
                lock.writeLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> loadVillage(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                // Check cache first
                if (cacheValid && villageCache.containsKey(id)) {
                    return Optional.of(villageCache.get(id));
                }
                
                String path = "villages." + id.toString();
                if (!config.isConfigurationSection(path)) {
                    return Optional.empty();
                }
                
                ConfigurationSection section = config.getConfigurationSection(path);
                Village village = deserializeVillage(id, section);
                
                if (village != null) {
                    villageCache.put(id, village);
                }
                
                return Optional.ofNullable(village);
                
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> loadVillageByBell(String worldName, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                
                return villageCache.values().stream()
                    .filter(v -> v.getWorldName().equals(worldName))
                    .filter(v -> v.getBellX() == x && v.getBellY() == y && v.getBellZ() == z)
                    .findFirst();
                    
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> loadVillageByChunk(String worldName, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                
                return villageCache.values().stream()
                    .filter(v -> v.getWorldName().equals(worldName))
                    .filter(v -> v.getChunkX() == chunkX && v.getChunkZ() == chunkZ)
                    .findFirst();
                    
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> loadVillagesInWorld(World world) {
        return loadVillagesInWorld(world.getName());
    }
    
    @Override
    public CompletableFuture<List<Village>> loadVillagesInWorld(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                
                return villageCache.values().stream()
                    .filter(v -> v.getWorldName().equals(worldName))
                    .collect(Collectors.toList());
                    
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> loadAllVillages() {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                return new ArrayList<>(villageCache.values());
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteVillage(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            lock.writeLock().lock();
            try {
                String path = "villages." + id.toString();
                if (!config.isConfigurationSection(path)) {
                    return false;
                }
                
                config.set(path, null);
                save();
                
                villageCache.remove(id);
                return true;
                
            } finally {
                lock.writeLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> villageExists(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                if (cacheValid) {
                    return villageCache.containsKey(id);
                }
                return config.isConfigurationSection("villages." + id.toString());
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    // ==================== Utility Operations ====================
    
    @Override
    public CompletableFuture<Integer> getVillageCount() {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                return villageCache.size();
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> getVillageCount(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                return (int) villageCache.values().stream()
                    .filter(v -> v.getWorldName().equals(worldName))
                    .count();
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> findVillagesNear(String worldName, int x, int z, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                
                int radiusSq = radius * radius;
                return villageCache.values().stream()
                    .filter(v -> v.getWorldName().equals(worldName))
                    .filter(v -> {
                        int dx = v.getBellX() - x;
                        int dz = v.getBellZ() - z;
                        return (dx * dx + dz * dz) <= radiusSq;
                    })
                    .collect(Collectors.toList());
                    
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> findVillageAt(String worldName, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                ensureCacheValid();
                
                return villageCache.values().stream()
                    .filter(v -> v.getWorldName().equals(worldName))
                    .filter(v -> v.hasBoundary() && v.getBoundary().contains(x, y, z))
                    .findFirst();
                    
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    // ==================== Backup and Migration ====================
    
    @Override
    public CompletableFuture<Void> backup(String backupPath) {
        return CompletableFuture.runAsync(() -> {
            lock.readLock().lock();
            try {
                File backupFile = new File(backupPath);
                Files.copy(storageFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Created backup at: " + backupPath);
            } catch (IOException e) {
                throw new StorageException("Failed to create backup", e);
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> exportAll() {
        return loadAllVillages();
    }
    
    @Override
    public CompletableFuture<Integer> importAll(List<Village> villages, boolean overwrite) {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            for (Village village : villages) {
                if (overwrite || !villageCache.containsKey(village.getId())) {
                    saveVillage(village).join();
                    count++;
                }
            }
            return count;
        });
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Saves the configuration to disk.
     */
    private void save() {
        try {
            config.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save villages.yml", e);
            throw new StorageException("Failed to save YAML storage", e);
        }
    }
    
    /**
     * Loads all villages into the cache.
     */
    private void loadCache() {
        villageCache.clear();
        
        ConfigurationSection villagesSection = config.getConfigurationSection("villages");
        if (villagesSection == null) {
            cacheValid = true;
            return;
        }
        
        for (String key : villagesSection.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection section = villagesSection.getConfigurationSection(key);
                Village village = deserializeVillage(id, section);
                if (village != null) {
                    villageCache.put(id, village);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid village UUID in storage: " + key);
            }
        }
        
        cacheValid = true;
    }
    
    /**
     * Ensures the cache is valid, loading if necessary.
     */
    private void ensureCacheValid() {
        if (!cacheValid) {
            loadCache();
        }
    }
    
    /**
     * Deserializes a village from a configuration section.
     */
    private Village deserializeVillage(UUID id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        
        try {
            String worldName = section.getString("world");
            String name = section.getString("name");
            
            // Bell location
            ConfigurationSection bellSection = section.getConfigurationSection("bell");
            if (bellSection == null) {
                return null;
            }
            int bellX = bellSection.getInt("x");
            int bellY = bellSection.getInt("y");
            int bellZ = bellSection.getInt("z");
            
            // Boundary
            VillageBoundary boundary = null;
            ConfigurationSection boundarySection = section.getConfigurationSection("boundary");
            if (boundarySection != null) {
                boundary = new VillageBoundary(
                    boundarySection.getInt("minX"),
                    boundarySection.getInt("minY"),
                    boundarySection.getInt("minZ"),
                    boundarySection.getInt("maxX"),
                    boundarySection.getInt("maxY"),
                    boundarySection.getInt("maxZ"),
                    boundarySection.getInt("centerX"),
                    boundarySection.getInt("centerY"),
                    boundarySection.getInt("centerZ")
                );
            }
            
            // Region ID
            String regionId = section.getString("regionId");
            
            // Timestamps
            Instant createdAt = parseInstant(section.getString("createdAt"));
            Instant updatedAt = parseInstant(section.getString("updatedAt"));
            
            // Create village
            Village village = new Village(id, worldName, name, bellX, bellY, bellZ,
                boundary, regionId, createdAt, updatedAt);
            
            // POIs
            List<?> poisList = section.getList("pois");
            if (poisList != null) {
                List<VillagePoi> pois = new ArrayList<>();
                for (Object poiObj : poisList) {
                    if (poiObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> poiMap = (Map<String, Object>) poiObj;
                        String type = (String) poiMap.get("type");
                        int px = ((Number) poiMap.get("x")).intValue();
                        int py = ((Number) poiMap.get("y")).intValue();
                        int pz = ((Number) poiMap.get("z")).intValue();
                        pois.add(new VillagePoi(type, px, py, pz));
                    }
                }
                village.setPois(pois);
            }
            
            // Entrances
            List<?> entrancesList = section.getList("entrances");
            if (entrancesList != null) {
                List<VillageEntrance> entrances = new ArrayList<>();
                for (Object entranceObj : entrancesList) {
                    if (entranceObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> entranceMap = (Map<String, Object>) entranceObj;
                        int ex = ((Number) entranceMap.get("x")).intValue();
                        int ey = ((Number) entranceMap.get("y")).intValue();
                        int ez = ((Number) entranceMap.get("z")).intValue();
                        String facing = (String) entranceMap.get("facing");
                        boolean autoDetected = (Boolean) entranceMap.getOrDefault("autoDetected", false);
                        entrances.add(new VillageEntrance(ex, ey, ez, 
                            VillageEntrance.faceFromString(facing), autoDetected));
                    }
                }
                village.setEntrances(entrances);
            }
            
            return village;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize village: " + id, e);
            return null;
        }
    }
    
    /**
     * Parses an ISO instant string.
     */
    private Instant parseInstant(String str) {
        if (str == null || str.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(str);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}

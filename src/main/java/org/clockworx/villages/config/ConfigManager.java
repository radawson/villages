package org.clockworx.villages.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.storage.StorageManager;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages plugin configuration with typed access to all settings.
 * 
 * This class provides:
 * - Typed getters for all configuration values
 * - Default value fallbacks
 * - Configuration reload support
 * - Runtime modification of debug settings
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class ConfigManager {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    private FileConfiguration config;
    
    // Cached values for frequently accessed settings
    private boolean debugEnabled;
    private boolean debugVerbose;
    private boolean debugLogStorage;
    private boolean debugLogRegions;
    private boolean debugLogBoundaries;
    private boolean debugLogEntrances;
    
    /**
     * Creates a new ConfigManager.
     * 
     * @param plugin The plugin instance
     */
    public ConfigManager(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        reload();
    }
    
    /**
     * Reloads the configuration from disk.
     * Call this after plugin.reloadConfig().
     */
    public void reload() {
        logger.debug(LogCategory.GENERAL, "Reloading configuration");
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadDebugSettings();
        logger.debug(LogCategory.GENERAL, "Configuration reloaded - Debug: " + debugEnabled + 
            ", Verbose: " + debugVerbose);
    }
    
    /**
     * Saves the current configuration to disk.
     */
    public void save() {
        plugin.saveConfig();
    }
    
    /**
     * Loads and caches debug settings for fast access.
     */
    private void loadDebugSettings() {
        this.debugEnabled = config.getBoolean("debug.enabled", false);
        this.debugVerbose = config.getBoolean("debug.verbose", false);
        this.debugLogStorage = config.getBoolean("debug.log-storage", false);
        this.debugLogRegions = config.getBoolean("debug.log-regions", false);
        this.debugLogBoundaries = config.getBoolean("debug.log-boundaries", false);
        this.debugLogEntrances = config.getBoolean("debug.log-entrances", false);
    }
    
    // ==================== Storage Configuration ====================
    
    /**
     * Gets the configured storage type.
     * 
     * @return The storage type (yaml, sqlite, mysql)
     */
    public StorageManager.StorageType getStorageType() {
        String type = config.getString("storage.type", "sqlite");
        return StorageManager.StorageType.fromId(type);
    }
    
    /**
     * Gets the SQLite database file name.
     * 
     * @return The SQLite file name
     */
    public String getSQLiteFile() {
        return config.getString("storage.sqlite.file", "villages.db");
    }
    
    /**
     * Gets MySQL configuration as a record.
     * 
     * @return MySQL configuration
     */
    public MySQLConfig getMySQLConfig() {
        return new MySQLConfig(
            config.getString("storage.mysql.host", "localhost"),
            config.getInt("storage.mysql.port", 3306),
            config.getString("storage.mysql.database", "villages"),
            config.getString("storage.mysql.username", "minecraft"),
            config.getString("storage.mysql.password", ""),
            config.getInt("storage.mysql.pool-size", 10),
            config.getString("storage.mysql.prefix", "")
        );
    }
    
    /**
     * MySQL configuration record.
     */
    public record MySQLConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize,
        String prefix
    ) {}
    
    // ==================== Region Configuration ====================
    
    /**
     * Checks if automatic region creation is enabled.
     * 
     * @return true if auto-create is enabled
     */
    public boolean isAutoCreateRegions() {
        return config.getBoolean("regions.auto-create", false);
    }
    
    /**
     * Gets the preferred region provider.
     * 
     * @return Provider name (worldguard, regionguard, auto)
     */
    public String getPreferredRegionProvider() {
        return config.getString("regions.provider", "auto");
    }
    
    /**
     * Gets the default flags for new village regions.
     * 
     * @return Map of flag names to values
     */
    public Map<String, String> getDefaultFlags() {
        Map<String, String> flags = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("regions.default-flags");
        
        if (section != null) {
            for (String key : section.getKeys(false)) {
                flags.put(key, section.getString(key));
            }
        }
        
        return flags;
    }
    
    // ==================== Detection Configuration ====================
    
    /**
     * Checks if automatic village detection is enabled.
     * 
     * @return true if auto-detect is enabled
     */
    public boolean isAutoDetectVillages() {
        return config.getBoolean("detection.auto-detect", true);
    }
    
    /**
     * Gets the boundary recalculation interval in ticks.
     * 
     * @return Interval in ticks (0 to disable)
     */
    public int getRecalculateInterval() {
        return config.getInt("detection.recalculate-interval", 72000);
    }
    
    /**
     * Gets the minimum distance between villages.
     * 
     * @return Distance in blocks
     */
    public int getMinVillageDistance() {
        return config.getInt("detection.min-village-distance", 97);
    }
    
    // ==================== Entrance Configuration ====================
    
    /**
     * Checks if automatic entrance detection is enabled.
     * 
     * @return true if auto-detect is enabled
     */
    public boolean isAutoDetectEntrances() {
        return config.getBoolean("entrances.auto-detect", true);
    }
    
    /**
     * Gets the list of materials considered as path blocks.
     * 
     * @return List of path materials
     */
    public List<Material> getPathBlockMaterials() {
        List<Material> materials = new ArrayList<>();
        List<String> names = config.getStringList("entrances.path-blocks");
        
        for (String name : names) {
            try {
                Material material = Material.valueOf(name.toUpperCase());
                materials.add(material);
            } catch (IllegalArgumentException e) {
                logger.warning(LogCategory.GENERAL, "Invalid path block material: " + name);
            }
        }
        
        // Default if empty
        if (materials.isEmpty()) {
            materials.add(Material.DIRT_PATH);
            materials.add(Material.COBBLESTONE);
            materials.add(Material.GRAVEL);
        }
        
        return materials;
    }
    
    /**
     * Gets the minimum path width for entrance detection.
     * 
     * @return Minimum width in blocks
     */
    public int getMinPathWidth() {
        return config.getInt("entrances.min-path-width", 2);
    }
    
    // ==================== Signs Configuration ====================
    
    /**
     * Checks if automatic sign placement is enabled.
     * 
     * @return true if auto-place is enabled
     */
    public boolean isAutoPlaceSigns() {
        return config.getBoolean("signs.auto-place", true);
    }
    
    /**
     * Gets the sign material.
     * 
     * @return Sign material
     */
    public Material getSignMaterial() {
        String name = config.getString("signs.material", "OAK_WALL_SIGN");
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.OAK_WALL_SIGN;
        }
    }
    
    /**
     * Gets the sign text lines.
     * 
     * @return Array of 4 lines
     */
    public String[] getSignLines() {
        List<String> lines = config.getStringList("signs.lines");
        String[] result = new String[4];
        
        for (int i = 0; i < 4; i++) {
            result[i] = (i < lines.size()) ? lines.get(i) : "";
        }
        
        return result;
    }
    
    // ==================== Performance Configuration ====================
    
    /**
     * Checks if caching is enabled.
     * 
     * @return true if caching is enabled
     */
    public boolean isCacheEnabled() {
        return config.getBoolean("performance.enable-cache", true);
    }
    
    /**
     * Gets the maximum cache size.
     * 
     * @return Max cache size (0 for unlimited)
     */
    public int getCacheSize() {
        return config.getInt("performance.cache-size", 1000);
    }
    
    /**
     * Checks if async storage is enabled.
     * 
     * @return true if async storage is enabled
     */
    public boolean isAsyncStorage() {
        return config.getBoolean("performance.async-storage", true);
    }
    
    // ==================== Debug Configuration ====================
    
    /**
     * Checks if debug logging is enabled.
     * 
     * @return true if debug is enabled
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    /**
     * Sets the debug enabled state.
     * This persists to config and takes effect immediately.
     * 
     * @param enabled true to enable debug logging
     */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        config.set("debug.enabled", enabled);
        save();
    }
    
    /**
     * Checks if verbose logging is enabled.
     * 
     * @return true if verbose is enabled
     */
    public boolean isVerbose() {
        return debugVerbose;
    }
    
    /**
     * Sets the verbose logging state.
     * 
     * @param verbose true to enable verbose logging
     */
    public void setVerbose(boolean verbose) {
        this.debugVerbose = verbose;
        config.set("debug.verbose", verbose);
        save();
    }
    
    /**
     * Checks if storage operation logging is enabled.
     * 
     * @return true if storage logging is enabled
     */
    public boolean shouldLogStorage() {
        return debugEnabled && debugLogStorage;
    }
    
    /**
     * Sets the storage logging state.
     * 
     * @param enabled true to enable storage logging
     */
    public void setLogStorage(boolean enabled) {
        this.debugLogStorage = enabled;
        config.set("debug.log-storage", enabled);
        save();
    }
    
    /**
     * Checks if region operation logging is enabled.
     * 
     * @return true if region logging is enabled
     */
    public boolean shouldLogRegions() {
        return debugEnabled && debugLogRegions;
    }
    
    /**
     * Sets the region logging state.
     * 
     * @param enabled true to enable region logging
     */
    public void setLogRegions(boolean enabled) {
        this.debugLogRegions = enabled;
        config.set("debug.log-regions", enabled);
        save();
    }
    
    /**
     * Checks if boundary calculation logging is enabled.
     * 
     * @return true if boundary logging is enabled
     */
    public boolean shouldLogBoundaries() {
        return debugEnabled && debugLogBoundaries;
    }
    
    /**
     * Sets the boundary logging state.
     * 
     * @param enabled true to enable boundary logging
     */
    public void setLogBoundaries(boolean enabled) {
        this.debugLogBoundaries = enabled;
        config.set("debug.log-boundaries", enabled);
        save();
    }
    
    /**
     * Checks if entrance detection logging is enabled.
     * 
     * @return true if entrance logging is enabled
     */
    public boolean shouldLogEntrances() {
        return debugEnabled && debugLogEntrances;
    }
    
    /**
     * Sets the entrance logging state.
     * 
     * @param enabled true to enable entrance logging
     */
    public void setLogEntrances(boolean enabled) {
        this.debugLogEntrances = enabled;
        config.set("debug.log-entrances", enabled);
        save();
    }
    
    /**
     * Gets a formatted string showing current debug state.
     * 
     * @return Debug status string
     */
    public String getDebugStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug: ").append(debugEnabled ? "ON" : "OFF");
        
        if (debugEnabled) {
            sb.append(" [");
            List<String> active = new ArrayList<>();
            if (debugVerbose) active.add("verbose");
            if (debugLogStorage) active.add("storage");
            if (debugLogRegions) active.add("regions");
            if (debugLogBoundaries) active.add("boundaries");
            if (debugLogEntrances) active.add("entrances");
            
            if (active.isEmpty()) {
                sb.append("no categories");
            } else {
                sb.append(String.join(", ", active));
            }
            sb.append("]");
        }
        
        return sb.toString();
    }
}

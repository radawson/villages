package org.clockworx.villages;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.clockworx.villages.boundary.VillageBoundaryCalculator;
import org.clockworx.villages.commands.VillageCommands;
import org.clockworx.villages.config.ConfigManager;
import org.clockworx.villages.detection.EntranceDetector;
import org.clockworx.villages.detection.EntranceMarker;
import org.clockworx.villages.listeners.VillageChunkListener;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.managers.VillageManager;
import org.clockworx.villages.regions.RegionManager;
import org.clockworx.villages.signs.WelcomeSignPlacer;
import org.clockworx.villages.storage.StorageManager;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

/**
 * Main plugin class for the Villages plugin.
 * 
 * This plugin provides comprehensive village management for Minecraft servers:
 * - Automatic village detection using Minecraft's native POI system
 * - Accurate boundary calculation based on beds, bells, and job sites
 * - Multiple storage backends (YAML, SQLite, MySQL)
 * - Region plugin integration (WorldGuard, RegionGuard)
 * - Entrance detection and welcome signs
 * - Village leadership (mayors and councils)
 * - Hero of the Village tracking
 * 
 * @author Clockworx
 * @version {$version}
 */
public class VillagesPlugin extends JavaPlugin {
    
    // Configuration
    private ConfigManager configManager;
    private PluginLogger pluginLogger;
    
    /**
     * Sets the ConfigManager (called by bootstrapper).
     * 
     * @param configManager The ConfigManager instance
     */
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    /**
     * Sets the PluginLogger (called by bootstrapper).
     * 
     * @param pluginLogger The PluginLogger instance
     */
    public void setPluginLogger(PluginLogger pluginLogger) {
        this.pluginLogger = pluginLogger;
    }
    
    // Core managers
    private StorageManager storageManager;
    private VillageManager villageManager;
    private SignManager signManager;
    private RegionManager regionManager;
    
    // Detection components
    private VillageBoundaryCalculator boundaryCalculator;
    private EntranceDetector entranceDetector;
    private EntranceMarker entranceMarker;
    private WelcomeSignPlacer welcomeSignPlacer;
    
    // Listeners
    private VillageChunkListener chunkListener;
    
    /**
     * Called when the plugin is loaded (before onEnable).
     * CommandAPI 11.1.0 requires initialization in onLoad() before commands can be registered.
     */
    @Override
    public void onLoad() {
        // Initialize CommandAPI with Paper configuration
        // This must be called before registering any commands
        CommandAPI.onLoad(new CommandAPIPaperConfig(this));
    }
    
    /**
     * Called when the plugin is enabled.
     * Initializes configuration, managers, listeners, and commands.
     */
    @Override
    public void onEnable() {
        // ===== Phase 1: Configuration =====
        // Save default config.yml if it doesn't exist
        saveDefaultConfig();
        
        // ConfigManager and PluginLogger are already initialized by the bootstrapper
        // If they weren't (e.g., if bootstrapper wasn't used), create them here as fallback
        if (configManager == null) {
            this.configManager = new ConfigManager(this);
        }
        if (pluginLogger == null) {
            this.pluginLogger = new PluginLogger(this, configManager);
            configManager.setLogger(pluginLogger);
        }
        
        pluginLogger.info("Initializing Villages plugin...");
        pluginLogger.debug(LogCategory.GENERAL, "Debug logging enabled: " + configManager.getDebugStatus());
        
        // ===== Phase 2: Storage =====
        // Initialize storage manager (SQLite, MySQL, or YAML based on config)
        this.storageManager = new StorageManager(this);
        try {
            storageManager.initialize().join();
            pluginLogger.info("Storage initialized: " + storageManager.getActiveType().name());
        } catch (Exception e) {
            pluginLogger.severe("Failed to initialize storage manager", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // ===== Phase 3: Detection Components =====
        // Initialize boundary calculator (required by VillageManager)
        this.boundaryCalculator = new VillageBoundaryCalculator(this);
        pluginLogger.debug(LogCategory.BOUNDARY, "Boundary calculator ready");
        
        // Initialize entrance detection
        this.entranceDetector = new EntranceDetector(this);
        this.entranceMarker = new EntranceMarker(this);
        pluginLogger.debug(LogCategory.ENTRANCE, "Entrance detection ready");
        
        // Initialize welcome sign placer
        this.welcomeSignPlacer = new WelcomeSignPlacer(this);
        pluginLogger.debug(LogCategory.GENERAL, "Welcome sign placer ready");
        
        // ===== Phase 4: Core Managers =====
        // Initialize VillageManager with storage and boundary calculator
        this.villageManager = new VillageManager(this, storageManager, boundaryCalculator);
        this.signManager = new SignManager(this);
        
        // Initialize region manager
        this.regionManager = new RegionManager(this);
        try {
            regionManager.initialize().join();
            pluginLogger.info("Region manager initialized: " + regionManager.getProviderName());
        } catch (Exception e) {
            pluginLogger.warning("Failed to initialize region manager: " + e.getMessage());
        }
        
        // ===== Phase 5: Event Listeners =====
        this.chunkListener = new VillageChunkListener(villageManager, signManager, this);
        getServer().getPluginManager().registerEvents(chunkListener, this);
        pluginLogger.debug(LogCategory.GENERAL, "Event listeners registered");
        
        // ===== Phase 6: Commands =====
        CommandAPI.onEnable();
        
        // Register the VillageCommands
        VillageCommands commands = new VillageCommands(
            this,
            storageManager,
            regionManager,
            boundaryCalculator,
            entranceDetector,
            entranceMarker,
            welcomeSignPlacer,
            signManager
        );
        commands.register();
        pluginLogger.debug(LogCategory.COMMAND, "Commands registered");
        
        // ===== Startup Complete =====
        pluginLogger.info("Villages plugin v" + getPluginMeta().getVersion() + " enabled successfully!");
        pluginLogger.info("Storage: " + storageManager.getActiveType().name() + 
                         " | Regions: " + regionManager.getProviderName());
        
        if (configManager.isDebugEnabled()) {
            pluginLogger.info("Debug mode is ENABLED - " + configManager.getDebugStatus());
        }
    }
    
    /**
     * Called when the plugin is disabled.
     * Performs cleanup and saves data.
     */
    @Override
    public void onDisable() {
        if (pluginLogger != null) {
            pluginLogger.info("Disabling Villages plugin...");
        }
        
        // Shutdown storage manager
        if (storageManager != null) {
            try {
                storageManager.shutdown().join();
                if (pluginLogger != null) {
                    pluginLogger.info("Storage manager shut down successfully");
                }
            } catch (Exception e) {
                if (pluginLogger != null) {
                    pluginLogger.severe("Error shutting down storage manager", e);
                }
            }
        }
        
        // Shutdown region manager
        if (regionManager != null) {
            try {
                regionManager.shutdown().join();
                if (pluginLogger != null) {
                    pluginLogger.debug(LogCategory.REGION, "Region manager shut down");
                }
            } catch (Exception e) {
                if (pluginLogger != null) {
                    pluginLogger.warning("Error shutting down region manager: " + e.getMessage());
                }
            }
        }
        
        // Unregister CommandAPI commands
        CommandAPI.onDisable();
        
        if (pluginLogger != null) {
            pluginLogger.info("Villages plugin disabled.");
            // Shutdown file logging - must be last to capture all log messages
            pluginLogger.shutdown();
        }
    }
    
    /**
     * Reloads the plugin configuration.
     * Called by /village reload command.
     */
    public void reloadPluginConfig() {
        reloadConfig();
        configManager.reload();
        
        // Reload component configurations
        if (entranceDetector != null) {
            entranceDetector.reload();
        }
        if (entranceMarker != null) {
            entranceMarker.reload();
        }
        if (welcomeSignPlacer != null) {
            welcomeSignPlacer.reload();
        }
        
        if (pluginLogger != null) {
            pluginLogger.info("Configuration reloaded");
        }
    }
    
    // ==================== Getters ====================
    
    /**
     * Gets the ConfigManager instance.
     * 
     * @return The ConfigManager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Gets the PluginLogger instance.
     * 
     * @return The PluginLogger
     */
    public PluginLogger getPluginLogger() {
        return pluginLogger;
    }
    
    /**
     * Gets the VillageManager instance.
     * 
     * @return The VillageManager
     */
    public VillageManager getVillageManager() {
        return villageManager;
    }
    
    /**
     * Gets the SignManager instance.
     * 
     * @return The SignManager
     */
    public SignManager getSignManager() {
        return signManager;
    }
    
    /**
     * Gets the StorageManager instance.
     * 
     * @return The StorageManager
     */
    public StorageManager getStorageManager() {
        return storageManager;
    }
    
    /**
     * Gets the RegionManager instance.
     * 
     * @return The RegionManager
     */
    public RegionManager getRegionManager() {
        return regionManager;
    }
    
    /**
     * Gets the VillageBoundaryCalculator instance.
     * 
     * @return The VillageBoundaryCalculator
     */
    public VillageBoundaryCalculator getBoundaryCalculator() {
        return boundaryCalculator;
    }
    
    /**
     * Gets the EntranceDetector instance.
     * 
     * @return The EntranceDetector
     */
    public EntranceDetector getEntranceDetector() {
        return entranceDetector;
    }
    
    /**
     * Gets the EntranceMarker instance.
     * 
     * @return The EntranceMarker
     */
    public EntranceMarker getEntranceMarker() {
        return entranceMarker;
    }
    
    /**
     * Gets the WelcomeSignPlacer instance.
     * 
     * @return The WelcomeSignPlacer
     */
    public WelcomeSignPlacer getWelcomeSignPlacer() {
        return welcomeSignPlacer;
    }
}

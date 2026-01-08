package org.clockworx.villages.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Integration with BlueMap for displaying village markers on the web map.
 * 
 * This class uses reflection to access BlueMap's API, allowing the plugin
 * to work even if BlueMap is not installed or uses a different API version.
 * 
 * BlueMap 5.15+ API structure:
 * - BlueMapAPI.getInstance() -> Optional<BlueMapAPI>
 * - BlueMapAPI.getWorld(world) -> Optional<BlueMapWorld>
 * - BlueMapWorld.getMaps() -> Collection<BlueMapMap>
 * - BlueMapMap.getMarkerSets() -> Map<String, MarkerSet>
 * - MarkerSet.put(id, marker) -> add marker
 * 
 * @author Clockworx
 * @since 0.3.0
 */
public class BlueMapIntegration {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    private final BlueMapMarkerManager markerManager;
    
    private boolean enabled;
    private Object blueMapApi;
    
    /**
     * Creates a new BlueMapIntegration instance.
     * 
     * @param plugin The Villages plugin instance
     */
    public BlueMapIntegration(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.markerManager = new BlueMapMarkerManager(plugin);
        this.enabled = false;
    }
    
    /**
     * Initializes the BlueMap integration.
     * Checks if BlueMap is installed and attempts to access its API.
     * 
     * @return true if BlueMap integration was successfully initialized
     */
    public boolean initialize() {
        logger.debug(LogCategory.GENERAL, "Initializing BlueMap integration...");
        
        // Check if BlueMap integration is enabled in config
        if (!plugin.getConfigManager().isBlueMapEnabled()) {
            logger.debug(LogCategory.GENERAL, "BlueMap integration is disabled in config");
            return false;
        }
        
        logger.debug(LogCategory.GENERAL, "BlueMap integration enabled in config, checking for BlueMap plugin");
        
        // Check if BlueMap plugin is installed
        Plugin blueMapPlugin = Bukkit.getPluginManager().getPlugin("BlueMap");
        if (blueMapPlugin == null) {
            logger.debug(LogCategory.GENERAL, "BlueMap plugin not found, integration disabled");
            return false;
        }
        
        String version = blueMapPlugin.getPluginMeta() != null ? 
            blueMapPlugin.getPluginMeta().getVersion() : "unknown";
        logger.debug(LogCategory.GENERAL, "BlueMap plugin found: " + blueMapPlugin.getName() + " v" + version);
        
        if (!blueMapPlugin.isEnabled()) {
            logger.debug(LogCategory.GENERAL, "BlueMap plugin is not enabled");
            return false;
        }
        
        logger.debug(LogCategory.GENERAL, "BlueMap plugin is enabled, attempting to access API");
        
        // Try to get BlueMap API using reflection
        try {
            // Try BlueMapAPI.getInstance() - this is the BlueMap 5.15+ way
            blueMapApi = getBlueMapApi();
            
            if (blueMapApi == null) {
                logger.warning(LogCategory.GENERAL, "Could not access BlueMap API. Integration disabled.");
                logger.warning(LogCategory.GENERAL, "BlueMap API structure may have changed. Please check compatibility.");
                return false;
            }
            
            logger.debug(LogCategory.GENERAL, "Found BlueMap API: " + blueMapApi.getClass().getName());
            
            // Initialize marker manager with BlueMap API
            logger.debug(LogCategory.GENERAL, "Initializing BlueMap marker manager...");
            if (markerManager.initialize(blueMapApi)) {
                this.enabled = true;
                logger.info(LogCategory.GENERAL, "BlueMap integration enabled successfully");
                logger.debug(LogCategory.GENERAL, "BlueMap integration ready - markers will be created for villages");
                return true;
            } else {
                logger.warning(LogCategory.GENERAL, "Failed to initialize BlueMap marker manager");
                return false;
            }
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error initializing BlueMap integration: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "BlueMap integration initialization error", e);
            }
            return false;
        }
    }
    
    /**
     * Attempts to get the BlueMap API instance.
     * Tries BlueMapAPI.getInstance() which returns Optional<BlueMapAPI>.
     * 
     * @return The BlueMap API instance, or null if not found
     */
    private Object getBlueMapApi() {
        try {
            logger.debug(LogCategory.GENERAL, "Trying BlueMapAPI.getInstance()...");
            
            // Try to get BlueMapAPI class
            Class<?> blueMapApiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            
            // Get getInstance() method
            Method getInstance = blueMapApiClass.getMethod("getInstance");
            
            // Invoke getInstance() - returns Optional<BlueMapAPI>
            Object optional = getInstance.invoke(null);
            
            if (optional == null) {
                logger.debug(LogCategory.GENERAL, "BlueMapAPI.getInstance() returned null");
                return null;
            }
            
            logger.debug(LogCategory.GENERAL, "BlueMapAPI.getInstance() returned: " + optional.getClass().getName());
            
            // Try to get the value from Optional
            try {
                Method isPresent = optional.getClass().getMethod("isPresent");
                Boolean present = (Boolean) isPresent.invoke(optional);
                
                if (!present) {
                    logger.debug(LogCategory.GENERAL, "BlueMapAPI.getInstance() returned empty Optional");
                    return null;
                }
                
                Method get = optional.getClass().getMethod("get");
                Object api = get.invoke(optional);
                
                if (api != null) {
                    logger.debug(LogCategory.GENERAL, "Found BlueMap API via BlueMapAPI.getInstance()");
                    return api;
                }
            } catch (NoSuchMethodException e) {
                // Optional API might not be available, try direct access
                logger.debug(LogCategory.GENERAL, "Optional methods not found, trying direct access");
                // If it's not an Optional, it might be the API directly
                if (blueMapApiClass.isInstance(optional)) {
                    return optional;
                }
            }
            
        } catch (ClassNotFoundException e) {
            logger.debug(LogCategory.GENERAL, "BlueMapAPI class not found: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            logger.debug(LogCategory.GENERAL, "BlueMapAPI.getInstance() method not found: " + e.getMessage());
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error accessing BlueMap API: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "BlueMap API access error", e);
            }
        }
        
        return null;
    }
    
    /**
     * Shuts down the BlueMap integration.
     */
    public void shutdown() {
        if (enabled) {
            logger.debug(LogCategory.GENERAL, "Shutting down BlueMap integration...");
            markerManager.shutdown();
            enabled = false;
            logger.debug(LogCategory.GENERAL, "BlueMap integration shut down");
        }
    }
    
    /**
     * Checks if the BlueMap integration is enabled.
     * 
     * @return true if integration is enabled and working
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the marker manager instance.
     * 
     * @return The marker manager
     */
    public BlueMapMarkerManager getMarkerManager() {
        return markerManager;
    }
    
    /**
     * Reloads the BlueMap integration configuration.
     */
    public void reload() {
        if (enabled) {
            logger.debug(LogCategory.GENERAL, "Reloading BlueMap integration configuration...");
            markerManager.reload();
            logger.debug(LogCategory.GENERAL, "BlueMap integration configuration reloaded");
        }
    }
}

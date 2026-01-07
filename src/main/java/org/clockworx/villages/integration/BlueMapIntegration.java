package org.clockworx.villages.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.lang.reflect.Method;

/**
 * Integration with BlueMap for displaying village markers on the web map.
 * 
 * This class uses reflection to access BlueMap's API, allowing the plugin
 * to work even if BlueMap is not installed or uses a different API version.
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
    private Object markerApi;
    
    /**
     * Creates a new BlueMapIntegration instance.
     * 
     * @param plugin The Villages plugin instance
     */
    public BlueMapIntegration(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.markerManager = new BlueMapMarkerManager(plugin, this);
        this.enabled = false;
    }
    
    /**
     * Initializes the BlueMap integration.
     * Checks if BlueMap is installed and attempts to access its API.
     * 
     * @return true if BlueMap integration was successfully initialized
     */
    public boolean initialize() {
        // Check if BlueMap integration is enabled in config
        if (!plugin.getConfigManager().isBlueMapEnabled()) {
            logger.debug(LogCategory.GENERAL, "BlueMap integration is disabled in config");
            return false;
        }
        
        // Check if BlueMap plugin is installed
        Plugin blueMapPlugin = Bukkit.getPluginManager().getPlugin("BlueMap");
        if (blueMapPlugin == null) {
            logger.debug(LogCategory.GENERAL, "BlueMap plugin not found, integration disabled");
            return false;
        }
        
        if (!blueMapPlugin.isEnabled()) {
            logger.debug(LogCategory.GENERAL, "BlueMap plugin is not enabled");
            return false;
        }
        
        logger.debug(LogCategory.GENERAL, "BlueMap plugin found, attempting to access API");
        
        // Try to get BlueMap API using reflection
        try {
            // Try common API access patterns
            blueMapApi = getBlueMapApi(blueMapPlugin);
            
            if (blueMapApi == null) {
                logger.warning("Could not access BlueMap API. Integration disabled.");
                logger.warning("BlueMap API structure may have changed. Please check compatibility.");
                return false;
            }
            
            // Try to get MarkerAPI
            markerApi = getMarkerApi(blueMapApi);
            
            if (markerApi == null) {
                logger.warning("Could not access BlueMap MarkerAPI. Integration disabled.");
                return false;
            }
            
            // Initialize marker manager
            if (markerManager.initialize(markerApi)) {
                this.enabled = true;
                logger.info("BlueMap integration enabled successfully");
                return true;
            } else {
                logger.warning("Failed to initialize BlueMap marker manager");
                return false;
            }
            
        } catch (Exception e) {
            logger.warning("Error initializing BlueMap integration: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "BlueMap integration error: " + e.getMessage(), e);
            }
            return false;
        }
    }
    
    /**
     * Attempts to get the BlueMap API instance using reflection.
     * Tries multiple common API access patterns.
     * 
     * @param blueMapPlugin The BlueMap plugin instance
     * @return The BlueMap API object, or null if not found
     */
    private Object getBlueMapApi(Plugin blueMapPlugin) {
        try {
            // Try pattern 1: blueMapPlugin.getBlueMap() or similar
            try {
                Method getBlueMap = blueMapPlugin.getClass().getMethod("getBlueMap");
                Object result = getBlueMap.invoke(blueMapPlugin);
                if (result != null) {
                    logger.debug(LogCategory.GENERAL, "Found BlueMap API via getBlueMap()");
                    return result;
                }
            } catch (NoSuchMethodException ignored) {
                // Try next pattern
            }
            
            // Try pattern 2: BlueMapAPI.getInstance()
            try {
                Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
                Method getInstance = apiClass.getMethod("getInstance");
                Object result = getInstance.invoke(null);
                if (result != null) {
                    logger.debug(LogCategory.GENERAL, "Found BlueMap API via BlueMapAPI.getInstance()");
                    return result;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Try next pattern
            }
            
            // Try pattern 3: Access via plugin's main class
            try {
                Class<?> mainClass = blueMapPlugin.getClass();
                // Look for a method that returns an API instance
                for (Method method : mainClass.getMethods()) {
                    if (method.getParameterCount() == 0 && 
                        method.getReturnType().getName().contains("BlueMap")) {
                        Object result = method.invoke(blueMapPlugin);
                        if (result != null) {
                            logger.debug(LogCategory.GENERAL, "Found BlueMap API via " + method.getName());
                            return result;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Continue
            }
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error accessing BlueMap API: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Attempts to get the MarkerAPI from the BlueMap API instance.
     * 
     * @param blueMapApi The BlueMap API instance
     * @return The MarkerAPI object, or null if not found
     */
    private Object getMarkerApi(Object blueMapApi) {
        try {
            // Try pattern 1: blueMapApi.getMarkerAPI()
            try {
                Method getMarkerAPI = blueMapApi.getClass().getMethod("getMarkerAPI");
                Object result = getMarkerAPI.invoke(blueMapApi);
                if (result != null) {
                    logger.debug(LogCategory.GENERAL, "Found MarkerAPI via getMarkerAPI()");
                    return result;
                }
            } catch (NoSuchMethodException ignored) {
                // Try next pattern
            }
            
            // Try pattern 2: MarkerAPI.getInstance()
            try {
                Class<?> markerApiClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerAPI");
                Method getInstance = markerApiClass.getMethod("getInstance");
                Object result = getInstance.invoke(null);
                if (result != null) {
                    logger.debug(LogCategory.GENERAL, "Found MarkerAPI via MarkerAPI.getInstance()");
                    return result;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Try next pattern
            }
            
            // Try pattern 3: Access via API instance methods
            try {
                for (Method method : blueMapApi.getClass().getMethods()) {
                    String methodName = method.getName().toLowerCase();
                    if ((methodName.contains("marker") || methodName.contains("api")) && 
                        method.getParameterCount() == 0) {
                        Object result = method.invoke(blueMapApi);
                        if (result != null && result.getClass().getName().contains("Marker")) {
                            logger.debug(LogCategory.GENERAL, "Found MarkerAPI via " + method.getName());
                            return result;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Continue
            }
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error accessing MarkerAPI: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Shuts down the BlueMap integration.
     */
    public void shutdown() {
        if (enabled) {
            markerManager.shutdown();
            enabled = false;
            logger.debug(LogCategory.GENERAL, "BlueMap integration shut down");
        }
    }
    
    /**
     * Checks if BlueMap integration is enabled and working.
     * 
     * @return true if integration is active
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the BlueMap API instance (for internal use by marker manager).
     * 
     * @return The BlueMap API object, or null if not available
     */
    Object getBlueMapApi() {
        return blueMapApi;
    }
    
    /**
     * Gets the MarkerAPI instance (for internal use by marker manager).
     * 
     * @return The MarkerAPI object, or null if not available
     */
    Object getMarkerApi() {
        return markerApi;
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
     * Reinitializes if needed.
     */
    public void reload() {
        if (enabled) {
            markerManager.reload();
        } else {
            // Try to initialize if it wasn't enabled before
            initialize();
        }
    }
}

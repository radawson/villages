package org.clockworx.villages.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
            // Try common API access patterns
            blueMapApi = getBlueMapApi(blueMapPlugin);
            
            if (blueMapApi == null) {
                logger.warning(LogCategory.GENERAL, "Could not access BlueMap API. Integration disabled.");
                logger.warning(LogCategory.GENERAL, "BlueMap API structure may have changed. Please check compatibility.");
                return false;
            }
            
            // Try to get MarkerAPI - try multiple approaches
            markerApi = getMarkerApi(blueMapApi);
            
            // If that fails, try getting it directly from the plugin
            if (markerApi == null) {
                logger.debug(LogCategory.GENERAL, "MarkerAPI not found via BlueMap API, trying direct plugin access...");
                markerApi = getMarkerApiFromPlugin(blueMapPlugin);
            }
            
            if (markerApi == null) {
                logger.warning(LogCategory.GENERAL, "Could not access BlueMap MarkerAPI. Integration disabled.");
                logger.warning(LogCategory.GENERAL, "Please check BlueMap version compatibility. BlueMap v5.15 may use a different API structure.");
                return false;
            }
            
            // Initialize marker manager
            logger.debug(LogCategory.GENERAL, "Initializing BlueMap marker manager...");
            if (markerManager.initialize(markerApi)) {
                this.enabled = true;
                logger.info(LogCategory.GENERAL, "BlueMap integration enabled successfully");
                logger.debug(LogCategory.GENERAL, "BlueMap integration ready - markers will be created for villages");
                return true;
            } else {
                logger.warning(LogCategory.GENERAL, "Failed to initialize BlueMap marker manager");
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
            logger.debug(LogCategory.GENERAL, "Attempting to access MarkerAPI from BlueMap API class: " + blueMapApi.getClass().getName());
            
            // Try pattern 1: blueMapApi.getMarkerAPI()
            try {
                logger.debug(LogCategory.GENERAL, "Trying getMarkerAPI() method...");
                Method getMarkerAPI = blueMapApi.getClass().getMethod("getMarkerAPI");
                Object result = getMarkerAPI.invoke(blueMapApi);
                if (result != null) {
                    logger.debug(LogCategory.GENERAL, "Found MarkerAPI via getMarkerAPI() - class: " + result.getClass().getName());
                    return result;
                }
                logger.debug(LogCategory.GENERAL, "getMarkerAPI() returned null");
            } catch (NoSuchMethodException e) {
                logger.debug(LogCategory.GENERAL, "getMarkerAPI() method not found");
            }
            
            // Try pattern 2: MarkerAPI.getInstance()
            try {
                logger.debug(LogCategory.GENERAL, "Trying MarkerAPI.getInstance()...");
                Class<?> markerApiClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerAPI");
                Method getInstance = markerApiClass.getMethod("getInstance");
                Object result = getInstance.invoke(null);
                if (result != null) {
                    logger.debug(LogCategory.GENERAL, "Found MarkerAPI via MarkerAPI.getInstance() - class: " + result.getClass().getName());
                    return result;
                }
                logger.debug(LogCategory.GENERAL, "MarkerAPI.getInstance() returned null");
            } catch (ClassNotFoundException e) {
                logger.debug(LogCategory.GENERAL, "MarkerAPI class not found: " + e.getMessage());
            } catch (NoSuchMethodException e) {
                logger.debug(LogCategory.GENERAL, "MarkerAPI.getInstance() method not found");
            }
            
            // Try pattern 3: Access via API instance methods - search all methods
            logger.debug(LogCategory.GENERAL, "Searching BlueMap API class for marker-related methods...");
            try {
                List<Method> candidateMethods = new ArrayList<>();
                for (Method method : blueMapApi.getClass().getMethods()) {
                    String methodName = method.getName().toLowerCase();
                    String returnType = method.getReturnType().getName().toLowerCase();
                    
                    // Look for methods that might return MarkerAPI
                    if (method.getParameterCount() == 0 && 
                        (methodName.contains("marker") || 
                         methodName.contains("api") ||
                         returnType.contains("marker"))) {
                        candidateMethods.add(method);
                        logger.debug(LogCategory.GENERAL, "Found candidate method: " + method.getName() + 
                            " -> " + method.getReturnType().getName());
                    }
                }
                
                // Try each candidate method
                for (Method method : candidateMethods) {
                    try {
                        logger.debug(LogCategory.GENERAL, "Trying method: " + method.getName());
                        Object result = method.invoke(blueMapApi);
                        if (result != null) {
                            String className = result.getClass().getName();
                            logger.debug(LogCategory.GENERAL, "Method " + method.getName() + " returned: " + className);
                            if (className.contains("Marker") || className.contains("marker")) {
                                logger.debug(LogCategory.GENERAL, "Found MarkerAPI via " + method.getName());
                                return result;
                            }
                        }
                    } catch (Exception e) {
                        logger.debug(LogCategory.GENERAL, "Error invoking " + method.getName() + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.debug(LogCategory.GENERAL, "Error searching for marker methods: " + e.getMessage());
            }
            
            // Try pattern 4: Check if MarkerAPI is a field
            logger.debug(LogCategory.GENERAL, "Checking for MarkerAPI as a field...");
            try {
                for (java.lang.reflect.Field field : blueMapApi.getClass().getFields()) {
                    if (field.getName().toLowerCase().contains("marker")) {
                        logger.debug(LogCategory.GENERAL, "Found marker-related field: " + field.getName());
                        Object result = field.get(blueMapApi);
                        if (result != null) {
                            logger.debug(LogCategory.GENERAL, "Field " + field.getName() + " contains: " + result.getClass().getName());
                            return result;
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug(LogCategory.GENERAL, "Error checking fields: " + e.getMessage());
            }
            
            // Log all available methods for debugging
            if (logger.isDebugEnabled()) {
                logger.debug(LogCategory.GENERAL, "All methods on BlueMap API class:");
                for (Method method : blueMapApi.getClass().getMethods()) {
                    if (method.getParameterCount() == 0) {
                        logger.debug(LogCategory.GENERAL, "  - " + method.getName() + "() -> " + method.getReturnType().getName());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error accessing MarkerAPI: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "MarkerAPI access error", e);
            }
        }
        
        logger.warning(LogCategory.GENERAL, "Could not find MarkerAPI - BlueMap API structure may differ from expected");
        return null;
    }
    
    /**
     * Attempts to get MarkerAPI directly from the BlueMap plugin instance.
     * This is an alternative approach for BlueMap versions that don't expose MarkerAPI through the main API.
     * 
     * @param blueMapPlugin The BlueMap plugin instance
     * @return The MarkerAPI object, or null if not found
     */
    private Object getMarkerApiFromPlugin(Plugin blueMapPlugin) {
        try {
            logger.debug(LogCategory.GENERAL, "Trying to get MarkerAPI directly from BlueMap plugin: " + blueMapPlugin.getClass().getName());
            
            // Try to find MarkerAPI as a method on the plugin
            for (Method method : blueMapPlugin.getClass().getMethods()) {
                String methodName = method.getName().toLowerCase();
                String returnType = method.getReturnType().getName().toLowerCase();
                
                if (method.getParameterCount() == 0 && 
                    (methodName.contains("marker") || returnType.contains("marker"))) {
                    try {
                        logger.debug(LogCategory.GENERAL, "Trying plugin method: " + method.getName());
                        Object result = method.invoke(blueMapPlugin);
                        if (result != null && result.getClass().getName().contains("Marker")) {
                            logger.debug(LogCategory.GENERAL, "Found MarkerAPI via plugin method " + method.getName());
                            return result;
                        }
                    } catch (Exception e) {
                        logger.debug(LogCategory.GENERAL, "Error invoking " + method.getName() + ": " + e.getMessage());
                    }
                }
            }
            
            // Try to find MarkerAPI as a field on the plugin
            try {
                for (java.lang.reflect.Field field : blueMapPlugin.getClass().getDeclaredFields()) {
                    if (field.getType().getName().contains("Marker")) {
                        field.setAccessible(true);
                        Object result = field.get(blueMapPlugin);
                        if (result != null) {
                            logger.debug(LogCategory.GENERAL, "Found MarkerAPI via plugin field " + field.getName());
                            return result;
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug(LogCategory.GENERAL, "Error checking plugin fields: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error accessing MarkerAPI from plugin: " + e.getMessage());
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
            logger.info(LogCategory.GENERAL, "BlueMap integration shut down");
        } else {
            logger.debug(LogCategory.GENERAL, "BlueMap integration was not enabled, skipping shutdown");
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
        logger.debug(LogCategory.GENERAL, "Reloading BlueMap integration configuration...");
        if (enabled) {
            logger.debug(LogCategory.GENERAL, "BlueMap integration is enabled, reloading marker manager");
            markerManager.reload();
            logger.info(LogCategory.GENERAL, "BlueMap integration configuration reloaded");
        } else {
            // Try to initialize if it wasn't enabled before
            logger.debug(LogCategory.GENERAL, "BlueMap integration was not enabled, attempting initialization");
            if (initialize()) {
                logger.info(LogCategory.GENERAL, "BlueMap integration enabled after reload");
            }
        }
    }
}

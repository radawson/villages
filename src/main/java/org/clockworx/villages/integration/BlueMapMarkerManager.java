package org.clockworx.villages.integration;

import org.bukkit.World;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages BlueMap markers for villages.
 * 
 * Creates and maintains POI markers (for village centers) and shape markers
 * (for village boundaries) on the BlueMap web interface.
 * 
 * @author Clockworx
 * @since 0.3.0
 */
public class BlueMapMarkerManager {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    
    private Object markerApi;
    private Object markerSet;
    private final Map<UUID, MarkerPair> villageMarkers = new HashMap<>();
    
    /**
     * Represents a pair of markers for a village (POI + shape).
     */
    private static class MarkerPair {
        final Object poiMarker;
        final Object shapeMarker;
        
        MarkerPair(Object poiMarker, Object shapeMarker) {
            this.poiMarker = poiMarker;
            this.shapeMarker = shapeMarker;
        }
    }
    
    /**
     * Creates a new BlueMapMarkerManager.
     * 
     * @param plugin The Villages plugin instance
     * @param integration The BlueMap integration instance (unused, kept for future use)
     */
    public BlueMapMarkerManager(VillagesPlugin plugin, BlueMapIntegration integration) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
    }
    
    /**
     * Initializes the marker manager and creates the marker set.
     * 
     * @param markerApi The BlueMap MarkerAPI instance
     * @return true if initialization was successful
     */
    public boolean initialize(Object markerApi) {
        logger.debug(LogCategory.GENERAL, "Initializing BlueMap marker manager...");
        this.markerApi = markerApi;
        
        try {
            // Create or get marker set
            logger.debug(LogCategory.GENERAL, "Creating BlueMap marker set...");
            markerSet = createMarkerSet();
            
            if (markerSet == null) {
                logger.warning(LogCategory.GENERAL, "Failed to create BlueMap marker set");
                return false;
            }
            
            logger.info(LogCategory.GENERAL, "BlueMap marker set created successfully");
            logger.debug(LogCategory.GENERAL, "Loading existing villages for BlueMap markers...");
            
            // Load existing villages and create markers for them
            loadExistingVillages();
            
            logger.debug(LogCategory.GENERAL, "BlueMap marker manager initialized successfully");
            return true;
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error initializing BlueMap marker manager: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Marker manager initialization error: " + e.getMessage(), e);
            }
            return false;
        }
    }
    
    /**
     * Creates or retrieves the marker set for villages.
     * 
     * @return The marker set object, or null if creation failed
     */
    private Object createMarkerSet() {
        try {
            String setId = "villages";
            String label = plugin.getConfigManager().getBlueMapMarkerSetLabel();
            
            logger.debug(LogCategory.GENERAL, "Creating BlueMap marker set - ID: " + setId + ", Label: " + label);
            logger.debug(LogCategory.GENERAL, "MarkerAPI class: " + markerApi.getClass().getName());
            
            // Try to get existing marker set first
            try {
                logger.debug(LogCategory.GENERAL, "Checking for existing marker set...");
                Method getMarkerSet = markerApi.getClass().getMethod("getMarkerSet", String.class);
                Object existing = getMarkerSet.invoke(markerApi, setId);
                if (existing != null) {
                    logger.debug(LogCategory.GENERAL, "Found existing BlueMap marker set: " + setId);
                    return existing;
                }
                logger.debug(LogCategory.GENERAL, "No existing marker set found, will create new one");
            } catch (NoSuchMethodException e) {
                logger.debug(LogCategory.GENERAL, "getMarkerSet method not found: " + e.getMessage());
                // Try create method
            }
            
            // Try to create marker set
            try {
                logger.debug(LogCategory.GENERAL, "Trying createMarkerSet(String)...");
                Method createMarkerSet = markerApi.getClass().getMethod("createMarkerSet", String.class);
                Object set = createMarkerSet.invoke(markerApi, setId);
                
                logger.debug(LogCategory.GENERAL, "Marker set created, attempting to set label...");
                
                // Try to set label
                try {
                    Method setLabel = set.getClass().getMethod("setLabel", String.class);
                    setLabel.invoke(set, label);
                    logger.debug(LogCategory.GENERAL, "Marker set label set to: " + label);
                } catch (NoSuchMethodException ignored) {
                    // Label setting not available
                    logger.debug(LogCategory.GENERAL, "setLabel method not available on marker set");
                }
                
                logger.debug(LogCategory.GENERAL, "Created BlueMap marker set: " + setId);
                return set;
                
            } catch (NoSuchMethodException e) {
                logger.debug(LogCategory.GENERAL, "createMarkerSet(String) not found, trying createMarkerSet(String, String)...");
                // Try alternative method signatures
                try {
                    Method createMarkerSet = markerApi.getClass().getMethod("createMarkerSet", String.class, String.class);
                    Object set = createMarkerSet.invoke(markerApi, setId, label);
                    logger.debug(LogCategory.GENERAL, "Created BlueMap marker set with label: " + setId);
                    return set;
                } catch (NoSuchMethodException e2) {
                    logger.debug(LogCategory.GENERAL, "createMarkerSet(String, String) also not found");
                    // Search for any marker set creation methods
                    logger.debug(LogCategory.GENERAL, "Searching MarkerAPI class for marker set methods...");
                    for (Method method : markerApi.getClass().getMethods()) {
                        String methodName = method.getName().toLowerCase();
                        if (methodName.contains("marker") && methodName.contains("set")) {
                            logger.debug(LogCategory.GENERAL, "Found potential marker set method: " + method.getName() + 
                                " with " + method.getParameterCount() + " parameters");
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error creating marker set: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Marker set creation error", e);
            }
        }
        
        logger.warning(LogCategory.GENERAL, "Failed to create marker set - no compatible API method found");
        return null;
    }
    
    /**
     * Loads all existing villages and creates markers for them.
     */
    private void loadExistingVillages() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                logger.debug(LogCategory.GENERAL, "Loading all villages from storage for BlueMap markers...");
                List<Village> villages = plugin.getStorageManager().loadAllVillages().join();
                logger.debug(LogCategory.GENERAL, "Found " + villages.size() + " villages to create markers for");
                
                int created = 0;
                int skipped = 0;
                for (Village village : villages) {
                    if (village.getWorld() != null) {
                        createVillageMarkers(village);
                        created++;
                    } else {
                        logger.debug(LogCategory.GENERAL, "Skipping village " + village.getId() + " - world not loaded: " + village.getWorldName());
                        skipped++;
                    }
                }
                
                logger.info(LogCategory.GENERAL, "Created BlueMap markers for " + created + " villages" + 
                    (skipped > 0 ? " (skipped " + skipped + " with unloaded worlds)" : ""));
            } catch (Exception e) {
                logger.warning(LogCategory.GENERAL, "Error loading villages for BlueMap markers: " + e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.warning(LogCategory.GENERAL, "Error loading villages for BlueMap markers", e);
                }
            }
        });
    }
    
    /**
     * Creates markers for a village (POI + shape).
     * 
     * @param village The village to create markers for
     */
    public void createVillageMarkers(Village village) {
        if (markerSet == null || village == null) {
            logger.debug(LogCategory.GENERAL, "Cannot create markers: markerSet=" + (markerSet != null) + ", village=" + (village != null));
            return;
        }
        
        logger.debug(LogCategory.GENERAL, "Creating BlueMap markers for village: " + village.getDisplayName() + " (" + village.getId() + ")");
        
        // Remove existing markers if any
        removeVillageMarkers(village.getId());
        
        try {
            // Create POI marker at village center
            logger.debug(LogCategory.GENERAL, "Creating POI marker for village " + village.getDisplayName());
            Object poiMarker = createPOIMarker(village);
            
            if (poiMarker == null) {
                logger.warning(LogCategory.GENERAL, "Failed to create POI marker for village " + village.getDisplayName());
            } else {
                logger.debug(LogCategory.GENERAL, "POI marker created successfully for village " + village.getDisplayName());
            }
            
            // Create shape marker for boundary (if enabled and boundary exists)
            Object shapeMarker = null;
            if (plugin.getConfigManager().isBlueMapShowBoundaries() && village.hasBoundary()) {
                logger.debug(LogCategory.GENERAL, "Creating shape marker for village " + village.getDisplayName() + " boundary");
                shapeMarker = createShapeMarker(village);
                if (shapeMarker == null) {
                    logger.debug(LogCategory.GENERAL, "Shape marker creation failed for village " + village.getDisplayName() + " (API may not support shape markers)");
                } else {
                    logger.debug(LogCategory.GENERAL, "Shape marker created successfully for village " + village.getDisplayName());
                }
            } else {
                if (!plugin.getConfigManager().isBlueMapShowBoundaries()) {
                    logger.debug(LogCategory.GENERAL, "Shape markers disabled in config for village " + village.getDisplayName());
                } else if (!village.hasBoundary()) {
                    logger.debug(LogCategory.GENERAL, "Village " + village.getDisplayName() + " has no boundary, skipping shape marker");
                }
            }
            
            // Store marker pair
            villageMarkers.put(village.getId(), new MarkerPair(poiMarker, shapeMarker));
            
            logger.info(LogCategory.GENERAL, "Created BlueMap markers for village: " + village.getDisplayName() + 
                " (POI: " + (poiMarker != null ? "yes" : "no") + 
                ", Shape: " + (shapeMarker != null ? "yes" : "no") + ")");
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error creating BlueMap markers for village " + village.getId() + ": " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Marker creation error for village " + village.getId(), e);
            }
        }
    }
    
    /**
     * Creates a POI marker for a village center.
     * 
     * @param village The village
     * @return The POI marker object, or null if creation failed
     */
    private Object createPOIMarker(Village village) {
        try {
            // Determine marker position (use boundary center if available, otherwise bell location)
            int x, y, z;
            World world = village.getWorld();
            
            if (village.hasBoundary()) {
                VillageBoundary boundary = village.getBoundary();
                x = boundary.getCenterX();
                y = boundary.getCenterY();
                z = boundary.getCenterZ();
            } else {
                x = village.getBellX();
                y = village.getBellY();
                z = village.getBellZ();
            }
            
            String markerId = "village_" + village.getId().toString().replace("-", "_");
            String label = village.getDisplayName();
            String icon = plugin.getConfigManager().getBlueMapIcon();
            
            logger.debug(LogCategory.GENERAL, "Creating POI marker - ID: " + markerId + ", Label: " + label + 
                ", Icon: " + icon + ", Position: " + x + "," + y + "," + z + ", World: " + world.getName());
            
            if (world == null) {
                logger.warning(LogCategory.GENERAL, "Cannot create POI marker: village world is not loaded: " + village.getWorldName());
                return null;
            }
            
            // Try to create POI marker using reflection
            // Common patterns: createPOIMarker(id, world, x, y, z, label, icon)
            try {
                logger.debug(LogCategory.GENERAL, "Trying createPOIMarker with double coordinates...");
                Method createPOI = markerSet.getClass().getMethod("createPOIMarker", 
                    String.class, World.class, double.class, double.class, double.class, String.class, String.class);
                Object marker = createPOI.invoke(markerSet, markerId, world, (double)x, (double)y, (double)z, label, icon);
                
                logger.debug(LogCategory.GENERAL, "POI marker created, attempting to add to marker set...");
                
                // Try to add to marker set
                try {
                    Method addMarker = markerSet.getClass().getMethod("addMarker", Object.class);
                    addMarker.invoke(markerSet, marker);
                    logger.debug(LogCategory.GENERAL, "POI marker added to marker set successfully");
                } catch (NoSuchMethodException ignored) {
                    // Marker might already be added by createPOIMarker
                    logger.debug(LogCategory.GENERAL, "addMarker method not found, marker may have been auto-added");
                }
                
                logger.debug(LogCategory.GENERAL, "POI marker creation successful using double coordinates");
                return marker;
                
            } catch (NoSuchMethodException e) {
                logger.debug(LogCategory.GENERAL, "createPOIMarker with double coordinates not found, trying int coordinates...");
                // Try alternative signatures
                try {
                    // Try with int coordinates
                    Method createPOI = markerSet.getClass().getMethod("createPOIMarker", 
                        String.class, World.class, int.class, int.class, int.class, String.class, String.class);
                    Object marker = createPOI.invoke(markerSet, markerId, world, x, y, z, label, icon);
                    logger.debug(LogCategory.GENERAL, "POI marker creation successful using int coordinates");
                    return marker;
                } catch (NoSuchMethodException e2) {
                    logger.debug(LogCategory.GENERAL, "createPOIMarker with int coordinates also not found");
                    // Try to find any method that might create markers
                    logger.debug(LogCategory.GENERAL, "Searching marker set class for marker creation methods...");
                    for (Method method : markerSet.getClass().getMethods()) {
                        String methodName = method.getName().toLowerCase();
                        if (methodName.contains("poi") || methodName.contains("marker")) {
                            logger.debug(LogCategory.GENERAL, "Found potential marker method: " + method.getName() + 
                                " with " + method.getParameterCount() + " parameters");
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning(LogCategory.GENERAL, "Exception creating POI marker: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.warning(LogCategory.GENERAL, "POI marker creation exception", e);
                }
            }
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error creating POI marker: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "POI marker creation error", e);
            }
        }
        
        logger.warning(LogCategory.GENERAL, "Failed to create POI marker - no compatible API method found");
        return null;
    }
    
    /**
     * Creates a shape marker for a village boundary.
     * 
     * @param village The village
     * @return The shape marker object, or null if creation failed
     */
    private Object createShapeMarker(Village village) {
        try {
            VillageBoundary boundary = village.getBoundary();
            if (boundary == null) {
                return null;
            }
            
            // Convert boundary to polygon
            List<BoundaryToPolygonConverter.Point2D> polygon = BoundaryToPolygonConverter.toPolygon(boundary);
            if (polygon.isEmpty()) {
                return null;
            }
            
            String markerId = "village_boundary_" + village.getId().toString().replace("-", "_");
            String label = village.getDisplayName() + " Boundary";
            World world = village.getWorld();
            
            String lineColor = plugin.getConfigManager().getBlueMapBoundaryColor();
            double fillOpacity = plugin.getConfigManager().getBlueMapBoundaryOpacity();
            int depth = BoundaryToPolygonConverter.getDepth(boundary);
            int y = BoundaryToPolygonConverter.getY(boundary);
            
            // Convert polygon to coordinate array format
            double[][] shape = new double[polygon.size()][2];
            for (int i = 0; i < polygon.size(); i++) {
                BoundaryToPolygonConverter.Point2D point = polygon.get(i);
                shape[i][0] = point.getX();
                shape[i][1] = point.getZ();
            }
            
            // Try to create shape marker using reflection
            // Common patterns vary, try multiple approaches
            try {
                // Try: createShapeMarker(id, world, shape, y, depth, label, lineColor, fillColor)
                Method createShape = markerSet.getClass().getMethod("createShapeMarker",
                    String.class, World.class, double[][].class, int.class, int.class, 
                    String.class, String.class, String.class);
                
                // Calculate fill color from line color and opacity
                String fillColor = calculateFillColor(lineColor, fillOpacity);
                
                Object marker = createShape.invoke(markerSet, markerId, world, shape, y, depth, 
                    label, lineColor, fillColor);
                
                // Try to add to marker set
                try {
                    Method addMarker = markerSet.getClass().getMethod("addMarker", Object.class);
                    addMarker.invoke(markerSet, marker);
                } catch (NoSuchMethodException ignored) {
                    // Marker might already be added
                }
                
                return marker;
                
            } catch (NoSuchMethodException e) {
                // Try alternative signatures - shape markers API varies significantly
                logger.debug(LogCategory.GENERAL, "Could not create shape marker - API signature not found");
            }
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error creating shape marker: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Calculates fill color from line color and opacity.
     * 
     * @param lineColor Hex color string (e.g., "#FF6B00")
     * @param opacity Opacity value (0.0 to 1.0)
     * @return Hex color string with alpha channel
     */
    private String calculateFillColor(String lineColor, double opacity) {
        // Remove # if present
        String color = lineColor.startsWith("#") ? lineColor.substring(1) : lineColor;
        
        // Convert opacity to hex (0-255)
        int alpha = (int) Math.round(opacity * 255);
        String alphaHex = String.format("%02X", alpha);
        
        // Return color with alpha: #RRGGBBAA
        return "#" + color + alphaHex;
    }
    
    /**
     * Updates markers for a village (e.g., when name or boundary changes).
     * 
     * @param village The village to update
     */
    public void updateVillageMarkers(Village village) {
        logger.debug(LogCategory.GENERAL, "Updating BlueMap markers for village: " + village.getDisplayName() + " (" + village.getId() + ")");
        // Remove old markers and create new ones
        createVillageMarkers(village);
        logger.debug(LogCategory.GENERAL, "BlueMap markers updated for village: " + village.getDisplayName());
    }
    
    /**
     * Removes markers for a village.
     * 
     * @param villageId The village UUID
     */
    public void removeVillageMarkers(UUID villageId) {
        logger.debug(LogCategory.GENERAL, "Removing BlueMap markers for village: " + villageId);
        MarkerPair pair = villageMarkers.remove(villageId);
        if (pair == null) {
            logger.debug(LogCategory.GENERAL, "No markers found for village " + villageId + " (may not have been created)");
            return;
        }
        
        if (markerSet == null) {
            logger.debug(LogCategory.GENERAL, "Marker set is null, cannot remove markers for village " + villageId);
            return;
        }
        
        try {
            // Try to remove markers from marker set
            boolean removed = false;
            try {
                Method removeMarker = markerSet.getClass().getMethod("removeMarker", Object.class);
                if (pair.poiMarker != null) {
                    removeMarker.invoke(markerSet, pair.poiMarker);
                    removed = true;
                    logger.debug(LogCategory.GENERAL, "Removed POI marker for village " + villageId);
                }
                if (pair.shapeMarker != null) {
                    removeMarker.invoke(markerSet, pair.shapeMarker);
                    removed = true;
                    logger.debug(LogCategory.GENERAL, "Removed shape marker for village " + villageId);
                }
            } catch (NoSuchMethodException ignored) {
                // Try alternative method
                try {
                    Method removeMarker = markerSet.getClass().getMethod("removeMarker", String.class);
                    String markerId = "village_" + villageId.toString().replace("-", "_");
                    removeMarker.invoke(markerSet, markerId);
                    String boundaryId = "village_boundary_" + villageId.toString().replace("-", "_");
                    removeMarker.invoke(markerSet, boundaryId);
                    removed = true;
                    logger.debug(LogCategory.GENERAL, "Removed markers by ID for village " + villageId);
                } catch (NoSuchMethodException ignored2) {
                    // Removal not available via API
                    logger.debug(LogCategory.GENERAL, "Marker removal API not available for village " + villageId);
                }
            }
            
            if (removed) {
                logger.info(LogCategory.GENERAL, "Removed BlueMap markers for village: " + villageId);
            } else {
                logger.debug(LogCategory.GENERAL, "Markers removed from internal map but not from BlueMap (API limitation)");
            }
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error removing BlueMap markers for village " + villageId + ": " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Error removing BlueMap markers", e);
            }
        }
    }
    
    /**
     * Reloads the marker manager configuration.
     */
    public void reload() {
        logger.debug(LogCategory.GENERAL, "Reloading BlueMap marker manager configuration...");
        int markerCount = villageMarkers.size();
        logger.debug(LogCategory.GENERAL, "Reloading " + markerCount + " existing village markers with new configuration");
        
        // Recreate all markers with new configuration
        Map<UUID, MarkerPair> existing = new HashMap<>(villageMarkers);
        villageMarkers.clear();
        
        int reloaded = 0;
        for (UUID villageId : existing.keySet()) {
            plugin.getStorageManager().loadVillage(villageId).thenAccept(village -> {
                if (village.isPresent()) {
                    createVillageMarkers(village.get());
                } else {
                    logger.debug(LogCategory.GENERAL, "Village " + villageId + " not found during reload, marker removed");
                }
            });
            reloaded++;
        }
        
        logger.info(LogCategory.GENERAL, "Reloaded BlueMap marker configuration for " + reloaded + " villages");
    }
    
    /**
     * Shuts down the marker manager and cleans up all markers.
     */
    public void shutdown() {
        logger.debug(LogCategory.GENERAL, "Shutting down BlueMap marker manager...");
        int markerCount = villageMarkers.size();
        logger.debug(LogCategory.GENERAL, "Removing " + markerCount + " village markers");
        
        // Remove all markers
        int removed = 0;
        for (UUID villageId : new HashMap<>(villageMarkers).keySet()) {
            removeVillageMarkers(villageId);
            removed++;
        }
        
        villageMarkers.clear();
        markerSet = null;
        markerApi = null;
        
        logger.info(LogCategory.GENERAL, "BlueMap marker manager shut down (removed " + removed + " markers)");
    }
}

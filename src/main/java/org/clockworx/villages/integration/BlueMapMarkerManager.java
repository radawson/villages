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
        this.markerApi = markerApi;
        
        try {
            // Create or get marker set
            markerSet = createMarkerSet();
            
            if (markerSet == null) {
                logger.warning("Failed to create BlueMap marker set");
                return false;
            }
            
            logger.debug(LogCategory.GENERAL, "BlueMap marker set created successfully");
            
            // Load existing villages and create markers for them
            loadExistingVillages();
            
            return true;
            
        } catch (Exception e) {
            logger.warning("Error initializing BlueMap marker manager: " + e.getMessage());
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
            
            // Try to get existing marker set first
            try {
                Method getMarkerSet = markerApi.getClass().getMethod("getMarkerSet", String.class);
                Object existing = getMarkerSet.invoke(markerApi, setId);
                if (existing != null) {
                    logger.debug(LogCategory.GENERAL, "Found existing BlueMap marker set: " + setId);
                    return existing;
                }
            } catch (NoSuchMethodException ignored) {
                // Try create method
            }
            
            // Try to create marker set
            try {
                Method createMarkerSet = markerApi.getClass().getMethod("createMarkerSet", String.class);
                Object set = createMarkerSet.invoke(markerApi, setId);
                
                // Try to set label
                try {
                    Method setLabel = set.getClass().getMethod("setLabel", String.class);
                    setLabel.invoke(set, label);
                } catch (NoSuchMethodException ignored) {
                    // Label setting not available
                }
                
                logger.debug(LogCategory.GENERAL, "Created BlueMap marker set: " + setId);
                return set;
                
            } catch (NoSuchMethodException e) {
                // Try alternative method signatures
                try {
                    Method createMarkerSet = markerApi.getClass().getMethod("createMarkerSet", String.class, String.class);
                    Object set = createMarkerSet.invoke(markerApi, setId, label);
                    logger.debug(LogCategory.GENERAL, "Created BlueMap marker set with label: " + setId);
                    return set;
                } catch (NoSuchMethodException ignored) {
                    // Continue
                }
            }
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error creating marker set: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Loads all existing villages and creates markers for them.
     */
    private void loadExistingVillages() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Village> villages = plugin.getStorageManager().loadAllVillages().join();
                logger.debug(LogCategory.GENERAL, "Loading " + villages.size() + " villages for BlueMap markers");
                
                for (Village village : villages) {
                    createVillageMarkers(village);
                }
                
                logger.debug(LogCategory.GENERAL, "Created BlueMap markers for " + villages.size() + " villages");
            } catch (Exception e) {
                logger.warning("Error loading villages for BlueMap markers: " + e.getMessage());
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
            return;
        }
        
        // Remove existing markers if any
        removeVillageMarkers(village.getId());
        
        try {
            // Create POI marker at village center
            Object poiMarker = createPOIMarker(village);
            
            // Create shape marker for boundary (if enabled and boundary exists)
            Object shapeMarker = null;
            if (plugin.getConfigManager().isBlueMapShowBoundaries() && village.hasBoundary()) {
                shapeMarker = createShapeMarker(village);
            }
            
            // Store marker pair
            villageMarkers.put(village.getId(), new MarkerPair(poiMarker, shapeMarker));
            
            logger.debug(LogCategory.GENERAL, "Created BlueMap markers for village: " + village.getDisplayName());
            
        } catch (Exception e) {
            logger.warning("Error creating BlueMap markers for village " + village.getId() + ": " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Marker creation error: " + e.getMessage(), e);
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
            
            // Try to create POI marker using reflection
            // Common patterns: createPOIMarker(id, world, x, y, z, label, icon)
            try {
                Method createPOI = markerSet.getClass().getMethod("createPOIMarker", 
                    String.class, World.class, double.class, double.class, double.class, String.class, String.class);
                Object marker = createPOI.invoke(markerSet, markerId, world, (double)x, (double)y, (double)z, label, icon);
                
                // Try to add to marker set
                try {
                    Method addMarker = markerSet.getClass().getMethod("addMarker", Object.class);
                    addMarker.invoke(markerSet, marker);
                } catch (NoSuchMethodException ignored) {
                    // Marker might already be added by createPOIMarker
                }
                
                return marker;
                
            } catch (NoSuchMethodException e) {
                // Try alternative signatures
                try {
                    // Try with int coordinates
                    Method createPOI = markerSet.getClass().getMethod("createPOIMarker", 
                        String.class, World.class, int.class, int.class, int.class, String.class, String.class);
                    Object marker = createPOI.invoke(markerSet, markerId, world, x, y, z, label, icon);
                    return marker;
                } catch (NoSuchMethodException ignored) {
                    // Continue
                }
            }
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error creating POI marker: " + e.getMessage());
        }
        
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
        // Remove old markers and create new ones
        createVillageMarkers(village);
    }
    
    /**
     * Removes markers for a village.
     * 
     * @param villageId The village UUID
     */
    public void removeVillageMarkers(UUID villageId) {
        MarkerPair pair = villageMarkers.remove(villageId);
        if (pair == null || markerSet == null) {
            return;
        }
        
        try {
            // Try to remove markers from marker set
            try {
                Method removeMarker = markerSet.getClass().getMethod("removeMarker", Object.class);
                if (pair.poiMarker != null) {
                    removeMarker.invoke(markerSet, pair.poiMarker);
                }
                if (pair.shapeMarker != null) {
                    removeMarker.invoke(markerSet, pair.shapeMarker);
                }
            } catch (NoSuchMethodException ignored) {
                // Try alternative method
                try {
                    Method removeMarker = markerSet.getClass().getMethod("removeMarker", String.class);
                    String markerId = "village_" + villageId.toString().replace("-", "_");
                    removeMarker.invoke(markerSet, markerId);
                    String boundaryId = "village_boundary_" + villageId.toString().replace("-", "_");
                    removeMarker.invoke(markerSet, boundaryId);
                } catch (NoSuchMethodException ignored2) {
                    // Removal not available via API
                }
            }
            
            logger.debug(LogCategory.GENERAL, "Removed BlueMap markers for village: " + villageId);
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error removing BlueMap markers: " + e.getMessage());
        }
    }
    
    /**
     * Reloads the marker manager configuration.
     */
    public void reload() {
        // Recreate all markers with new configuration
        Map<UUID, MarkerPair> existing = new HashMap<>(villageMarkers);
        villageMarkers.clear();
        
        for (UUID villageId : existing.keySet()) {
            plugin.getStorageManager().loadVillage(villageId).thenAccept(village -> {
                if (village.isPresent()) {
                    createVillageMarkers(village.get());
                }
            });
        }
    }
    
    /**
     * Shuts down the marker manager and cleans up all markers.
     */
    public void shutdown() {
        // Remove all markers
        for (UUID villageId : new HashMap<>(villageMarkers).keySet()) {
            removeVillageMarkers(villageId);
        }
        
        villageMarkers.clear();
        markerSet = null;
        markerApi = null;
        
        logger.debug(LogCategory.GENERAL, "BlueMap marker manager shut down");
    }
}

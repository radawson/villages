package org.clockworx.villages.integration;

import org.bukkit.World;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.Arrays;

/**
 * Manages BlueMap markers for villages using BlueMap 5.15+ API.
 * 
 * BlueMap 5.15+ API structure:
 * - BlueMapAPI.getWorld(world) -> Optional<BlueMapWorld>
 * - BlueMapWorld.getMaps() -> Collection<BlueMapMap>
 * - BlueMapMap.getMarkerSets() -> Map<String, MarkerSet>
 * - MarkerSet.put(id, marker) -> add marker
 * 
 * @author Clockworx
 * @since 0.3.0
 */
public class BlueMapMarkerManager {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    
    private Object blueMapApi;
    private final String markerSetId = "villages";
    private final Map<UUID, Map<String, MarkerPair>> villageMarkers = new HashMap<>(); // villageId -> mapId -> markers
    
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
     */
    public BlueMapMarkerManager(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
    }
    
    /**
     * Initializes the marker manager.
     * 
     * @param blueMapApi The BlueMap API instance
     * @return true if initialization was successful
     */
    public boolean initialize(Object blueMapApi) {
        logger.debug(LogCategory.GENERAL, "Initializing BlueMap marker manager...");
        this.blueMapApi = blueMapApi;
        
        try {
            logger.debug(LogCategory.GENERAL, "BlueMap marker manager initialized successfully");
            logger.debug(LogCategory.GENERAL, "Loading existing villages for BlueMap markers...");
            
            // Load existing villages and create markers for them
            loadExistingVillages();
            
            return true;
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error initializing BlueMap marker manager: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Marker manager initialization error", e);
            }
            return false;
        }
    }
    
    /**
     * Gets or creates a MarkerSet for a specific map.
     * 
     * @param map The BlueMapMap instance
     * @return The MarkerSet, or null if creation failed
     */
    private Object getOrCreateMarkerSet(Object map) {
        try {
            // Get marker sets map
            Method getMarkerSets = map.getClass().getMethod("getMarkerSets");
            Object markerSetsMap = getMarkerSets.invoke(map);
            
            // Check if marker set already exists
            Method get = markerSetsMap.getClass().getMethod("get", Object.class);
            Object existing = get.invoke(markerSetsMap, markerSetId);
            
            if (existing != null) {
                logger.debug(LogCategory.GENERAL, "Found existing marker set: " + markerSetId);
                return existing;
            }
            
            // Create new marker set
            logger.debug(LogCategory.GENERAL, "Creating new marker set: " + markerSetId);
            String label = plugin.getConfigManager().getBlueMapMarkerSetLabel();
            
            // Create MarkerSet using reflection
            Class<?> markerSetClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet");
            Constructor<?> constructor = markerSetClass.getConstructor(String.class);
            Object markerSet = constructor.newInstance(label);
            
            // Add to map
            Method put = markerSetsMap.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(markerSetsMap, markerSetId, markerSet);
            
            logger.debug(LogCategory.GENERAL, "Created marker set: " + markerSetId + " with label: " + label);
            return markerSet;
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error getting/creating marker set: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Marker set creation error", e);
            }
            return null;
        }
    }
    
    /**
     * Gets all maps for a world.
     * 
     * @param world The Bukkit world
     * @return List of BlueMapMap instances, or empty list if none found
     */
    private List<Object> getMapsForWorld(World world) {
        List<Object> maps = new ArrayList<>();
        
        try {
            // Get BlueMapWorld
            Method getWorld = blueMapApi.getClass().getMethod("getWorld", Object.class);
            Object optional = getWorld.invoke(blueMapApi, world);
            
            if (optional == null) {
                logger.debug(LogCategory.GENERAL, "BlueMapWorld not found for world: " + world.getName());
                return maps;
            }
            
            // Get value from Optional
            Method isPresent = optional.getClass().getMethod("isPresent");
            if (!((Boolean) isPresent.invoke(optional))) {
                logger.debug(LogCategory.GENERAL, "BlueMapWorld Optional is empty for world: " + world.getName());
                return maps;
            }
            
            Method get = optional.getClass().getMethod("get");
            Object blueMapWorld = get.invoke(optional);
            
            // Get maps
            Method getMaps = blueMapWorld.getClass().getMethod("getMaps");
            Object mapsCollection = getMaps.invoke(blueMapWorld);
            
            // Convert collection to list
            if (mapsCollection instanceof Collection) {
                maps.addAll((Collection<?>) mapsCollection);
            }
            
            logger.debug(LogCategory.GENERAL, "Found " + maps.size() + " map(s) for world: " + world.getName());
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error getting maps for world " + world.getName() + ": " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Error getting maps for world", e);
            }
        }
        
        return maps;
    }
    
    /**
     * Creates markers for a village (POI + shape) on all maps for the village's world.
     * 
     * @param village The village to create markers for
     */
    public void createVillageMarkers(Village village) {
        if (village == null || blueMapApi == null) {
            logger.debug(LogCategory.GENERAL, "Cannot create markers: village=" + (village != null) + ", api=" + (blueMapApi != null));
            return;
        }
        
        World world = village.getWorld();
        if (world == null) {
            logger.debug(LogCategory.GENERAL, "Cannot create markers: village world is not loaded: " + village.getWorldName());
            return;
        }
        
        logger.debug(LogCategory.GENERAL, "Creating BlueMap markers for village: " + village.getDisplayName() + " (" + village.getId() + ")");
        
        // Remove existing markers
        removeVillageMarkers(village.getId());
        
        // Get all maps for this world
        List<Object> maps = getMapsForWorld(world);
        if (maps.isEmpty()) {
            logger.debug(LogCategory.GENERAL, "No BlueMap maps found for world: " + world.getName());
            return;
        }
        
        Map<String, MarkerPair> mapMarkers = new HashMap<>();
        
        // Create markers on each map
        for (Object map : maps) {
            try {
                // Get map ID
                Method getId = map.getClass().getMethod("getId");
                String mapId = (String) getId.invoke(map);
                
                // Get or create marker set
                Object markerSet = getOrCreateMarkerSet(map);
                if (markerSet == null) {
                    logger.warning(LogCategory.GENERAL, "Failed to get/create marker set for map: " + mapId);
                    continue;
                }
                
                // Create POI marker
                Object poiMarker = createPOIMarker(village, markerSet);
                
                // Create shape marker (if enabled and boundary exists)
                Object shapeMarker = null;
                if (plugin.getConfigManager().isBlueMapShowBoundaries() && village.hasBoundary()) {
                    shapeMarker = createShapeMarker(village, markerSet);
                }
                
                mapMarkers.put(mapId, new MarkerPair(poiMarker, shapeMarker));
                
                logger.debug(LogCategory.GENERAL, "Created markers for village " + village.getDisplayName() + " on map: " + mapId);
                
            } catch (Exception e) {
                logger.warning(LogCategory.GENERAL, "Error creating markers for village on map: " + e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.warning(LogCategory.GENERAL, "Marker creation error", e);
                }
            }
        }
        
        villageMarkers.put(village.getId(), mapMarkers);
        
        logger.info(LogCategory.GENERAL, "Created BlueMap markers for village: " + village.getDisplayName() + 
            " on " + mapMarkers.size() + " map(s)");
    }
    
    /**
     * Creates a POI marker for a village center.
     * 
     * @param village The village
     * @param markerSet The marker set to add the marker to
     * @return The POI marker object, or null if creation failed
     */
    private Object createPOIMarker(Village village, Object markerSet) {
        try {
            // Determine position
            double x, y, z;
            if (village.hasBoundary()) {
                VillageBoundary boundary = village.getBoundary();
                x = boundary.getCenterX() + 0.5;
                y = boundary.getCenterY();
                z = boundary.getCenterZ() + 0.5;
            } else {
                x = village.getBellX() + 0.5;
                y = village.getBellY();
                z = village.getBellZ() + 0.5;
            }
            
            String markerId = "village_" + village.getId().toString().replace("-", "_");
            String label = village.getDisplayName();
            String icon = plugin.getConfigManager().getBlueMapIcon();
            
            logger.debug(LogCategory.GENERAL, "Creating POI marker - ID: " + markerId + ", Label: " + label + 
                ", Icon: " + icon + ", Position: " + x + "," + y + "," + z);
            
            // Create Vector3d for position
            Class<?> vector3dClass = Class.forName("com.flowpowered.math.vector.Vector3d");
            Constructor<?> vector3dConstructor = vector3dClass.getConstructor(double.class, double.class, double.class);
            Object position = vector3dConstructor.newInstance(x, y, z);
            
            // Create Vector2i for anchor (default: 25, 45)
            Class<?> vector2iClass = Class.forName("com.flowpowered.math.vector.Vector2i");
            Constructor<?> vector2iConstructor = vector2iClass.getConstructor(int.class, int.class);
            Object anchor = vector2iConstructor.newInstance(25, 45);
            
            // Create POIMarker
            Class<?> poiMarkerClass = Class.forName("de.bluecolored.bluemap.api.markers.POIMarker");
            Class<?> markerClass = Class.forName("de.bluecolored.bluemap.api.markers.Marker");
            Constructor<?> poiConstructor = poiMarkerClass.getConstructor(String.class, vector3dClass, String.class, vector2iClass);
            Object poiMarker = poiConstructor.newInstance(label, position, icon, anchor);
            
            // Add to marker set (put expects Marker, not Object)
            Method put = markerSet.getClass().getMethod("put", String.class, markerClass);
            put.invoke(markerSet, markerId, poiMarker);
            
            logger.debug(LogCategory.GENERAL, "POI marker created successfully: " + markerId);
            return poiMarker;
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error creating POI marker: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "POI marker creation error", e);
            }
            return null;
        }
    }
    
    /**
     * Creates a shape marker for a village boundary.
     * 
     * @param village The village
     * @param markerSet The marker set to add the marker to
     * @return The shape marker object, or null if creation failed
     */
    private Object createShapeMarker(Village village, Object markerSet) {
        try {
            VillageBoundary boundary = village.getBoundary();
            if (boundary == null) {
                return null;
            }
            
            // Convert boundary to polygon points
            List<BoundaryToPolygonConverter.Point2D> points = BoundaryToPolygonConverter.toPolygon(boundary);
            if (points.isEmpty()) {
                return null;
            }
            
            String markerId = "village_boundary_" + village.getId().toString().replace("-", "_");
            String label = village.getDisplayName() + " Boundary";
            float shapeY = BoundaryToPolygonConverter.getY(boundary);
            
            logger.debug(LogCategory.GENERAL, "Creating shape marker - ID: " + markerId + ", Label: " + label + 
                ", Y: " + shapeY + ", Points: " + points.size());
            
            // Create Vector2d array for shape points
            Class<?> vector2dClass = Class.forName("com.flowpowered.math.vector.Vector2d");
            Constructor<?> vector2dConstructor = vector2dClass.getConstructor(double.class, double.class);
            
            // Create array of Vector2d
            Object[] shapePointsArray = (Object[]) java.lang.reflect.Array.newInstance(vector2dClass, points.size());
            for (int i = 0; i < points.size(); i++) {
                BoundaryToPolygonConverter.Point2D point = points.get(i);
                shapePointsArray[i] = vector2dConstructor.newInstance(point.getX(), point.getZ());
            }
            
            // Create Shape - try constructor with Collection first (more reliable)
            Class<?> shapeClass = Class.forName("de.bluecolored.bluemap.api.math.Shape");
            Object shape;
            try {
                // Try constructor with Collection<Vector2d>
                Constructor<?> shapeConstructor = shapeClass.getConstructor(Collection.class);
                List<Object> shapePointsList = Arrays.asList(shapePointsArray);
                shape = shapeConstructor.newInstance(shapePointsList);
            } catch (NoSuchMethodException e) {
                // Try constructor with Vector2d... (varargs) - need to unpack array
                Constructor<?> shapeConstructor = shapeClass.getConstructor(vector2dClass.arrayType());
                shape = shapeConstructor.newInstance((Object) shapePointsArray);
            }
            
            // Create ShapeMarker
            Class<?> shapeMarkerClass = Class.forName("de.bluecolored.bluemap.api.markers.ShapeMarker");
            Constructor<?> shapeMarkerConstructor = shapeMarkerClass.getConstructor(String.class, shapeClass, float.class);
            Object shapeMarker = shapeMarkerConstructor.newInstance(label, shape, shapeY);
            
            // Set colors (use same color for line and fill, with opacity for fill)
            String boundaryColorHex = plugin.getConfigManager().getBlueMapBoundaryColor();
            double fillOpacity = plugin.getConfigManager().getBlueMapBoundaryOpacity();
            
            // Parse hex colors and set them (same color for line and fill)
            setShapeMarkerColors(shapeMarker, boundaryColorHex, boundaryColorHex, fillOpacity);
            
            // Add to marker set (put expects Marker, not Object)
            Class<?> markerClass = Class.forName("de.bluecolored.bluemap.api.markers.Marker");
            Method put = markerSet.getClass().getMethod("put", String.class, markerClass);
            put.invoke(markerSet, markerId, shapeMarker);
            
            logger.debug(LogCategory.GENERAL, "Shape marker created successfully: " + markerId);
            return shapeMarker;
            
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Error creating shape marker: " + e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warning(LogCategory.GENERAL, "Shape marker creation error", e);
            }
            return null;
        }
    }
    
    /**
     * Sets the colors for a shape marker.
     * 
     * @param shapeMarker The shape marker
     * @param lineColorHex Hex color for the line (e.g., "#FF6B00")
     * @param fillColorHex Hex color for the fill (e.g., "#FF6B00")
     * @param fillOpacity Opacity for the fill (0.0 to 1.0)
     */
    private void setShapeMarkerColors(Object shapeMarker, String lineColorHex, String fillColorHex, double fillOpacity) {
        try {
            // Parse hex colors
            int[] lineColor = parseHexColor(lineColorHex);
            int[] fillColor = parseHexColor(fillColorHex);
            
            // Create Color objects
            Class<?> colorClass = Class.forName("de.bluecolored.bluemap.api.math.Color");
            Constructor<?> colorConstructor = colorClass.getConstructor(int.class, int.class, int.class, float.class);
            
            Object lineColorObj = colorConstructor.newInstance(lineColor[0], lineColor[1], lineColor[2], 1.0f);
            Object fillColorObj = colorConstructor.newInstance(fillColor[0], fillColor[1], fillColor[2], (float) fillOpacity);
            
            // Set colors
            Method setLineColor = shapeMarker.getClass().getMethod("setLineColor", colorClass);
            setLineColor.invoke(shapeMarker, lineColorObj);
            
            Method setFillColor = shapeMarker.getClass().getMethod("setFillColor", colorClass);
            setFillColor.invoke(shapeMarker, fillColorObj);
            
        } catch (Exception e) {
            logger.debug(LogCategory.GENERAL, "Error setting shape marker colors: " + e.getMessage());
            // Non-fatal, continue without colors
        }
    }
    
    /**
     * Parses a hex color string to RGB values.
     * 
     * @param hex Hex color string (e.g., "#FF6B00" or "FF6B00")
     * @return Array of [R, G, B] values (0-255)
     */
    private int[] parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new int[]{255, 0, 0}; // Default red
        }
        
        // Remove # if present
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        
        try {
            int color = Integer.parseInt(hex, 16);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            return new int[]{r, g, b};
        } catch (NumberFormatException e) {
            logger.debug(LogCategory.GENERAL, "Invalid hex color: " + hex + ", using default red");
            return new int[]{255, 0, 0};
        }
    }
    
    /**
     * Updates markers for a village (removes old and creates new).
     * 
     * @param village The village to update
     */
    public void updateVillageMarkers(Village village) {
        createVillageMarkers(village);
    }
    
    /**
     * Removes markers for a village.
     * 
     * @param villageId The village UUID
     */
    public void removeVillageMarkers(UUID villageId) {
        Map<String, MarkerPair> mapMarkers = villageMarkers.remove(villageId);
        if (mapMarkers == null) {
            return;
        }
        
        logger.debug(LogCategory.GENERAL, "Removing BlueMap markers for village: " + villageId);
        
        // Remove markers from all maps
        for (Map.Entry<String, MarkerPair> entry : mapMarkers.entrySet()) {
            String mapId = entry.getKey();
            MarkerPair markers = entry.getValue();
            
            try {
                // Get map
                Method getMap = blueMapApi.getClass().getMethod("getMap", String.class);
                Object optional = getMap.invoke(blueMapApi, mapId);
                
                if (optional == null) {
                    continue;
                }
                
                Method isPresent = optional.getClass().getMethod("isPresent");
                if (!((Boolean) isPresent.invoke(optional))) {
                    continue;
                }
                
                Method get = optional.getClass().getMethod("get");
                Object map = get.invoke(optional);
                
                // Get marker set
                Method getMarkerSets = map.getClass().getMethod("getMarkerSets");
                Object markerSetsMap = getMarkerSets.invoke(map);
                Method getMarkerSet = markerSetsMap.getClass().getMethod("get", Object.class);
                Object markerSet = getMarkerSet.invoke(markerSetsMap, markerSetId);
                
                if (markerSet == null) {
                    continue;
                }
                
                // Remove markers
                Method remove = markerSet.getClass().getMethod("remove", String.class);
                
                if (markers.poiMarker != null) {
                    String poiId = "village_" + villageId.toString().replace("-", "_");
                    remove.invoke(markerSet, poiId);
                }
                
                if (markers.shapeMarker != null) {
                    String shapeId = "village_boundary_" + villageId.toString().replace("-", "_");
                    remove.invoke(markerSet, shapeId);
                }
                
                logger.debug(LogCategory.GENERAL, "Removed markers for village " + villageId + " from map: " + mapId);
                
            } catch (Exception e) {
                logger.warning(LogCategory.GENERAL, "Error removing markers for village " + villageId + " from map " + mapId + ": " + e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.warning(LogCategory.GENERAL, "Marker removal error", e);
                }
            }
        }
    }
    
    /**
     * Shuts down the marker manager.
     */
    public void shutdown() {
        logger.debug(LogCategory.GENERAL, "Shutting down BlueMap marker manager...");
        villageMarkers.clear();
        logger.debug(LogCategory.GENERAL, "BlueMap marker manager shut down");
    }
    
    /**
     * Reloads the marker manager configuration.
     */
    public void reload() {
        logger.debug(LogCategory.GENERAL, "Reloading BlueMap marker manager configuration...");
        // Update existing markers with new configuration
        // For now, just log - full reload would require recreating all markers
        logger.debug(LogCategory.GENERAL, "BlueMap marker manager configuration reloaded");
    }
    
    /**
     * Loads existing villages and creates markers for them.
     */
    private void loadExistingVillages() {
        CompletableFuture.runAsync(() -> {
            try {
                logger.debug(LogCategory.GENERAL, "Loading all villages from storage for BlueMap markers...");
                
                // Load all villages from storage
                plugin.getStorageManager().loadAllVillages().thenAccept(villages -> {
                    int created = 0;
                    int skipped = 0;
                    
                    for (Village village : villages) {
                        if (village.getWorld() == null) {
                            skipped++;
                            continue;
                        }
                        
                        createVillageMarkers(village);
                        created++;
                    }
                    
                    logger.info(LogCategory.GENERAL, "Created BlueMap markers for " + created + " villages" + 
                        (skipped > 0 ? " (skipped " + skipped + " with unloaded worlds)" : ""));
                });
                
            } catch (Exception e) {
                logger.warning(LogCategory.GENERAL, "Error loading villages for BlueMap markers: " + e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.warning(LogCategory.GENERAL, "Error loading villages for BlueMap markers", e);
                }
            }
        });
    }
}

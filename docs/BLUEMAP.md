# BlueMap Integration Guide

This document explains how the Villages plugin integrates with BlueMap to display village markers on the web map interface.

## Overview

The BlueMap integration automatically creates markers for all villages on your BlueMap web interface. Each village will have:

- **POI Marker** - An icon at the village center (bell location or boundary center) with the village name
- **Shape Marker** - A polygon outline showing the village boundary (optional)

## Requirements

- **BlueMap Plugin** - Must be installed and enabled on your server
- **Villages Plugin** - Version 0.3.0 or higher

The integration uses reflection to access BlueMap's API, so it works even if BlueMap's API structure changes between versions.

## Configuration

In `config.yml`:

```yaml
bluemap:
  # Enable BlueMap integration
  enabled: true
  
  # Icon path for village markers (relative to BlueMap assets directory)
  # Default: "assets/village-icon.png"
  icon: "assets/village-icon.png"
  
  # Show village boundary outlines on the map
  show-boundaries: true
  
  # Boundary outline color (hex format, e.g., "#FF6B00")
  boundary-color: "#FF6B00"
  
  # Boundary fill opacity (0.0 to 1.0, where 0.0 is transparent and 1.0 is opaque)
  boundary-opacity: 0.3
  
  # Label for the marker set in BlueMap UI
  marker-set-label: "Villages"
```

## Icon Setup

The village icon must be placed in BlueMap's assets directory:

1. Navigate to `plugins/BlueMap/web/assets/`
2. Place your icon file as `village-icon.png` (or update the config path)
3. Recommended size: 32x32 or 64x64 pixels
4. Use PNG format with transparent background for best appearance

If you want to use a different icon path, update the `bluemap.icon` setting in `config.yml`.

## How It Works

### Automatic Marker Creation

When a village is detected or created:
1. A POI marker is created at the village center
2. If boundaries are enabled and the village has a calculated boundary, a shape marker is created
3. Markers are automatically updated when:
   - Village name changes
   - Village boundary is recalculated
   - Village is deleted

### Marker Set

All village markers are grouped in a single marker set called "Villages" (configurable via `marker-set-label`). This allows players to toggle all village markers on/off in the BlueMap interface.

### Marker Positions

- **POI Marker**: Placed at the village center
  - If the village has a boundary: Uses the boundary center
  - If no boundary: Uses the bell location
- **Shape Marker**: Creates a rectangle polygon from the village boundary (AABB)

## Troubleshooting

### Markers Not Appearing

1. **Check BlueMap is installed:**
   - Verify BlueMap plugin is in your `plugins` folder
   - Check server logs for "BlueMap integration enabled successfully"
   - Run `/plugins` to confirm BlueMap is loaded

2. **Check integration is enabled:**
   - Verify `bluemap.enabled: true` in `config.yml`
   - Check server logs for BlueMap integration messages

3. **Check icon exists:**
   - Ensure `village-icon.png` is in `plugins/BlueMap/web/assets/`
   - Or update `bluemap.icon` to point to your icon

4. **Check village has data:**
   - Ensure villages have been detected (check with `/village info`)
   - Verify villages have names or boundaries if needed

### Integration Not Initializing

If you see "BlueMap integration not available" in logs:

1. **BlueMap not installed:**
   - Install BlueMap plugin
   - Restart server

2. **BlueMap API not accessible:**
   - Check BlueMap version compatibility
   - Integration uses reflection, so it should work with most versions
   - Check server logs for specific error messages

3. **Integration disabled in config:**
   - Set `bluemap.enabled: true` in `config.yml`
   - Run `/village reload` or restart server

### Markers Not Updating

If markers don't update when villages change:

1. **Check marker manager is initialized:**
   - Look for "BlueMap marker manager initialized successfully" in logs

2. **Verify village changes:**
   - Ensure villages are actually being updated (check storage)
   - Check that village lifecycle hooks are working

3. **Reload integration:**
   - Run `/village reload` to reload BlueMap configuration
   - Or restart server

## Debugging

Enable debug logging to see detailed BlueMap integration information:

```yaml
debug:
  enabled: true
```

This will show:
- BlueMap plugin detection
- API access attempts
- Marker creation/update/removal operations
- Error details

Example debug output:
```
[DEBUG] [14:23:45] [General] Initializing BlueMap integration...
[DEBUG] [14:23:45] [General] BlueMap plugin found: BlueMap v3.0.0
[DEBUG] [14:23:45] [General] Found BlueMap API via BlueMapAPI.getInstance()
[DEBUG] [14:23:45] [General] Found MarkerAPI via getMarkerAPI()
[DEBUG] [14:23:45] [General] Creating BlueMap marker set...
[INFO] [14:23:45] [General] BlueMap marker set created successfully
[DEBUG] [14:23:45] [General] Loading all villages from storage for BlueMap markers...
[INFO] [14:23:45] [General] Created BlueMap markers for 5 villages
[INFO] [14:23:45] [General] BlueMap integration enabled successfully
```

## Technical Details

### Reflection-Based API Access

The integration uses Java reflection to access BlueMap's API, which means:

- **No compile-time dependency** - The plugin doesn't require BlueMap API at build time
- **Version flexibility** - Works with different BlueMap versions
- **Graceful degradation** - If BlueMap isn't installed, integration simply doesn't activate

### API Access Patterns

The integration tries multiple patterns to find BlueMap's API:

1. `blueMapPlugin.getBlueMap()` - Direct method call
2. `BlueMapAPI.getInstance()` - Static factory method
3. Method scanning - Searches plugin class for API-returning methods

For MarkerAPI:
1. `blueMapApi.getMarkerAPI()` - Direct method call
2. `MarkerAPI.getInstance()` - Static factory method
3. Method scanning - Searches API class for marker-related methods

### Marker Management

- **Marker IDs**: Use village UUIDs converted to safe IDs (e.g., `village_abc12345`)
- **Marker Set**: Single set for all villages, toggleable in BlueMap UI
- **Lifecycle**: Markers are created, updated, and removed automatically

### Boundary Conversion

Village boundaries (AABB) are converted to polygons:
- Four corners: (minX, minZ), (maxX, minZ), (maxX, maxZ), (minX, maxZ)
- Uses block centers for cleaner appearance
- Depth/height from boundary Y coordinates

## Performance Considerations

- Marker operations are performed asynchronously to avoid blocking the main thread
- Existing villages are loaded and marked on plugin startup
- Marker updates happen only when villages change
- No performance impact if BlueMap is not installed

## Future Enhancements

Potential future features:
- Configurable icon per village
- Click actions on markers (teleport, info panel)
- Different colors/styles per village
- Marker visibility based on zoom level
- Integration with village entrances (mark entrance markers)

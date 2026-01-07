# Region Integration Guide

This document explains how the Villages plugin integrates with region management plugins to provide village protection and area-based features.

## Supported Region Plugins

### WorldGuard (Primary)

WorldGuard is the most widely-used region protection plugin for Minecraft servers. The Villages plugin provides full integration with WorldGuard's region and flag system.

**Requirements:**
- WorldGuard 7.0.9+
- WorldEdit 7.3.0+

**Features:**
- Automatic cuboid region creation matching village boundaries
- Full flag support (PVP, mob-spawning, greeting, farewell, etc.)
- Region priority configuration
- Automatic flag application from config

### RegionGuard (Alternative)

RegionGuard is a lightweight alternative that doesn't require WorldEdit. Support is provided for servers preferring a simpler solution.

**Note:** RegionGuard integration uses reflection and may require updates for different RegionGuard versions.

## Configuration

In `config.yml`:

```yaml
regions:
  # Enable automatic region creation when villages are detected
  auto-create: false
  
  # Preferred region plugin: worldguard, regionguard, or auto (first available)
  provider: auto
  
  # Default flags to apply to new village regions
  default-flags:
    mob-griefing: deny        # Prevent creeper/enderman damage
    pvp: allow                # Allow PvP (set to deny to disable)
    greeting: "&aWelcome to %village_name%!"
    farewell: "&7Leaving %village_name%..."
```

## Region Naming Convention

Regions are named using the pattern: `village_[identifier]`

- If village has a name: `village_my_village_name` (slugified)
- If village has no name: `village_abc12345` (first 8 chars of UUID)

## Commands

### Creating Regions

```
/village region create
```

Creates a WorldGuard/RegionGuard region matching the village's calculated boundary. Applies default flags from config.

**Permission:** `villages.region.create`

### Deleting Regions

```
/village region delete
```

Removes the region associated with the village.

**Permission:** `villages.region.delete`

### Managing Flags

```
/village region flags              # List all flags
/village region flags <flag>       # Get a specific flag value
/village region flags <flag> <value>  # Set a flag
```

**Permission:** `villages.region.flags`

**Supported Flags:**
- `pvp` - allow/deny
- `mob-spawning` - allow/deny
- `mob-griefing` - allow/deny (creeper explosions, enderman)
- `greeting` - message shown on entry
- `farewell` - message shown on exit
- `entry` - allow/deny
- `build` - allow/deny

## Programmatic Usage

### Creating a Region

```java
RegionManager regionManager = plugin.getRegionManager();

// Check if available
if (regionManager.isAvailable()) {
    // Create region with default flags
    regionManager.createRegionWithDefaults(village, boundary)
        .thenAccept(regionId -> {
            if (regionId.isPresent()) {
                village.setRegionId(regionId.get());
                // Save village...
            }
        });
}
```

### Setting Flags

```java
regionManager.setFlag(village, "greeting", "&aWelcome!")
    .thenAccept(success -> {
        if (success) {
            // Flag set successfully
        }
    });
```

### Custom Provider

To add support for a different region plugin:

1. Create a class implementing `RegionProvider`:

```java
public class MyRegionProvider implements RegionProvider {
    @Override
    public String getName() {
        return "myregion";
    }
    
    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("MyRegion") != null;
    }
    
    @Override
    public CompletableFuture<String> createRegion(Village village, VillageBoundary boundary) {
        // Implementation...
    }
    
    // Implement other methods...
}
```

2. Register in `RegionManager.registerProviders()`:

```java
private void registerProviders() {
    // Existing providers...
    
    RegionProvider myRegion = new MyRegionProvider(plugin);
    if (myRegion.isAvailable()) {
        providers.add(myRegion);
        plugin.getLogger().info("MyRegion detected");
    }
}
```

3. Add soft dependency in `paper-plugin.yml`:

```yaml
dependencies:
  soft:
    WorldGuard: { load: BEFORE }
    RegionGuard: { load: BEFORE }
    MyRegion: { load: BEFORE }
```

## Placeholder Support

The following placeholders are available in flag values:

- `%village_name%` - Village display name (name if set, otherwise shortened UUID)

Example:
```yaml
greeting: "&aWelcome to %village_name%!"
```

## Region Updates

When village boundaries are recalculated (via POI changes or `/village border recalculate`), existing regions can be updated:

```java
// Recalculate boundary
VillageBoundary newBoundary = boundaryCalculator.calculateAndPopulate(village);
village.setBoundary(newBoundary);

// Update region if it exists
if (village.hasRegion()) {
    regionManager.updateRegion(village, newBoundary);
}
```

## Troubleshooting

### Region Not Created

1. Check that WorldGuard/RegionGuard is installed and enabled
2. Verify village has a calculated boundary (`/village border show`)
3. Check console for error messages
4. Ensure you have permission `villages.region.create`

### Flags Not Working

1. Verify region exists with `/village region flags`
2. Check WorldGuard's priority system - village regions have priority 10
3. Ensure flag name is correct (see supported flags above)

### Provider Not Detected

1. Check that the region plugin loads before Villages
2. Verify paper-plugin.yml has correct soft dependency
3. Check console for "detected" messages on startup

## Performance Considerations

- Region operations are performed asynchronously
- WorldGuard API calls are thread-safe
- Region creation happens only when explicitly triggered or if `auto-create: true`
- Batch operations (applying multiple flags) are sequential to avoid race conditions

# Installation Guide

This guide covers installing and configuring the Villages plugin for your Minecraft server.

## Requirements

### Server Requirements

- **Minecraft Version:** 1.21.1 or higher
- **Server Software:** Paper (required - uses NMS features)
- **Java Version:** 21 or higher

### Optional Dependencies

- **WorldGuard 7.0.9+** - For region protection features
- **WorldEdit 7.3.0+** - Required by WorldGuard
- **RegionGuard** - Alternative to WorldGuard (lighter weight)
- **BlueMap** - For web map integration with village markers

## Installation Steps

### 1. Download the Plugin

Download the latest `Villages-x.x.x.jar` from the releases page.

### 2. Install Dependencies

#### CommandAPI (Required)

The Villages plugin bundles CommandAPI, so no separate installation is needed.

#### WorldGuard (Optional)

If you want region protection features:

1. Download WorldEdit from [EngineHub](https://enginehub.org/worldedit)
2. Download WorldGuard from [EngineHub](https://enginehub.org/worldguard)
3. Place both JARs in your `plugins` folder

#### BlueMap (Optional)

If you want village markers on your web map:

1. Download BlueMap from [Modrinth](https://modrinth.com/plugin/bluemap) or [GitHub](https://github.com/BlueMap-Minecraft/BlueMap)
2. Place the JAR in your `plugins` folder
3. Configure BlueMap according to its documentation
4. Place a village icon at `plugins/BlueMap/web/assets/village-icon.png` (or configure custom path)
5. Enable integration in `config.yml`: `bluemap.enabled: true`

### 3. Install Villages

1. Place `Villages-x.x.x.jar` in your server's `plugins` folder
2. Start (or restart) your server
3. The plugin will create default configuration files

### 4. Configure the Plugin

#### Main Configuration

Edit `plugins/Villages/config.yml` to customize:

```yaml
# Storage backend (yaml, sqlite, mysql)
storage:
  type: sqlite  # Recommended for most servers

# Region integration
regions:
  auto-create: false  # Set to true for automatic protection
  provider: auto      # Uses WorldGuard if available

# Entrance detection
entrances:
  auto-detect: true
  min-path-width: 2

# Welcome signs
signs:
  auto-place: true
  material: OAK_WALL_SIGN

# BlueMap integration
bluemap:
  enabled: true
  icon: "assets/village-icon.png"
  show-boundaries: true
  boundary-color: "#FF6B00"
  boundary-opacity: 0.3
```

#### Village Naming Configuration

The plugin automatically generates names for villages using adjective+noun patterns. Customize the word lists in `plugins/Villages/names.yml`:

```yaml
# Biome-specific word lists
plains:
  adjectives: [Green, Golden, Peaceful, Quiet, Bright, Verdant, Breezy, Rolling, Sunny, Gentle, Open, Lush]
  nouns: [Meadow, Valley, Hill, Field, Grove, Pasture, Prairie, Orchard, Ridge, Hollow, Crossing, Brook]

desert:
  adjectives: [Sandy, Golden, Sunlit, Arid, Warm, Blazing, Dry, Dusty, Scorched, Ember, Mirage, Windy]
  nouns: [Oasis, Dune, Mesa, Well, Shade, Caravan, Canyon, Spire, Wadi, Basin, Outcrop, Mirage]

# ... other biomes ...

# Terrain feature modifiers (used as prefixes or suffixes)
coastal:
  prefixes: [Port, Seaside, Coastal, Harbor, Bay, Tide, Salt, Ocean, Breaker, Gull, Wave, Anchor]
  suffixes: [Harbor, Port, Cove, Bay, Shore, Reef, Strand, Point, Quay, Inlet, Sound, Breakwater]

river:
  prefixes: [River, Riverside, Flowing, Brook, Bend, Delta, Ford, Current, Rapids, Willow, Channel, Stream]
  suffixes: [Ford, Crossing, Bridge, Bend, Reach, Flow, Run, Bank, Channel, Rapids, Shoal, Delta]

beach:
  prefixes: [Beach, Sandy, Shoreline, Dune, Sunlit, Shell, Drift, Tide, Sun, Surf, Palm, Coral]
  suffixes: [Beach, Shore, Dune, Strand, Sands, Surf, Lagoon, Spit, Bight, Bay, Reef, Point]
```

**Naming Patterns:**
- **Base**: `[biome adjective] [biome noun]` (e.g., "Green Meadow", "Pine Rest")
- **Prefix mode**: `[modifier prefix] [biome adjective] [biome noun]` (e.g., "Port Pine Rest")
- **Suffix mode**: `[biome adjective] [biome noun] [modifier suffix]` (e.g., "Pine Rest Harbor")
- **Both (rare)**: `[modifier prefix] [biome adjective] [biome noun] [modifier suffix]` (2% chance)

The plugin will automatically:
- Generate names for new villages based on their biome type
- Detect coastal, river, and beach villages and add modifiers (prefix or suffix)
- Always preserve biome identity in names (modifiers wrap the base name)
- Never override player-assigned names
- Generate names during periodic recheck for unnamed villages or UUID-named villages

If you want to regenerate a name manually, run `/village name` with a blank name.

### 5. Set Permissions

Default permissions are set for operators only. Configure with your permissions plugin:

```yaml
# Basic permissions
villages.name: true          # Name villages
villages.info: true          # View village info

# Border commands
villages.border.show: true   # Visualize boundaries
villages.border.recalculate: op

# Entrance commands
villages.entrance.add: op
villages.entrance.remove: op
villages.entrance.list: true
villages.entrance.detect: op

# Region commands
villages.region.create: op
villages.region.delete: op
villages.region.flags: op

# Admin commands
villages.admin.reload: op
villages.admin.migrate: op
villages.admin.backup: op
villages.admin.debug: op     # Debug toggle commands
```

## Storage Configuration

### SQLite (Recommended)

```yaml
storage:
  type: sqlite
  sqlite:
    file: villages.db  # Stored in plugins/Villages/
```

SQLite is the recommended default:
- No external server required
- Good performance for most servers
- Single file backup

### YAML

```yaml
storage:
  type: yaml
```

Best for:
- Small servers (< 50 villages)
- Human-readable data
- Easy manual editing

### MySQL

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: villages
    username: minecraft
    password: your_password
    pool-size: 10
```

Best for:
- Multi-server networks (BungeeCord/Velocity)
- Very large servers
- External tool access

**Note:** MySQL connector is loaded dynamically when MySQL is configured.

## Migrating Data

To migrate between storage backends:

```
/village migrate <from> <to>
```

Example: `/village migrate yaml sqlite`

This copies all village data from the source to the destination.

## Periodic Village Recheck

The plugin includes a periodic recheck system that scans loaded chunks for bells and merges them into existing villages:

```yaml
detection:
  # Recalculate village boundaries periodically (in ticks, 0 to disable)
  # 72000 ticks = 1 hour
  # During recheck: scans loaded chunks for bells, merges them into existing villages,
  # and recalculates boundaries to catch POI changes
  recalculate-interval: 72000
```

**What the recheck does:**
- Scans all loaded chunks in all worlds for bells
- Merges bells into existing villages if they're within village boundaries
- Updates signs around all bells found
- Recalculates boundaries for existing villages to catch POI changes
- Only processes loaded chunks (for performance)

**Performance Notes:**
- Set to `0` to disable periodic recheck (bells still detected on chunk load)
- Lower intervals increase server load but catch changes faster
- Higher intervals reduce load but may miss changes until chunks reload

## Debug Configuration

For troubleshooting, enable debug logging in `config.yml`:

```yaml
debug:
  enabled: true         # Master switch - must be true
  verbose: false        # Extra detailed output
  log-storage: true     # Storage operations
  log-regions: true     # Region operations
  log-boundaries: true  # Boundary calculations
  log-entrances: true   # Entrance detection
```

Or toggle at runtime without editing config:

```
/village debug on          # Enable debug mode
/village debug storage     # Toggle storage logging
/village debug regions     # Toggle region logging
/village debug boundaries  # Toggle boundary logging
/village debug entrances   # Toggle entrance logging
/village debug             # Show current status
/village debug off         # Disable all debug
```

Debug output format: `[DEBUG] [HH:mm:ss] [Category] Message`

## First-Time Setup

After installation:

1. **Verify plugin loaded:**
   ```
   /plugins
   ```
   Look for "Villages" in green

2. **Check plugin info:**
   ```
   /village info
   ```
   Shows version, storage type, village count

3. **Test village detection:**
   - Go to a village with a bell
   - Run `/village info` to see if it's detected

4. **Name a village:**
   ```
   /village name My Village
   ```

5. **View the boundary:**
   ```
   /village border show 30
   ```
   Shows particles for 30 seconds

## Upgrading

### From 0.1.x to 0.2.x

Version 0.2.0 introduces a new storage system. Your existing data will be migrated automatically:

1. Stop your server
2. Backup `plugins/Villages/villages.yml`
3. Replace the plugin JAR
4. Start the server
5. Data will be migrated to the new format

If you switch to SQLite:
1. After startup, run `/village migrate yaml sqlite`
2. Set `storage.type: sqlite` in config.yml
3. Restart the server

## Troubleshooting

### Plugin Not Loading

1. Check server log for errors
2. Verify Java 21+: `java -version`
3. Verify Paper server (not Spigot)
4. Check plugin dependencies

### Villages Not Detected

1. Ensure chunk has a bell block
2. Wait for chunk to fully load
3. Check console for detection messages

### Region Features Not Working

1. Verify WorldGuard is installed
2. Check `/village info` shows "worldguard" as provider
3. Ensure village has boundary: `/village border show`

### BlueMap Markers Not Appearing

1. Verify BlueMap is installed and enabled
2. Check server logs for "BlueMap integration enabled successfully"
3. Ensure `bluemap.enabled: true` in config.yml
4. Verify village icon exists at `plugins/BlueMap/web/assets/village-icon.png`
5. Check that villages have been detected (use `/village info`)
6. See `docs/BLUEMAP.md` for detailed troubleshooting

### Performance Issues

1. Switch from YAML to SQLite storage
2. Enable async storage: `performance.async-storage: true`
3. Limit cache size: `performance.cache-size: 500`

### Debug Logging Not Working

1. Ensure `debug.enabled: true` in config.yml
2. Enable specific category: `debug.log-storage: true`
3. Or use runtime toggle: `/village debug on`
4. Check you have `villages.admin.debug` permission

## Uninstallation

1. Stop your server
2. Remove `Villages-x.x.x.jar` from plugins
3. (Optional) Remove `plugins/Villages/` folder

Data is preserved in the Villages folder unless manually deleted.

## Getting Help

- **Issues:** Report on GitHub Issues
- **Documentation:** See other docs in this folder
- **Source:** Available on GitHub

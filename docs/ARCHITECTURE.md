# Villages Plugin Architecture

This document describes the architecture of the Villages plugin, including its components, data flow, and extension points.

## Overview

The Villages plugin provides comprehensive village management for Minecraft servers, including:
- Automatic village detection using NMS POI system
- Accurate boundary calculation matching Minecraft mechanics
- Multiple storage backends (YAML, SQLite, MySQL)
- Region plugin integration (WorldGuard, RegionGuard)
- Entrance detection and welcome signs
- Extensive command interface

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        VillagesPluginBootstrap                                │
│                    (Paper Plugin Bootstrapper)                                │
│  - Initializes ConfigManager and PluginLogger before plugin creation           │
│  - Ensures proper initialization order                                       │
└──────────────────────────────┬───────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              VillagesPlugin                                  │
│                           (Main Plugin Class)                                │
└─────────────────┬───────────────────────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
    │             │             │              │              │              │              │
    ▼             ▼             ▼              ▼              ▼              ▼              ▼
┌─────────┐ ┌──────────┐ ┌───────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────┐
│ Config  │ │ Plugin   │ │ Storage   │ │  Region    │ │ Detection  │ │ BlueMap    │ │Commands│
│ Manager │ │ Logger   │ │ Manager   │ │  Manager   │ │  System    │ │Integration│ │        │
└────┬────┘ └────┬─────┘ └─────┬─────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └────────┘
     │           │             │             │              │              │
     │           │             ▼             ▼              ▼              ▼
     │           │       ┌─────────┐  ┌───────────┐  ┌─────────────┐ ┌──────────────┐
     │           │       │Provider │  │ Provider  │  │ Entrance    │ │   Marker     │
     │           │       │Interface│  │ Interface │  │ Detector    │ │  Manager     │
     │           │       └────┬────┘  └─────┬─────┘  │ & Marker    │ └──────────────┘
     │           │            │             │        └──────┬──────┘
     │           │    ┌───────┼───────┐     │               │
     │           │    ▼       ▼       ▼     ▼               ▼
     │           │ ┌────────┐ ┌────────┐ ┌───────────────┐ ┌─────────────┐
     │           │ │  YAML  │ │ SQLite │ │  WorldGuard   │ │  Welcome    │
     │           │ │Provider│ │Provider│ │   Provider    │ │  Signs      │
     │           │ └────────┘ └────────┘ └───────────────┘ └─────────────┘
     │           │
     └───────────┴─── Used by all components for logging ───────────────────
```

## Core Components

### VillagesPluginBootstrap

The Paper plugin bootstrapper that initializes core components before the plugin is created:
- Implements `PluginBootstrap` from Paper's API
- Creates `ConfigManager` and `PluginLogger` in `createPlugin()` method
- Ensures proper initialization order to prevent `NullPointerException` issues
- Sets components in the plugin instance via setter methods

**Why a bootstrapper?**
- `ConfigManager` needs `PluginLogger` for logging, but `PluginLogger` needs `ConfigManager` for debug settings
- The bootstrapper resolves this circular dependency by creating both in the correct order
- Components are available immediately when the plugin instance is created

### VillagesPlugin

The main plugin class that bootstraps all components:
- Initializes CommandAPI in `onLoad()`
- Receives pre-initialized `ConfigManager` and `PluginLogger` from bootstrapper
- Creates and links all managers in `onEnable()`
- Handles configuration loading and reloading
- Manages plugin lifecycle

**Initialization order:**

**Bootstrap Phase (VillagesPluginBootstrap.createPlugin()):**
1. Create `VillagesPlugin` instance
2. Create `ConfigManager` (logger may be null initially)
3. Create `PluginLogger` with `ConfigManager`
4. Set logger in `ConfigManager` (now that it's available)
5. Set both components in plugin instance

**Enable Phase (VillagesPlugin.onEnable()):**
1. `saveDefaultConfig()` - Creates config.yml if missing
2. Verify `ConfigManager` and `PluginLogger` are set (fallback creation if bootstrapper wasn't used)
3. `StorageManager` - Initialize async storage
4. `RegionManager` - Detect and initialize region plugins
5. `VillageBoundaryCalculator`, `EntranceDetector`, `EntranceMarker`, `WelcomeSignPlacer`
6. Event listeners and commands

### Configuration System

Located in `org.clockworx.villages.config`:

- **ConfigManager** - Provides typed access to all configuration values
  - Caches frequently accessed values (debug settings)
  - Supports runtime modification of debug settings
  - Handles `reload()` to refresh values from disk
  - Provides `MySQLConfig` record for database settings

```java
// Example usage
ConfigManager config = plugin.getConfigManager();
boolean debug = config.isDebugEnabled();
config.setLogStorage(true);  // Persists to config.yml
```

### Logging System

Located in `org.clockworx.villages.util`:

- **PluginLogger** - Enhanced logging with categories and timestamps
- **LogCategory** - Enum of log categories (GENERAL, STORAGE, REGION, BOUNDARY, ENTRANCE, COMMAND)

Log format: `[DEBUG] [HH:mm:ss] [Category] Message`

Features:
- Respects `debug.enabled` master switch
- Per-category filtering via config
- Thread-safe operation
- Convenience methods for each category

```java
// Example usage
PluginLogger logger = plugin.getPluginLogger();
logger.info("Normal message");
logger.debugStorage("Storage operation details");  // Only if debug.log-storage: true
logger.verbose(LogCategory.BOUNDARY, "Very detailed info");  // Only if verbose: true
```

### Storage System

Located in `org.clockworx.villages.storage`:

- **StorageProvider** - Interface defining CRUD operations for villages
- **StorageManager** - Manages provider lifecycle and selection
- **YamlStorageProvider** - Human-readable file storage
- **SQLiteStorageProvider** - Embedded database (recommended default)
- **MySQLStorageProvider** - Network database for multi-server setups

All storage operations return `CompletableFuture` for async execution.

### Data Models

Located in `org.clockworx.villages.model`:

- **Village** - Primary data model with UUID, name, bell location, boundary, POIs, entrances
- **VillageBoundary** - Axis-aligned bounding box with center calculation
- **VillagePoi** - Point of Interest (bell, bed, job site)
- **VillageEntrance** - Entry point with facing direction

### Bell Merging System

The plugin automatically merges multiple bells within the same village polygon:

1. **Boundary Check** - Before creating a new village, checks if bell location is within any existing village's boundary
2. **PDC Synchronization** - All bells in the same village share the same UUID in their Persistent Data Containers
3. **Boundary Recalculation** - When bells are merged, village boundary is recalculated to ensure accurate coverage
4. **Storage Integration** - Uses `StorageManager.findVillageAt()` to check for existing villages at bell location

### Boundary Calculation

Located in `org.clockworx.villages.boundary`:

- **VillageBoundaryCalculator** - Uses NMS PoiManager to access Minecraft's internal POI data
- Implements Minecraft's village expansion rules (32H/52V)
- Calculates bounding box from connected POIs

NMS Access via paperweight-userdev:
```java
ServerLevel serverLevel = ((CraftWorld) world).getHandle();
PoiManager poiManager = serverLevel.getPoiManager();
```

### Region Integration

Located in `org.clockworx.villages.regions`:

- **RegionProvider** - Interface for region operations
- **RegionManager** - Detects available plugins and routes calls
- **WorldGuardProvider** - WorldGuard API implementation
- **RegionGuardProvider** - RegionGuard fallback

Supports automatic region creation with configurable default flags.

### Detection System

Located in `org.clockworx.villages.detection`:

- **EntranceDetector** - Scans boundary perimeter for path blocks
- **EntranceMarker** - Handles manual entrance marking via commands

### Signs System

Located in `org.clockworx.villages.signs` and `org.clockworx.villages.managers`:

- **SignManager** - Manages sign placement around village bells
  - **Duplicate detection** - Searches for existing signs within a 3-block radius
  - **Content verification** - Reads sign content to identify village signs
  - **Automatic cleanup** - Removes duplicate signs that don't belong to the current village
  - **Bell-attachment placement** - Uses a single strategy driven by how the bell is placed (wall, floor, ceiling)
- **WelcomeSignPlacer** - Places/updates welcome signs at entrances
- Configurable text with %village_name% placeholder
- Non-destructive placement
- **BiomeSignPlacementStrategy** - Interface for sign placement strategies (block location and facing)
- **BellAttachmentSignPlacementStrategy** - Single implementation used for all bells:
  - **Wall-mounted bells** - Places up to two signs above or below the bell on the same support pillar when the support has solid above/below; otherwise places signs on the horizontal facets of the support block
  - **Ceiling bells** - Places wall signs on the four horizontal facets of the block the bell hangs from
  - **Floor bells** - Places wall signs on the four horizontal facets of the block the bell stands on
- **VillageBiomeDetector** - Detects village biome type (used by naming system, not sign placement)

**Sign Placement Flow:**
1. Read bell block data (attachment: FLOOR, CEILING, SINGLE_WALL, DOUBLE_WALL) and compute support block
2. Calculate sign positions from attachment (above/below bell or on support block facets)
3. Search for existing signs within 3-block radius
4. Remove duplicate signs that don't match current village
5. Update existing signs that do match; place new signs where position is clear

### Naming System

Located in `org.clockworx.villages.naming`:

- **VillageNameGenerator** - Generates automatic names using adjective+noun patterns
- **TerrainFeatureDetector** - Detects terrain features (coastal, rivers, beaches)
- **Biome-Specific Naming** - Names are generated based on village biome type
- **Configurable Word Lists** - All naming words stored in `names.yml` for customization
- **Terrain Feature Modifiers** - Coastal, river, and beach modifiers use prefixes/suffixes around the base name
- **Automatic Naming Triggers** - Villages are named on creation and during periodic recheck (including UUID-named villages)
- **Never Overrides Player Names** - Automatic naming skips user-provided names unless regeneration is requested

**Naming Patterns:**
- **Base**: `[biome adjective] + [biome noun]` (e.g., "Green Meadow", "Pine Rest")
- **Prefix mode**: `[modifier prefix] + [biome adjective] + [biome noun]` (e.g., "Port Pine Rest", "River Golden Meadow")
- **Suffix mode**: `[biome adjective] + [biome noun] + [modifier suffix]` (e.g., "Pine Rest Harbor", "Golden Meadow Crossing")
- **Both (rare)**: `[modifier prefix] + [biome adjective] + [biome noun] + [modifier suffix]` (2% chance)

Features:
- Always uses biome words for base name (never replaces entirely)
- Terrain modifiers add prefix/suffix words around the base pair
- Avoids duplicate words across prefix/adjective/noun/suffix (prevents "Bay Bay")
- Biome-specific word lists (Plains, Desert, Savanna, Taiga, Snowy Plains)
- Coastal detection using boundary scanning and radius checking
- River and beach detection using biome sampling around bell/boundary
- Word lists loaded from `names.yml` on startup and can be reloaded

### Commands

Located in `org.clockworx.villages.commands`:

- **VillageCommands** - Extended command handler using CommandAPI
- Subcommands for: name, info, border, entrance, region, admin

### Scheduled Tasks

Located in `org.clockworx.villages.tasks`:

- **VillageRecheckTask** - Periodic task that rechecks villages for bell merging and boundary recalculation
- Runs at configurable interval (default: 72000 ticks = 1 hour)
- Scans all loaded chunks for bells
- Merges bells into existing villages if within boundaries
- Recalculates boundaries for existing villages to catch POI changes
- Only processes loaded chunks for performance

### BlueMap Integration

Located in `org.clockworx.villages.integration`:

- **BlueMapIntegration** - Main integration class using reflection to access BlueMap API
- **BlueMapMarkerManager** - Manages POI and shape markers for villages
- **BoundaryToPolygonConverter** - Converts village boundaries to polygon coordinates

Features:
- Reflection-based API access (no compile-time dependency)
- Automatic marker creation/updates/deletion
- Configurable icons, colors, and visibility
- Graceful degradation if BlueMap is not installed

## Data Flow

### Village Detection Flow

```
Chunk Loads → ChunkLoadEvent → Scan for Bells → For each Bell:
    → Check PDC for UUID
    → Check Storage for existing village by bell location
    → Check if bell is within existing village boundary (bell merging)
    → If merged: Update PDC, recalculate boundary
    → If new: Create Village object
    → Calculate Boundary (NMS POI scan)
    → Generate Name (if unnamed, using biome and terrain features)
    → Detect Entrances
    → Place Signs (bell-attachment placement)
    → Create Region (if configured)
    → Place Welcome Signs (if configured)
    → Save to Storage

Periodic Recheck (every recalculate-interval ticks):
    → Scan all loaded chunks for bells
    → Process bells without UUIDs (triggers merging)
    → Update signs for all bells found
    → Recalculate boundaries for existing villages (if bell chunk loaded)
    → Generate names for unnamed villages or UUID-named villages (if name generator available)
```

### Command Flow

```
Player executes /village command
    → CommandAPI parses and validates
    → Handler finds nearest village
    → Performs operation
    → Saves changes async
    → Sends feedback to player
```

## Configuration

Main config at `plugins/Villages/config.yml`:

```yaml
storage:
  type: sqlite  # yaml, sqlite, mysql
  sqlite:
    file: villages.db
  mysql:
    host: localhost
    ...

regions:
  auto-create: false
  provider: auto  # worldguard, regionguard, auto
  default-flags:
    mob-griefing: deny
    greeting: "&aWelcome to %village_name%!"

entrances:
  auto-detect: true
  path-blocks: [DIRT_PATH, COBBLESTONE, ...]
  min-path-width: 2

signs:
  auto-place: true
  material: OAK_WALL_SIGN
  lines:
    - "Welcome to"
    - "%village_name%"
    ...

debug:
  enabled: false        # Master switch for all debug logging
  verbose: false        # Extra detailed output
  log-storage: false    # Storage operations (saves, loads, queries)
  log-regions: false    # Region operations (create, delete, flags)
  log-boundaries: false # Boundary calculations (POI scanning)
  log-entrances: false  # Entrance detection (path scanning)
```

### Debug Configuration

The debug system uses a hierarchical approach:
1. `debug.enabled` must be `true` for any debug logging
2. Individual categories can be toggled independently
3. `debug.verbose` enables extra-detailed logging

All debug settings can be changed at runtime via `/village debug` commands.

## Extension Points

### Adding a Storage Provider

1. Implement `StorageProvider` interface
2. Add case to `StorageManager.createProvider()`
3. Add config options

### Adding a Region Provider

1. Implement `RegionProvider` interface
2. Register in `RegionManager.registerProviders()`
3. Add soft dependency in paper-plugin.yml

### Adding Commands

1. Create command method returning `CommandAPICommand`
2. Add `.withSubcommand()` in `VillageCommands.register()`
3. Implement handler method

## Dependencies

### Runtime
- Paper API 1.21.1+ (required for Paper plugin bootstrapper)
- CommandAPI 11.1.0

### Optional
- WorldGuard 7.0.9+
- WorldEdit 7.3.0+
- RegionGuard (version TBD)
- BlueMap (any version - integration uses reflection)

### Shaded
- SQLite JDBC 3.45.1
- HikariCP 5.1.0

## Paper Plugin Features

The plugin uses Paper's plugin system features:

### Bootstrapper
- `VillagesPluginBootstrap` implements `PluginBootstrap`
- Declared in `paper-plugin.yml` with `bootstrapper:` field
- Runs before plugin instance creation
- Enables early initialization of core components

### Plugin Configuration
- Uses `paper-plugin.yml` for dependency management
- Supports `load: BEFORE/AFTER` for load order control
- Declares soft dependencies for WorldGuard, WorldEdit, RegionGuard, BlueMap

## Threading Model

- Main thread: Block operations, command handlers (immediate feedback)
- Async: Storage operations, boundary calculations, region API calls
- BukkitScheduler: Particle effects, periodic tasks

All async operations use `CompletableFuture` for proper chaining and error handling.

## Version Compatibility

- Minecraft: 1.21.1+
- Paper: Required (uses NMS access)
- Java: 21+

NMS mappings via paperweight-userdev ensure compatibility with Paper's remapped environment.

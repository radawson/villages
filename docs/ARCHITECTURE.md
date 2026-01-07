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
│                              VillagesPlugin                                  │
│                           (Main Plugin Class)                                │
└─────────────────┬───────────────────────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┬──────────────┬──────────────┐
    │             │             │              │              │
    ▼             ▼             ▼              ▼              ▼
┌─────────┐ ┌──────────┐ ┌───────────┐ ┌────────────┐ ┌────────────┐
│ Storage │ │ Boundary │ │  Region   │ │ Detection  │ │  Commands  │
│ Manager │ │Calculator│ │  Manager  │ │  System    │ │            │
└────┬────┘ └────┬─────┘ └─────┬─────┘ └─────┬──────┘ └────────────┘
     │           │             │             │
     ▼           ▼             ▼             ▼
┌─────────┐ ┌─────────┐ ┌───────────┐ ┌─────────────┐
│Provider │ │NMS POI  │ │ Provider  │ │ Entrance    │
│Interface│ │Access   │ │ Interface │ │ Detector    │
└────┬────┘ └─────────┘ └─────┬─────┘ │ & Marker    │
     │                        │       └──────┬──────┘
     ├───────────┬───────────┐│              │
     ▼           ▼           ▼▼              ▼
┌────────┐ ┌────────┐ ┌───────────────┐ ┌─────────────┐
│  YAML  │ │ SQLite │ │  WorldGuard   │ │  Welcome    │
│Provider│ │Provider│ │   Provider    │ │  Signs      │
└────────┘ └────────┘ └───────────────┘ └─────────────┘
```

## Core Components

### VillagesPlugin

The main plugin class that bootstraps all components:
- Initializes CommandAPI in `onLoad()`
- Creates and links all managers in `onEnable()`
- Handles configuration loading and reloading
- Manages plugin lifecycle

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

Located in `org.clockworx.villages.signs`:

- **WelcomeSignPlacer** - Places/updates welcome signs at entrances
- Configurable text with %village_name% placeholder
- Non-destructive placement

### Commands

Located in `org.clockworx.villages.commands`:

- **VillageCommands** - Extended command handler using CommandAPI
- Subcommands for: name, info, border, entrance, region, admin

## Data Flow

### Village Detection Flow

```
Chunk Loads → ChunkLoadEvent → Scan for Bells → For each Bell:
    → Check PDC for UUID
    → Check Storage for existing village
    → Create/Update Village object
    → Calculate Boundary (NMS POI scan)
    → Detect Entrances
    → Create Region (if configured)
    → Place Welcome Signs (if configured)
    → Save to Storage
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
```

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
- Paper API 1.21.1+
- CommandAPI 11.1.0

### Optional
- WorldGuard 7.0.9+
- WorldEdit 7.3.0+
- RegionGuard (version TBD)

### Shaded
- SQLite JDBC 3.45.1
- HikariCP 5.1.0

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

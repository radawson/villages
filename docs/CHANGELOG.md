# Changelog

All notable changes to the Villages plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - BlueMap Integration

### Added

#### BlueMap Integration
- **Automatic marker creation** - Villages automatically appear on BlueMap web interface
- **POI markers** - Village icons with names at village centers
- **Shape markers** - Optional polygon outlines showing village boundaries
- **Reflection-based API access** - Works with any BlueMap version without compile-time dependency
- **Automatic updates** - Markers update when villages are renamed or boundaries change
- **Graceful degradation** - Plugin works normally if BlueMap is not installed

#### Configuration Options
New `bluemap` section in `config.yml`:
```yaml
bluemap:
  enabled: true
  icon: "assets/village-icon.png"
  show-boundaries: true
  boundary-color: "#FF6B00"
  boundary-opacity: 0.3
  marker-set-label: "Villages"
```

#### New Classes
- `org.clockworx.villages.integration.BlueMapIntegration` - Main integration class
- `org.clockworx.villages.integration.BlueMapMarkerManager` - Marker management
- `org.clockworx.villages.integration.BoundaryToPolygonConverter` - Boundary conversion utility

#### Documentation
- `docs/BLUEMAP.md` - Complete BlueMap integration guide
- Updated architecture documentation with BlueMap integration
- Updated installation guide with BlueMap requirements

### Changed
- `VillageManager` now notifies BlueMap integration when villages are created, updated, or deleted
- `VillagesPlugin` initializes BlueMap integration on startup
- Configuration reload now includes BlueMap integration reload

### Technical Details
- Uses Java reflection to access BlueMap API at runtime
- No compile-time dependency on BlueMap API
- Marker operations performed asynchronously
- Comprehensive logging for debugging integration issues

---

## [0.2.3] - File Logging

### Added

#### File-Based Logging
- **File output** - All log messages are now written to log files in addition to console
- **Daily rotation** - Log files are automatically rotated each day (format: `villages-YYYY-MM-DD.log`)
- **Size-based rotation** - Files exceeding the configured size limit are rotated automatically
- **Automatic cleanup** - Old log files are deleted when the maximum file count is exceeded
- **Full timestamp format** - File logs include full date and time: `[YYYY-MM-DD HH:mm:ss]`
- **Stack traces** - Exception stack traces are written to log files for debugging
- **Thread-safe** - File operations use synchronization for safe async logging

#### Configuration Options
New `logging.file` section in `config.yml`:
```yaml
logging:
  file:
    enabled: true           # Enable/disable file logging
    directory: logs         # Log directory under plugins/Villages/
    max-file-size-mb: 10    # Rotate when file exceeds this size
    max-files: 14           # Keep last N log files (0 = unlimited)
    include-debug: true     # Include DEBUG/VERBOSE messages in file
```

#### New PluginLogger Methods
- `shutdown()` - Flushes and closes the log file (called on plugin disable)
- `flush()` - Manually flush pending log data to file
- `isFileLoggingActive()` - Check if file logging is enabled and initialized
- `getCurrentLogFile()` - Get the path to the current log file

### Changed
- `PluginLogger` now writes to both console and file simultaneously
- File log format uses full date-time: `[2026-01-07 14:23:45] [INFO] [Category] Message`
- Console format remains unchanged for readability
- Shutdown sequence now properly closes log files

### Technical Details

#### Log File Format
```
[2026-01-07 14:23:45] [INFO] Villages plugin enabled
[2026-01-07 14:23:45] [DEBUG] [Storage] Loading village cache...
[2026-01-07 14:23:46] [SEVERE] [Region] Error creating region
java.lang.Exception: Stack trace details
    at org.clockworx.villages...
```

#### Rotation Behavior
- **Daily**: New file created at midnight (server time)
- **Size**: Current file renamed with index (`.1.log`, `.2.log`, etc.)
- **Cleanup**: Oldest files deleted when `max-files` limit exceeded

#### File Locations
- Log directory: `plugins/Villages/logs/`
- File naming: `villages-2026-01-07.log`
- Rotated files: `villages-2026-01-07.1.log`

---

## [0.2.2] - Comprehensive Logging Implementation

### Added

#### Comprehensive Logging Coverage
- **Command Logging** - All command handlers now log execution flow, parameters, and results
  - Entry/exit logging for all commands
  - Parameter validation logging
  - Success and failure logging with context
  - Command category logging for all village commands (name, info, border, entrance, region, admin, debug)

- **Manager Logging** - Detailed logging throughout all manager classes
  - **VillageManager**: Village creation, loading, saving, boundary recalculation, naming operations
  - **SignManager**: Sign placement, updates, and state management
  - All operations include debug-level flow tracking and info-level success messages

- **Storage Provider Logging** - Complete CRUD operation logging
  - **YamlStorageProvider**: File operations, serialization, cache management, backup operations
  - **SQLiteStorageProvider**: Database operations, transactions, migrations, connection management
  - **MySQLStorageProvider**: Connection pool operations, queries, migrations, backup operations
  - All storage operations include debug logging for entry/exit, parameters, and results

- **Region Provider Logging** - Region operation tracking
  - **WorldGuardProvider**: Region creation, updates, deletion, flag operations
  - **RegionGuardProvider**: Reflection-based operations, command execution, region management
  - Debug logging for all region plugin interactions

- **Detection Component Logging** - Enhanced logging for detection algorithms
  - **VillageBoundaryCalculator**: POI collection, boundary calculation steps, bounding box computation
  - **EntranceDetector**: Edge scanning, path detection, entrance merging
  - **EntranceMarker**: Manual entrance marking, validation, facing calculation
  - **WelcomeSignPlacer**: Sign placement operations, location finding, text setting

- **Event Listener Logging** - Chunk event processing
  - **VillageChunkListener**: Chunk load events, bell detection, village processing
  - Bell count tracking and village discovery logging

- **Configuration Logging** - Config reload and validation
  - **ConfigManager**: Configuration reload operations, invalid value warnings
  - Debug logging for config changes

#### Logging System Enhancements
- Added `warning(LogCategory, String, Throwable)` method to PluginLogger for exception logging
- All components now consistently use PluginLogger instead of direct `plugin.getLogger()` calls
- Exception logging with full stack traces for error diagnosis
- Verbose logging support for detailed algorithm step tracking

### Changed
- Replaced all direct `plugin.getLogger()` calls with `PluginLogger` throughout the codebase
- Standardized logging patterns across all components
- Enhanced error messages with context (village IDs, locations, operation types)
- Improved log message clarity with operation-specific details

### Technical Details

#### Components Updated
- `VillageCommands` - All command handlers
- `VillageManager` - All village lifecycle operations
- `SignManager` - Sign placement and updates
- `VillageChunkListener` - Chunk event processing
- `YamlStorageProvider` - File-based storage operations
- `SQLiteStorageProvider` - SQLite database operations
- `MySQLStorageProvider` - MySQL database operations
- `WorldGuardProvider` - WorldGuard integration
- `RegionGuardProvider` - RegionGuard integration
- `ConfigManager` - Configuration management
- `VillageBoundaryCalculator` - Boundary calculation algorithms
- `EntranceDetector` - Entrance detection algorithms
- `EntranceMarker` - Manual entrance marking
- `WelcomeSignPlacer` - Sign placement operations

#### Log Levels Used
- **DEBUG**: Method entry/exit, parameter values, intermediate steps (only when debug enabled)
- **INFO**: Successful operations, important state changes, operation completion
- **WARNING**: Validation failures, missing data, non-fatal errors, fallback operations
- **SEVERE**: Fatal errors, exceptions, critical system failures

#### Log Categories
- **COMMAND**: All command execution and validation
- **STORAGE**: All storage provider operations (YAML, SQLite, MySQL)
- **REGION**: All region plugin operations (WorldGuard, RegionGuard)
- **BOUNDARY**: Boundary calculation and POI collection
- **ENTRANCE**: Entrance detection and marking operations
- **GENERAL**: General plugin operations, configuration, sign placement

---

## [0.2.1] - Logging and Configuration System

### Added

#### Configuration Management
- **ConfigManager** - Typed access to all configuration values with caching
- Automatic `config.yml` creation on first startup via `saveDefaultConfig()`
- Runtime configuration reload via `/village reload`
- All configuration sections now have typed getter methods

#### Logging System
- **PluginLogger** - Custom logger with timestamps and category tags
- **LogCategory** enum - Categories: GENERAL, STORAGE, REGION, BOUNDARY, ENTRANCE, COMMAND
- Per-category debug filtering via config
- Log format: `[DEBUG] [HH:mm:ss] [Category] Message`
- Thread-safe logging operations

#### Debug Commands
- `/village debug` - Show current debug status
- `/village debug on` - Enable debug logging
- `/village debug off` - Disable debug logging
- `/village debug storage` - Toggle storage operation logging
- `/village debug regions` - Toggle region operation logging
- `/village debug boundaries` - Toggle boundary calculation logging
- `/village debug entrances` - Toggle entrance detection logging

### Changed
- All components now use `PluginLogger` instead of direct `plugin.getLogger()`
- Debug settings consolidated under `debug:` section in config.yml
- Removed duplicate `debug: false` from config.yml root level
- `VillagesPlugin.onEnable()` now properly initializes all managers in correct order
- Storage and region managers now use PluginLogger for debug output
- Config reload now properly refreshes all component configurations

### Fixed
- `config.yml` not being created on first startup
- Debug flag not being respected by logging calls
- Configuration not being passed to component managers

### Technical Details

#### New Classes
- `org.clockworx.villages.config.ConfigManager` - Configuration management
- `org.clockworx.villages.util.PluginLogger` - Enhanced logging
- `org.clockworx.villages.util.LogCategory` - Log category enumeration

#### Debug Configuration Structure
```yaml
debug:
  enabled: false        # Master switch
  verbose: false        # Extra detailed logging
  log-storage: false    # Storage operations
  log-regions: false    # Region operations
  log-boundaries: false # Boundary calculations
  log-entrances: false  # Entrance detection
```

---

## [0.2.0] - Major Architecture Update

### Added

#### Storage System
- **StorageProvider interface** - Abstraction layer for multiple storage backends
- **SQLiteStorageProvider** - Embedded database storage (recommended default)
- **MySQLStorageProvider** - Network database for multi-server deployments
- **YamlStorageProvider** - Refactored file-based storage
- **StorageManager** - Manages provider lifecycle and data migration
- Storage configuration in `config.yml` for backend selection and connection settings
- `/village migrate <from> <to>` command for data migration between backends
- `/village backup` command for creating storage backups

#### Village Boundary System
- **VillageBoundaryCalculator** - NMS-based POI scanning for accurate boundaries
- Implements Minecraft's village expansion rules (32H/52V)
- Calculates bounding box from connected POIs (beds, bells, job sites)
- `/village border show [duration]` - Visualize village boundary with particles
- `/village border recalculate` - Force boundary recalculation

#### Data Models
- **Village** - Comprehensive village data model with UUID, name, boundary, POIs, entrances
- **VillageBoundary** - Axis-aligned bounding box with center calculation
- **VillagePoi** - Point of Interest representation
- **VillageEntrance** - Entry point with facing direction and auto-detection flag

#### Region Integration
- **RegionProvider interface** - Abstraction for region plugins
- **WorldGuardProvider** - Full WorldGuard API integration
- **RegionGuardProvider** - Alternative lightweight provider
- **RegionManager** - Detects available plugins and routes operations
- `/village region create` - Create protected region matching village boundary
- `/village region delete` - Remove village region
- `/village region flags [flag] [value]` - View/set region flags
- Default flag configuration in `config.yml`

#### Entrance Detection
- **EntranceDetector** - Automatic road entrance detection at village boundaries
- Scans for path blocks (DIRT_PATH, COBBLESTONE, etc.)
- Configurable path materials and minimum path width
- **EntranceMarker** - Manual entrance marking via commands
- `/village entrance add` - Mark current location as entrance
- `/village entrance remove` - Remove nearest entrance
- `/village entrance list` - List all entrances
- `/village entrance detect` - Run automatic detection

#### Welcome Signs
- **WelcomeSignPlacer** - Places welcome signs at village entrances
- Configurable sign text with `%village_name%` placeholder
- Non-destructive placement (only replaces air/vegetation)
- Auto-placement when entrances are detected or marked

#### Commands
- Extended `/village` command with subcommand structure
- `/village reload` - Reload configuration
- New permissions for all features

#### Documentation
- `docs/ARCHITECTURE.md` - System architecture overview
- `docs/REGIONS.md` - Region integration guide
- `docs/INSTALLATION.md` - Installation and configuration guide
- Updated `docs/CHANGELOG.md` with this version

### Changed
- Complete storage system rewrite from chunk-based to village-centric model
- Storage now persists full village data (boundary, POIs, entrances, region ID)
- Configuration file expanded with new sections for storage, regions, entrances, signs
- Build system updated with new dependencies (SQLite, HikariCP, WorldGuard/WorldEdit)

### Technical Details

#### NMS Access
- Uses paperweight-userdev for mapped NMS access
- Accesses PoiManager for native POI data
- Compatible with Paper's mojang-mapped environment

#### Dependencies Added
- `org.xerial:sqlite-jdbc:3.45.1.0` - SQLite driver
- `com.zaxxer:HikariCP:5.1.0` - Connection pooling for MySQL
- `com.sk89q.worldguard:worldguard-bukkit:7.0.9` - Region integration (compileOnly)
- `com.sk89q.worldedit:worldedit-bukkit:7.3.0` - WorldGuard dependency (compileOnly)

#### Database Schema
- `villages` table with boundary, region, and timestamp columns
- `village_pois` table for POI storage
- `village_entrances` table for entrance points
- Indexed for efficient queries

## [0.1.3]

### Added
- Village naming feature via `/village name <name>` command
- CommandAPI integration for command handling
- Persistent Data Container (PDC) storage for village names
- File-based UUID persistence system (`villages.yml`) for maintaining UUIDs across bell removal
- Sign display now shows village names instead of UUIDs when a name is set
- Permission system: `villages.name` (default: OP only)
- Proximity-based village detection: command finds nearest bell in player's chunk
- `/village info` command to display plugin version, village count, and storage information
- Documentation: `docs/fongi.md` - Command system guide for adding new commands
- Documentation: `docs/changelog.md` - This file

### Changed
- SignManager now accepts optional village name parameter
- Signs display "Village: [name]" when named, or "Village UUID: [uuid]" when unnamed
- VillageChunkListener now retrieves and passes village names when placing signs
- Sign placement distance increased from 1 block to 2 blocks away from bell to prevent blocking access
- Sign replacement logic: existing signs are now updated instead of being replaced when renaming villages
- UUID storage: Added file-based persistence alongside PDC to maintain UUIDs when bells are removed
- VillageManager now checks file storage when PDC doesn't contain a UUID, restoring UUIDs to new bells in the same chunk

### Technical Details

#### Command Implementation
- Uses CommandAPI (by Skepter) version 11.1.0
- Command structure: `/village name <name>`
- Requires permission: `villages.name` (default: OP)
- Detection method: Finds nearest bell block in player's current chunk

#### Data Storage
- Village names stored in PDC using NamespacedKey: `villages:village_name`
- Names persist across server restarts
- Names are stored alongside existing UUID data

#### Sign Display Logic
- If village has a name: Displays "Village: [name]" (name split across lines if needed)
- If village has no name: Displays "Village UUID: [uuid]" (UUID split across lines)
- Signs are updated automatically when a name is set
- Signs are placed 2 blocks away from the bell horizontally to prevent blocking access
- Existing signs at target locations are updated rather than replaced

#### File-based UUID Storage
- New `VillageStorage` class manages persistent UUID storage
- Storage file: `plugins/Villages/villages.yml`
- Format: `world_name -> chunkX_chunkZ -> uuid`
- UUIDs are stored by chunk coordinates, allowing UUID persistence when bells are removed
- When a new bell is placed in a chunk with an existing UUID, the UUID is restored to the bell's PDC
- Dual storage system: PDC (on bell) + File storage (by chunk) ensures maximum persistence

## [0.1.0] - Initial Release

### Added
- Automatic village detection when chunks load
- Unique UUID assignment to each village bell
- Persistent Data Container (PDC) storage for UUIDs
- Visual identification: Signs placed around bells displaying UUIDs
- Zero configuration required

### Technical Details
- Detects village bells (`Material.BELL`) when chunks load
- Stores UUIDs in PDC using NamespacedKey: `villages:village_uuid`
- Places signs on all four cardinal directions around each bell
- Signs display UUID split across multiple lines for readability

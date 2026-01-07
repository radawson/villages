# Changelog

All notable changes to the Villages plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

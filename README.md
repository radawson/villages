# Villages Plugin

A Paper plugin for Minecraft that provides comprehensive village management with automatic boundary detection, region protection, and welcome signs.

## Overview

The Villages plugin enhances Minecraft villages by:
- **Detecting villages** using Minecraft's native POI (Point of Interest) system
- **Calculating accurate boundaries** based on beds, bells, and job sites
- **Creating protected regions** via WorldGuard or RegionGuard integration
- **Detecting road entrances** and placing welcome signs
- **Storing village data** in YAML, SQLite, or MySQL databases

## Features

### Core Features
- **Automatic Village Detection**: Uses NMS POI access for accurate Minecraft-native detection
- **Accurate Boundaries**: Implements Minecraft's village expansion rules (32H/52V from POIs)
- **Unique Identification**: Each village gets a persistent UUID
- **Village Naming**: Name villages with `/village name <name>`

### Storage System
- **Multiple Backends**: YAML (simple), SQLite (recommended), MySQL (networks)
- **Async Operations**: Non-blocking storage for smooth performance
- **Data Migration**: Easily migrate between storage backends
- **Automatic Backups**: Built-in backup command

### Region Protection
- **WorldGuard Integration**: Create protected regions matching village boundaries
- **RegionGuard Support**: Lightweight alternative option
- **Configurable Flags**: Set default flags for new village regions
- **Welcome Messages**: Automatic greeting/farewell messages

### Entrance System
- **Automatic Detection**: Finds road entrances by scanning for path blocks
- **Manual Marking**: Add custom entrance points via commands
- **Welcome Signs**: Place "Welcome to [Village Name]" signs at entrances

## Requirements

- **Minecraft Version**: 1.21.1+
- **Server Software**: Paper (required for NMS access)
- **Java Version**: 21+

### Optional Dependencies
- WorldGuard 7.0.9+ (for region protection)
- WorldEdit 7.3.0+ (required by WorldGuard)
- RegionGuard (alternative to WorldGuard)

## Installation

1. Download the latest `Villages-x.x.x.jar` from releases
2. Place in your server's `plugins` folder
3. Start your server
4. Configure `plugins/Villages/config.yml` as needed

See [docs/INSTALLATION.md](docs/INSTALLATION.md) for detailed setup instructions.

## Commands

### Basic Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/village name <name>` | `villages.name` | Name the nearest village |
| `/village info` | `villages.info` | Show plugin and village info |

### Border Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/village border show [duration]` | `villages.border.show` | Visualize boundary with particles |
| `/village border recalculate` | `villages.border.recalculate` | Force boundary recalculation |

### Entrance Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/village entrance add` | `villages.entrance.add` | Mark current location as entrance |
| `/village entrance remove` | `villages.entrance.remove` | Remove nearest entrance |
| `/village entrance list` | `villages.entrance.list` | List all village entrances |
| `/village entrance detect` | `villages.entrance.detect` | Run automatic entrance detection |

### Region Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/village region create` | `villages.region.create` | Create WorldGuard region |
| `/village region delete` | `villages.region.delete` | Delete village region |
| `/village region flags [flag] [value]` | `villages.region.flags` | View/set region flags |

### Admin Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/village reload` | `villages.admin.reload` | Reload configuration |
| `/village migrate <from> <to>` | `villages.admin.migrate` | Migrate storage backend |
| `/village backup` | `villages.admin.backup` | Create data backup |

### Debug Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/village debug` | `villages.admin.debug` | Show current debug status |
| `/village debug on` | `villages.admin.debug` | Enable debug logging |
| `/village debug off` | `villages.admin.debug` | Disable debug logging |
| `/village debug storage` | `villages.admin.debug` | Toggle storage logging |
| `/village debug regions` | `villages.admin.debug` | Toggle region logging |
| `/village debug boundaries` | `villages.admin.debug` | Toggle boundary logging |
| `/village debug entrances` | `villages.admin.debug` | Toggle entrance logging |

## Configuration

```yaml
# Storage backend
storage:
  type: sqlite  # yaml, sqlite, mysql

# Region protection
regions:
  auto-create: false
  provider: auto  # worldguard, regionguard, auto
  default-flags:
    mob-griefing: deny
    greeting: "&aWelcome to %village_name%!"

# Entrance detection
entrances:
  auto-detect: true
  min-path-width: 2

# Welcome signs
signs:
  auto-place: true
  material: OAK_WALL_SIGN

# Debug logging
debug:
  enabled: false       # Master debug switch
  verbose: false       # Extra detailed output
  log-storage: false   # Storage operations
  log-regions: false   # Region operations
  log-boundaries: false # Boundary calculations
  log-entrances: false  # Entrance detection
```

## Project Structure

```
villages/
├── src/main/java/org/clockworx/villages/
│   ├── VillagesPlugin.java           # Main plugin class
│   ├── boundary/
│   │   └── VillageBoundaryCalculator.java  # NMS POI scanning
│   ├── commands/
│   │   └── VillageCommands.java      # All commands
│   ├── config/
│   │   └── ConfigManager.java        # Typed config access
│   ├── detection/
│   │   ├── EntranceDetector.java     # Auto entrance detection
│   │   └── EntranceMarker.java       # Manual entrance marking
│   ├── model/
│   │   ├── Village.java              # Village data model
│   │   ├── VillageBoundary.java      # Boundary representation
│   │   ├── VillageEntrance.java      # Entrance points
│   │   └── VillagePoi.java           # POI data
│   ├── regions/
│   │   ├── RegionProvider.java       # Region abstraction
│   │   ├── RegionManager.java        # Provider management
│   │   ├── WorldGuardProvider.java   # WorldGuard implementation
│   │   └── RegionGuardProvider.java  # RegionGuard implementation
│   ├── signs/
│   │   └── WelcomeSignPlacer.java    # Welcome sign placement
│   ├── storage/
│   │   ├── StorageProvider.java      # Storage abstraction
│   │   ├── StorageManager.java       # Provider management
│   │   ├── YamlStorageProvider.java  # YAML file storage
│   │   ├── SQLiteStorageProvider.java # SQLite database
│   │   └── MySQLStorageProvider.java  # MySQL database
│   └── util/
│       ├── LogCategory.java          # Log category enum
│       └── PluginLogger.java         # Enhanced logging
└── docs/
    ├── ARCHITECTURE.md               # System architecture
    ├── CHANGELOG.md                  # Version history
    ├── COMMANDS.md                   # Command reference
    ├── INSTALLATION.md               # Setup guide
    └── REGIONS.md                    # Region integration
```

## Building from Source

```bash
git clone https://github.com/radawson/villages.git
cd villages
./gradlew build
```

The compiled JAR will be in `build/libs/Villages-<version>.jar`

## Documentation

- [Installation Guide](docs/INSTALLATION.md)
- [Architecture Overview](docs/ARCHITECTURE.md)
- [Region Integration](docs/REGIONS.md)
- [Command Reference](docs/COMMANDS.md)
- [Changelog](docs/CHANGELOG.md)

## Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Open a Pull Request

## License

MIT License - see [LICENSE](LICENSE) for details.

## Author

**RADawson**
- GitHub: [@radawson](https://github.com/radawson)

## Acknowledgments

- Built for the Paper API
- Uses paperweight-userdev for NMS access
- CommandAPI by Skepter for command handling
- WorldGuard/WorldEdit by EngineHub for region protection

# Logging System

The Villages plugin uses a comprehensive logging system designed to help track the flow of operations and diagnose issues. All logging is performed through the `PluginLogger` class, which provides structured, categorized output with timestamps.

## Overview

The logging system provides:
- **Categorized logging** - Messages are tagged with categories for easy filtering
- **Configurable debug levels** - Enable/disable debug logging per category
- **Timestamped output** - All log messages include timestamps
- **Exception logging** - Full stack traces for error diagnosis
- **Thread-safe operation** - Safe for use in async operations

## Log Levels

The plugin uses standard Java logging levels with specific purposes:

### DEBUG
Detailed flow information, method entry/exit, parameter values. Only appears when debug is enabled for the relevant category.

**Example:**
```
[DEBUG] [14:23:45] [Storage] Loading village 550e8400-e29b-41d4-a716-446655440000 from SQLite storage
[DEBUG] [14:23:45] [Storage] Village 550e8400-e29b-41d4-a716-446655440000 loaded successfully from SQLite storage
```

### INFO
Important operations, successful state changes, operation completion. Always logged regardless of debug settings.

**Example:**
```
[INFO] [14:23:45] [Command] Village 550e8400-e29b-41d4-a716-446655440000 named to 'My Village' by PlayerName
[INFO] [14:23:45] [Storage] YAML storage initialized with 5 villages
```

### WARNING
Non-fatal errors, validation failures, missing data, fallback operations. Always logged.

**Example:**
```
[WARNING] [14:23:45] [Command] Village name command failed: empty name provided by PlayerName
[WARNING] [14:23:45] [Storage] Invalid UUID in council list: invalid-uuid-string
```

### SEVERE
Fatal errors, exceptions, critical system failures. Always logged with full stack traces.

**Example:**
```
[SEVERE] [14:23:45] [Storage] Failed to save villages.yml
java.io.IOException: Permission denied
    at java.io.FileOutputStream.open0(Native Method)
    ...
```

## Log Categories

Log messages are categorized to allow selective filtering. Each category can be independently enabled or disabled.

### GENERAL
General plugin operations, configuration management, sign placement, and other miscellaneous operations.

**Components:**
- `VillageManager` - Village lifecycle operations
- `SignManager` - Sign placement and updates
- `ConfigManager` - Configuration operations
- `WelcomeSignPlacer` - Welcome sign placement
- `VillageChunkListener` - Chunk event processing

**Example:**
```
[DEBUG] [14:23:45] [General] getOrCreateVillage called for bell at world:100,64,200
[INFO] [14:23:45] [General] Created new village 550e8400-e29b-41d4-a716-446655440000 at world:100,64,200
```

### STORAGE
All storage provider operations including saves, loads, queries, migrations, and backups.

**Components:**
- `YamlStorageProvider` - YAML file operations
- `SQLiteStorageProvider` - SQLite database operations
- `MySQLStorageProvider` - MySQL database operations
- `StorageManager` - Storage provider management

**Example:**
```
[DEBUG] [14:23:45] [Storage] Saving village 550e8400-e29b-41d4-a716-446655440000 to SQLite storage
[DEBUG] [14:23:45] [Storage] Village 550e8400-e29b-41d4-a716-446655440000 saved successfully to SQLite storage
[INFO] [14:23:45] [Storage] Created SQLite backup at: /backups/villages_2024-01-15_14-23-45.db
```

### REGION
Region plugin operations including region creation, updates, deletion, and flag management.

**Components:**
- `WorldGuardProvider` - WorldGuard integration
- `RegionGuardProvider` - RegionGuard integration
- `RegionManager` - Region provider management

**Example:**
```
[DEBUG] [14:23:45] [Region] WorldGuard region created: village_myvillage for village 550e8400-e29b-41d4-a716-446655440000
[INFO] [14:23:45] [Region] Created WorldGuard region: village_myvillage
[DEBUG] [14:23:45] [Region] Setting flag 'greeting' to 'Welcome to My Village' for region village_myvillage
```

### BOUNDARY
Boundary calculation operations including POI collection, flood-fill algorithms, and bounding box computation.

**Components:**
- `VillageBoundaryCalculator` - Boundary calculation algorithms

**Example:**
```
[DEBUG] [14:23:45] [Boundary] Calculating boundary for bell at world:100,64,200
[DEBUG] [14:23:45] [Boundary] Collected 15 POIs for village
[DEBUG] [14:23:45] [Boundary] Calculated boundary for village 550e8400-e29b-41d4-a716-446655440000 - Size: 64 x 24 x 64
```

### ENTRANCE
Entrance detection and marking operations including edge scanning, path detection, and manual marking.

**Components:**
- `EntranceDetector` - Automatic entrance detection
- `EntranceMarker` - Manual entrance marking

**Example:**
```
[DEBUG] [14:23:45] [Entrance] detectEntrances called for village 550e8400-e29b-41d4-a716-446655440000
[DEBUG] [14:23:45] [Entrance] Detecting entrances for village 550e8400-e29b-41d4-a716-446655440000 with boundary size: 64 x 24 x 64
[INFO] [14:23:45] [Entrance] Detected 3 entrances for village 550e8400-e29b-41d4-a716-446655440000
```

### COMMAND
All command execution including parameter validation, operation results, and error handling.

**Components:**
- `VillageCommands` - All command handlers

**Example:**
```
[DEBUG] [14:23:45] [Command] Executing /village name command by PlayerName with name: My Village
[DEBUG] [14:23:45] [Command] Found village 550e8400-e29b-41d4-a716-446655440000 for naming by PlayerName
[INFO] [14:23:45] [Command] Village 550e8400-e29b-41d4-a716-446655440000 named to 'My Village' by PlayerName
```

## Configuration

Logging behavior is controlled through the `config.yml` file under the `debug:` section:

```yaml
debug:
  enabled: false        # Master switch for all debug logging
  verbose: false        # Extra detailed logging (very verbose)
  log-storage: false    # Storage operations (STORAGE category)
  log-regions: false    # Region operations (REGION category)
  log-boundaries: false # Boundary calculations (BOUNDARY category)
  log-entrances: false  # Entrance detection (ENTRANCE category)
```

### Enabling Debug Logging

**Via Configuration:**
1. Edit `plugins/Villages/config.yml`
2. Set `debug.enabled: true`
3. Enable specific categories as needed
4. Run `/village reload` or restart the server

**Via Commands:**
- `/village debug on` - Enable all debug logging
- `/village debug storage` - Toggle storage logging
- `/village debug regions` - Toggle region logging
- `/village debug boundaries` - Toggle boundary logging
- `/village debug entrances` - Toggle entrance logging
- `/village debug` - Show current debug status

**Note:** The `COMMAND` and `GENERAL` categories are always enabled when `debug.enabled` is true. They cannot be disabled independently.

## Log Output Format

All log messages follow this format:

```
[LEVEL] [HH:mm:ss] [Category] Message
```

**Examples:**
```
[INFO] [14:23:45] [Storage] YAML storage initialized with 5 villages
[DEBUG] [14:23:46] [Command] Executing /village name command by PlayerName with name: My Village
[WARNING] [14:23:47] [General] Invalid UUID in bell PDC: invalid-uuid
[SEVERE] [14:23:48] [Storage] Failed to save villages.yml
```

## Using the Logging System

### In Code

All components should use `PluginLogger` instead of direct `plugin.getLogger()` calls:

```java
public class MyComponent {
    private final PluginLogger logger;
    
    public MyComponent(VillagesPlugin plugin) {
        this.logger = plugin.getPluginLogger();
    }
    
    public void doSomething() {
        logger.debug(LogCategory.GENERAL, "doSomething called");
        
        try {
            // Operation code
            logger.info(LogCategory.GENERAL, "Operation completed successfully");
        } catch (Exception e) {
            logger.warning(LogCategory.GENERAL, "Operation failed", e);
        }
    }
}
```

### Logging Methods

**Standard Logging:**
- `logger.info(String message)` - Info message
- `logger.info(LogCategory category, String message)` - Info with category
- `logger.warning(String message)` - Warning message
- `logger.warning(LogCategory category, String message)` - Warning with category
- `logger.warning(LogCategory category, String message, Throwable throwable)` - Warning with exception
- `logger.severe(String message)` - Severe error
- `logger.severe(LogCategory category, String message)` - Severe with category
- `logger.severe(LogCategory category, String message, Throwable throwable)` - Severe with exception

**Debug Logging (category-specific):**
- `logger.debug(LogCategory category, String message)` - Debug message (filtered by category)
- `logger.debugStorage(String message)` - Storage debug (shortcut for `LogCategory.STORAGE`)
- `logger.debugRegion(String message)` - Region debug (shortcut for `LogCategory.REGION`)
- `logger.debugBoundary(String message)` - Boundary debug (shortcut for `LogCategory.BOUNDARY`)
- `logger.debugEntrance(String message)` - Entrance debug (shortcut for `LogCategory.ENTRANCE`)
- `logger.debugCommand(String message)` - Command debug (shortcut for `LogCategory.COMMAND`)

**Verbose Logging:**
- `logger.verbose(LogCategory category, String message)` - Very detailed logging (requires `debug.verbose: true`)

## Best Practices

1. **Use appropriate log levels:**
   - DEBUG for flow tracking and parameter values
   - INFO for successful operations and important state changes
   - WARNING for non-fatal errors and validation failures
   - SEVERE for fatal errors and exceptions

2. **Include context in messages:**
   - Village UUIDs
   - Player names
   - Locations (world, x, y, z)
   - Operation types

3. **Use categories consistently:**
   - Choose the most specific category
   - Use GENERAL only when no other category fits

4. **Log exceptions properly:**
   - Use the exception-accepting methods for warnings and severe errors
   - Include context about what operation was being performed

5. **Avoid excessive logging:**
   - Don't log in tight loops
   - Use verbose logging for very detailed algorithm steps
   - Consider performance impact of debug logging

## Troubleshooting

### Enable All Debug Logging

To diagnose issues, enable all debug logging:

```yaml
debug:
  enabled: true
  verbose: true
  log-storage: true
  log-regions: true
  log-boundaries: true
  log-entrances: true
```

### Finding Specific Operations

Use log file search to find specific operations:
- Search for village UUIDs to track a specific village
- Search for player names to track player actions
- Search for category tags to filter by operation type

### Common Log Patterns

**Village Creation:**
```
[DEBUG] [General] getOrCreateVillage called for bell at world:100,64,200
[DEBUG] [General] Creating new village with UUID: 550e8400-e29b-41d4-a716-446655440000
[DEBUG] [Boundary] Calculating boundary for bell at world:100,64,200
[INFO] [General] Created new village 550e8400-e29b-41d4-a716-446655440000 at world:100,64,200
```

**Storage Operations:**
```
[DEBUG] [Storage] Saving village 550e8400-e29b-41d4-a716-446655440000 to SQLite storage
[DEBUG] [Storage] Village 550e8400-e29b-41d4-a716-446655440000 saved successfully to SQLite storage
```

**Command Execution:**
```
[DEBUG] [Command] Executing /village name command by PlayerName with name: My Village
[DEBUG] [Command] Found village 550e8400-e29b-41d4-a716-446655440000 for naming by PlayerName
[INFO] [Command] Village 550e8400-e29b-41d4-a716-446655440000 named to 'My Village' by PlayerName
```

## Version History

- **0.2.2** - Comprehensive logging implementation across all components
- **0.2.1** - Initial logging system with PluginLogger and LogCategory
- **0.2.0** - Basic logging with standard Java logger

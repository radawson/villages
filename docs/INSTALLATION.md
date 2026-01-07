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

### 3. Install Villages

1. Place `Villages-x.x.x.jar` in your server's `plugins` folder
2. Start (or restart) your server
3. The plugin will create default configuration files

### 4. Configure the Plugin

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
```

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

### Performance Issues

1. Switch from YAML to SQLite storage
2. Enable async storage: `performance.async-storage: true`
3. Limit cache size: `performance.cache-size: 500`

## Uninstallation

1. Stop your server
2. Remove `Villages-x.x.x.jar` from plugins
3. (Optional) Remove `plugins/Villages/` folder

Data is preserved in the Villages folder unless manually deleted.

## Getting Help

- **Issues:** Report on GitHub Issues
- **Documentation:** See other docs in this folder
- **Source:** Available on GitHub

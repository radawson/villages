# Villages Plugin

A Paper/Spigot plugin for Minecraft that automatically detects villages and assigns unique identifiers to them.

## Overview

The Villages plugin enhances Minecraft villages by automatically detecting village bells when chunks load and assigning each village a unique UUID. The plugin stores this UUID persistently on the bell block and displays it on signs placed around the bell for easy identification.

## Features

- **Automatic Village Detection**: Detects village bells automatically when chunks are loaded
- **Unique Village Identification**: Assigns a unique UUID to each village bell
- **Persistent Storage**: Uses Paper's Persistent Data Container (PDC) to store UUIDs directly on bell blocks
- **Visual Identification**: Places signs on all four cardinal directions (North, South, East, West) around each bell displaying the village UUID
- **Zero Configuration**: Works out of the box with no configuration required

## Requirements

- **Minecraft Version**: 1.21.11
- **Server Software**: Paper or compatible Spigot fork
- **Java Version**: 21 or higher
- **API Version**: Paper API 1.21.11

## Installation

1. Download the latest release from the [Releases](https://github.com/radawson/villages/releases) page
2. Place the `Villages.jar` file in your server's `plugins` folder
3. Restart your server or use `/reload` (not recommended for production)
4. The plugin will automatically start detecting villages as chunks load

## How It Works

### Village Detection

When a chunk loads, the plugin:
1. Scans all blocks in the chunk for bell blocks (`Material.BELL`)
2. For each bell found, checks if it already has a UUID assigned
3. If no UUID exists, generates a new unique UUID
4. Stores the UUID in the bell block's Persistent Data Container (PDC)
5. Places or updates signs around the bell displaying the UUID

### UUID Storage

The plugin uses Paper's Persistent Data Container (PDC) system to store UUIDs directly on bell blocks. This means:
- UUIDs persist across server restarts
- No external database or configuration files needed
- Data is stored with the world, making it portable

### Sign Placement

Signs are automatically placed on all four cardinal directions around each bell:
- **North**: Sign facing south toward the bell
- **South**: Sign facing north toward the bell
- **East**: Sign facing west toward the bell
- **West**: Sign facing east toward the bell

Each sign displays:
- Line 1: "Village UUID:"
- Lines 2-4: The UUID split across three lines (36 characters total)

Signs will only be placed if the target location is air or a replaceable block (grass, flowers, etc.).

## Building from Source

### Prerequisites

- Java 21 or higher
- Gradle 8.0 or higher

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/radawson/villages.git
cd villages
```

2. Build the plugin:
```bash
./gradlew build
```

3. The compiled JAR will be in `build/libs/Villages-<version>.jar`

### Development Setup

The project uses:
- **Gradle** for build management
- **Paperweight** for development environment setup
- **Shadow** plugin for creating shaded JARs

To set up a development environment:

```bash
./gradlew paperweightDevelopment
```

This will download the necessary Paper server files for testing.

## Project Structure

```
villages/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/
│       │       └── clockworx/
│       │           └── villages/
│       │               ├── VillagesPlugin.java      # Main plugin class
│       │               ├── managers/
│       │               │   ├── VillageManager.java  # UUID generation and PDC storage
│       │               │   └── SignManager.java     # Sign placement logic
│       │               └── listeners/
│       │                   └── VillageChunkListener.java  # Chunk load event handling
│       └── resources/
│           ├── plugin.yml          # Plugin metadata
│           └── paper-plugin.yml     # Paper-specific configuration
├── build.gradle.kts                 # Build configuration
├── gradle.properties                # Version properties
└── settings.gradle.kts              # Gradle settings
```

## API Usage

If you're developing a plugin that needs to interact with Villages:

```java
VillagesPlugin villagesPlugin = (VillagesPlugin) Bukkit.getPluginManager().getPlugin("Villages");
if (villagesPlugin != null) {
    VillageManager villageManager = villagesPlugin.getVillageManager();
    UUID villageUuid = villageManager.getOrCreateVillageUuid(bellBlock);
    // Use the UUID...
}
```

## Performance Considerations

- The plugin scans all blocks in a chunk when it loads (16×16×384 = 98,304 blocks per chunk)
- Chunk load events are relatively infrequent, so this is generally acceptable
- Future optimizations may include using structure data or other detection methods

## Troubleshooting

### Signs Not Appearing

- Ensure the blocks around the bell are air or replaceable (grass, flowers, etc.)
- Check server logs for any errors
- Verify the plugin is enabled with `/plugins`

### UUIDs Not Persisting

- Ensure you're using Paper (not just Spigot), as PDC is a Paper feature
- Check that the bell block's chunk is loaded when assigning UUIDs
- Verify server has write permissions to the world folder

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

**RADawson**

- GitHub: [@radawson](https://github.com/radawson)
- Website: https://github.com/radawson/villages

## Acknowledgments

- Built for the Paper/Spigot API
- Uses Paper's Persistent Data Container for data storage
- Designed for Minecraft 1.21.11

## Version History

- **0.1.0** - Initial release
  - Automatic village detection on chunk load
  - UUID assignment and PDC storage
  - Automatic sign placement around bells

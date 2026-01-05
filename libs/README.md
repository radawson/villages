# Local Dependencies

This directory contains local JAR file dependencies that aren't yet available in Maven repositories.

## CommandAPI 11.1.0

- **File**: `commandapi-bukkit-shade-11.1.0.jar`
- **Source**: [Hangar - CommandAPI 11.1.0](https://hangar.papermc.io/Skepter/CommandAPI/versions/11.1.0)
- **Download URL**: https://hangarcdn.papermc.io/plugins/Skepter/CommandAPI/versions/11.1.0/PAPER/CommandAPI-11.1.0-Paper.jar
- **Reason**: Version 11.1.0 supports Minecraft 1.21.11 but isn't published to Maven repositories yet

Once CommandAPI 11.1.0 is available in Maven repositories, this local file dependency can be replaced with a standard Maven dependency in `build.gradle.kts`.

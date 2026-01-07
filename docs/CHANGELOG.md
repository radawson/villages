# Changelog

All notable changes to the Villages plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

## [1.0.0] - Initial Release

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

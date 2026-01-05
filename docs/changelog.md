# Changelog

All notable changes to the Villages plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Village naming feature via `/village name <name>` command
- CommandAPI integration for command handling
- Persistent Data Container (PDC) storage for village names
- Sign display now shows village names instead of UUIDs when a name is set
- Permission system: `villages.name` (default: OP only)
- Proximity-based village detection: command finds nearest bell in player's chunk
- Documentation: `docs/fongi.md` - Command system guide for adding new commands
- Documentation: `docs/changelog.md` - This file

### Changed
- SignManager now accepts optional village name parameter
- Signs display "Village: [name]" when named, or "Village UUID: [uuid]" when unnamed
- VillageChunkListener now retrieves and passes village names when placing signs

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

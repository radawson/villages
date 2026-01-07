# Village Icon for BlueMap

This directory would contain the village icon asset, but since BlueMap expects icons to be in its own assets directory, you need to place the icon there instead.

## Icon Requirements

- **Format**: PNG
- **Size**: 32x32 or 64x64 pixels (recommended: 64x64)
- **Location**: Place the icon in BlueMap's assets directory:
  - `plugins/BlueMap/web/assets/village-icon.png`
  
  Or update the `bluemap.icon` setting in `config.yml` to point to your custom icon path.

## Default Configuration

The default icon path is `assets/village-icon.png`, which BlueMap will look for in its web assets directory.

## Icon Suggestions

You can use any icon that represents a village, such as:
- A bell icon (represents the village bell)
- A house icon
- A village/town icon
- A custom village symbol

Make sure the icon has a transparent background for best appearance on the map.

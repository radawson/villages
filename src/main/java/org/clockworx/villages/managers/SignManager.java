package org.clockworx.villages.managers;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.signs.BellAttachmentSignPlacementStrategy;
import org.clockworx.villages.signs.BiomeSignPlacementStrategy;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages sign placement around village bells.
 *
 * Uses a single bell-attachment-based strategy: signs are placed above/below
 * wall-mounted bells when the support has solid above/below, or on the facets
 * of the support block for ceiling/floor bells (or as fallback for walls).
 *
 * Key concepts:
 * - BlockFace: Represents directions (NORTH, SOUTH, EAST, WEST)
 * - Sign blocks: Special blocks that can display text on multiple lines
 * - BlockState: Used to modify block data (like sign text) before applying changes
 */
public class SignManager {

    private final VillagesPlugin plugin;
    private final PluginLogger logger;

    private final BiomeSignPlacementStrategy signPlacementStrategy = new BellAttachmentSignPlacementStrategy();

    /**
     * Creates a new SignManager.
     *
     * @param plugin The plugin instance
     */
    public SignManager(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
    }
    
    /**
     * Places or updates signs on all four sides of a bell block with the village UUID.
     * 
     * This method:
     * 1. Gets the bell's location
     * 2. For each cardinal direction, tries to place a sign one block down (at the base)
     * 3. If the block below is not replaceable, falls back to the same level as the bell
     * 4. Checks if the target block is air or a replaceable block (like grass, flowers, etc.)
     * 5. Places a wall sign facing the bell
     * 6. Sets the sign text to display the UUID
     * 
     * Placing signs at the base (Y-1) makes the bell easier to ring and looks more aesthetic
     * than placing them at the same level as the bell itself.
     * 
     * @param bellBlock The bell block to place signs around
     * @param villageUuid The UUID to display on the signs
     */
    public void placeSignsAroundBell(Block bellBlock, UUID villageUuid) {
        placeSignsAroundBell(bellBlock, villageUuid, null);
    }
    
    /**
     * Places or updates signs on all four sides of a bell block with the village name or UUID.
     * 
     * This method uses bell-attachment-based placement (wall/floor/ceiling) so signs
     * are placed above/below the bell or on the support block's facets.
     * 
     * @param bellBlock The bell block to place signs around
     * @param villageUuid The UUID to display on the signs (used as fallback if name is null)
     * @param villageName The name to display on the signs (null to display UUID instead)
     */
    public void placeSignsAroundBell(Block bellBlock, UUID villageUuid, String villageName) {
        logger.debug(LogCategory.GENERAL, "placeSignsAroundBell called for village " + villageUuid + 
            " at bell " + bellBlock.getLocation() + " with name: " + villageName);
        
        // Calculate sign positions from bell attachment (wall/floor/ceiling)
        List<BiomeSignPlacementStrategy.SignPosition> positions = signPlacementStrategy.calculateSignPositions(bellBlock);

        // Place or update signs at calculated positions
        for (BiomeSignPlacementStrategy.SignPosition position : positions) {
            Block signBlock = position.getBlock();
            BlockFace facing = position.getFacing();

            // Remove duplicate signs in the area before placing/updating
            removeDuplicateSigns(signBlock, villageUuid, villageName);

            // Check if a sign already exists at this location
            if (isSign(signBlock)) {
                // Update the existing sign
                updateSign(signBlock, facing, villageUuid, villageName);
                logger.debug(LogCategory.GENERAL, "Updated existing sign at " + signBlock.getLocation() +
                    " facing " + facing + " with " +
                    (villageName != null ? "name: " + villageName : "UUID: " + villageUuid));
            } else if (signPlacementStrategy.canPlaceSign(signBlock)) {
                // Place a new wall sign
                placeWallSign(signBlock, facing, villageUuid, villageName);
                logger.debug(LogCategory.GENERAL, "Placed sign at " + signBlock.getLocation() +
                    " facing " + facing + " with " +
                    (villageName != null ? "name: " + villageName : "UUID: " + villageUuid));
            } else {
                logger.debug(LogCategory.GENERAL, "Cannot place sign at " + signBlock.getLocation() + 
                    " - block is not replaceable");
            }
        }
    }
    
    /**
     * Checks if a block is a sign (wall sign or standing sign).
     * 
     * @param block The block to check
     * @return true if the block is a sign
     */
    private boolean isSign(Block block) {
        Material type = block.getType();
        return type.name().endsWith("_WALL_SIGN") || type.name().endsWith("_SIGN");
    }
    
    /**
     * Updates an existing sign with new text.
     * 
     * @param block The sign block to update
     * @param facing The direction the sign should face (away from the bell)
     * @param villageUuid The UUID to display (used if villageName is null)
     * @param villageName The name to display (null to display UUID instead)
     */
    private void updateSign(Block block, BlockFace facing, UUID villageUuid, String villageName) {
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            logger.warning(LogCategory.GENERAL, "Failed to get Sign state for block at " + block.getLocation());
            return;
        }
        
        // Update the facing direction if needed (for wall signs)
        org.bukkit.block.data.BlockData blockData = block.getBlockData();
        if (blockData instanceof org.bukkit.block.data.type.WallSign wallSignData) {
            wallSignData.setFacing(facing);
            block.setBlockData(wallSignData);
            // Re-get the state after updating block data
            state = block.getState();
            if (!(state instanceof Sign updatedSign)) {
                logger.warning(LogCategory.GENERAL, "Failed to get Sign state after updating block data at " + block.getLocation());
                return;
            }
            sign = updatedSign;
        }
        
        // Get the front side of the sign (the side that faces the bell)
        SignSide signSide = sign.getSide(Side.FRONT);
        
        // Set the text on the sign using Kyori Adventure components
        if (villageName != null && !villageName.trim().isEmpty()) {
            // Display the village name
            signSide.line(0, Component.text("Village:"));
            // Split long names across multiple lines if needed
            String[] nameParts = splitNameForSign(villageName);
            signSide.line(1, Component.text(nameParts[0]));
            signSide.line(2, Component.text(nameParts.length > 1 ? nameParts[1] : ""));
            signSide.line(3, Component.text(nameParts.length > 2 ? nameParts[2] : ""));
        } else {
            // Display the UUID as fallback
            String uuidString = villageUuid.toString();
            String[] uuidParts = splitUuidForSign(uuidString);
            signSide.line(0, Component.text("Village UUID:"));
            signSide.line(1, Component.text(uuidParts[0])); // First part of UUID
            signSide.line(2, Component.text(uuidParts[1])); // Second part of UUID
            signSide.line(3, Component.text(uuidParts[2])); // Third part of UUID
        }
        
        // Apply the changes
        sign.update();
    }
    
    /**
     * Checks if a block can be replaced with a sign.
     * 
     * @param block The block to check
     * @return true if the block can be replaced (air or replaceable materials)
     * @deprecated Use strategy.canPlaceSign() instead
     */
    @Deprecated
    private boolean canPlaceSign(Block block) {
        Material type = block.getType();
        // Air and various replaceable blocks (grass, flowers, etc.) can be replaced
        // Using isAir() as the primary check, plus common replaceable vegetation blocks
        if (type.isAir()) {
            return true;
        }
        
        // Check for common replaceable vegetation blocks
        // Note: Material enum names may vary by version, so we check multiple possibilities
        return type == Material.TALL_GRASS ||
               type == Material.FERN || 
               type == Material.LARGE_FERN ||
               type == Material.DANDELION || 
               type == Material.POPPY ||
               type == Material.SNOW || 
               type == Material.VINE ||
               // Try alternative grass material names that may exist in different versions
               type.name().equals("SHORT_GRASS") ||
               type.name().equals("GRASS");
    }
    
    /**
     * Places a wall sign at the given location facing the specified direction.
     * 
     * @param block The block where the sign should be placed
     * @param facing The direction the sign should face (away from the bell)
     * @param villageUuid The UUID to display (used if villageName is null)
     * @param villageName The name to display (null to display UUID instead)
     */
    private void placeWallSign(Block block, BlockFace facing, UUID villageUuid, String villageName) {
        // Set the block type to a wall sign
        // We need to use the appropriate sign material based on the block's material
        Material signMaterial = getSignMaterial(block);
        block.setType(signMaterial);
        
        // Set the sign to face the correct direction
        // For wall signs, we use the block data to set the facing direction
        org.bukkit.block.data.BlockData blockData = block.getBlockData();
        if (blockData instanceof org.bukkit.block.data.type.WallSign wallSignData) {
            wallSignData.setFacing(facing);
            block.setBlockData(wallSignData);
        }
        
        // Get the block state and cast it to Sign AFTER setting the block data
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            logger.warning(LogCategory.GENERAL, "Failed to get Sign state for block at " + block.getLocation());
            return;
        }
        
        // Get the front side of the sign (the side that faces the bell)
        SignSide signSide = sign.getSide(Side.FRONT);
        
        // Set the text on the sign using Kyori Adventure components
        // This is the modern, non-deprecated way to set sign text
        if (villageName != null && !villageName.trim().isEmpty()) {
            // Display the village name
            signSide.line(0, Component.text("Village:"));
            // Split long names across multiple lines if needed
            String[] nameParts = splitNameForSign(villageName);
            signSide.line(1, Component.text(nameParts[0]));
            signSide.line(2, Component.text(nameParts.length > 1 ? nameParts[1] : ""));
            signSide.line(3, Component.text(nameParts.length > 2 ? nameParts[2] : ""));
        } else {
            // Display the UUID as fallback
            String uuidString = villageUuid.toString();
            String[] uuidParts = splitUuidForSign(uuidString);
            signSide.line(0, Component.text("Village UUID:"));
            signSide.line(1, Component.text(uuidParts[0])); // First part of UUID
            signSide.line(2, Component.text(uuidParts[1])); // Second part of UUID
            signSide.line(3, Component.text(uuidParts[2])); // Third part of UUID
        }
        
        // Apply the changes
        sign.update();
    }
    
    /**
     * Splits a village name into parts that fit on sign lines.
     * Sign lines can hold 15 characters, so we'll split long names appropriately.
     * 
     * @param name The village name to split
     * @return Array of strings for the sign lines (up to 3 lines)
     */
    private String[] splitNameForSign(String name) {
        String trimmed = name.trim();
        // Sign lines can hold 15 characters
        int maxLineLength = 15;
        String[] parts = new String[3];
        
        if (trimmed.length() <= maxLineLength) {
            // Name fits on one line
            parts[0] = trimmed;
            parts[1] = "";
            parts[2] = "";
        } else if (trimmed.length() <= maxLineLength * 2) {
            // Name fits on two lines
            parts[0] = trimmed.substring(0, maxLineLength);
            parts[1] = trimmed.substring(maxLineLength);
            parts[2] = "";
        } else {
            // Name needs three lines (truncate if longer)
            parts[0] = trimmed.substring(0, maxLineLength);
            parts[1] = trimmed.substring(maxLineLength, Math.min(maxLineLength * 2, trimmed.length()));
            if (trimmed.length() > maxLineLength * 2) {
                parts[2] = trimmed.substring(maxLineLength * 2, Math.min(maxLineLength * 3, trimmed.length()));
            } else {
                parts[2] = "";
            }
        }
        
        return parts;
    }
    
    /**
     * Determines the appropriate sign material based on the block's material.
     * 
     * @param block The block where the sign will be placed
     * @return The appropriate sign material
     */
    private Material getSignMaterial(Block block) {
        // For now, we'll use oak wall sign as default
        // In the future, we could match the sign material to nearby blocks
        return Material.OAK_WALL_SIGN;
    }
    
    /**
     * Splits a UUID string into parts that fit on sign lines.
     * 
     * UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     * We'll split it into three parts:
     * - Part 1: First 13 characters (xxxxxxxx-xxxx)
     * - Part 2: Next 13 characters (-xxxx-xxxx-)
     * - Part 3: Last 10 characters (xxxxxxxxxxxx)
     * 
     * @param uuidString The UUID string to split
     * @return Array of three strings for the sign lines
     */
    private String[] splitUuidForSign(String uuidString) {
        // Sign lines can hold 15 characters, so we'll split the 36-character UUID
        // into three parts of roughly equal length
        String[] parts = new String[3];
        
        if (uuidString.length() >= 36) {
            parts[0] = uuidString.substring(0, 13);  // xxxxxxxx-xxxx
            parts[1] = uuidString.substring(13, 26);  // -xxxx-xxxx-xx
            parts[2] = uuidString.substring(26);      // xxxxxxxxxxxx
        } else {
            // Fallback if UUID format is unexpected
            parts[0] = uuidString.length() > 0 ? uuidString.substring(0, Math.min(15, uuidString.length())) : "";
            parts[1] = uuidString.length() > 15 ? uuidString.substring(15, Math.min(30, uuidString.length())) : "";
            parts[2] = uuidString.length() > 30 ? uuidString.substring(30) : "";
        }
        
        return parts;
    }
    
    /**
     * Finds all existing signs within a radius of the center block.
     * 
     * @param center The center block to search around
     * @param radius The radius to search (default: 3 blocks)
     * @return List of sign blocks found within the radius
     */
    private List<Block> findExistingSignsInRadius(Block center, int radius) {
        List<Block> signs = new ArrayList<>();
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();
        
        // Search all blocks within radius
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Skip the center block itself (we'll handle it separately)
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    
                    Block block = center.getWorld().getBlockAt(centerX + dx, centerY + dy, centerZ + dz);
                    if (isSign(block)) {
                        signs.add(block);
                    }
                }
            }
        }
        
        return signs;
    }
    
    /**
     * Checks if a sign belongs to the current village by reading its content.
     * 
     * @param signBlock The sign block to check
     * @param villageUuid The village UUID to match
     * @param villageName The village name to match (may be null)
     * @return true if the sign belongs to this village
     */
    private boolean isVillageSign(Block signBlock, UUID villageUuid, String villageName) {
        BlockState state = signBlock.getState();
        if (!(state instanceof Sign sign)) {
            return false;
        }
        
        SignSide signSide = sign.getSide(Side.FRONT);
        
        // Check first line for "Village:" or "Village UUID:"
        String firstLine = getSignLineText(signSide, 0);
        if (firstLine == null || (!firstLine.contains("Village") && !firstLine.contains("UUID"))) {
            return false;
        }
        
        // If we have a village name, check if it matches
        if (villageName != null && !villageName.trim().isEmpty()) {
            // Check if any line contains the village name
            for (int i = 1; i < 4; i++) {
                String line = getSignLineText(signSide, i);
                if (line != null && line.contains(villageName)) {
                    return true;
                }
            }
        } else {
            // Check if any line contains the UUID
            String uuidString = villageUuid.toString();
            for (int i = 1; i < 4; i++) {
                String line = getSignLineText(signSide, i);
                if (line != null && line.contains(uuidString.substring(0, Math.min(13, uuidString.length())))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Gets the text content from a sign line, handling Kyori Adventure components.
     * 
     * @param signSide The sign side to read from
     * @param lineIndex The line index (0-3)
     * @return The text content, or null if empty
     */
    private String getSignLineText(SignSide signSide, int lineIndex) {
        Component component = signSide.line(lineIndex);
        if (component == null) {
            return null;
        }
        
        // Convert component to plain text
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
        return text.trim().isEmpty() ? null : text.trim();
    }
    
    /**
     * Removes duplicate signs that don't belong to the current village.
     * Searches within a radius and removes signs that don't match.
     * 
     * @param calculatedPosition The calculated position where a sign should be
     * @param villageUuid The current village UUID
     * @param villageName The current village name (may be null)
     */
    private void removeDuplicateSigns(Block calculatedPosition, UUID villageUuid, String villageName) {
        List<Block> nearbySigns = findExistingSignsInRadius(calculatedPosition, 3);
        
        for (Block signBlock : nearbySigns) {
            // Skip if this is the exact calculated position (we'll handle it separately)
            if (signBlock.getLocation().equals(calculatedPosition.getLocation())) {
                continue;
            }
            
            // Check if this sign belongs to our village
            if (!isVillageSign(signBlock, villageUuid, villageName)) {
                // This sign doesn't belong to our village - remove it
                logger.debug(LogCategory.GENERAL, "Removing duplicate sign at " + signBlock.getLocation() + 
                    " that doesn't belong to village " + villageUuid);
                signBlock.setType(Material.AIR);
            }
        }
    }
}

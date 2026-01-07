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
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.util.UUID;

/**
 * Manages sign placement around village bells.
 * 
 * This class handles placing signs on all four cardinal directions (North, South, East, West)
 * around a bell block, displaying the village UUID.
 * 
 * Key concepts:
 * - BlockFace: Represents directions (NORTH, SOUTH, EAST, WEST)
 * - Sign blocks: Special blocks that can display text on multiple lines
 * - BlockState: Used to modify block data (like sign text) before applying changes
 */
public class SignManager {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    
    // The four cardinal directions where we'll place signs
    private static final BlockFace[] CARDINAL_DIRECTIONS = {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
    };
    
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
     * This method:
     * 1. Gets the bell's location
     * 2. For each cardinal direction, tries to place a sign two blocks away (at the base)
     * 3. If the block below is not replaceable, falls back to the same level as the bell
     * 4. Checks if a sign already exists at the target location and updates it if so
     * 5. Otherwise checks if the target block is air or a replaceable block (like grass, flowers, etc.)
     * 6. Places a wall sign facing the bell
     * 7. Sets the sign text to display the name (if provided) or UUID (if name is null)
     * 
     * Placing signs two blocks away prevents them from blocking bell access.
     * 
     * @param bellBlock The bell block to place signs around
     * @param villageUuid The UUID to display on the signs (used as fallback if name is null)
     * @param villageName The name to display on the signs (null to display UUID instead)
     */
    public void placeSignsAroundBell(Block bellBlock, UUID villageUuid, String villageName) {
        logger.debug(LogCategory.GENERAL, "placeSignsAroundBell called for village " + villageUuid + 
            " at bell " + bellBlock.getLocation() + " with name: " + villageName);
        
        for (BlockFace direction : CARDINAL_DIRECTIONS) {
            // First, try to place the sign two blocks away horizontally, one block down (at the base)
            // This prevents signs from blocking bell access
            Block signBlock = bellBlock.getRelative(direction).getRelative(direction).getRelative(BlockFace.DOWN);
            
            // Check if a sign already exists at this location
            if (isSign(signBlock)) {
                // Update the existing sign instead of placing a new one
                updateSign(signBlock, direction, villageUuid, villageName);
                
                logger.debug(LogCategory.GENERAL, "Updated existing sign at base level " + signBlock.getLocation() + 
                    " facing " + direction + " with " + 
                    (villageName != null ? "name: " + villageName : "UUID: " + villageUuid));
            } else if (canPlaceSign(signBlock)) {
                // Place a wall sign facing away from the bell (in the same direction as placement)
                placeWallSign(signBlock, direction, villageUuid, villageName);
                
                logger.debug(LogCategory.GENERAL, "Placed sign at base level " + signBlock.getLocation() + 
                    " facing " + direction + " (away from bell) with " + 
                    (villageName != null ? "name: " + villageName : "UUID: " + villageUuid));
            } else {
                // Fall back to the same level as the bell if the base block isn't replaceable
                Block fallbackBlock = bellBlock.getRelative(direction).getRelative(direction);
                
                // Check if a sign already exists at the fallback location
                if (isSign(fallbackBlock)) {
                    // Update the existing sign instead of placing a new one
                    updateSign(fallbackBlock, direction, villageUuid, villageName);
                    
                    logger.debug(LogCategory.GENERAL, "Updated existing sign at bell level " + fallbackBlock.getLocation() + 
                        " facing " + direction + " with " + 
                        (villageName != null ? "name: " + villageName : "UUID: " + villageUuid));
                } else if (canPlaceSign(fallbackBlock)) {
                    // Place a wall sign facing away from the bell (in the same direction as placement)
                    placeWallSign(fallbackBlock, direction, villageUuid, villageName);
                    
                    logger.debug(LogCategory.GENERAL, "Placed sign at bell level " + fallbackBlock.getLocation() + 
                        " facing " + direction + " (away from bell) with " + 
                        (villageName != null ? "name: " + villageName : "UUID: " + villageUuid));
                } else {
                    logger.debug(LogCategory.GENERAL, "Cannot place sign at " + signBlock.getLocation() + 
                        " or " + fallbackBlock.getLocation() + " - blocks are not replaceable");
                }
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
     */
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
}

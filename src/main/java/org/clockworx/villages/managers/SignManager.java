package org.clockworx.villages.managers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.clockworx.villages.VillagesPlugin;

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
        Location bellLocation = bellBlock.getLocation();
        String uuidString = villageUuid.toString();
        
        // Split UUID into parts for display across sign lines
        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 characters)
        // We'll put "Village UUID:" on line 1, then split the UUID across lines 2-4
        String[] uuidParts = splitUuidForSign(uuidString);
        
        for (BlockFace direction : CARDINAL_DIRECTIONS) {
            // First, try to place the sign one block down (at the base of the bell)
            // This makes ringing the bell easier and looks better
            Block signBlock = bellBlock.getRelative(direction).getRelative(BlockFace.DOWN);
            
            // Check if we can place a sign here (air or replaceable blocks)
            if (canPlaceSign(signBlock)) {
                // Place a wall sign facing the opposite direction (toward the bell)
                BlockFace oppositeDirection = direction.getOppositeFace();
                placeWallSign(signBlock, oppositeDirection, uuidParts);
                
                plugin.getLogger().fine("Placed sign at base level " + signBlock.getLocation() + 
                    " facing " + oppositeDirection + " with UUID: " + villageUuid);
            } else {
                // Fall back to the same level as the bell if the base block isn't replaceable
                Block fallbackBlock = bellBlock.getRelative(direction);
                
                if (canPlaceSign(fallbackBlock)) {
                    BlockFace oppositeDirection = direction.getOppositeFace();
                    placeWallSign(fallbackBlock, oppositeDirection, uuidParts);
                    
                    plugin.getLogger().fine("Placed sign at bell level " + fallbackBlock.getLocation() + 
                        " facing " + oppositeDirection + " with UUID: " + villageUuid);
                } else {
                    plugin.getLogger().fine("Cannot place sign at " + signBlock.getLocation() + 
                        " or " + fallbackBlock.getLocation() + " - blocks are not replaceable");
                }
            }
        }
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
        return type.isAir() || type == Material.GRASS || type == Material.TALL_GRASS ||
               type == Material.FERN || type == Material.LARGE_FERN ||
               type == Material.DANDELION || type == Material.POPPY ||
               type == Material.SNOW || type == Material.VINE;
    }
    
    /**
     * Places a wall sign at the given location facing the specified direction.
     * 
     * @param block The block where the sign should be placed
     * @param facing The direction the sign should face (toward the bell)
     * @param uuidParts The UUID text split into parts for the sign lines
     */
    private void placeWallSign(Block block, BlockFace facing, String[] uuidParts) {
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
            plugin.getLogger().warning("Failed to get Sign state for block at " + block.getLocation());
            return;
        }
        
        // Get the front side of the sign (the side that faces the bell)
        SignSide signSide = sign.getSide(Side.FRONT);
        
        // Set the text on the sign
        signSide.setLine(0, "Village UUID:");
        signSide.setLine(1, uuidParts[0]); // First part of UUID
        signSide.setLine(2, uuidParts[1]); // Second part of UUID
        signSide.setLine(3, uuidParts[2]); // Third part of UUID
        
        // Apply the changes
        sign.update();
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

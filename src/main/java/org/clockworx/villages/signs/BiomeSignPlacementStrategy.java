package org.clockworx.villages.signs;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Strategy interface for biome-specific sign placement around village bells.
 * 
 * Different biomes have different bell structures (wells, posts, buildings),
 * so sign placement needs to be adjusted accordingly for aesthetic appeal.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public interface BiomeSignPlacementStrategy {
    
    /**
     * Calculates the optimal sign positions for a bell in this biome.
     * 
     * @param bellBlock The bell block
     * @return List of sign positions, each containing the block location and facing direction
     */
    List<SignPosition> calculateSignPositions(Block bellBlock);
    
    /**
     * Gets the horizontal offset from the bell for sign placement.
     * 
     * @param bellBlock The bell block
     * @return Number of blocks away from bell horizontally (default: 2)
     */
    default int getHorizontalOffset(Block bellBlock) {
        return 2;
    }
    
    /**
     * Gets the vertical offset from the bell for sign placement.
     * 
     * @param bellBlock The bell block
     * @return Number of blocks down from bell (negative = down, positive = up, default: -1)
     */
    default int getVerticalOffset(Block bellBlock) {
        return -1; // One block down
    }
    
    /**
     * Checks if a sign can be placed at the given position.
     * 
     * @param block The block to check
     * @return true if a sign can be placed here
     */
    default boolean canPlaceSign(Block block) {
        if (block.getType().isAir()) {
            return true;
        }
        
        // Check for common replaceable vegetation blocks
        return block.getType() == org.bukkit.Material.TALL_GRASS ||
               block.getType() == org.bukkit.Material.FERN || 
               block.getType() == org.bukkit.Material.LARGE_FERN ||
               block.getType() == org.bukkit.Material.DANDELION || 
               block.getType() == org.bukkit.Material.POPPY ||
               block.getType() == org.bukkit.Material.SNOW || 
               block.getType() == org.bukkit.Material.VINE ||
               block.getType().name().equals("SHORT_GRASS") ||
               block.getType().name().equals("GRASS");
    }
    
    /**
     * Represents a sign position with location and facing direction.
     */
    class SignPosition {
        private final Block block;
        private final BlockFace facing;
        
        public SignPosition(Block block, BlockFace facing) {
            this.block = block;
            this.facing = facing;
        }
        
        public Block getBlock() {
            return block;
        }
        
        public BlockFace getFacing() {
            return facing;
        }
    }
}

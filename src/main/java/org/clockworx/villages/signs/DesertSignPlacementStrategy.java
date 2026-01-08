package org.clockworx.villages.signs;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign placement strategy for Desert biome villages.
 * 
 * Desert villages have bells in sandstone structures, often in wells.
 * Signs are placed slightly further out to avoid sandstone structures.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class DesertSignPlacementStrategy implements BiomeSignPlacementStrategy {
    
    private static final BlockFace[] CARDINAL_DIRECTIONS = {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
    };
    
    @Override
    public List<SignPosition> calculateSignPositions(Block bellBlock) {
        List<SignPosition> positions = new ArrayList<>();
        
        // Check if bell is in a well (sandstone blocks below)
        boolean inWell = isInWell(bellBlock);
        
        for (BlockFace direction : CARDINAL_DIRECTIONS) {
            Block signBlock;
            
            if (inWell) {
                // For wells, place signs at ground level, 3 blocks away
                signBlock = bellBlock.getRelative(direction)
                                    .getRelative(direction)
                                    .getRelative(direction);
                
                // Try to find ground level
                while (signBlock.getY() > bellBlock.getWorld().getMinHeight() && 
                       (signBlock.getType().isAir() || signBlock.getType() == Material.SAND ||
                        signBlock.getType() == Material.SANDSTONE)) {
                    signBlock = signBlock.getRelative(BlockFace.DOWN);
                }
                signBlock = signBlock.getRelative(BlockFace.UP);
            } else {
                // For other structures, use standard placement
                signBlock = bellBlock.getRelative(direction)
                                    .getRelative(direction)
                                    .getRelative(BlockFace.DOWN);
            }
            
            if (canPlaceSign(signBlock)) {
                positions.add(new SignPosition(signBlock, direction));
            } else {
                // Fall back to same level as bell
                Block fallbackBlock = bellBlock.getRelative(direction).getRelative(direction);
                if (canPlaceSign(fallbackBlock)) {
                    positions.add(new SignPosition(fallbackBlock, direction));
                }
            }
        }
        
        return positions;
    }
    
    /**
     * Checks if the bell is in a well structure (sandstone blocks below).
     */
    private boolean isInWell(Block bellBlock) {
        Block below = bellBlock.getRelative(BlockFace.DOWN);
        return below.getType() == Material.SANDSTONE || 
               below.getType() == Material.CUT_SANDSTONE ||
               below.getType() == Material.SMOOTH_SANDSTONE;
    }
}

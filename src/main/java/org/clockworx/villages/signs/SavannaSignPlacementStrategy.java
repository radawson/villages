package org.clockworx.villages.signs;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign placement strategy for Savanna biome villages.
 * 
 * Savanna villages use acacia wood structures.
 * Signs are placed at ground level, accounting for acacia log structures.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class SavannaSignPlacementStrategy implements BiomeSignPlacementStrategy {
    
    private static final BlockFace[] CARDINAL_DIRECTIONS = {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
    };
    
    @Override
    public List<SignPosition> calculateSignPositions(Block bellBlock) {
        List<SignPosition> positions = new ArrayList<>();
        
        for (BlockFace direction : CARDINAL_DIRECTIONS) {
            // Place signs 2 blocks away, at ground level
            Block signBlock = bellBlock.getRelative(direction)
                                      .getRelative(direction);
            
            // Find ground level (non-air block)
            while (signBlock.getY() > bellBlock.getWorld().getMinHeight() && 
                   signBlock.getType().isAir()) {
                signBlock = signBlock.getRelative(BlockFace.DOWN);
            }
            
            // Place sign one block above ground
            signBlock = signBlock.getRelative(BlockFace.UP);
            
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
}

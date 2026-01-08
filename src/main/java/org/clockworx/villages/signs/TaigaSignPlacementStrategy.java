package org.clockworx.villages.signs;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign placement strategy for Taiga biome villages.
 * 
 * Taiga villages use spruce wood structures.
 * Signs are placed accounting for spruce log posts and building structures.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class TaigaSignPlacementStrategy implements BiomeSignPlacementStrategy {
    
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
            // Place signs 2 blocks away, 1 block down (standard placement)
            Block signBlock = bellBlock.getRelative(direction)
                                      .getRelative(direction)
                                      .getRelative(BlockFace.DOWN);
            
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

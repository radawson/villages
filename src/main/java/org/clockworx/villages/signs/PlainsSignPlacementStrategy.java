package org.clockworx.villages.signs;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign placement strategy for Plains biome villages.
 * 
 * Plains villages typically have bells in wells or on posts.
 * Uses default placement: 2 blocks away horizontally, 1 block down.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class PlainsSignPlacementStrategy implements BiomeSignPlacementStrategy {
    
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
            // Try to place sign 2 blocks away horizontally, 1 block down (at the base)
            Block signBlock = bellBlock.getRelative(direction)
                                      .getRelative(direction)
                                      .getRelative(BlockFace.DOWN);
            
            if (canPlaceSign(signBlock)) {
                positions.add(new SignPosition(signBlock, direction));
            } else {
                // Fall back to same level as bell if base block isn't replaceable
                Block fallbackBlock = bellBlock.getRelative(direction).getRelative(direction);
                if (canPlaceSign(fallbackBlock)) {
                    positions.add(new SignPosition(fallbackBlock, direction));
                }
            }
        }
        
        return positions;
    }
}

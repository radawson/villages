package org.clockworx.villages.signs;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign placement strategy for Snowy Plains biome villages.
 * 
 * Snowy villages have snow-covered structures.
 * Signs are placed at ground level, accounting for snow layers.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class SnowyPlainsSignPlacementStrategy implements BiomeSignPlacementStrategy {
    
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
            // Place signs 2 blocks away, find top of snow/ground
            Block signBlock = bellBlock.getRelative(direction)
                                      .getRelative(direction);
            
            // Find the top solid block (accounting for snow layers)
            while (signBlock.getY() > bellBlock.getWorld().getMinHeight() && 
                   (signBlock.getType().isAir() || 
                    signBlock.getType() == Material.SNOW ||
                    signBlock.getType() == Material.SNOW_BLOCK)) {
                signBlock = signBlock.getRelative(BlockFace.DOWN);
            }
            
            // Place sign on top of solid block
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

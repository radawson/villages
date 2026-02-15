package org.clockworx.villages.signs;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bell;

import java.util.ArrayList;
import java.util.List;

/**
 * Sign placement strategy driven by bell attachment (wall, floor, ceiling).
 * Places signs above/below wall-mounted bells when the support has solid above/below,
 * or on the facets of the support block for ceiling/floor bells or as fallback for walls.
 *
 * @author Clockworx
 * @since 0.2.4
 */
public class BellAttachmentSignPlacementStrategy implements BiomeSignPlacementStrategy {

    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
    };

    @Override
    public List<SignPosition> calculateSignPositions(Block bellBlock) {
        List<SignPosition> positions = new ArrayList<>();

        if (!(bellBlock.getBlockData() instanceof Bell bellData)) {
            return positions;
        }

        switch (bellData.getAttachment()) {
            case SINGLE_WALL, DOUBLE_WALL -> addWallSignPositions(bellBlock, bellData, positions);
            case CEILING -> addFacetSignPositions(bellBlock.getRelative(BlockFace.UP), positions);
            case FLOOR -> addFacetSignPositions(bellBlock.getRelative(BlockFace.DOWN), positions);
            default -> { }
        }

        return positions;
    }

    /**
     * Wall-mounted bell: try above/below the bell on the same pillar; fallback to facets of support.
     */
    private void addWallSignPositions(Block bellBlock, Bell bellData, List<SignPosition> positions) {
        BlockFace facing = bellData.getFacing();
        Block support = bellBlock.getRelative(facing.getOppositeFace());

        Block belowBell = bellBlock.getRelative(BlockFace.DOWN);
        Block aboveBell = bellBlock.getRelative(BlockFace.UP);
        Block belowSupport = support.getRelative(BlockFace.DOWN);
        Block aboveSupport = support.getRelative(BlockFace.UP);

        if (belowSupport.getType().isSolid() && canPlaceSign(belowBell)) {
            positions.add(new SignPosition(belowBell, facing));
        }
        if (aboveSupport.getType().isSolid() && canPlaceSign(aboveBell)) {
            positions.add(new SignPosition(aboveBell, facing));
        }

        if (positions.isEmpty()) {
            addFacetSignPositions(support, positions);
        }
    }

    /**
     * Add sign positions on the four horizontal facets of the support block.
     */
    private void addFacetSignPositions(Block support, List<SignPosition> positions) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Block pos = support.getRelative(face);
            if (canPlaceSign(pos)) {
                positions.add(new SignPosition(pos, face.getOppositeFace()));
            }
        }
    }
}

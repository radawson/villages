package org.clockworx.villages.signs;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageEntrance;
import org.clockworx.villages.util.PluginLogger;

import java.util.List;

/**
 * Places "Welcome to [Village Name]" signs at village entrances.
 * 
 * Signs are placed at detected entrance points, facing outward to greet
 * players entering the village.
 * 
 * Features:
 * - Configurable sign text lines
 * - Configurable sign material (wood type)
 * - Support for %village_name% placeholder
 * - Non-destructive placement (only replaces air/vegetation)
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class WelcomeSignPlacer {
    
    private final VillagesPlugin plugin;
    private PluginLogger logger;
    
    // Configuration
    private Material signMaterial;
    private String[] signLines;
    private boolean autoPlace;
    
    // Default sign text
    private static final String[] DEFAULT_LINES = {
        "Welcome to",
        "%village_name%",
        "---",
        ""
    };
    
    /**
     * Creates a new WelcomeSignPlacer.
     * 
     * @param plugin The plugin instance
     */
    public WelcomeSignPlacer(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        loadConfiguration();
    }
    
    /**
     * Loads sign configuration.
     */
    private void loadConfiguration() {
        // Load sign material
        String materialName = plugin.getConfig().getString("signs.material", "OAK_WALL_SIGN");
        try {
            signMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            logWarning("Invalid sign material: " + materialName + ", using OAK_WALL_SIGN");
            signMaterial = Material.OAK_WALL_SIGN;
        }
        
        // Load sign text lines
        List<String> lines = plugin.getConfig().getStringList("signs.lines");
        if (lines.isEmpty()) {
            signLines = DEFAULT_LINES;
        } else {
            signLines = new String[4];
            for (int i = 0; i < 4; i++) {
                signLines[i] = i < lines.size() ? lines.get(i) : "";
            }
        }
        
        // Load auto-place setting
        autoPlace = plugin.getConfig().getBoolean("signs.auto-place", true);
    }
    
    /**
     * Places welcome signs at all entrances of a village.
     * 
     * @param village The village
     * @return Number of signs placed
     */
    public int placeSignsAtEntrances(Village village) {
        World world = village.getWorld();
        if (world == null) {
            return 0;
        }
        
        int placed = 0;
        
        for (VillageEntrance entrance : village.getEntrances()) {
            if (placeSignAtEntrance(village, entrance, world)) {
                placed++;
            }
        }
        
        if (placed > 0) {
            logDebug("Placed " + placed + " welcome signs for village: " + 
                village.getDisplayName());
        }
        
        return placed;
    }
    
    /**
     * Places a welcome sign at a specific entrance.
     * 
     * @param village The village
     * @param entrance The entrance
     * @param world The world
     * @return true if sign was placed
     */
    public boolean placeSignAtEntrance(Village village, VillageEntrance entrance, World world) {
        try {
            // Get sign location (1 block behind entrance, facing outward)
            Block signBlock = findSignLocation(world, entrance);
            
            if (signBlock == null) {
                return false;
            }
            
            // Check if we can place here
            if (!canPlaceSign(signBlock)) {
                return false;
            }
            
            // Place the sign
            signBlock.setType(signMaterial);
            
            // Set sign facing
            if (signBlock.getBlockData() instanceof WallSign wallSign) {
                // Sign faces outward (away from village) to be read by incoming players
                wallSign.setFacing(entrance.getFacing().getOppositeFace());
                signBlock.setBlockData(wallSign);
            }
            
            // Set sign text
            BlockState state = signBlock.getState();
            if (state instanceof Sign sign) {
                setSignText(sign, village);
                sign.update();
            }
            
            logDebug("Placed welcome sign at " + 
                signBlock.getX() + ", " + signBlock.getY() + ", " + signBlock.getZ());
            
            return true;
            
        } catch (Exception e) {
            logWarning("Failed to place welcome sign: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Finds a suitable location for a sign near an entrance.
     * Prefers posts on the side of paths.
     * 
     * @param world The world
     * @param entrance The entrance
     * @return Block to place sign on, or null if no suitable location
     */
    private Block findSignLocation(World world, VillageEntrance entrance) {
        int x = entrance.getX();
        int y = entrance.getY();
        int z = entrance.getZ();
        
        // Try to find a post block on the side of the path
        BlockFace[] sides = getSideDirections(entrance.getFacing());
        
        for (BlockFace side : sides) {
            Block candidate = world.getBlockAt(
                x + side.getModX(),
                y + 1, // One block above ground
                z + side.getModZ()
            );
            
            if (canPlaceSign(candidate)) {
                return candidate;
            }
        }
        
        // Fall back to the entrance location itself, one block up
        Block fallback = world.getBlockAt(x, y + 1, z);
        if (canPlaceSign(fallback)) {
            return fallback;
        }
        
        // Try two blocks up
        fallback = world.getBlockAt(x, y + 2, z);
        if (canPlaceSign(fallback)) {
            return fallback;
        }
        
        return null;
    }
    
    /**
     * Gets the side directions perpendicular to a facing.
     */
    private BlockFace[] getSideDirections(BlockFace facing) {
        return switch (facing) {
            case NORTH, SOUTH -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            default -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
        };
    }
    
    /**
     * Checks if a sign can be placed at a location.
     * 
     * @param block The block to check
     * @return true if placement is allowed
     */
    private boolean canPlaceSign(Block block) {
        Material type = block.getType();
        
        // Allow if air or replaceable vegetation
        if (type.isAir()) {
            return true;
        }
        
        // Allow replacing certain vegetation
        return type == Material.SHORT_GRASS ||
               type == Material.TALL_GRASS ||
               type == Material.FERN ||
               type == Material.LARGE_FERN ||
               type == Material.DANDELION ||
               type == Material.POPPY ||
               type.name().contains("FLOWER") ||
               type.name().equals("GRASS");
    }
    
    /**
     * Sets the text on a sign.
     * 
     * @param sign The sign block state
     * @param village The village (for placeholder replacement)
     */
    private void setSignText(Sign sign, Village village) {
        SignSide front = sign.getSide(Side.FRONT);
        
        for (int i = 0; i < 4; i++) {
            String line = signLines[i];
            
            // Replace placeholders
            line = line.replace("%village_name%", village.getDisplayName());
            
            front.line(i, Component.text(line));
        }
    }
    
    /**
     * Removes all welcome signs at village entrances.
     * 
     * @param village The village
     * @return Number of signs removed
     */
    public int removeSignsAtEntrances(Village village) {
        World world = village.getWorld();
        if (world == null) {
            return 0;
        }
        
        int removed = 0;
        
        for (VillageEntrance entrance : village.getEntrances()) {
            if (removeSignAtEntrance(entrance, world)) {
                removed++;
            }
        }
        
        return removed;
    }
    
    /**
     * Removes a welcome sign at a specific entrance.
     * 
     * @param entrance The entrance
     * @param world The world
     * @return true if a sign was removed
     */
    public boolean removeSignAtEntrance(VillageEntrance entrance, World world) {
        int x = entrance.getX();
        int y = entrance.getY();
        int z = entrance.getZ();
        
        // Check possible sign locations
        BlockFace[] sides = getSideDirections(entrance.getFacing());
        
        for (BlockFace side : sides) {
            Block block = world.getBlockAt(
                x + side.getModX(),
                y + 1,
                z + side.getModZ()
            );
            
            if (isWelcomeSign(block)) {
                block.setType(Material.AIR);
                return true;
            }
        }
        
        // Check entrance location
        for (int dy = 1; dy <= 2; dy++) {
            Block block = world.getBlockAt(x, y + dy, z);
            if (isWelcomeSign(block)) {
                block.setType(Material.AIR);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a block is a welcome sign placed by this plugin.
     * 
     * @param block The block to check
     * @return true if it's a welcome sign
     */
    private boolean isWelcomeSign(Block block) {
        if (!block.getType().name().contains("SIGN")) {
            return false;
        }
        
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return false;
        }
        
        // Check if it has our text pattern
        SignSide front = sign.getSide(Side.FRONT);
        Component line0 = front.line(0);
        
        // Check if first line contains "Welcome"
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(line0);
        
        return text.toLowerCase().contains("welcome");
    }
    
    /**
     * Updates signs at all entrances (e.g., after village rename).
     * 
     * @param village The village
     * @return Number of signs updated
     */
    public int updateSignsAtEntrances(Village village) {
        // Remove old signs and place new ones
        removeSignsAtEntrances(village);
        return placeSignsAtEntrances(village);
    }
    
    /**
     * Checks if auto-placement is enabled.
     * 
     * @return true if signs should be placed automatically
     */
    public boolean isAutoPlaceEnabled() {
        return autoPlace;
    }
    
    /**
     * Reloads configuration.
     */
    public void reload() {
        this.logger = plugin.getPluginLogger();
        loadConfiguration();
    }
    
    // ==================== Logging Helpers ====================
    
    private void logWarning(String message) {
        if (logger != null) {
            logger.warning(message);
        } else {
            plugin.getLogger().warning(message);
        }
    }
    
    private void logDebug(String message) {
        if (logger != null) {
            logger.debugEntrance(message);
        }
    }
}

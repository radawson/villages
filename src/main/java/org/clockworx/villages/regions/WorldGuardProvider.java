package org.clockworx.villages.regions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * WorldGuard implementation of the RegionProvider interface.
 * 
 * WorldGuard is the most widely-used region management plugin for Minecraft,
 * providing extensive flag support and protection features.
 * 
 * This provider:
 * - Creates cuboid regions matching village boundaries
 * - Sets flags for village protection and welcome messages
 * - Integrates with WorldGuard's region manager
 * 
 * @author Clockworx
 * @since 0.2.0
 * @see <a href="https://enginehub.org/worldguard">WorldGuard Documentation</a>
 */
public class WorldGuardProvider implements RegionProvider {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    private boolean available;
    
    /**
     * Creates a new WorldGuardProvider.
     * 
     * @param plugin The plugin instance
     */
    public WorldGuardProvider(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.available = checkAvailability();
    }
    
    @Override
    public String getName() {
        return "worldguard";
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Checks if WorldGuard is available on the server.
     */
    private boolean checkAvailability() {
        try {
            // Check if WorldGuard plugin is present
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
                return false;
            }
            
            // Check if we can access WorldGuard classes
            Class.forName("com.sk89q.worldguard.WorldGuard");
            return true;
            
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            if (!available) {
                logger.warning(LogCategory.REGION, "WorldGuard not available");
                return;
            }
            logger.info(LogCategory.REGION, "WorldGuard provider initialized");
            logger.debugRegion("WorldGuard provider ready");
        });
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Gets the WorldGuard RegionManager for a world.
     */
    private RegionManager getRegionManager(World world) {
        if (world == null) return null;
        
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        return container.get(weWorld);
    }
    
    // ==================== Region CRUD Operations ====================
    
    @Override
    public CompletableFuture<String> createRegion(Village village, VillageBoundary boundary) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                World world = village.getWorld();
                if (world == null) {
                    throw new IllegalStateException("Village world not loaded: " + village.getWorldName());
                }
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) {
                    throw new IllegalStateException("Could not get RegionManager for world: " + world.getName());
                }
                
                String regionId = generateRegionId(village);
                
                // Create cuboid region from boundary
                BlockVector3 min = BlockVector3.at(
                    boundary.getMinX(),
                    boundary.getMinY(),
                    boundary.getMinZ()
                );
                BlockVector3 max = BlockVector3.at(
                    boundary.getMaxX(),
                    boundary.getMaxY(),
                    boundary.getMaxZ()
                );
                
                ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
                
                // Set region priority (higher than default)
                region.setPriority(10);
                
                // Add region to manager
                rm.addRegion(region);
                
                logger.info(LogCategory.REGION, "Created WorldGuard region: " + regionId);
                logger.debugRegion("WorldGuard region created: " + regionId + " for village " + village.getId());
                return regionId;
                
            } catch (Exception e) {
                logger.warning(LogCategory.REGION, "Failed to create WorldGuard region", e);
                throw new RuntimeException("Failed to create region", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> updateRegion(Village village, VillageBoundary boundary) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                World world = village.getWorld();
                if (world == null) return false;
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) return false;
                
                String regionId = village.getRegionId();
                if (regionId == null) {
                    regionId = generateRegionId(village);
                }
                
                ProtectedRegion existing = rm.getRegion(regionId);
                if (existing == null) {
                    // Region doesn't exist, create it
                    createRegion(village, boundary).join();
                    return true;
                }
                
                // Create new region with updated bounds
                BlockVector3 min = BlockVector3.at(
                    boundary.getMinX(),
                    boundary.getMinY(),
                    boundary.getMinZ()
                );
                BlockVector3 max = BlockVector3.at(
                    boundary.getMaxX(),
                    boundary.getMaxY(),
                    boundary.getMaxZ()
                );
                
                ProtectedCuboidRegion newRegion = new ProtectedCuboidRegion(regionId, min, max);
                
                // Copy existing flags and settings
                newRegion.copyFrom(existing);
                
                // Replace in manager
                rm.addRegion(newRegion);
                
                logger.info(LogCategory.REGION, "Updated WorldGuard region: " + regionId);
                logger.debugRegion("WorldGuard region updated: " + regionId + " for village " + village.getId());
                return true;
                
            } catch (Exception e) {
                logger.warning(LogCategory.REGION, "Failed to update WorldGuard region", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteRegion(Village village) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                World world = village.getWorld();
                if (world == null) return false;
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) return false;
                
                String regionId = village.getRegionId();
                if (regionId == null) {
                    regionId = generateRegionId(village);
                }
                
                if (rm.getRegion(regionId) == null) {
                    return false;
                }
                
                rm.removeRegion(regionId);
                logger.info(LogCategory.REGION, "Deleted WorldGuard region: " + regionId);
                logger.debugRegion("WorldGuard region deleted: " + regionId + " for village " + village.getId());
                return true;
                
            } catch (Exception e) {
                logger.warning(LogCategory.REGION, "Failed to delete WorldGuard region", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> regionExists(Village village) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                World world = village.getWorld();
                if (world == null) return false;
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) return false;
                
                String regionId = village.getRegionId();
                if (regionId == null) {
                    regionId = generateRegionId(village);
                }
                
                return rm.getRegion(regionId) != null;
                
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<String>> getRegionId(Village village) {
        return regionExists(village).thenApply(exists -> {
            if (exists) {
                String regionId = village.getRegionId();
                return Optional.ofNullable(regionId != null ? regionId : generateRegionId(village));
            }
            return Optional.empty();
        });
    }
    
    // ==================== Flag Operations ====================
    
    @Override
    public CompletableFuture<Boolean> setFlag(Village village, String flagName, String value) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                World world = village.getWorld();
                if (world == null) return false;
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) return false;
                
                String regionId = village.getRegionId();
                if (regionId == null) {
                    regionId = generateRegionId(village);
                }
                
                ProtectedRegion region = rm.getRegion(regionId);
                if (region == null) return false;
                
                // Apply the flag
                applyFlag(region, flagName, value);
                
                return true;
                
            } catch (Exception e) {
                logger.warning(LogCategory.REGION, "Failed to set flag: " + flagName, e);
                return false;
            }
        });
    }
    
    /**
     * Applies a flag to a region.
     */
    private void applyFlag(ProtectedRegion region, String flagName, String value) {
        // Handle common flags
        switch (flagName.toLowerCase()) {
            case "pvp" -> {
                StateFlag.State state = "allow".equalsIgnoreCase(value) ? 
                    StateFlag.State.ALLOW : StateFlag.State.DENY;
                region.setFlag(Flags.PVP, state);
            }
            case "mob-spawning" -> {
                StateFlag.State state = "allow".equalsIgnoreCase(value) ? 
                    StateFlag.State.ALLOW : StateFlag.State.DENY;
                region.setFlag(Flags.MOB_SPAWNING, state);
            }
            case "mob-griefing" -> {
                StateFlag.State state = "allow".equalsIgnoreCase(value) ? 
                    StateFlag.State.ALLOW : StateFlag.State.DENY;
                region.setFlag(Flags.CREEPER_EXPLOSION, state);
                region.setFlag(Flags.ENDER_BUILD, state);
            }
            case "greeting" -> {
                region.setFlag(Flags.GREET_MESSAGE, value);
            }
            case "farewell" -> {
                region.setFlag(Flags.FAREWELL_MESSAGE, value);
            }
            case "entry" -> {
                StateFlag.State state = "allow".equalsIgnoreCase(value) ? 
                    StateFlag.State.ALLOW : StateFlag.State.DENY;
                region.setFlag(Flags.ENTRY, state);
            }
            case "build" -> {
                StateFlag.State state = "allow".equalsIgnoreCase(value) ? 
                    StateFlag.State.ALLOW : StateFlag.State.DENY;
                region.setFlag(Flags.BUILD, state);
            }
            default -> logger.debugRegion("Unknown flag: " + flagName);
        }
    }
    
    @Override
    public CompletableFuture<Optional<String>> getFlag(Village village, String flagName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                World world = village.getWorld();
                if (world == null) return Optional.empty();
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) return Optional.empty();
                
                String regionId = village.getRegionId();
                if (regionId == null) {
                    regionId = generateRegionId(village);
                }
                
                ProtectedRegion region = rm.getRegion(regionId);
                if (region == null) return Optional.empty();
                
                // Get flag value
                Object value = getFlagValue(region, flagName);
                return Optional.ofNullable(value != null ? value.toString() : null);
                
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }
    
    /**
     * Gets a flag value from a region.
     */
    private Object getFlagValue(ProtectedRegion region, String flagName) {
        return switch (flagName.toLowerCase()) {
            case "pvp" -> region.getFlag(Flags.PVP);
            case "mob-spawning" -> region.getFlag(Flags.MOB_SPAWNING);
            case "greeting" -> region.getFlag(Flags.GREET_MESSAGE);
            case "farewell" -> region.getFlag(Flags.FAREWELL_MESSAGE);
            case "entry" -> region.getFlag(Flags.ENTRY);
            case "build" -> region.getFlag(Flags.BUILD);
            default -> null;
        };
    }
    
    @Override
    public CompletableFuture<Map<String, String>> getAllFlags(Village village) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> flags = new HashMap<>();
            
            try {
                World world = village.getWorld();
                if (world == null) return flags;
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) return flags;
                
                String regionId = village.getRegionId();
                if (regionId == null) {
                    regionId = generateRegionId(village);
                }
                
                ProtectedRegion region = rm.getRegion(regionId);
                if (region == null) return flags;
                
                // Extract all flags
                for (Map.Entry<Flag<?>, Object> entry : region.getFlags().entrySet()) {
                    flags.put(entry.getKey().getName(), entry.getValue().toString());
                }
                
            } catch (Exception e) {
                logger.warning(LogCategory.REGION, "Failed to get all flags", e);
            }
            
            return flags;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> removeFlag(Village village, String flagName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                World world = village.getWorld();
                if (world == null) return false;
                
                RegionManager rm = getRegionManager(world);
                if (rm == null) return false;
                
                String regionId = village.getRegionId();
                if (regionId == null) {
                    regionId = generateRegionId(village);
                }
                
                ProtectedRegion region = rm.getRegion(regionId);
                if (region == null) return false;
                
                // Remove the flag
                Flag<?> flag = getFlagByName(flagName);
                if (flag != null) {
                    region.setFlag(flag, null);
                    return true;
                }
                
                return false;
                
            } catch (Exception e) {
                logger.warning(LogCategory.REGION, "Failed to remove flag: " + flagName, e);
                return false;
            }
        });
    }
    
    /**
     * Gets a WorldGuard flag by name.
     */
    private Flag<?> getFlagByName(String name) {
        return switch (name.toLowerCase()) {
            case "pvp" -> Flags.PVP;
            case "mob-spawning" -> Flags.MOB_SPAWNING;
            case "greeting" -> Flags.GREET_MESSAGE;
            case "farewell" -> Flags.FAREWELL_MESSAGE;
            case "entry" -> Flags.ENTRY;
            case "build" -> Flags.BUILD;
            default -> null;
        };
    }
    
    @Override
    public CompletableFuture<Void> applyDefaultFlags(Village village, Map<String, String> defaultFlags) {
        return CompletableFuture.runAsync(() -> {
            // Replace placeholders in flag values
            Map<String, String> processedFlags = new HashMap<>();
            for (Map.Entry<String, String> entry : defaultFlags.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    value = value.replace("%village_name%", village.getDisplayName());
                }
                processedFlags.put(entry.getKey(), value);
            }
            
            // Apply each flag
            for (Map.Entry<String, String> entry : processedFlags.entrySet()) {
                setFlag(village, entry.getKey(), entry.getValue()).join();
            }
        });
    }
}

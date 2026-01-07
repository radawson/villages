package org.clockworx.villages.regions;

import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining region management operations for village protection.
 * 
 * This abstraction allows the plugin to support multiple region management plugins:
 * - WorldGuard: The most widely-used region plugin
 * - RegionGuard: A lightweight alternative
 * 
 * Implementations wrap the specific plugin's API and translate our village
 * boundary data into the plugin's region format.
 * 
 * Region naming convention: "village_[uuid or name]"
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public interface RegionProvider {
    
    /**
     * Gets the name of this region provider.
     * 
     * @return The provider name (e.g., "worldguard", "regionguard")
     */
    String getName();
    
    /**
     * Checks if this provider's plugin is available on the server.
     * 
     * @return true if the plugin is installed and enabled
     */
    boolean isAvailable();
    
    /**
     * Initializes the region provider.
     * Called when the plugin enables if this provider is available.
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    CompletableFuture<Void> initialize();
    
    /**
     * Shuts down the region provider.
     * Called when the plugin disables.
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    CompletableFuture<Void> shutdown();
    
    // ==================== Region CRUD Operations ====================
    
    /**
     * Creates a protected region for a village.
     * 
     * The region will be named "village_[name]" if the village has a name,
     * or "village_[uuid]" otherwise.
     * 
     * @param village The village to create a region for
     * @param boundary The boundary to use for the region
     * @return CompletableFuture with the created region ID
     */
    CompletableFuture<String> createRegion(Village village, VillageBoundary boundary);
    
    /**
     * Updates an existing region with new boundaries.
     * 
     * @param village The village whose region to update
     * @param boundary The new boundary
     * @return CompletableFuture that completes with true if updated
     */
    CompletableFuture<Boolean> updateRegion(Village village, VillageBoundary boundary);
    
    /**
     * Deletes a village's region.
     * 
     * @param village The village whose region to delete
     * @return CompletableFuture that completes with true if deleted
     */
    CompletableFuture<Boolean> deleteRegion(Village village);
    
    /**
     * Checks if a village has a region defined.
     * 
     * @param village The village to check
     * @return CompletableFuture that completes with true if region exists
     */
    CompletableFuture<Boolean> regionExists(Village village);
    
    /**
     * Gets the region ID for a village.
     * 
     * @param village The village
     * @return CompletableFuture with the region ID if it exists
     */
    CompletableFuture<Optional<String>> getRegionId(Village village);
    
    // ==================== Flag Operations ====================
    
    /**
     * Sets a flag on a village's region.
     * 
     * Flag names and values are provider-specific. Common WorldGuard flags:
     * - pvp: allow/deny
     * - mob-spawning: allow/deny
     * - greeting: message text
     * - farewell: message text
     * - entry: allow/deny
     * 
     * @param village The village
     * @param flag The flag name
     * @param value The flag value
     * @return CompletableFuture that completes with true if set successfully
     */
    CompletableFuture<Boolean> setFlag(Village village, String flag, String value);
    
    /**
     * Gets a flag value from a village's region.
     * 
     * @param village The village
     * @param flag The flag name
     * @return CompletableFuture with the flag value if set
     */
    CompletableFuture<Optional<String>> getFlag(Village village, String flag);
    
    /**
     * Gets all flags set on a village's region.
     * 
     * @param village The village
     * @return CompletableFuture with a map of flag names to values
     */
    CompletableFuture<Map<String, String>> getAllFlags(Village village);
    
    /**
     * Removes a flag from a village's region.
     * 
     * @param village The village
     * @param flag The flag name
     * @return CompletableFuture that completes with true if removed
     */
    CompletableFuture<Boolean> removeFlag(Village village, String flag);
    
    /**
     * Applies default flags to a village's region.
     * Default flags are configured in config.yml under regions.default-flags.
     * 
     * @param village The village
     * @param defaultFlags Map of flag names to values
     * @return CompletableFuture that completes when all flags are applied
     */
    CompletableFuture<Void> applyDefaultFlags(Village village, Map<String, String> defaultFlags);
    
    // ==================== Utility Operations ====================
    
    /**
     * Generates a region ID for a village.
     * Format: "village_[name_slug]" or "village_[uuid_short]"
     * 
     * @param village The village
     * @return The generated region ID
     */
    default String generateRegionId(Village village) {
        if (village.hasName()) {
            // Convert name to slug: lowercase, spaces to underscores, remove special chars
            String slug = village.getName().toLowerCase()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "");
            return "village_" + slug;
        } else {
            // Use first 8 chars of UUID
            return "village_" + village.getId().toString().substring(0, 8);
        }
    }
    
    /**
     * Checks if a region ID belongs to this plugin.
     * 
     * @param regionId The region ID to check
     * @return true if the region was created by this plugin
     */
    default boolean isVillageRegion(String regionId) {
        return regionId != null && regionId.startsWith("village_");
    }
}

package org.clockworx.villages.regions;

import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages region providers and routes operations to the appropriate provider.
 * 
 * The RegionManager handles:
 * - Detecting available region plugins (WorldGuard, RegionGuard)
 * - Selecting the appropriate provider based on configuration
 * - Routing region operations to the active provider
 * - Fallback handling if preferred provider is unavailable
 * 
 * Provider selection order (configurable):
 * 1. WorldGuard (if available) - most widely used, extensive features
 * 2. RegionGuard (if available) - lightweight alternative
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class RegionManager {
    
    private final VillagesPlugin plugin;
    private final List<RegionProvider> providers;
    private RegionProvider activeProvider;
    private boolean initialized;
    
    /**
     * Creates a new RegionManager.
     * 
     * @param plugin The plugin instance
     */
    public RegionManager(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.providers = new ArrayList<>();
        this.initialized = false;
    }
    
    /**
     * Initializes the region manager and detects available providers.
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            plugin.getLogger().info("Initializing region manager...");
            
            // Register available providers
            registerProviders();
            
            // Select active provider based on config
            selectProvider();
            
            // Initialize the active provider
            if (activeProvider != null) {
                try {
                    activeProvider.initialize().join();
                    plugin.getLogger().info("Region provider initialized: " + activeProvider.getName());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to initialize region provider", e);
                    activeProvider = null;
                }
            } else {
                plugin.getLogger().info("No region plugins detected. Region features disabled.");
            }
            
            initialized = true;
        });
    }
    
    /**
     * Registers all supported region providers.
     */
    private void registerProviders() {
        // Register WorldGuard provider
        RegionProvider worldGuard = new WorldGuardProvider(plugin);
        if (worldGuard.isAvailable()) {
            providers.add(worldGuard);
            plugin.getLogger().info("WorldGuard detected");
        }
        
        // Register RegionGuard provider
        RegionProvider regionGuard = new RegionGuardProvider(plugin);
        if (regionGuard.isAvailable()) {
            providers.add(regionGuard);
            plugin.getLogger().info("RegionGuard detected");
        }
    }
    
    /**
     * Selects the active provider based on configuration.
     */
    private void selectProvider() {
        String preferred = plugin.getConfig().getString("regions.provider", "auto");
        
        if (providers.isEmpty()) {
            activeProvider = null;
            return;
        }
        
        if ("auto".equalsIgnoreCase(preferred)) {
            // Use first available provider
            activeProvider = providers.get(0);
        } else {
            // Try to find the preferred provider
            activeProvider = providers.stream()
                .filter(p -> p.getName().equalsIgnoreCase(preferred))
                .findFirst()
                .orElse(providers.get(0)); // Fallback to first available
        }
    }
    
    /**
     * Shuts down the region manager and all providers.
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            if (activeProvider != null) {
                try {
                    activeProvider.shutdown().join();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error shutting down region provider", e);
                }
            }
            providers.clear();
            activeProvider = null;
            initialized = false;
        });
    }
    
    /**
     * Checks if region management is available.
     * 
     * @return true if a region provider is active
     */
    public boolean isAvailable() {
        return initialized && activeProvider != null && activeProvider.isAvailable();
    }
    
    /**
     * Gets the active region provider.
     * 
     * @return The active provider, or null if none available
     */
    public RegionProvider getProvider() {
        return activeProvider;
    }
    
    /**
     * Gets the name of the active provider.
     * 
     * @return The provider name, or "none" if no provider is active
     */
    public String getProviderName() {
        return activeProvider != null ? activeProvider.getName() : "none";
    }
    
    /**
     * Gets all registered providers.
     * 
     * @return List of registered providers
     */
    public List<RegionProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }
    
    // ==================== Delegated Operations ====================
    
    /**
     * Creates a region for a village.
     * 
     * @param village The village
     * @param boundary The boundary
     * @return CompletableFuture with the region ID, or empty if not available
     */
    public CompletableFuture<Optional<String>> createRegion(Village village, VillageBoundary boundary) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return activeProvider.createRegion(village, boundary)
            .thenApply(Optional::of)
            .exceptionally(e -> {
                plugin.getLogger().log(Level.WARNING, "Failed to create region", e);
                return Optional.empty();
            });
    }
    
    /**
     * Creates a region with default flags.
     * 
     * @param village The village
     * @param boundary The boundary
     * @return CompletableFuture with the region ID
     */
    public CompletableFuture<Optional<String>> createRegionWithDefaults(Village village, VillageBoundary boundary) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        return createRegion(village, boundary)
            .thenCompose(regionId -> {
                if (regionId.isEmpty()) {
                    return CompletableFuture.completedFuture(Optional.<String>empty());
                }
                
                // Apply default flags
                Map<String, String> defaultFlags = getDefaultFlags();
                if (!defaultFlags.isEmpty()) {
                    return activeProvider.applyDefaultFlags(village, defaultFlags)
                        .thenApply(v -> regionId);
                }
                
                return CompletableFuture.completedFuture(regionId);
            });
    }
    
    /**
     * Updates a village's region.
     * 
     * @param village The village
     * @param boundary The new boundary
     * @return CompletableFuture that completes with true if updated
     */
    public CompletableFuture<Boolean> updateRegion(Village village, VillageBoundary boundary) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return activeProvider.updateRegion(village, boundary);
    }
    
    /**
     * Deletes a village's region.
     * 
     * @param village The village
     * @return CompletableFuture that completes with true if deleted
     */
    public CompletableFuture<Boolean> deleteRegion(Village village) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return activeProvider.deleteRegion(village);
    }
    
    /**
     * Checks if a village has a region.
     * 
     * @param village The village
     * @return CompletableFuture that completes with true if exists
     */
    public CompletableFuture<Boolean> regionExists(Village village) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return activeProvider.regionExists(village);
    }
    
    /**
     * Sets a flag on a village's region.
     * 
     * @param village The village
     * @param flag The flag name
     * @param value The flag value
     * @return CompletableFuture that completes with true if set
     */
    public CompletableFuture<Boolean> setFlag(Village village, String flag, String value) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return activeProvider.setFlag(village, flag, value);
    }
    
    /**
     * Gets a flag from a village's region.
     * 
     * @param village The village
     * @param flag The flag name
     * @return CompletableFuture with the flag value
     */
    public CompletableFuture<Optional<String>> getFlag(Village village, String flag) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return activeProvider.getFlag(village, flag);
    }
    
    /**
     * Gets default flags from configuration.
     */
    private Map<String, String> getDefaultFlags() {
        Map<String, String> flags = new HashMap<>();
        
        var section = plugin.getConfig().getConfigurationSection("regions.default-flags");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                flags.put(key, section.getString(key));
            }
        }
        
        return flags;
    }
}

package org.clockworx.villages;

import org.bukkit.plugin.java.JavaPlugin;
import org.clockworx.villages.listeners.VillageChunkListener;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.managers.VillageManager;

/**
 * Main plugin class for the Villages plugin.
 * 
 * This plugin detects villages when chunks load by checking for bell blocks,
 * assigns unique UUIDs to each village, stores them in Persistent Data Container (PDC),
 * and places signs around the bell displaying the UUID.
 * 
 * @author Clockworx
 * @version {$version}
 */
public class VillagesPlugin extends JavaPlugin {
    
    private VillageManager villageManager;
    private SignManager signManager;
    private VillageChunkListener chunkListener;
    
    /**
     * Called when the plugin is enabled.
     * Initializes managers and registers event listeners.
     */
    @Override
    public void onEnable() {
        // Initialize managers
        this.villageManager = new VillageManager(this);
        this.signManager = new SignManager(this);
        
        // Register event listener
        this.chunkListener = new VillageChunkListener(villageManager, signManager);
        getServer().getPluginManager().registerEvents(chunkListener, this);
        
        getLogger().info("Villages plugin has been enabled!");
        getLogger().info("Village detection active - monitoring chunk loads for bell blocks.");
    }
    
    /**
     * Called when the plugin is disabled.
     * Performs cleanup if needed.
     */
    @Override
    public void onDisable() {
        getLogger().info("Villages plugin has been disabled!");
    }
    
    /**
     * Gets the VillageManager instance.
     * 
     * @return The VillageManager
     */
    public VillageManager getVillageManager() {
        return villageManager;
    }
    
    /**
     * Gets the SignManager instance.
     * 
     * @return The SignManager
     */
    public SignManager getSignManager() {
        return signManager;
    }
}

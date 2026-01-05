package org.clockworx.villages;

import org.bukkit.plugin.java.JavaPlugin;
import org.clockworx.villages.commands.VillageCommand;
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
 * @version 0.1.2
 */
public class VillagesPlugin extends JavaPlugin {
    
    private VillageManager villageManager;
    private SignManager signManager;
    private VillageChunkListener chunkListener;
    private VillageCommand villageCommand;
    
    /**
     * Called when the plugin is loaded (before onEnable).
     * For shaded CommandAPI, initialization is typically not needed in onLoad.
     */
    @Override
    public void onLoad() {
        // Shaded CommandAPI doesn't require onLoad initialization
    }
    
    /**
     * Called when the plugin is enabled.
     * Initializes managers and registers event listeners and commands.
     */
    @Override
    public void onEnable() {
        // Initialize managers
        this.villageManager = new VillageManager(this);
        this.signManager = new SignManager(this);
        
        // Register event listener
        this.chunkListener = new VillageChunkListener(villageManager, signManager);
        getServer().getPluginManager().registerEvents(chunkListener, this);
        
        // Register commands (CommandAPI shaded version doesn't require explicit onEnable)
        this.villageCommand = new VillageCommand(this, villageManager, signManager);
        villageCommand.register();
        
        getLogger().info("Villages plugin has been enabled!");
        getLogger().info("Village detection active - monitoring chunk loads for bell blocks.");
        getLogger().info("Village naming command registered: /village name <name>");
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

package org.clockworx.villages;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.clockworx.villages.commands.VillageCommand;
import org.clockworx.villages.listeners.VillageChunkListener;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.managers.VillageManager;
import org.clockworx.villages.storage.VillageStorage;

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
    
    private VillageStorage villageStorage;
    private VillageManager villageManager;
    private SignManager signManager;
    private VillageChunkListener chunkListener;
    private VillageCommand villageCommand;
    
    /**
     * Called when the plugin is loaded (before onEnable).
     * CommandAPI 11.1.0 requires initialization in onLoad() before commands can be registered.
     */
    @Override
    public void onLoad() {
        // Initialize CommandAPI with Paper configuration
        // This must be called before registering any commands
        CommandAPI.onLoad(new CommandAPIPaperConfig(this));
    }
    
    /**
     * Called when the plugin is enabled.
     * Initializes managers and registers event listeners and commands.
     */
    @Override
    public void onEnable() {
        // Initialize storage
        this.villageStorage = new VillageStorage(this);
        
        // Initialize managers
        this.villageManager = new VillageManager(this, villageStorage);
        this.signManager = new SignManager(this);
        
        // Register event listener
        this.chunkListener = new VillageChunkListener(villageManager, signManager);
        getServer().getPluginManager().registerEvents(chunkListener, this);
        
        // Initialize CommandAPI for this plugin
        CommandAPI.onEnable();
        
        // Register commands
        this.villageCommand = new VillageCommand(this, villageManager, signManager, villageStorage);
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
        // Unregister CommandAPI commands
        CommandAPI.onDisable();
        
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

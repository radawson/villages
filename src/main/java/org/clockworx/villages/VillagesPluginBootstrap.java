package org.clockworx.villages;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.clockworx.villages.config.ConfigManager;
import org.clockworx.villages.util.PluginLogger;

/**
 * Bootstrapper for the Villages plugin.
 * 
 * Initializes core components (ConfigManager and PluginLogger) before the plugin
 * is created, ensuring they are available during plugin construction.
 * 
 * @author Clockworx
 * @since 0.2.2
 */
public class VillagesPluginBootstrap implements PluginBootstrap {
    
    private static ConfigManager configManager;
    private static PluginLogger pluginLogger;
    
    /**
     * Called during plugin bootstrap, before the server starts.
     * 
     * @param context The bootstrap context
     */
    @Override
    public void bootstrap(BootstrapContext context) {
        // Bootstrap phase - minimal setup if needed
        // Most initialization happens in createPlugin()
    }
    
    /**
     * Creates the plugin instance with pre-initialized components.
     * 
     * @param context The plugin provider context
     * @return The plugin instance
     */
    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        VillagesPlugin plugin = new VillagesPlugin();
        
        // Initialize ConfigManager first (needs plugin instance for config access)
        configManager = new ConfigManager(plugin);
        
        // Initialize PluginLogger with ConfigManager
        pluginLogger = new PluginLogger(plugin, configManager);
        
        // Update ConfigManager with logger now that it's available
        configManager.setLogger(pluginLogger);
        
        // Set the components in the plugin instance
        plugin.setConfigManager(configManager);
        plugin.setPluginLogger(pluginLogger);
        
        return plugin;
    }
    
    /**
     * Gets the ConfigManager created during bootstrap.
     * 
     * @return The ConfigManager instance
     */
    public static ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Gets the PluginLogger created during bootstrap.
     * 
     * @return The PluginLogger instance
     */
    public static PluginLogger getPluginLogger() {
        return pluginLogger;
    }
}

package org.clockworx.villages.storage;

import org.bukkit.World;
import org.clockworx.villages.VillagesPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages file-based storage of village UUIDs by chunk coordinates.
 * 
 * This class provides persistent storage of village UUIDs that survives bell removal.
 * UUIDs are stored in YAML files, organized by world and chunk coordinates.
 * 
 * Storage format:
 * - Single file: plugins/Villages/villages.yml
 * - Structure: world_name -> chunkX_chunkZ -> uuid
 */
public class VillageStorage {
    
    private final VillagesPlugin plugin;
    private final File storageFile;
    
    /**
     * Creates a new VillageStorage instance.
     * 
     * @param plugin The plugin instance
     */
    public VillageStorage(VillagesPlugin plugin) {
        this.plugin = plugin;
        
        // Create the data folder if it doesn't exist
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        // Create the storage file
        this.storageFile = new File(dataFolder, "villages.yml");
        
        // Create the file if it doesn't exist
        if (!storageFile.exists()) {
            try {
                storageFile.createNewFile();
                plugin.getLogger().info("Created villages.yml storage file");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create villages.yml storage file", e);
            }
        }
    }
    
    /**
     * Gets the village UUID for a specific chunk, if one exists.
     * 
     * @param world The world containing the chunk
     * @param chunkX The X coordinate of the chunk
     * @param chunkZ The Z coordinate of the chunk
     * @return The UUID if found, null otherwise
     */
    public UUID getVillageUuid(World world, int chunkX, int chunkZ) {
        try {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(storageFile);
            
            String worldName = world.getName();
            String chunkKey = chunkX + "_" + chunkZ;
            
            String uuidString = config.getString(worldName + "." + chunkKey);
            
            if (uuidString != null && !uuidString.isEmpty()) {
                try {
                    return UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID format in storage for chunk " + 
                        chunkKey + " in world " + worldName + ": " + uuidString);
                    return null;
                }
            }
            
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read village UUID from storage", e);
            return null;
        }
    }
    
    /**
     * Stores a village UUID for a specific chunk.
     * 
     * @param world The world containing the chunk
     * @param chunkX The X coordinate of the chunk
     * @param chunkZ The Z coordinate of the chunk
     * @param uuid The UUID to store
     */
    public void setVillageUuid(World world, int chunkX, int chunkZ, UUID uuid) {
        try {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(storageFile);
            
            String worldName = world.getName();
            String chunkKey = chunkX + "_" + chunkZ;
            
            config.set(worldName + "." + chunkKey, uuid.toString());
            config.save(storageFile);
            
            plugin.getLogger().fine("Stored village UUID " + uuid + " for chunk " + 
                chunkKey + " in world " + worldName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save village UUID to storage", e);
        }
    }
    
    /**
     * Removes a village UUID for a specific chunk.
     * This is optional and mainly for cleanup purposes.
     * 
     * @param world The world containing the chunk
     * @param chunkX The X coordinate of the chunk
     * @param chunkZ The Z coordinate of the chunk
     */
    public void removeVillageUuid(World world, int chunkX, int chunkZ) {
        try {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(storageFile);
            
            String worldName = world.getName();
            String chunkKey = chunkX + "_" + chunkZ;
            
            config.set(worldName + "." + chunkKey, null);
            config.save(storageFile);
            
            plugin.getLogger().fine("Removed village UUID for chunk " + 
                chunkKey + " in world " + worldName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove village UUID from storage", e);
        }
    }
    
    /**
     * Gets the total number of villages tracked across all worlds.
     * 
     * This method counts all unique village UUIDs stored in the villages.yml file
     * by iterating through all world sections and counting chunk entries.
     * 
     * @return The total number of tracked villages
     */
    public int getVillageCount() {
        try {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(storageFile);
            
            int count = 0;
            
            // Iterate through all world sections
            for (String worldName : config.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection worldSection = config.getConfigurationSection(worldName);
                if (worldSection != null) {
                    // Count all chunk entries in this world
                    count += worldSection.getKeys(false).size();
                }
            }
            
            return count;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to count villages from storage", e);
            return 0;
        }
    }
}


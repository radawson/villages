package org.clockworx.villages.signs;

import org.bukkit.block.Block;
import org.bukkit.block.Biome;

/**
 * Detects the village biome type at a bell location.
 * 
 * Maps Minecraft biomes to village types for sign placement strategies.
 * Based on Minecraft's village biome variants: Plains, Desert, Savanna, Taiga, Snowy Plains.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class VillageBiomeDetector {
    
    /**
     * Village biome types that affect sign placement.
     */
    public enum VillageBiomeType {
        PLAINS,
        DESERT,
        SAVANNA,
        TAIGA,
        SNOWY_PLAINS,
        UNKNOWN
    }
    
    /**
     * Detects the village biome type at the given block location.
     * 
     * @param block The block to check (typically a bell block)
     * @return The village biome type
     */
    public static VillageBiomeType detectBiomeType(Block block) {
        Biome biome = block.getBiome();
        // Use getKey() instead of deprecated name() method
        String biomeName = biome.getKey().getKey().toUpperCase();
        
        // Desert biomes
        if (biomeName.contains("DESERT")) {
            return VillageBiomeType.DESERT;
        }
        
        // Savanna biomes
        if (biomeName.contains("SAVANNA")) {
            return VillageBiomeType.SAVANNA;
        }
        
        // Taiga biomes
        if (biomeName.contains("TAIGA")) {
            return VillageBiomeType.TAIGA;
        }
        
        // Snowy biomes (snowy plains, snowy tundra, etc.)
        if (biomeName.contains("SNOWY") || biomeName.contains("ICE") || 
            biomeName.contains("FROZEN") || biomeName.equals("TUNDRA")) {
            return VillageBiomeType.SNOWY_PLAINS;
        }
        
        // Plains biomes (default for most village types)
        if (biomeName.contains("PLAINS") || biomeName.contains("MEADOW") ||
            biomeName.contains("SUNFLOWER") || biomeName.equals("PLAINS")) {
            return VillageBiomeType.PLAINS;
        }
        
        // Default to plains for unknown biomes (most common village type)
        return VillageBiomeType.PLAINS;
    }
}

package org.clockworx.villages.naming;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.signs.VillageBiomeDetector;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.io.File;
import java.util.*;

/**
 * Generates automatic names for villages using adjective+noun patterns.
 * 
 * Names are biome-specific and can be enhanced with terrain features.
 * All word lists are loaded from names.yml configuration file.
 * 
 * @author Clockworx
 * @since 0.2.4
 */
public class VillageNameGenerator {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    private final TerrainFeatureDetector terrainDetector;
    
    /** Word lists loaded from names.yml */
    private Map<String, WordList> biomeWordLists;
    private WordList coastalWordList;
    
    /** Set of used names to avoid duplicates (optional) */
    private final Set<String> usedNames;
    
    /**
     * Creates a new VillageNameGenerator.
     * 
     * @param plugin The plugin instance
     * @param terrainDetector The terrain feature detector
     */
    public VillageNameGenerator(VillagesPlugin plugin, TerrainFeatureDetector terrainDetector) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.terrainDetector = terrainDetector;
        this.usedNames = new HashSet<>();
        loadWordLists();
    }
    
    /**
     * Loads word lists from names.yml configuration file.
     */
    private void loadWordLists() {
        // Ensure names.yml exists in plugin data folder
        File namesFile = new File(plugin.getDataFolder(), "names.yml");
        if (!namesFile.exists()) {
            // Copy default names.yml from resources
            plugin.saveResource("names.yml", false);
            logger.info(LogCategory.GENERAL, "Created default names.yml configuration file");
        }
        
        // Load configuration
        FileConfiguration config = YamlConfiguration.loadConfiguration(namesFile);
        biomeWordLists = new HashMap<>();
        
        // Load biome-specific word lists
        String[] biomeTypes = {"plains", "desert", "savanna", "taiga", "snowy_plains"};
        for (String biomeType : biomeTypes) {
            WordList wordList = loadWordList(config, biomeType);
            if (wordList != null) {
                biomeWordLists.put(biomeType, wordList);
                logger.debug(LogCategory.GENERAL, "Loaded " + wordList.adjectives.size() + 
                    " adjectives and " + wordList.nouns.size() + " nouns for " + biomeType);
            } else {
                logger.warning(LogCategory.GENERAL, "Failed to load word list for biome: " + biomeType);
            }
        }
        
        // Load coastal word list
        coastalWordList = loadWordList(config, "coastal");
        if (coastalWordList != null) {
            logger.debug(LogCategory.GENERAL, "Loaded coastal word list with " + 
                coastalWordList.adjectives.size() + " adjectives and " + 
                coastalWordList.nouns.size() + " nouns");
        }
        
        logger.info(LogCategory.GENERAL, "Village name generator initialized with " + 
            biomeWordLists.size() + " biome word lists");
    }
    
    /**
     * Loads a word list from configuration.
     * 
     * @param config The configuration
     * @param section The section name (biome type or terrain feature)
     * @return The word list, or null if not found
     */
    private WordList loadWordList(FileConfiguration config, String section) {
        if (!config.contains(section)) {
            return null;
        }
        
        List<String> adjectives = config.getStringList(section + ".adjectives");
        List<String> nouns = config.getStringList(section + ".nouns");
        
        if (adjectives.isEmpty() || nouns.isEmpty()) {
            logger.warning(LogCategory.GENERAL, "Word list for " + section + " is empty or missing adjectives/nouns");
            return null;
        }
        
        return new WordList(new ArrayList<>(adjectives), new ArrayList<>(nouns));
    }
    
    /**
     * Generates a name for a village.
     * 
     * @param village The village to name
     * @return The generated name, or null if generation fails
     */
    public String generateName(Village village) {
        if (village == null) {
            return null;
        }
        
        // Check if village already has a name (never override)
        if (village.hasName()) {
            logger.debug(LogCategory.GENERAL, "Village " + village.getId() + " already has a name, skipping generation");
            return null;
        }
        
        // Detect biome type
        org.bukkit.block.Block bellBlock = village.getBellLocation() != null ? 
            village.getBellLocation().getBlock() : null;
        if (bellBlock == null) {
            logger.debug(LogCategory.GENERAL, "Cannot generate name: village " + village.getId() + " has no bell location");
            return null;
        }
        
        VillageBiomeDetector.VillageBiomeType biomeType = VillageBiomeDetector.detectBiomeType(bellBlock);
        String biomeKey = biomeTypeToKey(biomeType);
        
        // Check for terrain features
        boolean isCoastal = terrainDetector.isCoastal(village);
        
        // Select word lists
        WordList wordList;
        if (isCoastal && coastalWordList != null) {
            // Use coastal words, but fall back to biome words if coastal list is incomplete
            wordList = coastalWordList;
            logger.debug(LogCategory.GENERAL, "Using coastal word list for village " + village.getId());
        } else {
            wordList = biomeWordLists.get(biomeKey);
            if (wordList == null) {
                // Fall back to plains if biome not found
                wordList = biomeWordLists.get("plains");
                logger.warning(LogCategory.GENERAL, "Word list not found for biome " + biomeKey + ", using plains");
            }
        }
        
        if (wordList == null || wordList.adjectives.isEmpty() || wordList.nouns.isEmpty()) {
            logger.warning(LogCategory.GENERAL, "Cannot generate name: word list is empty or null");
            return null;
        }
        
        // Generate name with retry for uniqueness (optional)
        String name = null;
        Random random = new Random();
        int attempts = 0;
        int maxAttempts = 10;
        
        while (name == null || (usedNames.contains(name) && attempts < maxAttempts)) {
            String adjective = wordList.adjectives.get(random.nextInt(wordList.adjectives.size()));
            String noun = wordList.nouns.get(random.nextInt(wordList.nouns.size()));
            name = adjective + " " + noun;
            attempts++;
        }
        
        // Add to used names (optional - can be cleared periodically)
        usedNames.add(name);
        
        logger.debug(LogCategory.GENERAL, "Generated name '" + name + "' for village " + village.getId() + 
            " (biome: " + biomeKey + ", coastal: " + isCoastal + ")");
        
        return name;
    }
    
    /**
     * Converts a VillageBiomeType to a configuration key.
     * 
     * @param biomeType The biome type
     * @return The configuration key
     */
    private String biomeTypeToKey(VillageBiomeDetector.VillageBiomeType biomeType) {
        return switch (biomeType) {
            case PLAINS -> "plains";
            case DESERT -> "desert";
            case SAVANNA -> "savanna";
            case TAIGA -> "taiga";
            case SNOWY_PLAINS -> "snowy_plains";
            case UNKNOWN -> "plains"; // Default fallback
        };
    }
    
    /**
     * Reloads word lists from names.yml.
     * Called when configuration is reloaded.
     */
    public void reload() {
        logger.debug(LogCategory.GENERAL, "Reloading village name generator word lists");
        loadWordLists();
        // Optionally clear used names on reload
        usedNames.clear();
    }
    
    /**
     * Clears the used names cache.
     * Useful if you want to allow duplicate names.
     */
    public void clearUsedNames() {
        usedNames.clear();
        logger.debug(LogCategory.GENERAL, "Cleared used names cache");
    }
    
    /**
     * Helper class to hold word lists.
     */
    private static class WordList {
        final List<String> adjectives;
        final List<String> nouns;
        
        WordList(List<String> adjectives, List<String> nouns) {
            this.adjectives = adjectives;
            this.nouns = nouns;
        }
    }
}

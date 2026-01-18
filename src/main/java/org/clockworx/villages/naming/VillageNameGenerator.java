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
 * Names are biome-specific and can be enhanced with terrain feature modifiers.
 * Modifiers are applied as prefixes/suffixes around the base adjective+noun pair
 * to preserve the biome identity, and duplicate words are avoided when possible.
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
    private ModifierList coastalModifierList;
    private ModifierList riverModifierList;
    private ModifierList beachModifierList;
    
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
        
        // Load terrain modifier word lists (prefixes/suffixes)
        coastalModifierList = loadModifierList(config, "coastal");
        if (coastalModifierList != null) {
            logger.debug(LogCategory.GENERAL, "Loaded coastal modifier list with " +
                coastalModifierList.prefixes.size() + " prefixes and " +
                coastalModifierList.suffixes.size() + " suffixes");
        }

        riverModifierList = loadModifierList(config, "river");
        if (riverModifierList != null) {
            logger.debug(LogCategory.GENERAL, "Loaded river modifier list with " +
                riverModifierList.prefixes.size() + " prefixes and " +
                riverModifierList.suffixes.size() + " suffixes");
        }

        beachModifierList = loadModifierList(config, "beach");
        if (beachModifierList != null) {
            logger.debug(LogCategory.GENERAL, "Loaded beach modifier list with " +
                beachModifierList.prefixes.size() + " prefixes and " +
                beachModifierList.suffixes.size() + " suffixes");
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
     * Loads a modifier list from configuration.
     *
     * @param config The configuration
     * @param section The section name (terrain feature)
     * @return The modifier list, or null if not found
     */
    private ModifierList loadModifierList(FileConfiguration config, String section) {
        if (!config.contains(section)) {
            return null;
        }

        List<String> prefixes = config.getStringList(section + ".prefixes");
        List<String> suffixes = config.getStringList(section + ".suffixes");

        if (prefixes.isEmpty() || suffixes.isEmpty()) {
            logger.warning(LogCategory.GENERAL, "Modifier list for " + section + " is empty or missing prefixes/suffixes");
            return null;
        }

        return new ModifierList(new ArrayList<>(prefixes), new ArrayList<>(suffixes));
    }
    
    /**
     * Generates a name for a village.
     *
     * @param village The village to name
     * @return The generated name, or null if generation fails
     */
    public String generateName(Village village) {
        return generateName(village, false);
    }

    /**
     * Generates a name for a village, optionally allowing renames.
     *
     * @param village The village to name
     * @param allowRename True to regenerate even if the village already has a name
     * @return The generated name, or null if generation fails
     */
    public String generateName(Village village, boolean allowRename) {
        if (village == null) {
            return null;
        }

        if (village.hasName() && !allowRename) {
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
        boolean isRiver = terrainDetector.isRiver(village);
        boolean isBeach = terrainDetector.isBeach(village);
        
        // Always use biome word list for base name
        WordList biomeWordList = biomeWordLists.get(biomeKey);
        if (biomeWordList == null) {
            // Fall back to plains if biome not found
            biomeWordList = biomeWordLists.get("plains");
            logger.warning(LogCategory.GENERAL, "Word list not found for biome " + biomeKey + ", using plains");
        }
        
        if (biomeWordList == null || biomeWordList.adjectives.isEmpty() || biomeWordList.nouns.isEmpty()) {
            logger.warning(LogCategory.GENERAL, "Cannot generate name: biome word list is empty or null");
            return null;
        }
        
        // Generate name with retry for uniqueness (optional)
        String name = null;
        Random random = new Random();
        int attempts = 0;
        int maxAttempts = 10;
        
        while (name == null || (usedNames.contains(name) && attempts < maxAttempts)) {
            // Always generate base name from biome words
            String biomeAdjective = biomeWordList.adjectives.get(random.nextInt(biomeWordList.adjectives.size()));
            String biomeNoun = pickDistinctWord(biomeWordList.nouns, Set.of(biomeAdjective), random);
            if (biomeNoun == null) {
                biomeNoun = biomeWordList.nouns.get(random.nextInt(biomeWordList.nouns.size()));
            }

            // Choose one terrain modifier list when multiple features apply
            ModifierList modifierList = selectModifierList(isCoastal, isRiver, isBeach, random);

            // Use modifiers as prefix/suffix around the base adjective+noun.
            name = buildNameWithModifiers(biomeAdjective, biomeNoun, modifierList, random);
            attempts++;
        }
        
        // Add to used names (optional - can be cleared periodically)
        usedNames.add(name);
        
        logger.debug(LogCategory.GENERAL, "Generated name '" + name + "' for village " + village.getId() +
            " (biome: " + biomeKey + ", coastal: " + isCoastal + ", river: " + isRiver + ", beach: " + isBeach + ")");
        
        return name;
    }

    /**
     * Chooses a single modifier list when multiple terrain features apply.
     * This keeps the naming consistent and avoids stacking multiple modifiers at once.
     */
    private ModifierList selectModifierList(boolean isCoastal, boolean isRiver, boolean isBeach, Random random) {
        List<ModifierList> availableLists = new ArrayList<>();
        if (isCoastal && coastalModifierList != null) {
            availableLists.add(coastalModifierList);
        }
        if (isRiver && riverModifierList != null) {
            availableLists.add(riverModifierList);
        }
        if (isBeach && beachModifierList != null) {
            availableLists.add(beachModifierList);
        }

        if (availableLists.isEmpty()) {
            return null;
        }

        return availableLists.get(random.nextInt(availableLists.size()));
    }

    /**
     * Builds a name that wraps a base adjective+noun with optional prefix/suffix modifiers.
     * The base adjective+noun is never replaced, and duplicate words are avoided when possible.
     */
    private String buildNameWithModifiers(String baseAdjective, String baseNoun, ModifierList modifierList, Random random) {
        if (modifierList == null) {
            return baseAdjective + " " + baseNoun;
        }

        boolean useBoth = random.nextInt(100) < 2; // 2% chance to use both
        boolean usePrefix = useBoth || random.nextBoolean();
        boolean useSuffix = useBoth || !usePrefix;

        Set<String> usedWords = new HashSet<>();
        usedWords.add(baseAdjective);
        usedWords.add(baseNoun);

        String prefix = null;
        String suffix = null;

        if (usePrefix) {
            prefix = pickDistinctWord(modifierList.prefixes, usedWords, random);
            if (prefix != null) {
                usedWords.add(prefix);
            }
        }

        if (useSuffix) {
            suffix = pickDistinctWord(modifierList.suffixes, usedWords, random);
            if (suffix != null) {
                usedWords.add(suffix);
            }
        }

        if (prefix != null && suffix != null) {
            return prefix + " " + baseAdjective + " " + baseNoun + " " + suffix;
        }
        if (prefix != null) {
            return prefix + " " + baseAdjective + " " + baseNoun;
        }
        if (suffix != null) {
            return baseAdjective + " " + baseNoun + " " + suffix;
        }

        return baseAdjective + " " + baseNoun;
    }

    /**
     * Picks a word that does not overlap with existing words.
     * Returns null if no distinct word can be found after a few attempts.
     */
    private String pickDistinctWord(List<String> options, Set<String> usedWords, Random random) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        int maxAttempts = Math.min(10, options.size() * 2);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = options.get(random.nextInt(options.size()));
            if (!usedWords.contains(candidate)) {
                return candidate;
            }
        }

        return null;
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

    /**
     * Helper class for terrain feature modifiers.
     */
    private static class ModifierList {
        final List<String> prefixes;
        final List<String> suffixes;

        ModifierList(List<String> prefixes, List<String> suffixes) {
            this.prefixes = prefixes;
            this.suffixes = suffixes;
        }
    }
}

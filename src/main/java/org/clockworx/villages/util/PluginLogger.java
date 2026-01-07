package org.clockworx.villages.util;

import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.config.ConfigManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom logger for the Villages plugin with:
 * - Timestamped output
 * - Component/category tags
 * - Debug level filtering based on config
 * - Thread-safe operation
 * 
 * Output format: [Villages] [LEVEL] [HH:mm:ss] [Category] Message
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class PluginLogger {
    
    private final Logger logger;
    private final ConfigManager configManager;
    private final DateTimeFormatter timeFormatter;
    
    /**
     * Creates a new PluginLogger.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager for debug settings
     */
    public PluginLogger(VillagesPlugin plugin, ConfigManager configManager) {
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    }
    
    // ==================== Standard Logging ====================
    
    /**
     * Logs an informational message.
     * Always logged regardless of debug settings.
     * 
     * @param message The message to log
     */
    public void info(String message) {
        logger.info(message);
    }
    
    /**
     * Logs an informational message with a category tag.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void info(LogCategory category, String message) {
        logger.info(formatWithCategory(category, message));
    }
    
    /**
     * Logs a warning message.
     * Always logged regardless of debug settings.
     * 
     * @param message The message to log
     */
    public void warning(String message) {
        logger.warning(message);
    }
    
    /**
     * Logs a warning message with a category tag.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void warning(LogCategory category, String message) {
        logger.warning(formatWithCategory(category, message));
    }
    
    /**
     * Logs a warning message with a category tag and exception.
     * 
     * @param category The log category
     * @param message The message to log
     * @param throwable The exception to include
     */
    public void warning(LogCategory category, String message, Throwable throwable) {
        logger.log(Level.WARNING, formatWithCategory(category, message), throwable);
    }
    
    /**
     * Logs a severe/error message.
     * Always logged regardless of debug settings.
     * 
     * @param message The message to log
     */
    public void severe(String message) {
        logger.severe(message);
    }
    
    /**
     * Logs a severe/error message with exception details.
     * 
     * @param message The message to log
     * @param throwable The exception to include
     */
    public void severe(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
    
    /**
     * Logs a severe/error message with a category tag.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void severe(LogCategory category, String message) {
        logger.severe(formatWithCategory(category, message));
    }
    
    /**
     * Logs a severe/error message with a category tag and exception.
     * 
     * @param category The log category
     * @param message The message to log
     * @param throwable The exception to include
     */
    public void severe(LogCategory category, String message, Throwable throwable) {
        logger.log(Level.SEVERE, formatWithCategory(category, message), throwable);
    }
    
    // ==================== Debug Logging ====================
    
    /**
     * Logs a debug message if debug is enabled.
     * 
     * @param message The message to log
     */
    public void debug(String message) {
        if (configManager.isDebugEnabled()) {
            logger.info(formatDebug(LogCategory.GENERAL, message));
        }
    }
    
    /**
     * Logs a debug message with a category if debug is enabled.
     * Respects per-category filtering.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void debug(LogCategory category, String message) {
        if (shouldLog(category)) {
            logger.info(formatDebug(category, message));
        }
    }
    
    /**
     * Logs a storage debug message.
     * Only logged if debug is enabled AND log-storage is enabled.
     * 
     * @param message The message to log
     */
    public void debugStorage(String message) {
        if (configManager.shouldLogStorage()) {
            logger.info(formatDebug(LogCategory.STORAGE, message));
        }
    }
    
    /**
     * Logs a region debug message.
     * Only logged if debug is enabled AND log-regions is enabled.
     * 
     * @param message The message to log
     */
    public void debugRegion(String message) {
        if (configManager.shouldLogRegions()) {
            logger.info(formatDebug(LogCategory.REGION, message));
        }
    }
    
    /**
     * Logs a boundary debug message.
     * Only logged if debug is enabled AND log-boundaries is enabled.
     * 
     * @param message The message to log
     */
    public void debugBoundary(String message) {
        if (configManager.shouldLogBoundaries()) {
            logger.info(formatDebug(LogCategory.BOUNDARY, message));
        }
    }
    
    /**
     * Logs an entrance debug message.
     * Only logged if debug is enabled AND log-entrances is enabled.
     * 
     * @param message The message to log
     */
    public void debugEntrance(String message) {
        if (configManager.shouldLogEntrances()) {
            logger.info(formatDebug(LogCategory.ENTRANCE, message));
        }
    }
    
    /**
     * Logs a command debug message.
     * Only logged if debug is enabled.
     * 
     * @param message The message to log
     */
    public void debugCommand(String message) {
        if (configManager.isDebugEnabled()) {
            logger.info(formatDebug(LogCategory.COMMAND, message));
        }
    }
    
    /**
     * Logs a verbose debug message.
     * Only logged if debug is enabled AND verbose is enabled.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void verbose(LogCategory category, String message) {
        if (configManager.isDebugEnabled() && configManager.isVerbose()) {
            logger.info(formatVerbose(category, message));
        }
    }
    
    /**
     * Logs a verbose debug message.
     * Only logged if debug is enabled AND verbose is enabled.
     * 
     * @param message The message to log
     */
    public void verbose(String message) {
        verbose(LogCategory.GENERAL, message);
    }
    
    // ==================== Formatting ====================
    
    /**
     * Checks if a category should be logged.
     * 
     * @param category The category to check
     * @return true if the category should be logged
     */
    private boolean shouldLog(LogCategory category) {
        if (!configManager.isDebugEnabled()) {
            return false;
        }
        
        return switch (category) {
            case STORAGE -> configManager.shouldLogStorage();
            case REGION -> configManager.shouldLogRegions();
            case BOUNDARY -> configManager.shouldLogBoundaries();
            case ENTRANCE -> configManager.shouldLogEntrances();
            case GENERAL, COMMAND -> true; // Always log if debug is enabled
        };
    }
    
    /**
     * Formats a message with a category tag.
     * 
     * @param category The category
     * @param message The message
     * @return Formatted message
     */
    private String formatWithCategory(LogCategory category, String message) {
        return "[" + category.getDisplayName() + "] " + message;
    }
    
    /**
     * Formats a debug message with timestamp and category.
     * Format: [DEBUG] [HH:mm:ss] [Category] Message
     * 
     * @param category The category
     * @param message The message
     * @return Formatted debug message
     */
    private String formatDebug(LogCategory category, String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        return "[DEBUG] [" + timestamp + "] [" + category.getDisplayName() + "] " + message;
    }
    
    /**
     * Formats a verbose message with timestamp and category.
     * Format: [VERBOSE] [HH:mm:ss] [Category] Message
     * 
     * @param category The category
     * @param message The message
     * @return Formatted verbose message
     */
    private String formatVerbose(LogCategory category, String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        return "[VERBOSE] [" + timestamp + "] [" + category.getDisplayName() + "] " + message;
    }
    
    // ==================== Utility ====================
    
    /**
     * Gets the underlying Java logger.
     * For use in cases where direct logger access is needed.
     * 
     * @return The Java logger
     */
    public Logger getUnderlyingLogger() {
        return logger;
    }
    
    /**
     * Checks if debug logging is currently enabled.
     * 
     * @return true if debug is enabled
     */
    public boolean isDebugEnabled() {
        return configManager.isDebugEnabled();
    }
}

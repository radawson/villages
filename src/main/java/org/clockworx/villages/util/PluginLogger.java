package org.clockworx.villages.util;

import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.config.ConfigManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom logger for the Villages plugin with:
 * - Timestamped output
 * - Component/category tags
 * - Debug level filtering based on config
 * - File logging with rotation
 * - Thread-safe operation
 * 
 * Output format: [Villages] [LEVEL] [HH:mm:ss] [Category] Message
 * File format: [YYYY-MM-DD HH:mm:ss] [LEVEL] [Category] Message
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class PluginLogger {
    
    private final Logger logger;
    private final ConfigManager configManager;
    private final DateTimeFormatter timeFormatter;
    private final DateTimeFormatter dateTimeFormatter;
    private final DateTimeFormatter dateFormatter;
    
    // File logging fields
    private final Path dataFolder;
    private Path logDirectory;
    private Path currentLogFile;
    private BufferedWriter fileWriter;
    private LocalDate currentLogDate;
    private long currentFileSize;
    private long maxFileSizeBytes;
    private boolean fileLoggingEnabled;
    private boolean fileLoggingInitialized;
    
    // Synchronization lock for thread-safe file writing
    private final Object fileLock = new Object();
    
    /**
     * Creates a new PluginLogger.
     * 
     * @param plugin The plugin instance
     * @param configManager The config manager for debug settings
     */
    public PluginLogger(VillagesPlugin plugin, ConfigManager configManager) {
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        this.fileLoggingInitialized = false;
        
        // Initialize file logging
        initializeFileLogging();
    }
    
    /**
     * Initializes file logging if enabled in configuration.
     */
    private void initializeFileLogging() {
        try {
            this.fileLoggingEnabled = configManager.isFileLoggingEnabled();
            
            if (!fileLoggingEnabled) {
                logger.info("File logging is disabled");
                return;
            }
            
            // Create log directory
            String logDirName = configManager.getLogDirectory();
            this.logDirectory = dataFolder.resolve(logDirName);
            
            if (!Files.exists(logDirectory)) {
                Files.createDirectories(logDirectory);
            }
            
            // Set max file size in bytes
            this.maxFileSizeBytes = configManager.getMaxLogFileSizeMb() * 1024L * 1024L;
            
            // Initialize current log file
            this.currentLogDate = LocalDate.now();
            this.currentLogFile = getLogFilePath(currentLogDate, 0);
            
            // Check if file already exists and get its size
            if (Files.exists(currentLogFile)) {
                this.currentFileSize = Files.size(currentLogFile);
            } else {
                this.currentFileSize = 0;
            }
            
            // Open file for appending
            openLogFile();
            
            this.fileLoggingInitialized = true;
            logger.info("File logging initialized: " + currentLogFile.getFileName());
            
            // Clean up old logs on startup
            cleanupOldLogs();
            
        } catch (IOException e) {
            logger.warning("Failed to initialize file logging: " + e.getMessage());
            this.fileLoggingEnabled = false;
        }
    }
    
    /**
     * Gets the path for a log file on a given date with optional rotation index.
     * 
     * @param date The date for the log file
     * @param rotationIndex The rotation index (0 for primary file)
     * @return The log file path
     */
    private Path getLogFilePath(LocalDate date, int rotationIndex) {
        String fileName;
        if (rotationIndex == 0) {
            fileName = "villages-" + date.format(dateFormatter) + ".log";
        } else {
            fileName = "villages-" + date.format(dateFormatter) + "." + rotationIndex + ".log";
        }
        return logDirectory.resolve(fileName);
    }
    
    /**
     * Opens the current log file for writing.
     */
    private void openLogFile() throws IOException {
        synchronized (fileLock) {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException ignored) {
                    // Ignore close errors
                }
            }
            
            // Open file for appending
            fileWriter = new BufferedWriter(new FileWriter(currentLogFile.toFile(), true));
        }
    }
    
    /**
     * Writes a message to the log file.
     * 
     * @param level The log level
     * @param category The log category (may be null)
     * @param message The message to write
     */
    private void writeToFile(String level, LogCategory category, String message) {
        writeToFile(level, category, message, null);
    }
    
    /**
     * Writes a message to the log file with optional exception.
     * 
     * @param level The log level
     * @param category The log category (may be null)
     * @param message The message to write
     * @param throwable Optional exception to include
     */
    private void writeToFile(String level, LogCategory category, String message, Throwable throwable) {
        if (!fileLoggingEnabled || !fileLoggingInitialized) {
            return;
        }
        
        synchronized (fileLock) {
            try {
                // Check for rotation before writing
                checkRotation();
                
                // Format the log line
                String timestamp = LocalDateTime.now().format(dateTimeFormatter);
                StringBuilder line = new StringBuilder();
                line.append("[").append(timestamp).append("] ");
                line.append("[").append(level).append("] ");
                if (category != null) {
                    line.append("[").append(category.getDisplayName()).append("] ");
                }
                line.append(message);
                
                // Write the line
                fileWriter.write(line.toString());
                fileWriter.newLine();
                currentFileSize += line.length() + 1;
                
                // Write stack trace if present
                if (throwable != null) {
                    StringWriter sw = new StringWriter();
                    throwable.printStackTrace(new PrintWriter(sw));
                    String stackTrace = sw.toString();
                    fileWriter.write(stackTrace);
                    currentFileSize += stackTrace.length();
                }
                
                // Flush periodically for important messages
                if ("SEVERE".equals(level) || "WARNING".equals(level)) {
                    fileWriter.flush();
                }
                
            } catch (IOException e) {
                // Log to console but don't throw - file logging should never break the plugin
                logger.warning("Failed to write to log file: " + e.getMessage());
            }
        }
    }
    
    /**
     * Checks if log rotation is needed (daily or size-based).
     */
    private void checkRotation() {
        LocalDate today = LocalDate.now();
        boolean needsRotation = false;
        
        // Daily rotation check
        if (!today.equals(currentLogDate)) {
            needsRotation = true;
        }
        
        // Size rotation check
        if (currentFileSize >= maxFileSizeBytes) {
            needsRotation = true;
        }
        
        if (needsRotation) {
            rotateLogFile();
        }
    }
    
    /**
     * Rotates the log file.
     * For daily rotation: creates a new file for the new day.
     * For size rotation: renames current file with index and creates new primary file.
     */
    private void rotateLogFile() {
        synchronized (fileLock) {
            try {
                // Close current file
                if (fileWriter != null) {
                    fileWriter.flush();
                    fileWriter.close();
                }
                
                LocalDate today = LocalDate.now();
                
                if (!today.equals(currentLogDate)) {
                    // Daily rotation - new day, new file
                    currentLogDate = today;
                    currentLogFile = getLogFilePath(currentLogDate, 0);
                } else {
                    // Size rotation - find next available index
                    int rotationIndex = 1;
                    while (Files.exists(getLogFilePath(currentLogDate, rotationIndex))) {
                        rotationIndex++;
                    }
                    
                    // Rename current file with rotation index
                    Path rotatedFile = getLogFilePath(currentLogDate, rotationIndex);
                    Files.move(currentLogFile, rotatedFile);
                    
                    // Current file stays as primary (will be recreated)
                }
                
                // Reset file size
                currentFileSize = 0;
                
                // Open new file
                openLogFile();
                
                // Clean up old logs after rotation
                cleanupOldLogs();
                
                logger.info("Log file rotated: " + currentLogFile.getFileName());
                
            } catch (IOException e) {
                logger.warning("Failed to rotate log file: " + e.getMessage());
            }
        }
    }
    
    /**
     * Cleans up old log files based on max-files configuration.
     */
    private void cleanupOldLogs() {
        int maxFiles = configManager.getMaxLogFiles();
        if (maxFiles <= 0) {
            return; // Unlimited files
        }
        
        try {
            File[] logFiles = logDirectory.toFile().listFiles((dir, name) -> 
                name.startsWith("villages-") && name.endsWith(".log"));
            
            if (logFiles == null || logFiles.length <= maxFiles) {
                return;
            }
            
            // Sort by last modified time (oldest first)
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));
            
            // Delete oldest files
            int filesToDelete = logFiles.length - maxFiles;
            for (int i = 0; i < filesToDelete; i++) {
                if (logFiles[i].delete()) {
                    logger.info("Deleted old log file: " + logFiles[i].getName());
                }
            }
            
        } catch (Exception e) {
            logger.warning("Failed to cleanup old log files: " + e.getMessage());
        }
    }
    
    /**
     * Shuts down the logger, flushing and closing the log file.
     * Should be called in plugin's onDisable().
     */
    public void shutdown() {
        synchronized (fileLock) {
            if (fileWriter != null) {
                try {
                    writeToFile("INFO", null, "=== Plugin shutting down ===");
                    fileWriter.flush();
                    fileWriter.close();
                    fileWriter = null;
                    logger.info("File logger shut down successfully");
                } catch (IOException e) {
                    logger.warning("Error closing log file: " + e.getMessage());
                }
            }
        }
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
        writeToFile("INFO", null, message);
    }
    
    /**
     * Logs an informational message with a category tag.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void info(LogCategory category, String message) {
        logger.info(formatWithCategory(category, message));
        writeToFile("INFO", category, message);
    }
    
    /**
     * Logs a warning message.
     * Always logged regardless of debug settings.
     * 
     * @param message The message to log
     */
    public void warning(String message) {
        logger.warning(message);
        writeToFile("WARNING", null, message);
    }
    
    /**
     * Logs a warning message with a category tag.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void warning(LogCategory category, String message) {
        logger.warning(formatWithCategory(category, message));
        writeToFile("WARNING", category, message);
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
        writeToFile("WARNING", category, message, throwable);
    }
    
    /**
     * Logs a severe/error message.
     * Always logged regardless of debug settings.
     * 
     * @param message The message to log
     */
    public void severe(String message) {
        logger.severe(message);
        writeToFile("SEVERE", null, message);
    }
    
    /**
     * Logs a severe/error message with exception details.
     * 
     * @param message The message to log
     * @param throwable The exception to include
     */
    public void severe(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
        writeToFile("SEVERE", null, message, throwable);
    }
    
    /**
     * Logs a severe/error message with a category tag.
     * 
     * @param category The log category
     * @param message The message to log
     */
    public void severe(LogCategory category, String message) {
        logger.severe(formatWithCategory(category, message));
        writeToFile("SEVERE", category, message);
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
        writeToFile("SEVERE", category, message, throwable);
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
        // Write to file if include-debug is enabled
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("DEBUG", LogCategory.GENERAL, message);
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
        // Write to file if include-debug is enabled
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("DEBUG", category, message);
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
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("DEBUG", LogCategory.STORAGE, message);
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
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("DEBUG", LogCategory.REGION, message);
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
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("DEBUG", LogCategory.BOUNDARY, message);
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
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("DEBUG", LogCategory.ENTRANCE, message);
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
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("DEBUG", LogCategory.COMMAND, message);
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
        if (configManager.shouldIncludeDebugInFile()) {
            writeToFile("VERBOSE", category, message);
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
    
    /**
     * Checks if file logging is currently enabled and initialized.
     * 
     * @return true if file logging is active
     */
    public boolean isFileLoggingActive() {
        return fileLoggingEnabled && fileLoggingInitialized;
    }
    
    /**
     * Gets the current log file path.
     * 
     * @return The current log file path, or null if file logging is disabled
     */
    public Path getCurrentLogFile() {
        return currentLogFile;
    }
    
    /**
     * Flushes the file writer to ensure all pending data is written.
     */
    public void flush() {
        synchronized (fileLock) {
            if (fileWriter != null) {
                try {
                    fileWriter.flush();
                } catch (IOException e) {
                    logger.warning("Failed to flush log file: " + e.getMessage());
                }
            }
        }
    }
}

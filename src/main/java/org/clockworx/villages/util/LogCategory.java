package org.clockworx.villages.util;

/**
 * Categories for debug logging.
 * Each category can be independently enabled/disabled in config.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public enum LogCategory {
    /**
     * General plugin operations.
     */
    GENERAL("General"),
    
    /**
     * Storage operations (saves, loads, queries).
     */
    STORAGE("Storage"),
    
    /**
     * Region operations (create, delete, flags).
     */
    REGION("Region"),
    
    /**
     * Boundary calculations.
     */
    BOUNDARY("Boundary"),
    
    /**
     * Entrance detection.
     */
    ENTRANCE("Entrance"),
    
    /**
     * Command execution.
     */
    COMMAND("Command");
    
    private final String displayName;
    
    LogCategory(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Gets the display name for log output.
     * 
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}

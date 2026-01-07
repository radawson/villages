package org.clockworx.villages.storage;

/**
 * Exception thrown when storage operations fail.
 * 
 * This exception wraps underlying database or I/O exceptions
 * to provide a consistent interface for storage error handling.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class StorageException extends RuntimeException {
    
    /**
     * Creates a new StorageException with a message.
     * 
     * @param message The error message
     */
    public StorageException(String message) {
        super(message);
    }
    
    /**
     * Creates a new StorageException with a message and cause.
     * 
     * @param message The error message
     * @param cause The underlying cause
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new StorageException from an underlying cause.
     * 
     * @param cause The underlying cause
     */
    public StorageException(Throwable cause) {
        super(cause);
    }
}

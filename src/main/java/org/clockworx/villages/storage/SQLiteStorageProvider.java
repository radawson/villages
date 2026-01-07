package org.clockworx.villages.storage;

import org.bukkit.World;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.model.VillageEntrance;
import org.clockworx.villages.model.VillageHero;
import org.clockworx.villages.model.VillagePoi;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * SQLite embedded database storage provider for village data.
 * 
 * This provider uses SQLite, an embedded database that requires no external
 * server setup. It's the recommended default for most servers as it provides:
 * - Good query performance with indexed lookups
 * - ACID compliance for data integrity
 * - Single file storage (easy to backup)
 * - Concurrent read access
 * 
 * The database schema includes:
 * - villages: Main village data
 * - village_pois: POIs linked to villages
 * - village_entrances: Entrance points
 * 
 * All operations are performed asynchronously to avoid blocking the main thread.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class SQLiteStorageProvider implements StorageProvider {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    private final File databaseFile;
    private Connection connection;
    private boolean available;
    
    // SQL Statements
    private static final String CREATE_VILLAGES_TABLE = """
        CREATE TABLE IF NOT EXISTS villages (
            id VARCHAR(36) PRIMARY KEY,
            world VARCHAR(64) NOT NULL,
            name VARCHAR(64),
            bell_x INT NOT NULL,
            bell_y INT NOT NULL,
            bell_z INT NOT NULL,
            min_x INT,
            min_y INT,
            min_z INT,
            max_x INT,
            max_y INT,
            max_z INT,
            center_x INT,
            center_y INT,
            center_z INT,
            region_id VARCHAR(64),
            mayor_id VARCHAR(36),
            council_members TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """;
    
    private static final String CREATE_POIS_TABLE = """
        CREATE TABLE IF NOT EXISTS village_pois (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            village_id VARCHAR(36) NOT NULL,
            poi_type VARCHAR(32) NOT NULL,
            x INT NOT NULL,
            y INT NOT NULL,
            z INT NOT NULL,
            FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE
        )
        """;
    
    private static final String CREATE_ENTRANCES_TABLE = """
        CREATE TABLE IF NOT EXISTS village_entrances (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            village_id VARCHAR(36) NOT NULL,
            x INT NOT NULL,
            y INT NOT NULL,
            z INT NOT NULL,
            facing VARCHAR(16) NOT NULL,
            auto_detected INTEGER DEFAULT 0,
            FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE
        )
        """;
    
    private static final String CREATE_HEROES_TABLE = """
        CREATE TABLE IF NOT EXISTS village_heroes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            village_id VARCHAR(36) NOT NULL,
            player_id VARCHAR(36) NOT NULL,
            earned_at TEXT NOT NULL,
            raid_level INT NOT NULL,
            defense_count INT NOT NULL,
            FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE,
            UNIQUE(village_id, player_id)
        )
        """;
    
    private static final String CREATE_WORLD_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_villages_world ON villages(world)";
    
    private static final String CREATE_BELL_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_villages_bell ON villages(world, bell_x, bell_y, bell_z)";
    
    private static final String CREATE_POI_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_pois_village ON village_pois(village_id)";
    
    private static final String CREATE_ENTRANCE_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_entrances_village ON village_entrances(village_id)";
    
    private static final String CREATE_HEROES_INDEX = 
        "CREATE INDEX IF NOT EXISTS idx_heroes_village ON village_heroes(village_id)";
    
    /**
     * Creates a new SQLiteStorageProvider.
     * 
     * @param plugin The plugin instance
     */
    public SQLiteStorageProvider(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        String filename = plugin.getConfig().getString("storage.sqlite.file", "villages.db");
        this.databaseFile = new File(plugin.getDataFolder(), filename);
        this.available = false;
    }
    
    @Override
    public String getName() {
        return "sqlite";
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Ensure data folder exists
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                
                // Load SQLite JDBC driver
                Class.forName("org.sqlite.JDBC");
                
                // Connect to database
                String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                
                // Enable foreign keys and WAL mode for better concurrency
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                    stmt.execute("PRAGMA journal_mode = WAL");
                }
                
                // Create tables
                createTables();
                
                available = true;
                logger.info(LogCategory.STORAGE, "SQLite storage initialized: " + databaseFile.getName());
                logger.debugStorage("SQLite database file: " + databaseFile.getAbsolutePath());
                
            } catch (ClassNotFoundException e) {
                throw new StorageException("SQLite JDBC driver not found", e);
            } catch (SQLException e) {
                throw new StorageException("Failed to initialize SQLite database", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            available = false;
            if (connection != null) {
                try {
                    connection.close();
                    logger.info(LogCategory.STORAGE, "SQLite connection closed");
                } catch (SQLException e) {
                    logger.warning(LogCategory.STORAGE, "Error closing SQLite connection", e);
                }
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        return available && connection != null;
    }
    
    /**
     * Creates database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_VILLAGES_TABLE);
            stmt.execute(CREATE_POIS_TABLE);
            stmt.execute(CREATE_ENTRANCES_TABLE);
            stmt.execute(CREATE_HEROES_TABLE);
            stmt.execute(CREATE_WORLD_INDEX);
            stmt.execute(CREATE_BELL_INDEX);
            stmt.execute(CREATE_POI_INDEX);
            stmt.execute(CREATE_ENTRANCE_INDEX);
            stmt.execute(CREATE_HEROES_INDEX);
            
            // Run migrations for existing databases
            migrateDatabase();
        }
    }
    
    /**
     * Migrates the database schema for existing databases.
     * Adds new columns that may not exist in older versions.
     */
    private void migrateDatabase() {
        // Add mayor_id column if it doesn't exist
        addColumnIfNotExists("villages", "mayor_id", "VARCHAR(36)");
        // Add council_members column if it doesn't exist
        addColumnIfNotExists("villages", "council_members", "TEXT");
    }
    
    /**
     * Adds a column to a table if it doesn't already exist.
     */
    private void addColumnIfNotExists(String table, String column, String type) {
        try {
            // Check if column exists
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (!rs.next()) {
                    // Column doesn't exist, add it
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                        logger.info(LogCategory.STORAGE, "Added column " + column + " to table " + table);
                        logger.debugStorage("Schema migration: added column " + column + " to " + table);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning(LogCategory.STORAGE, "Failed to add column " + column + " to " + table, e);
        }
    }
    
    // ==================== Village CRUD Operations ====================
    
    @Override
    public CompletableFuture<Void> saveVillage(Village village) {
        return CompletableFuture.runAsync(() -> {
            logger.debugStorage("Saving village " + village.getId() + " to SQLite storage");
            try {
                connection.setAutoCommit(false);
                
                // Upsert village
                String upsert = """
                    INSERT OR REPLACE INTO villages 
                    (id, world, name, bell_x, bell_y, bell_z, 
                     min_x, min_y, min_z, max_x, max_y, max_z,
                     center_x, center_y, center_z, region_id, 
                     mayor_id, council_members, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                
                try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                    ps.setString(1, village.getId().toString());
                    ps.setString(2, village.getWorldName());
                    ps.setString(3, village.getName());
                    ps.setInt(4, village.getBellX());
                    ps.setInt(5, village.getBellY());
                    ps.setInt(6, village.getBellZ());
                    
                    VillageBoundary boundary = village.getBoundary();
                    if (boundary != null) {
                        ps.setInt(7, boundary.getMinX());
                        ps.setInt(8, boundary.getMinY());
                        ps.setInt(9, boundary.getMinZ());
                        ps.setInt(10, boundary.getMaxX());
                        ps.setInt(11, boundary.getMaxY());
                        ps.setInt(12, boundary.getMaxZ());
                        ps.setInt(13, boundary.getCenterX());
                        ps.setInt(14, boundary.getCenterY());
                        ps.setInt(15, boundary.getCenterZ());
                    } else {
                        for (int i = 7; i <= 15; i++) {
                            ps.setNull(i, Types.INTEGER);
                        }
                    }
                    
                    ps.setString(16, village.getRegionId());
                    
                    // Mayor
                    if (village.getMayorId() != null) {
                        ps.setString(17, village.getMayorId().toString());
                    } else {
                        ps.setNull(17, Types.VARCHAR);
                    }
                    
                    // Council members as JSON array
                    ps.setString(18, serializeUuidList(village.getCouncilMembers()));
                    
                    ps.setString(19, village.getCreatedAt().toString());
                    ps.setString(20, village.getUpdatedAt().toString());
                    
                    ps.executeUpdate();
                }
                
                // Delete existing POIs, entrances, and heroes
                String villageId = village.getId().toString();
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM village_pois WHERE village_id = ?")) {
                    ps.setString(1, villageId);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM village_entrances WHERE village_id = ?")) {
                    ps.setString(1, villageId);
                    ps.executeUpdate();
                }
                
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM village_heroes WHERE village_id = ?")) {
                    ps.setString(1, villageId);
                    ps.executeUpdate();
                }
                
                // Insert POIs
                if (!village.getPois().isEmpty()) {
                    String insertPoi = "INSERT INTO village_pois (village_id, poi_type, x, y, z) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = connection.prepareStatement(insertPoi)) {
                        for (VillagePoi poi : village.getPois()) {
                            ps.setString(1, villageId);
                            ps.setString(2, poi.getTypeId());
                            ps.setInt(3, poi.getX());
                            ps.setInt(4, poi.getY());
                            ps.setInt(5, poi.getZ());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                
                // Insert entrances
                if (!village.getEntrances().isEmpty()) {
                    String insertEntrance = "INSERT INTO village_entrances (village_id, x, y, z, facing, auto_detected) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = connection.prepareStatement(insertEntrance)) {
                        for (VillageEntrance entrance : village.getEntrances()) {
                            ps.setString(1, villageId);
                            ps.setInt(2, entrance.getX());
                            ps.setInt(3, entrance.getY());
                            ps.setInt(4, entrance.getZ());
                            ps.setString(5, entrance.getFacingName());
                            ps.setInt(6, entrance.isAutoDetected() ? 1 : 0);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                
                // Insert heroes
                if (!village.getHeroes().isEmpty()) {
                    String insertHero = """
                        INSERT INTO village_heroes 
                        (village_id, player_id, earned_at, raid_level, defense_count) 
                        VALUES (?, ?, ?, ?, ?)
                        """;
                    try (PreparedStatement ps = connection.prepareStatement(insertHero)) {
                        for (VillageHero hero : village.getHeroes()) {
                            ps.setString(1, villageId);
                            ps.setString(2, hero.playerId().toString());
                            ps.setString(3, hero.earnedAt().toString());
                            ps.setInt(4, hero.raidLevel());
                            ps.setInt(5, hero.defenseCount());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                
                connection.commit();
                logger.debugStorage("Village " + village.getId() + " saved successfully to SQLite storage");
                
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    logger.debugStorage("Transaction rolled back due to error");
                } catch (SQLException rollbackEx) {
                    logger.severe(LogCategory.STORAGE, "Rollback failed", rollbackEx);
                }
                throw new StorageException("Failed to save village", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.warning(LogCategory.STORAGE, "Failed to reset auto-commit", e);
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> loadVillage(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debugStorage("Loading village " + id + " from SQLite storage");
            try {
                String query = "SELECT * FROM villages WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, id.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            logger.debugStorage("Village " + id + " loaded successfully from SQLite storage");
                            return Optional.of(deserializeVillage(rs));
                        }
                    }
                }
                logger.debugStorage("Village " + id + " not found in SQLite storage");
                return Optional.empty();
            } catch (SQLException e) {
                throw new StorageException("Failed to load village", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> loadVillageByBell(String worldName, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debugStorage("Loading village by bell location: " + worldName + " " + x + ", " + y + ", " + z);
            try {
                String query = "SELECT * FROM villages WHERE world = ? AND bell_x = ? AND bell_y = ? AND bell_z = ?";
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, x);
                    ps.setInt(3, y);
                    ps.setInt(4, z);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            logger.debugStorage("Found village by bell location");
                            return Optional.of(deserializeVillage(rs));
                        }
                    }
                }
                logger.debugStorage("No village found by bell location");
                return Optional.empty();
            } catch (SQLException e) {
                throw new StorageException("Failed to load village by bell", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> loadVillageByChunk(String worldName, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Convert chunk coords to block range
                int minX = chunkX << 4;
                int maxX = minX + 15;
                int minZ = chunkZ << 4;
                int maxZ = minZ + 15;
                
                String query = "SELECT * FROM villages WHERE world = ? AND bell_x >= ? AND bell_x <= ? AND bell_z >= ? AND bell_z <= ?";
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, minX);
                    ps.setInt(3, maxX);
                    ps.setInt(4, minZ);
                    ps.setInt(5, maxZ);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(deserializeVillage(rs));
                        }
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new StorageException("Failed to load village by chunk", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> loadVillagesInWorld(World world) {
        return loadVillagesInWorld(world.getName());
    }
    
    @Override
    public CompletableFuture<List<Village>> loadVillagesInWorld(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Village> villages = new ArrayList<>();
                String query = "SELECT * FROM villages WHERE world = ?";
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            villages.add(deserializeVillage(rs));
                        }
                    }
                }
                return villages;
            } catch (SQLException e) {
                throw new StorageException("Failed to load villages in world", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> loadAllVillages() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Village> villages = new ArrayList<>();
                String query = "SELECT * FROM villages";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    while (rs.next()) {
                        villages.add(deserializeVillage(rs));
                    }
                }
                return villages;
            } catch (SQLException e) {
                throw new StorageException("Failed to load all villages", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> deleteVillage(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debugStorage("Deleting village " + id + " from SQLite storage");
            try {
                String delete = "DELETE FROM villages WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(delete)) {
                    ps.setString(1, id.toString());
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        logger.debugStorage("Village " + id + " deleted successfully from SQLite storage");
                    } else {
                        logger.debugStorage("Village " + id + " not found for deletion");
                    }
                    return rows > 0;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to delete village", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> villageExists(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = "SELECT 1 FROM villages WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, id.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to check village existence", e);
            }
        });
    }
    
    // ==================== Utility Operations ====================
    
    @Override
    public CompletableFuture<Integer> getVillageCount() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = "SELECT COUNT(*) FROM villages";
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
                return 0;
            } catch (SQLException e) {
                throw new StorageException("Failed to count villages", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> getVillageCount(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = "SELECT COUNT(*) FROM villages WHERE world = ?";
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
                return 0;
            } catch (SQLException e) {
                throw new StorageException("Failed to count villages in world", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> findVillagesNear(String worldName, int x, int z, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Village> villages = new ArrayList<>();
                // Use bounding box for initial filter, then calculate actual distance
                String query = """
                    SELECT * FROM villages 
                    WHERE world = ? 
                    AND bell_x >= ? AND bell_x <= ?
                    AND bell_z >= ? AND bell_z <= ?
                    """;
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, x - radius);
                    ps.setInt(3, x + radius);
                    ps.setInt(4, z - radius);
                    ps.setInt(5, z + radius);
                    
                    int radiusSq = radius * radius;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int vx = rs.getInt("bell_x");
                            int vz = rs.getInt("bell_z");
                            int dx = vx - x;
                            int dz = vz - z;
                            if (dx * dx + dz * dz <= radiusSq) {
                                villages.add(deserializeVillage(rs));
                            }
                        }
                    }
                }
                return villages;
            } catch (SQLException e) {
                throw new StorageException("Failed to find villages near location", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> findVillageAt(String worldName, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Find villages that might contain this point based on boundary
                String query = """
                    SELECT * FROM villages 
                    WHERE world = ?
                    AND min_x IS NOT NULL
                    AND ? >= min_x AND ? <= max_x
                    AND ? >= min_y AND ? <= max_y
                    AND ? >= min_z AND ? <= max_z
                    """;
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, x);
                    ps.setInt(3, x);
                    ps.setInt(4, y);
                    ps.setInt(5, y);
                    ps.setInt(6, z);
                    ps.setInt(7, z);
                    
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(deserializeVillage(rs));
                        }
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new StorageException("Failed to find village at location", e);
            }
        });
    }
    
    // ==================== Backup and Migration ====================
    
    @Override
    public CompletableFuture<Void> backup(String backupPath) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Checkpoint WAL to ensure all data is in the main database file
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(FULL)");
                }
                
                File backupFile = new File(backupPath);
                Files.copy(databaseFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info(LogCategory.STORAGE, "Created SQLite backup at: " + backupPath);
                logger.debugStorage("SQLite backup created: " + backupPath);
            } catch (SQLException | IOException e) {
                throw new StorageException("Failed to create backup", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<Village>> exportAll() {
        return loadAllVillages();
    }
    
    @Override
    public CompletableFuture<Integer> importAll(List<Village> villages, boolean overwrite) {
        return CompletableFuture.supplyAsync(() -> {
            int count = 0;
            for (Village village : villages) {
                try {
                    if (overwrite) {
                        saveVillage(village).join();
                        count++;
                    } else {
                        if (!villageExists(village.getId()).join()) {
                            saveVillage(village).join();
                            count++;
                        }
                    }
                } catch (Exception e) {
                    logger.warning(LogCategory.STORAGE, "Failed to import village: " + village.getId(), e);
                }
            }
            return count;
        });
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Deserializes a village from a ResultSet row.
     */
    private Village deserializeVillage(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String worldName = rs.getString("world");
        String name = rs.getString("name");
        int bellX = rs.getInt("bell_x");
        int bellY = rs.getInt("bell_y");
        int bellZ = rs.getInt("bell_z");
        
        // Boundary
        VillageBoundary boundary = null;
        int minX = rs.getInt("min_x");
        if (!rs.wasNull()) {
            boundary = new VillageBoundary(
                minX,
                rs.getInt("min_y"),
                rs.getInt("min_z"),
                rs.getInt("max_x"),
                rs.getInt("max_y"),
                rs.getInt("max_z"),
                rs.getInt("center_x"),
                rs.getInt("center_y"),
                rs.getInt("center_z")
            );
        }
        
        String regionId = rs.getString("region_id");
        
        // Mayor
        String mayorIdStr = rs.getString("mayor_id");
        UUID mayorId = (mayorIdStr != null && !mayorIdStr.isEmpty()) ? UUID.fromString(mayorIdStr) : null;
        
        Instant createdAt = parseInstant(rs.getString("created_at"));
        Instant updatedAt = parseInstant(rs.getString("updated_at"));
        
        Village village = new Village(id, worldName, name, bellX, bellY, bellZ,
            boundary, regionId, mayorId, createdAt, updatedAt);
        
        // Load council members
        String councilJson = rs.getString("council_members");
        if (councilJson != null && !councilJson.isEmpty()) {
            village.setCouncilMembers(deserializeUuidList(councilJson));
        }
        
        // Load POIs
        loadPois(village);
        
        // Load entrances
        loadEntrances(village);
        
        // Load heroes
        loadHeroes(village);
        
        return village;
    }
    
    /**
     * Loads POIs for a village.
     */
    private void loadPois(Village village) throws SQLException {
        String query = "SELECT * FROM village_pois WHERE village_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, village.getId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<VillagePoi> pois = new ArrayList<>();
                while (rs.next()) {
                    pois.add(new VillagePoi(
                        rs.getString("poi_type"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                    ));
                }
                village.setPois(pois);
            }
        }
    }
    
    /**
     * Loads entrances for a village.
     */
    private void loadEntrances(Village village) throws SQLException {
        String query = "SELECT * FROM village_entrances WHERE village_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, village.getId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<VillageEntrance> entrances = new ArrayList<>();
                while (rs.next()) {
                    entrances.add(new VillageEntrance(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        VillageEntrance.faceFromString(rs.getString("facing")),
                        rs.getInt("auto_detected") == 1
                    ));
                }
                village.setEntrances(entrances);
            }
        }
    }
    
    /**
     * Loads heroes for a village.
     */
    private void loadHeroes(Village village) throws SQLException {
        String query = "SELECT * FROM village_heroes WHERE village_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, village.getId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<VillageHero> heroes = new ArrayList<>();
                while (rs.next()) {
                    VillageHero hero = VillageHero.fromStorage(
                        UUID.fromString(rs.getString("player_id")),
                        parseInstant(rs.getString("earned_at")),
                        rs.getInt("raid_level"),
                        rs.getInt("defense_count")
                    );
                    if (hero != null) {
                        heroes.add(hero);
                    }
                }
                village.setHeroes(heroes);
            }
        }
    }
    
    /**
     * Serializes a list of UUIDs to a JSON array string.
     */
    private String serializeUuidList(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < uuids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(uuids.get(i).toString()).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Deserializes a JSON array string to a list of UUIDs.
     */
    private List<UUID> deserializeUuidList(String json) {
        List<UUID> uuids = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return uuids;
        }
        // Simple JSON array parsing: ["uuid1","uuid2",...]
        String content = json.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
            if (!content.isEmpty()) {
                String[] parts = content.split(",");
                for (String part : parts) {
                    String uuid = part.trim().replace("\"", "");
                    if (!uuid.isEmpty()) {
                        try {
                            uuids.add(UUID.fromString(uuid));
                        } catch (IllegalArgumentException e) {
                            logger.warning(LogCategory.STORAGE, "Invalid UUID in council list: " + uuid);
                        }
                    }
                }
            }
        }
        return uuids;
    }
    
    /**
     * Parses an ISO instant string.
     */
    private Instant parseInstant(String str) {
        if (str == null || str.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(str);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}

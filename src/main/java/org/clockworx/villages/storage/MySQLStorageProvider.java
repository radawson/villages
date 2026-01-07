package org.clockworx.villages.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.model.VillageEntrance;
import org.clockworx.villages.model.VillageHero;
import org.clockworx.villages.model.VillagePoi;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * MySQL/MariaDB network database storage provider for village data.
 * 
 * This provider uses a MySQL or MariaDB server for storage, making it suitable for:
 * - Multi-server deployments (BungeeCord/Velocity networks)
 * - Large servers with many villages
 * - External tools that need database access
 * 
 * Uses HikariCP for efficient connection pooling.
 * 
 * Configuration in config.yml:
 * <pre>
 * storage:
 *   type: mysql
 *   mysql:
 *     host: localhost
 *     port: 3306
 *     database: villages
 *     username: minecraft
 *     password: secret
 *     pool-size: 10
 * </pre>
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class MySQLStorageProvider implements StorageProvider {
    
    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    private HikariDataSource dataSource;
    private boolean available;
    
    // SQL Statements (MySQL syntax)
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
            created_at VARCHAR(64) NOT NULL,
            updated_at VARCHAR(64) NOT NULL,
            INDEX idx_world (world),
            INDEX idx_bell (world, bell_x, bell_y, bell_z)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    
    private static final String CREATE_POIS_TABLE = """
        CREATE TABLE IF NOT EXISTS village_pois (
            id INT AUTO_INCREMENT PRIMARY KEY,
            village_id VARCHAR(36) NOT NULL,
            poi_type VARCHAR(32) NOT NULL,
            x INT NOT NULL,
            y INT NOT NULL,
            z INT NOT NULL,
            INDEX idx_village (village_id),
            FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    
    private static final String CREATE_ENTRANCES_TABLE = """
        CREATE TABLE IF NOT EXISTS village_entrances (
            id INT AUTO_INCREMENT PRIMARY KEY,
            village_id VARCHAR(36) NOT NULL,
            x INT NOT NULL,
            y INT NOT NULL,
            z INT NOT NULL,
            facing VARCHAR(16) NOT NULL,
            auto_detected TINYINT(1) DEFAULT 0,
            INDEX idx_village (village_id),
            FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    
    private static final String CREATE_HEROES_TABLE = """
        CREATE TABLE IF NOT EXISTS village_heroes (
            id INT AUTO_INCREMENT PRIMARY KEY,
            village_id VARCHAR(36) NOT NULL,
            player_id VARCHAR(36) NOT NULL,
            earned_at VARCHAR(64) NOT NULL,
            raid_level INT NOT NULL,
            defense_count INT NOT NULL,
            INDEX idx_village (village_id),
            FOREIGN KEY (village_id) REFERENCES villages(id) ON DELETE CASCADE,
            UNIQUE KEY unique_hero (village_id, player_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    
    /**
     * Creates a new MySQLStorageProvider.
     * 
     * @param plugin The plugin instance
     */
    public MySQLStorageProvider(VillagesPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.available = false;
    }
    
    @Override
    public String getName() {
        return "mysql";
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Read configuration
                ConfigurationSection mysqlConfig = plugin.getConfig().getConfigurationSection("storage.mysql");
                if (mysqlConfig == null) {
                    throw new StorageException("MySQL configuration not found in config.yml");
                }
                
                String host = mysqlConfig.getString("host", "localhost");
                int port = mysqlConfig.getInt("port", 3306);
                String database = mysqlConfig.getString("database", "villages");
                String username = mysqlConfig.getString("username", "minecraft");
                String password = mysqlConfig.getString("password", "");
                int poolSize = mysqlConfig.getInt("pool-size", 10);
                
                // Configure HikariCP
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + 
                    "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8");
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(poolSize);
                config.setMinimumIdle(2);
                config.setIdleTimeout(300000); // 5 minutes
                config.setConnectionTimeout(10000); // 10 seconds
                config.setMaxLifetime(1800000); // 30 minutes
                config.setPoolName("Villages-MySQL-Pool");
                
                // Performance optimizations
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                
                // Create connection pool
                dataSource = new HikariDataSource(config);
                
                // Create tables
                createTables();
                
                available = true;
                logger.info(LogCategory.STORAGE, "MySQL storage initialized: " + host + ":" + port + "/" + database);
                logger.debugStorage("MySQL connection pool configured: " + host + ":" + port + "/" + database);
                
            } catch (SQLException e) {
                throw new StorageException("Failed to initialize MySQL database", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            available = false;
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                logger.info(LogCategory.STORAGE, "MySQL connection pool closed");
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        return available && dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Gets a connection from the pool.
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource not initialized");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Creates database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_VILLAGES_TABLE);
            stmt.execute(CREATE_POIS_TABLE);
            stmt.execute(CREATE_ENTRANCES_TABLE);
            stmt.execute(CREATE_HEROES_TABLE);
        }
        
        // Run migrations for existing databases
        migrateDatabase();
    }
    
    /**
     * Migrates the database schema for existing databases.
     */
    private void migrateDatabase() {
        try (Connection conn = getConnection()) {
            addColumnIfNotExists(conn, "villages", "mayor_id", "VARCHAR(36)");
            addColumnIfNotExists(conn, "villages", "council_members", "TEXT");
        } catch (SQLException e) {
            logger.warning(LogCategory.STORAGE, "Failed to run database migrations", e);
        }
    }
    
    /**
     * Adds a column to a table if it doesn't already exist.
     */
    private void addColumnIfNotExists(Connection conn, String table, String column, String type) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (!rs.next()) {
                    try (Statement stmt = conn.createStatement()) {
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
            logger.debugStorage("Saving village " + village.getId() + " to MySQL storage");
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                
                try {
                    // Upsert village (MySQL ON DUPLICATE KEY syntax)
                    String upsert = """
                        INSERT INTO villages 
                        (id, world, name, bell_x, bell_y, bell_z, 
                         min_x, min_y, min_z, max_x, max_y, max_z,
                         center_x, center_y, center_z, region_id,
                         mayor_id, council_members, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                        world = VALUES(world), name = VALUES(name),
                        bell_x = VALUES(bell_x), bell_y = VALUES(bell_y), bell_z = VALUES(bell_z),
                        min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                        max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                        center_x = VALUES(center_x), center_y = VALUES(center_y), center_z = VALUES(center_z),
                        region_id = VALUES(region_id), mayor_id = VALUES(mayor_id),
                        council_members = VALUES(council_members), updated_at = VALUES(updated_at)
                        """;
                    
                    try (PreparedStatement ps = conn.prepareStatement(upsert)) {
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
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM village_pois WHERE village_id = ?")) {
                        ps.setString(1, villageId);
                        ps.executeUpdate();
                    }
                    
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM village_entrances WHERE village_id = ?")) {
                        ps.setString(1, villageId);
                        ps.executeUpdate();
                    }
                    
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM village_heroes WHERE village_id = ?")) {
                        ps.setString(1, villageId);
                        ps.executeUpdate();
                    }
                    
                    // Insert POIs
                    if (!village.getPois().isEmpty()) {
                        String insertPoi = "INSERT INTO village_pois (village_id, poi_type, x, y, z) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insertPoi)) {
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
                        try (PreparedStatement ps = conn.prepareStatement(insertEntrance)) {
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
                        try (PreparedStatement ps = conn.prepareStatement(insertHero)) {
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
                    
                    conn.commit();
                    logger.debugStorage("Village " + village.getId() + " saved successfully to MySQL storage");
                    
                } catch (SQLException e) {
                    conn.rollback();
                    logger.debugStorage("Transaction rolled back due to error");
                    throw e;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to save village", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<Village>> loadVillage(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debugStorage("Loading village " + id + " from MySQL storage");
            try (Connection conn = getConnection()) {
                String query = "SELECT * FROM villages WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, id.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            logger.debugStorage("Village " + id + " loaded successfully from MySQL storage");
                            return Optional.of(deserializeVillage(conn, rs));
                        }
                    }
                }
                logger.debugStorage("Village " + id + " not found in MySQL storage");
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
            try (Connection conn = getConnection()) {
                String query = "SELECT * FROM villages WHERE world = ? AND bell_x = ? AND bell_y = ? AND bell_z = ?";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, x);
                    ps.setInt(3, y);
                    ps.setInt(4, z);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            logger.debugStorage("Found village by bell location");
                            return Optional.of(deserializeVillage(conn, rs));
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
            try (Connection conn = getConnection()) {
                int minX = chunkX << 4;
                int maxX = minX + 15;
                int minZ = chunkZ << 4;
                int maxZ = minZ + 15;
                
                String query = "SELECT * FROM villages WHERE world = ? AND bell_x >= ? AND bell_x <= ? AND bell_z >= ? AND bell_z <= ?";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, minX);
                    ps.setInt(3, maxX);
                    ps.setInt(4, minZ);
                    ps.setInt(5, maxZ);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(deserializeVillage(conn, rs));
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
            try (Connection conn = getConnection()) {
                List<Village> villages = new ArrayList<>();
                String query = "SELECT * FROM villages WHERE world = ?";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            villages.add(deserializeVillage(conn, rs));
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
            try (Connection conn = getConnection()) {
                List<Village> villages = new ArrayList<>();
                String query = "SELECT * FROM villages";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    while (rs.next()) {
                        villages.add(deserializeVillage(conn, rs));
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
            logger.debugStorage("Deleting village " + id + " from MySQL storage");
            try (Connection conn = getConnection()) {
                String delete = "DELETE FROM villages WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(delete)) {
                    ps.setString(1, id.toString());
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        logger.debugStorage("Village " + id + " deleted successfully from MySQL storage");
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
            try (Connection conn = getConnection()) {
                String query = "SELECT 1 FROM villages WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
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
            try (Connection conn = getConnection()) {
                String query = "SELECT COUNT(*) FROM villages";
                try (Statement stmt = conn.createStatement();
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
            try (Connection conn = getConnection()) {
                String query = "SELECT COUNT(*) FROM villages WHERE world = ?";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
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
            try (Connection conn = getConnection()) {
                List<Village> villages = new ArrayList<>();
                // Use bounding box query, then filter by actual distance
                String query = """
                    SELECT * FROM villages 
                    WHERE world = ? 
                    AND bell_x BETWEEN ? AND ?
                    AND bell_z BETWEEN ? AND ?
                    """;
                try (PreparedStatement ps = conn.prepareStatement(query)) {
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
                                villages.add(deserializeVillage(conn, rs));
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
            try (Connection conn = getConnection()) {
                String query = """
                    SELECT * FROM villages 
                    WHERE world = ?
                    AND min_x IS NOT NULL
                    AND ? BETWEEN min_x AND max_x
                    AND ? BETWEEN min_y AND max_y
                    AND ? BETWEEN min_z AND max_z
                    """;
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, worldName);
                    ps.setInt(2, x);
                    ps.setInt(3, y);
                    ps.setInt(4, z);
                    
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(deserializeVillage(conn, rs));
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
            // For MySQL, export to SQL dump format
            try {
                File backupFile = new File(backupPath);
                try (Connection conn = getConnection();
                     FileWriter writer = new FileWriter(backupFile)) {
                    
                    writer.write("-- Villages plugin MySQL backup\n");
                    writer.write("-- Generated: " + Instant.now() + "\n\n");
                    
                    // Export villages
                    exportTable(conn, writer, "villages");
                    exportTable(conn, writer, "village_pois");
                    exportTable(conn, writer, "village_entrances");
                    
                    logger.info(LogCategory.STORAGE, "Created MySQL backup at: " + backupPath);
                    logger.debugStorage("MySQL backup created: " + backupPath);
                }
            } catch (SQLException | IOException e) {
                throw new StorageException("Failed to create backup", e);
            }
        });
    }
    
    private void exportTable(Connection conn, FileWriter writer, String tableName) throws SQLException, IOException {
        writer.write("-- Table: " + tableName + "\n");
        String query = "SELECT * FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            
            while (rs.next()) {
                StringBuilder insert = new StringBuilder("INSERT INTO " + tableName + " VALUES (");
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) insert.append(", ");
                    Object val = rs.getObject(i);
                    if (val == null) {
                        insert.append("NULL");
                    } else if (val instanceof String) {
                        insert.append("'").append(((String) val).replace("'", "''")).append("'");
                    } else {
                        insert.append(val);
                    }
                }
                insert.append(");\n");
                writer.write(insert.toString());
            }
        }
        writer.write("\n");
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
    private Village deserializeVillage(Connection conn, ResultSet rs) throws SQLException {
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
        loadPois(conn, village);
        
        // Load entrances
        loadEntrances(conn, village);
        
        // Load heroes
        loadHeroes(conn, village);
        
        return village;
    }
    
    /**
     * Loads POIs for a village.
     */
    private void loadPois(Connection conn, Village village) throws SQLException {
        String query = "SELECT * FROM village_pois WHERE village_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
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
    private void loadEntrances(Connection conn, Village village) throws SQLException {
        String query = "SELECT * FROM village_entrances WHERE village_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
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
    private void loadHeroes(Connection conn, Village village) throws SQLException {
        String query = "SELECT * FROM village_heroes WHERE village_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
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

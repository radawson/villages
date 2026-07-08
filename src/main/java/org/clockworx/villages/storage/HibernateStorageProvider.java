package org.clockworx.villages.storage;

import org.bukkit.World;
import org.clockworx.data.DatabaseSettings;
import org.clockworx.data.flyway.FlywayMigrator;
import org.clockworx.data.hibernate.HibernateSessionManager;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.entity.VillageEntranceEntity;
import org.clockworx.villages.entity.VillageEntity;
import org.clockworx.villages.entity.VillageHeroEntity;
import org.clockworx.villages.entity.VillagePoiEntity;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.model.VillageEntrance;
import org.clockworx.villages.model.VillageHero;
import org.clockworx.villages.model.VillagePoi;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;
import org.hibernate.Session;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Hibernate/Flyway database storage provider for village data.
 *
 * <p>Supports both SQLite (embedded) and MySQL/MariaDB (network) backends via the
 * shared clockworx-data library. Schema is managed by Flyway; CRUD uses
 * {@link HibernateSessionManager}.</p>
 *
 * @author Clockworx
 * @since 0.5.0
 */
public class HibernateStorageProvider implements StorageProvider {

    private final VillagesPlugin plugin;
    private final PluginLogger logger;
    private final Executor executor;
    private final String providerName;
    private final HibernateSessionManager sessions;
    private final DatabaseSettings databaseSettings;
    private volatile boolean available;

    private CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    /**
     * Creates a new HibernateStorageProvider.
     *
     * @param plugin       the plugin instance
     * @param executor     the dedicated storage executor
     * @param providerName the provider name ({@code sqlite} or {@code mysql})
     */
    public HibernateStorageProvider(VillagesPlugin plugin, Executor executor, String providerName) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.executor = executor;
        this.providerName = providerName;
        this.databaseSettings = plugin.getConfigManager().getDatabaseSettings();
        this.sessions = new HibernateSessionManager(
                databaseSettings,
                List.of(VillageEntity.class, VillagePoiEntity.class,
                        VillageEntranceEntity.class, VillageHeroEntity.class),
                executor,
                plugin.getLogger());
        this.available = false;
    }

    @Override
    public String getName() {
        return providerName;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return runAsync(() -> {
            try {
                FlywayMigrator.migrate(plugin.getClass().getClassLoader(),
                        databaseSettings, plugin.getLogger());
                sessions.getSessionFactory();
                available = true;
                logger.info(LogCategory.STORAGE,
                        providerName.toUpperCase() + " storage initialized via Hibernate/Flyway");
                logger.debugStorage(providerName + " Hibernate SessionFactory ready");
            } catch (Exception e) {
                throw new StorageException("Failed to initialize " + providerName + " database", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        available = false;
        sessions.shutdown();
        logger.info(LogCategory.STORAGE, providerName.toUpperCase() + " Hibernate storage shut down");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isAvailable() {
        return available && !sessions.isShuttingDown();
    }

    // ==================== Village CRUD Operations ====================

    @Override
    public CompletableFuture<Void> saveVillage(Village village) {
        return sessions.executeTransactionVoid(session -> {
            logger.debugStorage("Saving village " + village.getId() + " to " + providerName + " storage");
            VillageEntity entity = toEntity(village);
            session.merge(entity);
            logger.debugStorage("Village " + village.getId() + " saved successfully to " + providerName + " storage");
        });
    }

    @Override
    public CompletableFuture<Optional<Village>> loadVillage(UUID id) {
        return supplyAsync(() -> {
            logger.debugStorage("Loading village " + id + " from " + providerName + " storage");
            try (Session session = sessions.getSessionFactory().openSession()) {
                VillageEntity entity = session.get(VillageEntity.class, id.toString());
                if (entity != null) {
                    logger.debugStorage("Village " + id + " loaded successfully from " + providerName + " storage");
                    return Optional.of(toVillage(entity));
                }
            }
            logger.debugStorage("Village " + id + " not found in " + providerName + " storage");
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<Village>> loadVillageByBell(String worldName, int x, int y, int z) {
        return supplyAsync(() -> {
            logger.debugStorage("Loading village by bell location: " + worldName + " " + x + ", " + y + ", " + z);
            try (Session session = sessions.getSessionFactory().openSession()) {
                VillageEntity entity = session.createQuery(
                                "FROM VillageEntity v WHERE v.world = :world "
                                        + "AND v.bellX = :x AND v.bellY = :y AND v.bellZ = :z",
                                VillageEntity.class)
                        .setParameter("world", worldName)
                        .setParameter("x", x)
                        .setParameter("y", y)
                        .setParameter("z", z)
                        .setMaxResults(1)
                        .uniqueResult();
                if (entity != null) {
                    logger.debugStorage("Found village by bell location");
                    return Optional.of(toVillage(entity));
                }
            }
            logger.debugStorage("No village found by bell location");
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<Village>> loadVillageByChunk(String worldName, int chunkX, int chunkZ) {
        return supplyAsync(() -> {
            int minX = chunkX << 4;
            int maxX = minX + 15;
            int minZ = chunkZ << 4;
            int maxZ = minZ + 15;

            try (Session session = sessions.getSessionFactory().openSession()) {
                VillageEntity entity = session.createQuery(
                                "FROM VillageEntity v WHERE v.world = :world "
                                        + "AND v.bellX >= :minX AND v.bellX <= :maxX "
                                        + "AND v.bellZ >= :minZ AND v.bellZ <= :maxZ "
                                        + "ORDER BY v.id",
                                VillageEntity.class)
                        .setParameter("world", worldName)
                        .setParameter("minX", minX)
                        .setParameter("maxX", maxX)
                        .setParameter("minZ", minZ)
                        .setParameter("maxZ", maxZ)
                        .setMaxResults(1)
                        .uniqueResult();
                if (entity != null) {
                    return Optional.of(toVillage(entity));
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<Village>> loadVillagesInWorld(World world) {
        return loadVillagesInWorld(world.getName());
    }

    @Override
    public CompletableFuture<List<Village>> loadVillagesInWorld(String worldName) {
        return supplyAsync(() -> {
            try (Session session = sessions.getSessionFactory().openSession()) {
                List<VillageEntity> entities = session.createQuery(
                                "FROM VillageEntity v WHERE v.world = :world",
                                VillageEntity.class)
                        .setParameter("world", worldName)
                        .list();
                return entities.stream().map(this::toVillage).toList();
            }
        });
    }

    @Override
    public CompletableFuture<List<Village>> loadAllVillages() {
        return supplyAsync(() -> {
            try (Session session = sessions.getSessionFactory().openSession()) {
                List<VillageEntity> entities = session.createQuery(
                                "FROM VillageEntity v",
                                VillageEntity.class)
                        .list();
                return entities.stream().map(this::toVillage).toList();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteVillage(UUID id) {
        return sessions.executeTransaction(session -> {
            logger.debugStorage("Deleting village " + id + " from " + providerName + " storage");
            VillageEntity entity = session.get(VillageEntity.class, id.toString());
            if (entity != null) {
                session.remove(entity);
                logger.debugStorage("Village " + id + " deleted successfully from " + providerName + " storage");
                return true;
            }
            logger.debugStorage("Village " + id + " not found for deletion");
            return false;
        });
    }

    @Override
    public CompletableFuture<Boolean> villageExists(UUID id) {
        return supplyAsync(() -> {
            try (Session session = sessions.getSessionFactory().openSession()) {
                Long count = session.createQuery(
                                "SELECT COUNT(v) FROM VillageEntity v WHERE v.id = :id",
                                Long.class)
                        .setParameter("id", id.toString())
                        .uniqueResult();
                return count != null && count > 0;
            }
        });
    }

    // ==================== Utility Operations ====================

    @Override
    public CompletableFuture<Integer> getVillageCount() {
        return supplyAsync(() -> {
            try (Session session = sessions.getSessionFactory().openSession()) {
                Long count = session.createQuery(
                                "SELECT COUNT(v) FROM VillageEntity v",
                                Long.class)
                        .uniqueResult();
                return count != null ? count.intValue() : 0;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getVillageCount(String worldName) {
        return supplyAsync(() -> {
            try (Session session = sessions.getSessionFactory().openSession()) {
                Long count = session.createQuery(
                                "SELECT COUNT(v) FROM VillageEntity v WHERE v.world = :world",
                                Long.class)
                        .setParameter("world", worldName)
                        .uniqueResult();
                return count != null ? count.intValue() : 0;
            }
        });
    }

    @Override
    public CompletableFuture<List<Village>> findVillagesNear(String worldName, int x, int z, int radius) {
        return supplyAsync(() -> {
            int radiusSq = radius * radius;
            List<Village> villages = new ArrayList<>();

            try (Session session = sessions.getSessionFactory().openSession()) {
                List<VillageEntity> entities = session.createQuery(
                                "FROM VillageEntity v WHERE v.world = :world "
                                        + "AND v.bellX BETWEEN :minX AND :maxX "
                                        + "AND v.bellZ BETWEEN :minZ AND :maxZ",
                                VillageEntity.class)
                        .setParameter("world", worldName)
                        .setParameter("minX", x - radius)
                        .setParameter("maxX", x + radius)
                        .setParameter("minZ", z - radius)
                        .setParameter("maxZ", z + radius)
                        .list();

                for (VillageEntity entity : entities) {
                    int dx = entity.getBellX() - x;
                    int dz = entity.getBellZ() - z;
                    if (dx * dx + dz * dz <= radiusSq) {
                        villages.add(toVillage(entity));
                    }
                }
            }
            return villages;
        });
    }

    @Override
    public CompletableFuture<Optional<Village>> findVillageAt(String worldName, int x, int y, int z) {
        return supplyAsync(() -> {
            try (Session session = sessions.getSessionFactory().openSession()) {
                VillageEntity entity = session.createQuery(
                                "FROM VillageEntity v WHERE v.world = :world "
                                        + "AND v.minX IS NOT NULL "
                                        + "AND :x BETWEEN v.minX AND v.maxX "
                                        + "AND :y BETWEEN v.minY AND v.maxY "
                                        + "AND :z BETWEEN v.minZ AND v.maxZ "
                                        + "ORDER BY v.id",
                                VillageEntity.class)
                        .setParameter("world", worldName)
                        .setParameter("x", x)
                        .setParameter("y", y)
                        .setParameter("z", z)
                        .setMaxResults(1)
                        .uniqueResult();
                if (entity != null) {
                    return Optional.of(toVillage(entity));
                }
            }
            return Optional.empty();
        });
    }

    // ==================== Backup and Migration ====================

    @Override
    public CompletableFuture<Void> backup(String backupPath) {
        return runAsync(() -> {
            try {
                File backupFile = new File(backupPath);
                try (Session session = sessions.getSessionFactory().openSession();
                     Connection conn = session.doReturningWork(connection -> connection);
                     FileWriter writer = new FileWriter(backupFile)) {

                    writer.write("-- Villages plugin " + providerName.toUpperCase() + " backup\n");
                    writer.write("-- Generated: " + Instant.now() + "\n\n");

                    String prefix = databaseSettings.tablePrefix();
                    exportTable(conn, writer, prefix + "villages");
                    exportTable(conn, writer, prefix + "village_pois");
                    exportTable(conn, writer, prefix + "village_entrances");
                    exportTable(conn, writer, prefix + "village_heroes");

                    logger.info(LogCategory.STORAGE,
                            "Created " + providerName.toUpperCase() + " backup at: " + backupPath);
                    logger.debugStorage(providerName + " backup created: " + backupPath);
                }
            } catch (Exception e) {
                throw new StorageException("Failed to create backup", e);
            }
        });
    }

    private void exportTable(Connection conn, FileWriter writer, String tableName)
            throws IOException, java.sql.SQLException {
        writer.write("-- Table: " + tableName + "\n");
        String query = "SELECT * FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                StringBuilder insert = new StringBuilder("INSERT INTO " + tableName + " VALUES (");
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) {
                        insert.append(", ");
                    }
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
        return supplyAsync(() -> {
            int count = 0;
            for (Village village : villages) {
                try {
                    if (overwrite) {
                        saveVillage(village).join();
                        count++;
                    } else if (!villageExists(village.getId()).join()) {
                        saveVillage(village).join();
                        count++;
                    }
                } catch (Exception e) {
                    logger.warning(LogCategory.STORAGE,
                            "Failed to import village: " + village.getId(), e);
                }
            }
            return count;
        });
    }

    // ==================== Entity Conversion ====================

    private VillageEntity toEntity(Village village) {
        VillageEntity entity = new VillageEntity();
        entity.setId(village.getId().toString());
        entity.setWorld(village.getWorldName());
        entity.setName(village.getName());
        entity.setBellX(village.getBellX());
        entity.setBellY(village.getBellY());
        entity.setBellZ(village.getBellZ());

        VillageBoundary boundary = village.getBoundary();
        if (boundary != null) {
            entity.setMinX(boundary.getMinX());
            entity.setMinY(boundary.getMinY());
            entity.setMinZ(boundary.getMinZ());
            entity.setMaxX(boundary.getMaxX());
            entity.setMaxY(boundary.getMaxY());
            entity.setMaxZ(boundary.getMaxZ());
            entity.setCenterX(boundary.getCenterX());
            entity.setCenterY(boundary.getCenterY());
            entity.setCenterZ(boundary.getCenterZ());
        }

        entity.setRegionId(village.getRegionId());
        entity.setMayorId(village.getMayorId() != null ? village.getMayorId().toString() : null);
        entity.setCouncilMembers(serializeUuidList(village.getCouncilMembers()));
        entity.setCreatedAt(village.getCreatedAt().toString());
        entity.setUpdatedAt(village.getUpdatedAt().toString());

        for (VillagePoi poi : village.getPois()) {
            VillagePoiEntity poiEntity = new VillagePoiEntity();
            poiEntity.setPoiType(poi.getTypeId());
            poiEntity.setX(poi.getX());
            poiEntity.setY(poi.getY());
            poiEntity.setZ(poi.getZ());
            entity.addPoi(poiEntity);
        }

        for (VillageEntrance entrance : village.getEntrances()) {
            VillageEntranceEntity entranceEntity = new VillageEntranceEntity();
            entranceEntity.setX(entrance.getX());
            entranceEntity.setY(entrance.getY());
            entranceEntity.setZ(entrance.getZ());
            entranceEntity.setFacing(entrance.getFacingName());
            entranceEntity.setAutoDetected(entrance.isAutoDetected());
            entity.addEntrance(entranceEntity);
        }

        for (VillageHero hero : village.getHeroes()) {
            VillageHeroEntity heroEntity = new VillageHeroEntity();
            heroEntity.setPlayerId(hero.playerId().toString());
            heroEntity.setEarnedAt(hero.earnedAt().toString());
            heroEntity.setRaidLevel(hero.raidLevel());
            heroEntity.setDefenseCount(hero.defenseCount());
            entity.addHero(heroEntity);
        }

        return entity;
    }

    private Village toVillage(VillageEntity entity) {
        UUID id = UUID.fromString(entity.getId());
        VillageBoundary boundary = null;
        if (entity.getMinX() != null) {
            boundary = new VillageBoundary(
                    entity.getMinX(),
                    entity.getMinY(),
                    entity.getMinZ(),
                    entity.getMaxX(),
                    entity.getMaxY(),
                    entity.getMaxZ(),
                    entity.getCenterX(),
                    entity.getCenterY(),
                    entity.getCenterZ()
            );
        }

        UUID mayorId = (entity.getMayorId() != null && !entity.getMayorId().isEmpty())
                ? UUID.fromString(entity.getMayorId()) : null;

        Village village = new Village(
                id,
                entity.getWorld(),
                entity.getName(),
                entity.getBellX(),
                entity.getBellY(),
                entity.getBellZ(),
                boundary,
                entity.getRegionId(),
                mayorId,
                parseInstant(entity.getCreatedAt()),
                parseInstant(entity.getUpdatedAt())
        );

        String councilJson = entity.getCouncilMembers();
        if (councilJson != null && !councilJson.isEmpty()) {
            village.setCouncilMembers(deserializeUuidList(councilJson));
        }

        List<VillagePoi> pois = entity.getPois().stream()
                .map(p -> new VillagePoi(p.getPoiType(), p.getX(), p.getY(), p.getZ()))
                .toList();
        village.setPois(pois);

        List<VillageEntrance> entrances = entity.getEntrances().stream()
                .map(e -> new VillageEntrance(
                        e.getX(),
                        e.getY(),
                        e.getZ(),
                        VillageEntrance.faceFromString(e.getFacing()),
                        e.isAutoDetected()))
                .toList();
        village.setEntrances(entrances);

        List<VillageHero> heroes = new ArrayList<>();
        for (VillageHeroEntity heroEntity : entity.getHeroes()) {
            VillageHero hero = VillageHero.fromStorage(
                    UUID.fromString(heroEntity.getPlayerId()),
                    parseInstant(heroEntity.getEarnedAt()),
                    heroEntity.getRaidLevel(),
                    heroEntity.getDefenseCount());
            if (hero != null) {
                heroes.add(hero);
            }
        }
        village.setHeroes(heroes);

        return village;
    }

    private String serializeUuidList(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < uuids.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(uuids.get(i).toString()).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<UUID> deserializeUuidList(String json) {
        List<UUID> uuids = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return uuids;
        }
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
                            logger.warning(LogCategory.STORAGE,
                                    "Invalid UUID in council list: " + uuid);
                        }
                    }
                }
            }
        }
        return uuids;
    }

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

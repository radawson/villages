-- Villages plugin initial schema (SQLite and MySQL compatible)

CREATE TABLE ${tablePrefix}villages (
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
    updated_at VARCHAR(64) NOT NULL
);

CREATE INDEX idx_${tablePrefix}villages_world ON ${tablePrefix}villages(world);
CREATE INDEX idx_${tablePrefix}villages_bell ON ${tablePrefix}villages(world, bell_x, bell_y, bell_z);

CREATE TABLE ${tablePrefix}village_pois (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    village_id VARCHAR(36) NOT NULL,
    poi_type VARCHAR(32) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    FOREIGN KEY (village_id) REFERENCES ${tablePrefix}villages(id) ON DELETE CASCADE
);

CREATE INDEX idx_${tablePrefix}village_pois_village ON ${tablePrefix}village_pois(village_id);

CREATE TABLE ${tablePrefix}village_entrances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    village_id VARCHAR(36) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    facing VARCHAR(16) NOT NULL,
    auto_detected BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (village_id) REFERENCES ${tablePrefix}villages(id) ON DELETE CASCADE
);

CREATE INDEX idx_${tablePrefix}village_entrances_village ON ${tablePrefix}village_entrances(village_id);

CREATE TABLE ${tablePrefix}village_heroes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    village_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    earned_at VARCHAR(64) NOT NULL,
    raid_level INT NOT NULL,
    defense_count INT NOT NULL,
    FOREIGN KEY (village_id) REFERENCES ${tablePrefix}villages(id) ON DELETE CASCADE,
    UNIQUE (village_id, player_id)
);

CREATE INDEX idx_${tablePrefix}village_heroes_village ON ${tablePrefix}village_heroes(village_id);

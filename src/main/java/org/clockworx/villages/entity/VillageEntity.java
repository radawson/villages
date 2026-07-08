package org.clockworx.villages.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a village stored in the database.
 * Maps to the {@code villages} table (with optional table prefix from config).
 */
@Entity
@Table(name = "villages")
public class VillageEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 64)
    private String world;

    @Column(length = 64)
    private String name;

    @Column(name = "bell_x", nullable = false)
    private int bellX;

    @Column(name = "bell_y", nullable = false)
    private int bellY;

    @Column(name = "bell_z", nullable = false)
    private int bellZ;

    @Column(name = "min_x")
    private Integer minX;

    @Column(name = "min_y")
    private Integer minY;

    @Column(name = "min_z")
    private Integer minZ;

    @Column(name = "max_x")
    private Integer maxX;

    @Column(name = "max_y")
    private Integer maxY;

    @Column(name = "max_z")
    private Integer maxZ;

    @Column(name = "center_x")
    private Integer centerX;

    @Column(name = "center_y")
    private Integer centerY;

    @Column(name = "center_z")
    private Integer centerZ;

    @Column(name = "region_id", length = 64)
    private String regionId;

    @Column(name = "mayor_id", length = 36)
    private String mayorId;

    @Column(name = "council_members", columnDefinition = "TEXT")
    private String councilMembers;

    @Column(name = "created_at", nullable = false, length = 64)
    private String createdAt;

    @Column(name = "updated_at", nullable = false, length = 64)
    private String updatedAt;

    @OneToMany(mappedBy = "village", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<VillagePoiEntity> pois = new ArrayList<>();

    @OneToMany(mappedBy = "village", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<VillageEntranceEntity> entrances = new ArrayList<>();

    @OneToMany(mappedBy = "village", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<VillageHeroEntity> heroes = new ArrayList<>();

    public VillageEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getBellX() {
        return bellX;
    }

    public void setBellX(int bellX) {
        this.bellX = bellX;
    }

    public int getBellY() {
        return bellY;
    }

    public void setBellY(int bellY) {
        this.bellY = bellY;
    }

    public int getBellZ() {
        return bellZ;
    }

    public void setBellZ(int bellZ) {
        this.bellZ = bellZ;
    }

    public Integer getMinX() {
        return minX;
    }

    public void setMinX(Integer minX) {
        this.minX = minX;
    }

    public Integer getMinY() {
        return minY;
    }

    public void setMinY(Integer minY) {
        this.minY = minY;
    }

    public Integer getMinZ() {
        return minZ;
    }

    public void setMinZ(Integer minZ) {
        this.minZ = minZ;
    }

    public Integer getMaxX() {
        return maxX;
    }

    public void setMaxX(Integer maxX) {
        this.maxX = maxX;
    }

    public Integer getMaxY() {
        return maxY;
    }

    public void setMaxY(Integer maxY) {
        this.maxY = maxY;
    }

    public Integer getMaxZ() {
        return maxZ;
    }

    public void setMaxZ(Integer maxZ) {
        this.maxZ = maxZ;
    }

    public Integer getCenterX() {
        return centerX;
    }

    public void setCenterX(Integer centerX) {
        this.centerX = centerX;
    }

    public Integer getCenterY() {
        return centerY;
    }

    public void setCenterY(Integer centerY) {
        this.centerY = centerY;
    }

    public Integer getCenterZ() {
        return centerZ;
    }

    public void setCenterZ(Integer centerZ) {
        this.centerZ = centerZ;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getMayorId() {
        return mayorId;
    }

    public void setMayorId(String mayorId) {
        this.mayorId = mayorId;
    }

    public String getCouncilMembers() {
        return councilMembers;
    }

    public void setCouncilMembers(String councilMembers) {
        this.councilMembers = councilMembers;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<VillagePoiEntity> getPois() {
        return pois;
    }

    public void setPois(List<VillagePoiEntity> pois) {
        this.pois = pois;
    }

    public List<VillageEntranceEntity> getEntrances() {
        return entrances;
    }

    public void setEntrances(List<VillageEntranceEntity> entrances) {
        this.entrances = entrances;
    }

    public List<VillageHeroEntity> getHeroes() {
        return heroes;
    }

    public void setHeroes(List<VillageHeroEntity> heroes) {
        this.heroes = heroes;
    }

    public void addPoi(VillagePoiEntity poi) {
        pois.add(poi);
        poi.setVillage(this);
    }

    public void addEntrance(VillageEntranceEntity entrance) {
        entrances.add(entrance);
        entrance.setVillage(this);
    }

    public void addHero(VillageHeroEntity hero) {
        heroes.add(hero);
        hero.setVillage(this);
    }
}

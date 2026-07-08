package org.clockworx.villages.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity representing a village hero record stored in the database.
 * Maps to the {@code village_heroes} table (with optional table prefix from config).
 */
@Entity
@Table(name = "village_heroes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"village_id", "player_id"})
})
public class VillageHeroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "village_id", nullable = false)
    private VillageEntity village;

    @Column(name = "player_id", nullable = false, length = 36)
    private String playerId;

    @Column(name = "earned_at", nullable = false, length = 64)
    private String earnedAt;

    @Column(name = "raid_level", nullable = false)
    private int raidLevel;

    @Column(name = "defense_count", nullable = false)
    private int defenseCount;

    public VillageHeroEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VillageEntity getVillage() {
        return village;
    }

    public void setVillage(VillageEntity village) {
        this.village = village;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getEarnedAt() {
        return earnedAt;
    }

    public void setEarnedAt(String earnedAt) {
        this.earnedAt = earnedAt;
    }

    public int getRaidLevel() {
        return raidLevel;
    }

    public void setRaidLevel(int raidLevel) {
        this.raidLevel = raidLevel;
    }

    public int getDefenseCount() {
        return defenseCount;
    }

    public void setDefenseCount(int defenseCount) {
        this.defenseCount = defenseCount;
    }
}

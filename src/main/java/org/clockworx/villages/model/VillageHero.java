package org.clockworx.villages.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player who has earned the "Hero of the Village" status
 * by defending the village from an illager raid.
 * 
 * Each hero record tracks:
 * - The player who earned the status
 * - When they earned it
 * - The raid level they defended against
 * - How many times they've defended (cumulative)
 * 
 * This data can be used for:
 * - Village leaderboards
 * - Granting special permissions or rewards
 * - Historical tracking of village defenders
 * 
 * @author Clockworx
 * @since 0.2.1
 */
public record VillageHero(
    /** UUID of the player who earned Hero of the Village */
    UUID playerId,
    
    /** Timestamp when the hero status was first earned */
    Instant earnedAt,
    
    /** The raid level (1-5) that was defended against */
    int raidLevel,
    
    /** Total number of successful raid defenses by this player */
    int defenseCount
) {
    
    /**
     * Creates a new VillageHero record.
     * 
     * @param playerId The player's UUID
     * @param earnedAt When the status was earned
     * @param raidLevel The raid level defended (1-5)
     * @param defenseCount Number of successful defenses
     */
    public VillageHero {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(earnedAt, "Earned timestamp cannot be null");
        if (raidLevel < 1 || raidLevel > 5) {
            throw new IllegalArgumentException("Raid level must be between 1 and 5, got: " + raidLevel);
        }
        if (defenseCount < 1) {
            throw new IllegalArgumentException("Defense count must be at least 1, got: " + defenseCount);
        }
    }
    
    /**
     * Creates a new hero record for a first-time defender.
     * 
     * @param playerId The player's UUID
     * @param raidLevel The raid level defended
     * @return A new VillageHero with defense count of 1
     */
    public static VillageHero firstDefense(UUID playerId, int raidLevel) {
        return new VillageHero(playerId, Instant.now(), raidLevel, 1);
    }
    
    /**
     * Creates a new hero record with an incremented defense count.
     * Updates the raid level to the new value if higher.
     * 
     * @param newRaidLevel The raid level of the new defense
     * @return A new VillageHero with updated stats
     */
    public VillageHero withAdditionalDefense(int newRaidLevel) {
        return new VillageHero(
            playerId,
            earnedAt,
            Math.max(raidLevel, newRaidLevel),
            defenseCount + 1
        );
    }
    
    /**
     * Creates a VillageHero from storage data.
     * Used when loading from database or YAML.
     * 
     * @param playerId The player's UUID
     * @param earnedAt When the status was earned
     * @param raidLevel The highest raid level defended
     * @param defenseCount Total defense count
     * @return A new VillageHero, or null if data is invalid
     */
    public static VillageHero fromStorage(UUID playerId, Instant earnedAt, int raidLevel, int defenseCount) {
        if (playerId == null || earnedAt == null) {
            return null;
        }
        // Clamp values to valid ranges for data loaded from storage
        int clampedRaidLevel = Math.max(1, Math.min(5, raidLevel));
        int clampedDefenseCount = Math.max(1, defenseCount);
        return new VillageHero(playerId, earnedAt, clampedRaidLevel, clampedDefenseCount);
    }
}

package com.fantasy.espn.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One ESPN player's season stat line, limited to the stats Yahoo does not report. Keyed by
 * ESPN's own player id; {@code fullName} and {@code position} exist so the BFF can match the
 * row to the Yahoo-sourced player read model.
 */
@Entity
@Table(name = "espn_player_stats")
public class EspnPlayerStats {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "position", nullable = false, length = 4)
    private String position;

    @Column(name = "sweater_number")
    private Integer sweaterNumber;

    @Column(name = "games_played")
    private Integer gamesPlayed;

    @Column(name = "hat_tricks")
    private Integer hatTricks;

    @Column(name = "shifts")
    private Integer shifts;

    @Column(name = "overtime_losses")
    private Integer overtimeLosses;

    @Column(name = "time_on_ice")
    private Integer timeOnIce;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public EspnPlayerStats() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Integer getSweaterNumber() {
        return sweaterNumber;
    }

    public void setSweaterNumber(Integer sweaterNumber) {
        this.sweaterNumber = sweaterNumber;
    }

    public Integer getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(Integer gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public Integer getHatTricks() {
        return hatTricks;
    }

    public void setHatTricks(Integer hatTricks) {
        this.hatTricks = hatTricks;
    }

    public Integer getShifts() {
        return shifts;
    }

    public void setShifts(Integer shifts) {
        this.shifts = shifts;
    }

    public Integer getOvertimeLosses() {
        return overtimeLosses;
    }

    public void setOvertimeLosses(Integer overtimeLosses) {
        this.overtimeLosses = overtimeLosses;
    }

    public Integer getTimeOnIce() {
        return timeOnIce;
    }

    public void setTimeOnIce(Integer timeOnIce) {
        this.timeOnIce = timeOnIce;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }
}

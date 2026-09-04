package com.beautysalonapp.license.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Güncelleme kanalı kaydı (§5.5). {@code /api/v1/updates/latest} bunu döndürür. */
@Entity
@Table(name = "app_release", indexes = @Index(name = "ix_release_channel", columnList = "channel,releasedAt"))
public class AppRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String channel = "stable";

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 400)
    private String url;

    @Column(length = 128)
    private String checksum;

    @Column(nullable = false)
    private boolean mandatory = false;

    @Column(nullable = false)
    private Instant releasedAt = Instant.now();

    @Column(length = 1000)
    private String notes;

    public Long getId() { return id; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public Instant getReleasedAt() { return releasedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

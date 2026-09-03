package com.beautysalonapp.backup.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/** Yedek arşivi içindeki {@code manifest.json} (§11.1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupManifest(
        int format,
        String appVersion,
        String schemaVersion,
        String dbType,
        String trigger,
        Instant createdAt,
        /** Arşiv girdisi → SHA-256 (hex). */
        Map<String, String> checksums,
        long uncompressedBytes
) {
    public static final int CURRENT_FORMAT = 1;
}

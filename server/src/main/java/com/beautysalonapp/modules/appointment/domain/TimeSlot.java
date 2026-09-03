package com.beautysalonapp.modules.appointment.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Zaman aralığı — çakışma kontrolü için saf domain (plan §10.10).
 * {@code [start, end)} yarı açık aralık: bitişik randevular çakışmaz.
 */
public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start ve end zorunlu");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Bitiş, başlangıçtan sonra olmalı");
        }
    }

    public static TimeSlot of(Instant start, int durationMinutes) {
        return new TimeSlot(start, start.plus(Duration.ofMinutes(durationMinutes)));
    }

    /** Hazırlık/temizlik payını iki uca ekleyerek genişletilmiş pencere. */
    public TimeSlot withBuffers(int bufferBeforeMin, int bufferAfterMin) {
        return new TimeSlot(
                start.minus(Duration.ofMinutes(Math.max(0, bufferBeforeMin))),
                end.plus(Duration.ofMinutes(Math.max(0, bufferAfterMin))));
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }
}

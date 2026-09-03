package com.beautysalonapp.modules.contract.application;

/**
 * Seans paketinden seans düşme portu (CLAUDE.md #5). Randevu modülü {@code GELDI}'de kullanır.
 */
public interface SessionConsumptionPort {

    /** İlgili sözleşme satırında {@code session_used}'ı 1 artırır (paket sınırını aşamaz). */
    void consumeSession(long contractLineId);

    /** Randevu iptal/geri alınırsa seansı iade eder. */
    void restoreSession(long contractLineId);

    /** Satırda kalan seans sayısı (session_count - session_used). */
    int remainingSessions(long contractLineId);
}

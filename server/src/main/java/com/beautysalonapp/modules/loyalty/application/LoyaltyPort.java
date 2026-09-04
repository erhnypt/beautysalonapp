package com.beautysalonapp.modules.loyalty.application;

import com.beautysalonapp.core.domain.Money;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Sadakat portu (CLAUDE.md #5). Fatura ve randevu modülleri satıştan otomatik puan
 * kazanımını bununla tetikler. Kart yoksa işlem sessizce no-op'tur.
 */
public interface LoyaltyPort {

    /** Harcamadan puan kazandırır; kazanılan puanı döndürür (kart yoksa 0). İdempotent (sourceRef). */
    int accrueFromSale(long partyId, BigDecimal spendAmount, String sourceRef);

    Optional<CardInfo> cardForParty(long partyId);

    /** Puanla ödeme: puanları düşer, TL karşılığını döndürür. */
    Money redeem(long cardId, int points, String sourceRef);

    record CardInfo(long cardId, String cardNo, long partyId, int pointsBalance, BigDecimal pointValue) {}
}

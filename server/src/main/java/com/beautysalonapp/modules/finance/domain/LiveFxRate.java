package com.beautysalonapp.modules.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dış kaynaktan çekilen anlık döviz kuru (plan §10.6 destekleyici). Kalıcı değildir;
 * yalnızca döviz işlemi ekranında "Kur" alanına öneri olarak sunulur. Operatör onaylar.
 *
 * @param symbol   ör. {@code "USD/TRY"}
 * @param rate     1 birim yabancı para = {@code rate} TRY (NUMERIC(19,6) ile uyumlu ölçek)
 * @param asOf     kaynağın bildirdiği fiyat zamanı (UTC)
 * @param source   insan-okur kaynak adı, ör. {@code "Yahoo Finance"}
 */
public record LiveFxRate(String symbol, BigDecimal rate, Instant asOf, String source) {
}

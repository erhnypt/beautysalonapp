package com.beautysalonapp.modules.reconciliation.domain;

/**
 * Bir ekstre satırı için önerilen kasa hareketi eşleşmesi.
 * {@code score} 0–100; büyük = daha güçlü öneri.
 */
public record MatchCandidate(long txnId, int score, String reason) implements Comparable<MatchCandidate> {

    @Override
    public int compareTo(MatchCandidate o) {
        return Integer.compare(o.score, this.score); // azalan
    }
}

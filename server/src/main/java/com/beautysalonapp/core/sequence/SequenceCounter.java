package com.beautysalonapp.core.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Belge numarası üreteci (§9.1). Şube × tür × yıl bazında artan sayaç.
 * Her şubenin kendi serisi olduğu için çok şubeli birleştirmede çakışma olmaz.
 */
@Entity
@Table(name = "sequence_counter", uniqueConstraints =
        @UniqueConstraint(name = "uq_seq", columnNames = {"branch_id", "seq_type", "seq_year"}))
public class SequenceCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** ör. INVOICE_SATIS, VOUCHER, CONTRACT, RECEIPT. */
    @Column(name = "seq_type", nullable = false, length = 40)
    private String type;

    @Column(name = "seq_year", nullable = false)
    private int year;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    /** Numaranın önüne konacak sabit (ör. "A", "PS"). */
    @Column(name = "prefix", length = 10)
    private String prefix;

    protected SequenceCounter() {
    }

    public SequenceCounter(Long branchId, String type, int year, String prefix) {
        this.branchId = branchId;
        this.type = type;
        this.year = year;
        this.prefix = prefix;
        this.currentValue = 0;
    }

    public long next() {
        return ++currentValue;
    }

    public Long getId() { return id; }
    public Long getBranchId() { return branchId; }
    public String getType() { return type; }
    public int getYear() { return year; }
    public long getCurrentValue() { return currentValue; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
}

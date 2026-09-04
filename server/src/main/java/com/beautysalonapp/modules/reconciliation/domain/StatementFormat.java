package com.beautysalonapp.modules.reconciliation.domain;

/** Desteklenen banka ekstresi biçimleri. */
public enum StatementFormat {
    /** SWIFT MT940 (çoğu Türk bankasının kurumsal ekstre indirmesi). */
    MT940,
    /** Serbest CSV; sütun eşlemesi {@link CsvLayout} ile verilir. */
    CSV
}

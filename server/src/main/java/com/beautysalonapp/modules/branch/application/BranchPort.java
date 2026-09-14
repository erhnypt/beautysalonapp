package com.beautysalonapp.modules.branch.application;

/**
 * Şube portu (CLAUDE.md #5). Diğer modüller şube varlığını yalnızca bununla doğrular
 * — {@code BranchRepository}'ye doğrudan erişmezler.
 */
public interface BranchPort {

    /** Silinmemiş bir şube mi? */
    boolean exists(long branchId);
}

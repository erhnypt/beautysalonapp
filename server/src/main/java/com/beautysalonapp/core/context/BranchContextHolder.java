package com.beautysalonapp.core.context;

/**
 * İsteğe bağlı, iş parçacığı bazlı "aktif şube" bağlamı (Faz 8 tam şube izolasyonu,
 * bkz. docs/adr/0006-merkezi-sube.md). Modülsüz, saf: {@code core} paketinde durur ki
 * {@link com.beautysalonapp.core.domain.BaseEntity} (ve herhangi bir modül) {@code modules.branch}'e
 * bağımlı olmadan bunu okuyabilsin (CLAUDE.md #5).
 *
 * <p>{@code null} = bağlam yok (istek başlığı gönderilmedi, arka plan işi, test) → çağıran taraf
 * varsayılan olarak {@code 1L} (v1 tek şube / merkez) kullanmalıdır. Bir HTTP isteği bağlamında
 * {@code BranchContextFilter} tarafından set edilir; istek bitince mutlaka temizlenir.
 */
public final class BranchContextHolder {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private BranchContextHolder() {
    }

    public static Long get() {
        return CURRENT.get();
    }

    /** {@link #get()} {@code null} ise {@code 1L} (v1 tek şube / merkez) döner. */
    public static long getOrDefault() {
        Long v = CURRENT.get();
        return v != null ? v : 1L;
    }

    public static void set(Long branchId) {
        CURRENT.set(branchId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}

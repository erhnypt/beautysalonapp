// Faz 8 tam şube izolasyonu (ADR 0006): tarayıcıda seçilen "aktif şube"yi tutar ve
// api.ts'nin her isteğe X-Branch-Id başlığı eklemesi için okunur. Seçim yalnızca bu
// tarayıcıya özeldir (localStorage); sunucu tarafında bir varsayılan değişmez.

const STORAGE_KEY = "beauty.activeBranchId";
const CHANGE_EVENT = "beauty:active-branch-changed";

export function getActiveBranchId(): number | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? n : null;
  } catch {
    return null;
  }
}

/** {@code null} = merkez şube / v1 tek şube davranışı (başlık gönderilmez). */
export function setActiveBranchId(id: number | null): void {
  try {
    if (id == null) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, String(id));
    }
  } catch {
    // localStorage kapalıysa (gizli sekme vb.) sessizce yok say; varsayılan şube kullanılır.
  }
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: id }));
}

export function onActiveBranchChange(handler: (id: number | null) => void): () => void {
  const listener = (e: Event) => handler((e as CustomEvent<number | null>).detail);
  window.addEventListener(CHANGE_EVENT, listener);
  return () => window.removeEventListener(CHANGE_EVENT, listener);
}

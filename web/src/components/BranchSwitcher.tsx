import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Building2 } from "lucide-react";
import { api } from "../lib/api";
import { getActiveBranchId, setActiveBranchId } from "../lib/branch";

interface BranchOption {
  id: number;
  code: string;
  title: string;
  headquarters: boolean;
}

/**
 * Faz 8 tam şube izolasyonu (ADR 0006): kullanıcının işlem yaptığı "aktif şube"yi seçer.
 * Birden fazla şube yoksa (v1 tek şube kurulumu) hiçbir şey göstermez. Seçim değişince
 * sayfa yenilenir — TanStack Query önbelleğinde şubesiz alınmış veri kalmasın diye
 * (basit ve güvenli; v2'de sorgu bazlı geçersizleştirmeyle iyileştirilebilir).
 */
export function BranchSwitcher() {
  const [active, setActive] = useState<number | null>(() => getActiveBranchId());

  const branches = useQuery({
    queryKey: ["branches"],
    queryFn: () => api<BranchOption[]>("/api/v1/branches"),
    staleTime: 5 * 60 * 1000,
  });

  useEffect(() => {
    setActive(getActiveBranchId());
  }, []);

  if (!branches.data || branches.data.length <= 1) {
    return null;
  }

  // Sunucu tarafı varsayılan (X-Branch-Id gönderilmediğinde) her zaman şube 1'dir — bu,
  // "merkez" bayrağı başka bir şubeye taşınsa bile değişmez (bkz. BaseEntity, ADR 0006).
  const defaultBranch = branches.data.find((b) => b.id === 1);
  const defaultLabel = defaultBranch ? `${defaultBranch.title} (varsayılan)` : "Varsayılan";

  return (
    <div className="px-3 pb-2">
      <label className="mb-1 flex items-center gap-1.5 px-2 text-[11px] font-medium text-slate-500">
        <Building2 size={13} /> Aktif Şube
      </label>
      <select
        className="input py-1.5 text-sm"
        value={active ?? ""}
        onChange={(e) => {
          const id = e.target.value ? Number(e.target.value) : null;
          setActiveBranchId(id);
          window.location.reload();
        }}
      >
        <option value="">{defaultLabel}</option>
        {branches.data
          .filter((b) => b.id !== 1)
          .map((b) => (
            <option key={b.id} value={b.id}>
              {b.title}
            </option>
          ))}
      </select>
    </div>
  );
}

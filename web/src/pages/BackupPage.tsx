import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface BackupStatus {
  lastSuccessfulAt: string | null;
  lastError: string | null;
  totalBackups: number;
  totalBytes: number;
  scheduledEnabled: boolean;
  dir: string;
}
interface BackupFile {
  name: string;
  sizeBytes: number;
  modifiedAt: string;
}

const kb = (n: number) => `${(n / 1024).toFixed(0)} KB`;

export function BackupPage() {
  const { has } = useAuth();
  const qc = useQueryClient();
  const canRun = has("BACKUP_RUN");
  const canRestore = has("BACKUP_RESTORE");
  const fileRef = useRef<HTMLInputElement>(null);
  const [adminPassword, setAdminPassword] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const status = useQuery({ queryKey: ["backup-status"], queryFn: () => api<BackupStatus>("/api/v1/backup/status") });
  const list = useQuery({ queryKey: ["backup-list"], queryFn: () => api<BackupFile[]>("/api/v1/backup/list") });

  const run = useMutation({
    mutationFn: () => api("/api/v1/backup/run", { method: "POST" }),
    onSuccess: () => {
      setMsg("Yedek alındı.");
      qc.invalidateQueries({ queryKey: ["backup-status"] });
      qc.invalidateQueries({ queryKey: ["backup-list"] });
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const verify = useMutation({
    mutationFn: (name: string) => api<{ ok: boolean; message: string }>(`/api/v1/backup/verify/${encodeURIComponent(name)}`, { method: "POST" }),
    onSuccess: (r) => setMsg(r.ok ? "Doğrulama başarılı." : `Doğrulama başarısız: ${r.message}`),
  });

  const restore = useMutation({
    mutationFn: async () => {
      const f = fileRef.current?.files?.[0];
      if (!f) throw new Error("Dosya seçin");
      const fd = new FormData();
      fd.append("file", f);
      fd.append("adminPassword", adminPassword);
      return api("/api/v1/backup/restore", { method: "POST", body: fd });
    },
    onSuccess: () => {
      setMsg("Geri yükleme tamamlandı. Uygulamayı yeniden başlatmanız önerilir.");
      setAdminPassword("");
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Yedekleme"
        actions={
          canRun && (
            <button className="btn-primary" disabled={run.isPending} onClick={() => { setErr(null); setMsg(null); run.mutate(); }}>
              Şimdi Yedek Al
            </button>
          )
        }
      />

      {msg && <div className="mb-3 rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700">{msg}</div>}
      {err && <div className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}

      {status.data && (
        <div className="card mb-4 space-y-1 text-sm">
          <Row label="Son başarılı yedek" value={status.data.lastSuccessfulAt ? new Date(status.data.lastSuccessfulAt).toLocaleString("tr-TR") : "—"} />
          <Row label="Otomatik yedek" value={status.data.scheduledEnabled ? "Açık (her gün 23:00)" : "Kapalı"} />
          <Row label="Yedek klasörü" value={status.data.dir} />
          <Row label="Toplam yedek" value={`${status.data.totalBackups} dosya · ${kb(status.data.totalBytes)}`} />
          {status.data.lastError && <div className="pt-1 text-red-700">Son hata: {status.data.lastError}</div>}
        </div>
      )}

      <div className="card mb-4">
        <h2 className="mb-2 text-sm font-semibold">Yedek dosyaları</h2>
        <table className="w-full text-sm">
          <tbody className="divide-y divide-slate-100">
            {(list.data ?? []).map((f) => (
              <tr key={f.name}>
                <td className="py-1.5 font-mono text-xs">{f.name}</td>
                <td className="py-1.5 text-right text-slate-500">{kb(f.sizeBytes)}</td>
                <td className="py-1.5 text-right text-slate-500">
                  {new Date(f.modifiedAt).toLocaleString("tr-TR")}
                </td>
                <td className="py-1.5 text-right">
                  <button className="text-xs text-brand-700 hover:underline" onClick={() => verify.mutate(f.name)}>
                    Doğrula
                  </button>
                </td>
              </tr>
            ))}
            {(list.data ?? []).length === 0 && (
              <tr>
                <td colSpan={4} className="py-4 text-center text-slate-400">Henüz yedek yok</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {canRestore && (
        <div className="card space-y-3">
          <h2 className="text-sm font-semibold text-red-700">Yedekten Geri Yükle</h2>
          <p className="text-xs text-slate-500">
            Mevcut verinin üzerine yazılır. İşlemden önce otomatik yedek alınır. Yönetici parolası gerekir.
          </p>
          <input ref={fileRef} type="file" accept=".bsa" className="block text-sm" />
          <input
            type="password"
            className="input max-w-xs"
            placeholder="Yönetici parolası"
            value={adminPassword}
            onChange={(e) => setAdminPassword(e.target.value)}
          />
          <button
            className="btn bg-red-600 text-white hover:bg-red-700"
            disabled={restore.isPending || !adminPassword}
            onClick={() => { setErr(null); setMsg(null); restore.mutate(); }}
          >
            Geri Yükle
          </button>
        </div>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b border-slate-100 py-1 last:border-0">
      <span className="text-slate-500">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}

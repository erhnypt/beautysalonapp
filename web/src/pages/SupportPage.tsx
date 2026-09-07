import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface DiagnosticsSnapshot {
  dbConnected: boolean;
  dbType: string;
  appVersion: string;
  schemaVersion: string;
  os: string;
  javaVersion: string;
  serverPort: number;
  diskFreeBytes: number;
  diskTotalBytes: number;
  lastSuccessfulBackupAt: string | null;
  lastBackupError: string | null;
  licenseStatus: string;
  licenseDaysRemaining: number | null;
  lastLicenseHeartbeatAt: string | null;
  logFileConfigured: boolean;
  serverTimeUtc: string;
}
interface BundleFile {
  name: string;
  sizeBytes: number;
  preview: string | null;
}

const gb = (n: number) => `${(n / 1024 / 1024 / 1024).toFixed(1)} GB`;
const kb = (n: number) => `${(n / 1024).toFixed(0)} KB`;

export function SupportPage() {
  const { has } = useAuth();
  const canBundle = has("BACKUP_RUN");

  const diag = useQuery({
    queryKey: ["support-diagnostics"],
    queryFn: () => api<DiagnosticsSnapshot>("/api/v1/support/diagnostics"),
  });
  const manifest = useQuery({
    queryKey: ["support-manifest"],
    queryFn: () => api<BundleFile[]>("/api/v1/support/bundle/manifest"),
    enabled: canBundle,
  });

  return (
    <div>
      <PageHeader title={t.nav.support} />

      {diag.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {diag.data && (
        <div className="card grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
          <Field label="Veritabanı">
            <span className={diag.data.dbConnected ? "text-green-700" : "text-red-700"}>
              {diag.data.dbConnected ? "Bağlı" : "Bağlantı yok"} ({diag.data.dbType})
            </span>
          </Field>
          <Field label="Uygulama Sürümü">{diag.data.appVersion}</Field>
          <Field label="Şema Sürümü">{diag.data.schemaVersion}</Field>
          <Field label="İşletim Sistemi">{diag.data.os}</Field>
          <Field label="Java Sürümü">{diag.data.javaVersion}</Field>
          <Field label="Sunucu Portu">{diag.data.serverPort}</Field>
          <Field label="Disk (boş / toplam)">
            {gb(diag.data.diskFreeBytes)} / {gb(diag.data.diskTotalBytes)}
          </Field>
          <Field label="Son Başarılı Yedek">
            {diag.data.lastSuccessfulBackupAt
              ? new Date(diag.data.lastSuccessfulBackupAt).toLocaleString("tr-TR")
              : "—"}
          </Field>
          <Field label="Son Yedek Hatası">{diag.data.lastBackupError ?? "—"}</Field>
          <Field label="Lisans Durumu">{diag.data.licenseStatus}</Field>
          <Field label="Lisans Kalan Gün">{diag.data.licenseDaysRemaining ?? "—"}</Field>
          <Field label="Son Lisans Yoklaması">
            {diag.data.lastLicenseHeartbeatAt
              ? new Date(diag.data.lastLicenseHeartbeatAt).toLocaleString("tr-TR")
              : "—"}
          </Field>
          <Field label="Log Dosyası">{diag.data.logFileConfigured ? "Yapılandırılmış" : "—"}</Field>
          <Field label="Sunucu Saati (UTC)">{new Date(diag.data.serverTimeUtc).toLocaleString("tr-TR")}</Field>
        </div>
      )}

      {canBundle && (
        <div className="card mt-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-semibold">Destek Paketi</h2>
            <a className="btn-primary" href="/api/v1/support/bundle" target="_blank" rel="noreferrer">
              Paketi İndir (.zip)
            </a>
          </div>
          <p className="mb-3 text-sm text-slate-500">
            Pakette yalnızca teşhis bilgisi, şema sürümü ve uygulama logları bulunur — iş verisi
            (müşteri, fatura, ödeme vb.) içermez. İndirmeden önce içeriği aşağıda gözden
            geçirebilirsiniz.
          </p>
          <table className="w-full text-sm">
            <thead className="text-left text-slate-500">
              <tr>
                <th className="py-1 font-medium">Dosya</th>
                <th className="py-1 text-right font-medium">Boyut</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {(manifest.data ?? []).map((f) => (
                <tr key={f.name}>
                  <td className="py-1.5 font-mono text-xs">{f.name}</td>
                  <td className="py-1.5 text-right tabular-nums">{kb(f.sizeBytes)}</td>
                </tr>
              ))}
              {(manifest.data ?? []).length === 0 && (
                <tr>
                  <td colSpan={2} className="py-4 text-center text-slate-400">
                    {t.common.noRecords}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  );
}

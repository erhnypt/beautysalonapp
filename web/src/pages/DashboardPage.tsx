import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { useLicense } from "../lib/license";
import { useAuth } from "../lib/auth";
import { t } from "../lib/i18n";

interface BackupStatus {
  lastSuccessfulAt: string | null;
  lastError: string | null;
}

interface BranchOption {
  id: number;
  code: string;
  title: string;
}

interface Alert {
  key: string;
  count: number;
  amount: number;
}
interface TrendPoint {
  date: string;
  amount: number;
}
interface NameCount {
  name: string;
  count: number;
}
interface Dashboard {
  date: string;
  invoiceRevenue: number;
  appointmentRevenue: number;
  totalRevenue: number;
  payments: { nakit: number; kart: number; havale: number };
  collections: number;
  expenses: number;
  appointmentsByStatus: Record<string, number>;
  newCustomers: number;
  alerts: Alert[];
  revenueTrend30d: TrendPoint[];
  serviceDistribution30d: NameCount[];
  staffOccupancy30d: NameCount[];
}

const tl = (n: number) =>
  new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY", maximumFractionDigits: 0 }).format(n);

const alertLabel: Record<string, (a: Alert) => string> = {
  installments_due: (a) => `${a.count} taksit vadesi geldi (${tl(a.amount)})`,
  critical_stock: (a) => `${a.count} ürün kritik stok seviyesinde`,
  cheques_due_week: (a) => `${a.count} çek vadesi bu hafta`,
};

export function DashboardPage() {
  const [branchId, setBranchId] = useState<number | "">("");
  const { has } = useAuth();
  const { data: license } = useLicense();
  // Günlük analiz mali özet içerir; backend /api/v1/dashboard/today artık
  // REPORTING_VIEW ister. Yetkisi olmayan kullanıcıyı 403'e sokmamak için sorguyu
  // ve mali kartları koşullu render ediyoruz.
  const canSeeDashboard = has("REPORTING_VIEW");
  const canSeeBackup = has("BACKUP_RUN") || has("BACKUP_RESTORE") || has("SETTINGS_VIEW");

  const branches = useQuery({
    queryKey: ["branches-for-switcher"],
    queryFn: () => api<BranchOption[]>("/api/v1/branches"),
  });
  const { data } = useQuery({
    queryKey: ["dashboard-today", branchId],
    queryFn: () =>
      api<Dashboard>(`/api/v1/dashboard/today${branchId ? `?branchId=${branchId}` : ""}`),
    refetchInterval: 2 * 60 * 1000,
    enabled: canSeeDashboard,
  });
  const { data: backupStatus } = useQuery({
    queryKey: ["backup-status-dashboard"],
    queryFn: () => api<BackupStatus>("/api/v1/backup/status"),
    enabled: canSeeBackup,
  });

  const showSwitcher = (branches.data?.length ?? 0) > 1;

  const licenseLine = (
    <li className="flex items-start gap-2 pt-1 text-slate-500">
      <span>{license?.status === "ACTIVE" || license?.devMode ? "✓" : "⚠"}</span>
      <span>Lisans: {license?.devMode ? "geliştirme modu" : license?.status ?? "—"}</span>
    </li>
  );
  const backupLine = canSeeBackup && backupStatus && (
    <li className={`flex items-start gap-2 ${backupStatus.lastError ? "text-red-700" : "text-slate-500"}`}>
      <span>{backupStatus.lastError ? "⚠" : backupStatus.lastSuccessfulAt ? "✓" : "⚠"}</span>
      <span>
        {backupStatus.lastError
          ? `Son yedek başarısız: ${backupStatus.lastError}`
          : backupStatus.lastSuccessfulAt
            ? `Son yedek: ${new Date(backupStatus.lastSuccessfulAt).toLocaleString("tr-TR")}`
            : "Henüz yedek alınmadı"}
      </span>
    </li>
  );

  if (!canSeeDashboard) {
    return (
      <div>
        <PageHeader title={t.dashboard.title} />
        <div className="card max-w-xl">
          <p className="text-sm text-slate-600">{t.dashboard.noReportingAccess}</p>
          <ul className="mt-3 space-y-1.5 text-sm">
            {licenseLine}
            {backupLine}
          </ul>
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div>
        <PageHeader title={t.dashboard.title} />
        <div className="text-slate-500">{t.common.loading}</div>
      </div>
    );
  }

  const appt = data.appointmentsByStatus;
  const maxTrend = Math.max(1, ...data.revenueTrend30d.map((p) => p.amount));

  return (
    <div>
      <PageHeader
        title={`${t.dashboard.title} · ${new Date(data.date).toLocaleDateString("tr-TR")}`}
        actions={
          showSwitcher && (
            <select
              className="input w-auto"
              value={branchId}
              onChange={(e) => setBranchId(e.target.value ? Number(e.target.value) : "")}
            >
              <option value="">Tüm şubeler</option>
              {branches.data!.map((b) => (
                <option key={b.id} value={b.id}>{b.code} — {b.title}</option>
              ))}
            </select>
          )
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Ciro (bugün)" value={tl(data.totalRevenue)} sub={`Fatura ${tl(data.invoiceRevenue)} · Randevu ${tl(data.appointmentRevenue)}`} />
        <Stat label="Tahsilat" value={tl(data.collections)} sub={`Gider ${tl(data.expenses)}`} />
        <Stat
          label="Ödeme türü"
          value={tl(data.payments.nakit + data.payments.kart + data.payments.havale)}
          sub={`Nakit ${tl(data.payments.nakit)} · Kart ${tl(data.payments.kart)} · Havale ${tl(data.payments.havale)}`}
        />
        <Stat
          label="Randevu"
          value={`${appt["GELDI"] ?? 0} geldi`}
          sub={`${Object.values(appt).reduce((a, b) => a + b, 0)} planlı · ${appt["GELMEDI"] ?? 0} gelmedi · ${appt["IPTAL"] ?? 0} iptal`}
        />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="card lg:col-span-2">
          <h2 className="mb-3 text-sm font-semibold">Son 30 gün ciro (fatura)</h2>
          <div className="flex h-32 items-end gap-1">
            {data.revenueTrend30d.length === 0 && (
              <div className="text-sm text-slate-400">Veri yok</div>
            )}
            {data.revenueTrend30d.map((p) => (
              <div
                key={p.date}
                title={`${new Date(p.date).toLocaleDateString("tr-TR")}: ${tl(p.amount)}`}
                className="flex-1 rounded-t bg-brand-500/80 hover:bg-brand-600"
                style={{ height: `${Math.max(3, (p.amount / maxTrend) * 100)}%` }}
              />
            ))}
          </div>
        </div>

        <div className="card">
          <h2 className="mb-2 text-sm font-semibold">Uyarılar</h2>
          <ul className="space-y-1.5 text-sm">
            {data.alerts
              .filter((a) => a.count > 0)
              .map((a) => (
                <li key={a.key} className="flex items-start gap-2 text-amber-800">
                  <span>⚠</span>
                  <span>{(alertLabel[a.key] ?? ((x: Alert) => `${x.key}: ${x.count}`))(a)}</span>
                </li>
              ))}
            {data.alerts.every((a) => a.count === 0) && (
              <li className="text-slate-400">Uyarı yok</li>
            )}
            {licenseLine}
            {backupLine}
          </ul>
        </div>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <DistList title="Hizmet dağılımı (30 gün)" rows={data.serviceDistribution30d} />
        <DistList title="Personel doluluk (30 gün)" rows={data.staffOccupancy30d} />
      </div>
    </div>
  );
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="card">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums">{value}</div>
      {sub && <div className="mt-1 text-xs text-slate-400">{sub}</div>}
    </div>
  );
}

function DistList({ title, rows }: { title: string; rows: NameCount[] }) {
  const max = Math.max(1, ...rows.map((r) => r.count));
  return (
    <div className="card">
      <h2 className="mb-2 text-sm font-semibold">{title}</h2>
      {rows.length === 0 && <div className="text-sm text-slate-400">Veri yok</div>}
      <ul className="space-y-1.5">
        {rows.slice(0, 8).map((r) => (
          <li key={r.name} className="text-sm">
            <div className="flex justify-between">
              <span className="truncate">{r.name}</span>
              <span className="text-slate-500">{r.count}</span>
            </div>
            <div className="mt-0.5 h-1.5 rounded bg-slate-100">
              <div className="h-full rounded bg-brand-400" style={{ width: `${(r.count / max) * 100}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}

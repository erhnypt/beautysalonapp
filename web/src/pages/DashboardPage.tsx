import { PageHeader } from "../components/PageHeader";
import { useLicense } from "../lib/license";
import { t } from "../lib/i18n";

export function DashboardPage() {
  const { data: license } = useLicense();

  return (
    <div>
      <PageHeader title={t.dashboard.title} />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: "Ciro", value: "—" },
          { label: "Randevu", value: "—" },
          { label: "Tahsilat", value: "—" },
          { label: "Gider", value: "—" },
        ].map((c) => (
          <div key={c.label} className="card">
            <div className="text-sm text-slate-500">{c.label}</div>
            <div className="mt-1 text-2xl font-semibold">{c.value}</div>
          </div>
        ))}
      </div>

      <div className="mt-6 card">
        <h2 className="mb-1 text-sm font-semibold">Faz 0 — Çekirdek</h2>
        <p className="text-sm text-slate-600">
          Kullanıcı/rol/yetki, ayarlar, işlem kayıtları, lisans motoru ve kademeli kısıtlama
          çalışıyor. Ticari modüller (cari, stok, kasa, randevu, sözleşme) yol haritasına göre
          eklenecek. {t.dashboard.comingSoon}
        </p>
        {license && (
          <p className="mt-3 text-xs text-slate-500">
            Lisans: <span className="font-medium">{license.devMode ? "DEV" : license.status}</span>
            {license.plan ? ` · Plan: ${license.plan}` : ""}
            {license.enabledModules?.length
              ? ` · Açık modüller: ${license.enabledModules.join(", ")}`
              : ""}
          </p>
        )}
      </div>
    </div>
  );
}

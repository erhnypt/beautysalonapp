import { useLicense, licenseTone } from "../lib/license";
import { t } from "../lib/i18n";

const toneClass: Record<string, string> = {
  ok: "hidden",
  warn: "bg-amber-100 text-amber-900 border-amber-300",
  danger: "bg-red-100 text-red-900 border-red-300",
};

/** Üst bant: EXPIRING sarı, GRACE/READ_ONLY/LOCKED/TAMPERED kırmızı (§6.4). */
export function LicenseBanner() {
  const { data } = useLicense();
  if (!data) return null;

  if (data.devMode) {
    return (
      <div className="border-b border-slate-300 bg-slate-200 px-4 py-1.5 text-center text-xs text-slate-600">
        {t.license.devMode}
      </div>
    );
  }

  const tone = licenseTone(data.status);
  if (tone === "ok") return null;

  const label = t.license[data.status] ?? data.status;

  return (
    <div className={`border-b px-4 py-2 text-center text-sm font-medium ${toneClass[tone]}`}>
      {label}
      {data.message ? ` — ${data.message}` : ""}
      {data.daysRemaining != null && data.status === "EXPIRING"
        ? ` (${data.daysRemaining} ${t.license.daysRemaining})`
        : ""}
    </div>
  );
}

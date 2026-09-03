import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import type { LicenseSnapshot } from "../lib/types";
import { useLicense } from "../lib/license";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

export function LicensePage() {
  const { data, isLoading } = useLicense();
  const { has } = useAuth();
  const qc = useQueryClient();
  const canManage = has("LICENSE_MANAGE");
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const upload = async () => {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      const fd = new FormData();
      fd.append("file", file);
      const snap = await api<LicenseSnapshot>("/api/v1/license/upload", { method: "POST", body: fd });
      setMsg(`Lisans yüklendi: ${snap.customerName ?? ""} (${snap.status})`);
      qc.invalidateQueries({ queryKey: ["license"] });
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : t.common.error);
    } finally {
      setBusy(false);
    }
  };

  const heartbeatNow = async () => {
    setBusy(true);
    setErr(null);
    try {
      await api("/api/v1/license/heartbeat/now", { method: "POST" });
      qc.invalidateQueries({ queryKey: ["license"] });
      setMsg("Doğrulama tamamlandı.");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : t.common.error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="max-w-2xl">
      <PageHeader title={t.nav.license} />
      {isLoading && <div className="text-slate-500">{t.common.loading}</div>}

      {data && (
        <div className="card space-y-2">
          <Field label="Durum" value={data.devMode ? "GELİŞTİRME MODU" : data.status} />
          {data.customerName && <Field label="Müşteri" value={data.customerName} />}
          {data.plan && <Field label="Plan" value={data.plan} />}
          {data.notAfter && (
            <Field label="Bitiş" value={new Date(data.notAfter).toLocaleDateString("tr-TR")} />
          )}
          {data.daysRemaining != null && (
            <Field label="Kalan gün" value={String(data.daysRemaining)} />
          )}
          <Field
            label="Son heartbeat"
            value={
              data.lastSuccessfulHeartbeatAt
                ? new Date(data.lastSuccessfulHeartbeatAt).toLocaleString("tr-TR")
                : "—"
            }
          />
          <Field label="Açık modüller" value={data.enabledModules?.join(", ") || "—"} />
          {data.message && <p className="pt-2 text-sm text-slate-600">{data.message}</p>}
        </div>
      )}

      {canManage && (
        <div className="card mt-4 space-y-3">
          <h2 className="text-sm font-semibold">{t.license.uploadTitle}</h2>
          {msg && <div className="rounded bg-green-50 px-3 py-2 text-sm text-green-700">{msg}</div>}
          {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
          <input ref={fileRef} type="file" accept=".lic" className="block text-sm" />
          <div className="flex gap-2">
            <button className="btn-primary" disabled={busy} onClick={upload}>
              {t.common.save}
            </button>
            <button className="btn-ghost" disabled={busy} onClick={heartbeatNow}>
              {t.license.heartbeatNow}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b border-slate-100 py-1.5 text-sm last:border-0">
      <span className="text-slate-500">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}

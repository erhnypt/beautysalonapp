import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface TemplateView {
  id: number;
  type: string;
  channel: "SMS" | "EMAIL";
  subject: string | null;
  body: string;
  active: boolean;
}
interface QueueView {
  id: number;
  toAddress: string;
  channel: string;
  type: string;
  status: string;
  attempts: number;
  scheduledAt: string;
  sentAt: string | null;
  lastError: string | null;
}
interface Page<T> {
  content: T[];
}

const statusChip: Record<string, string> = {
  PENDING: "bg-slate-100 text-slate-600",
  SENT: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-red-700",
  SKIPPED: "bg-amber-100 text-amber-800",
};

export function NotificationsPage() {
  const { has } = useAuth();
  const qc = useQueryClient();
  const [tab, setTab] = useState<"queue" | "templates">("queue");
  const [testTo, setTestTo] = useState("");
  const [testTpl, setTestTpl] = useState<number | "">("");
  const [msg, setMsg] = useState<string | null>(null);

  const templates = useQuery({ queryKey: ["notif-templates"], queryFn: () => api<TemplateView[]>("/api/v1/notifications/templates") });
  const queue = useQuery({ queryKey: ["notif-queue"], queryFn: () => api<Page<QueueView>>("/api/v1/notifications/queue?size=100") });

  const test = useMutation({
    mutationFn: () => api<string>("/api/v1/notifications/test", { method: "POST", body: { templateId: Number(testTpl), to: testTo } }),
    onSuccess: (r) => setMsg(r),
    onError: (e) => setMsg(e instanceof ApiError ? e.message : t.common.error),
  });
  const processNow = useMutation({
    mutationFn: () => api<{ processed: number }>("/api/v1/notifications/process-now", { method: "POST" }),
    onSuccess: (r) => {
      setMsg(`${r.processed} bildirim işlendi`);
      qc.invalidateQueries({ queryKey: ["notif-queue"] });
    },
  });
  const toggle = useMutation({
    mutationFn: (tpl: TemplateView) =>
      api("/api/v1/notifications/templates", { method: "POST", body: { ...tpl, active: !tpl.active } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notif-templates"] }),
  });

  return (
    <div>
      <PageHeader
        title={t.nav.notifications}
        actions={
          has("NOTIFICATION_SEND") && (
            <button className="btn-ghost" disabled={processNow.isPending} onClick={() => processNow.mutate()}>
              Kuyruğu İşle
            </button>
          )
        }
      />

      {msg && <div className="mb-3 rounded-lg bg-slate-100 px-3 py-2 text-sm">{msg}</div>}

      <div className="mb-4 flex gap-1 border-b border-slate-200">
        {(["queue", "templates"] as const).map((k) => (
          <button
            key={k}
            onClick={() => setTab(k)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              tab === k ? "border-brand-600 text-brand-700" : "border-transparent text-slate-500"
            }`}
          >
            {k === "queue" ? "Kuyruk" : "Şablonlar"}
          </button>
        ))}
      </div>

      {tab === "templates" && (
        <>
          {has("NOTIFICATION_SEND") && (
            <div className="card mb-4 flex items-end gap-2">
              <div>
                <label className="label">Şablon</label>
                <select className="input" value={testTpl} onChange={(e) => setTestTpl(e.target.value ? Number(e.target.value) : "")}>
                  <option value="">Seçin</option>
                  {(templates.data ?? []).map((tp) => (
                    <option key={tp.id} value={tp.id}>
                      {tp.type} / {tp.channel}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label">Alıcı (test)</label>
                <input className="input w-56" value={testTo} onChange={(e) => setTestTo(e.target.value)} />
              </div>
              <button className="btn-primary" disabled={test.isPending || !testTpl || !testTo} onClick={() => test.mutate()}>
                Test Gönder
              </button>
            </div>
          )}
          <div className="space-y-2">
            {(templates.data ?? []).map((tp) => (
              <div key={tp.id} className="card">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-semibold">
                    {tp.type} · {tp.channel}
                  </div>
                  {has("NOTIFICATION_TEMPLATE_EDIT") && (
                    <button className="text-xs text-brand-700 hover:underline" onClick={() => toggle.mutate(tp)}>
                      {tp.active ? "Pasifleştir" : "Aktifleştir"}
                    </button>
                  )}
                </div>
                {tp.subject && <div className="mt-1 text-xs text-slate-500">Konu: {tp.subject}</div>}
                <div className="mt-1 whitespace-pre-wrap text-sm text-slate-700">{tp.body}</div>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === "queue" && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">Alıcı</th>
                <th className="px-4 py-2 font-medium">Tür</th>
                <th className="px-4 py-2 font-medium">Kanal</th>
                <th className="px-4 py-2 font-medium">Durum</th>
                <th className="px-4 py-2 font-medium">Planlanan</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {(queue.data?.content ?? []).map((q) => (
                <tr key={q.id}>
                  <td className="px-4 py-2 font-mono text-xs">{q.toAddress}</td>
                  <td className="px-4 py-2">{q.type}</td>
                  <td className="px-4 py-2">{q.channel}</td>
                  <td className="px-4 py-2">
                    <span className={`rounded px-1.5 py-0.5 text-xs ${statusChip[q.status]}`}>{q.status}</span>
                    {q.lastError && <span className="ml-2 text-xs text-red-500">{q.lastError}</span>}
                  </td>
                  <td className="px-4 py-2 text-slate-500">{new Date(q.scheduledAt).toLocaleString("tr-TR")}</td>
                </tr>
              ))}
              {(queue.data?.content ?? []).length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
                    Kuyruk boş
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

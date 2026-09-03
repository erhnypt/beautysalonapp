import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface StaffView {
  id: number;
  partyId: number;
  title: string;
  hireDate: string | null;
  defaultServiceRate: number | null;
}
interface CommissionView {
  id: number;
  periodYm: string;
  sourceType: string;
  sourceRef: string;
  baseAmount: number;
  amount: number;
  status: "TAHAKKUK" | "ODENDI" | "IPTAL";
}
interface PartyRow {
  id: number;
  title: string;
}
interface Page<T> {
  content: T[];
}

const tl = (n: number) =>
  new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);
const ym = () => new Date().toISOString().slice(0, 7);

export function StaffPage() {
  const { has } = useAuth();
  const [showForm, setShowForm] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const list = useQuery({ queryKey: ["staff"], queryFn: () => api<StaffView[]>("/api/v1/staff") });

  return (
    <div>
      <PageHeader
        title={t.nav.staff}
        actions={
          has("STAFF_ADD") && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              {t.common.add}
            </button>
          )
        }
      />
      {showForm && <NewStaffForm onClose={() => setShowForm(false)} />}
      {list.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {list.data && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">Ad</th>
                <th className="px-4 py-2 font-medium">İşe Giriş</th>
                <th className="px-4 py-2 text-right font-medium">Hizmet Primi %</th>
                <th className="px-4 py-2"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.data.map((s) => (
                <tr
                  key={s.id}
                  className="cursor-pointer hover:bg-slate-50"
                  onClick={() => setOpenId(openId === s.id ? null : s.id)}
                >
                  <td className="px-4 py-2 font-medium">{s.title}</td>
                  <td className="px-4 py-2 text-slate-500">
                    {s.hireDate ? new Date(s.hireDate).toLocaleDateString("tr-TR") : "—"}
                  </td>
                  <td className="px-4 py-2 text-right">{s.defaultServiceRate ?? "—"}</td>
                  <td className="px-4 py-2 text-right text-xs text-brand-700">detay</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {openId && <StaffDetail id={openId} />}
    </div>
  );
}

function StaffDetail({ id }: { id: number }) {
  const qc = useQueryClient();
  const { has } = useAuth();
  const period = ym();
  const commissions = useQuery({
    queryKey: ["staff-commissions", id, period],
    queryFn: () => api<CommissionView[]>(`/api/v1/staff/${id}/commissions?period=${period}`),
  });
  const [advAmount, setAdvAmount] = useState("");

  const pay = useMutation({
    mutationFn: () => api<number>(`/api/v1/staff/${id}/commissions/pay`, { method: "POST", body: { period } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["staff-commissions", id] });
      qc.invalidateQueries({ queryKey: ["fin-accounts"] });
    },
  });
  const advance = useMutation({
    mutationFn: () => api(`/api/v1/staff/${id}/advances`, { method: "POST", body: { amount: Number(advAmount) } }),
    onSuccess: () => {
      setAdvAmount("");
      qc.invalidateQueries({ queryKey: ["fin-accounts"] });
    },
  });

  const pending = (commissions.data ?? []).filter((c) => c.status === "TAHAKKUK");
  const pendingTotal = pending.reduce((s, c) => s + c.amount, 0);

  return (
    <div className="mt-4 card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold">{period} — Prim Tahakkukları</h2>
        {has("STAFF_EDIT") && (
          <button className="btn-primary" disabled={pay.isPending || pendingTotal === 0} onClick={() => pay.mutate()}>
            Prim Öde ({tl(pendingTotal)})
          </button>
        )}
      </div>
      <table className="w-full text-sm">
        <thead className="text-left text-slate-500">
          <tr>
            <th className="py-1 font-medium">Kaynak</th>
            <th className="py-1 text-right font-medium">Baz</th>
            <th className="py-1 text-right font-medium">Prim</th>
            <th className="py-1 font-medium">Durum</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {(commissions.data ?? []).map((c) => (
            <tr key={c.id}>
              <td className="py-1.5 font-mono text-xs">{c.sourceRef}</td>
              <td className="py-1.5 text-right tabular-nums">{tl(c.baseAmount)}</td>
              <td className="py-1.5 text-right tabular-nums">{tl(c.amount)}</td>
              <td className="py-1.5">
                <span
                  className={`rounded px-1.5 py-0.5 text-xs ${
                    c.status === "ODENDI" ? "bg-green-100 text-green-700" : "bg-slate-100 text-slate-600"
                  }`}
                >
                  {c.status}
                </span>
              </td>
            </tr>
          ))}
          {(commissions.data ?? []).length === 0 && (
            <tr>
              <td colSpan={4} className="py-4 text-center text-slate-400">
                Bu ay prim tahakkuku yok
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {has("STAFF_EDIT") && (
        <div className="mt-4 flex items-end gap-2 border-t border-slate-100 pt-3">
          <div>
            <label className="label">Avans Tutarı</label>
            <input className="input w-40" value={advAmount} onChange={(e) => setAdvAmount(e.target.value)} />
          </div>
          <button className="btn-ghost" disabled={advance.isPending || !advAmount} onClick={() => advance.mutate()}>
            Avans Ver
          </button>
        </div>
      )}
    </div>
  );
}

function NewStaffForm({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const personel = useQuery({
    queryKey: ["parties", "PERSONEL", ""],
    queryFn: () => api<Page<PartyRow>>("/api/v1/parties?type=PERSONEL&size=100"),
  });
  const [partyId, setPartyId] = useState<number | "">("");
  const [title, setTitle] = useState("");
  const [serviceRate, setServiceRate] = useState("10");
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/staff", {
        method: "POST",
        body: {
          partyId: partyId === "" ? null : Number(partyId),
          title: title || personel.data?.content.find((p) => p.id === partyId)?.title || "Personel",
          defaultServiceRate: Number(serviceRate),
        },
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["staff"] });
      onClose();
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    create.mutate();
  };

  return (
    <form onSubmit={submit} className="card mb-4 grid gap-3 sm:grid-cols-3">
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-3">{err}</div>}
      <div>
        <label className="label">Mevcut Personel Cari</label>
        <select className="input" value={partyId} onChange={(e) => setPartyId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">(yeni oluştur)</option>
          {(personel.data?.content ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Ünvan / Ad</label>
        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <div>
        <label className="label">Varsayılan Hizmet Primi %</label>
        <input className="input" value={serviceRate} onChange={(e) => setServiceRate(e.target.value)} />
      </div>
      <div className="flex items-end gap-2 sm:col-span-3">
        <button className="btn-primary" disabled={create.isPending}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface CardView {
  id: number;
  cardNo: string;
  magneticId: string | null;
  partyId: number;
  status: string;
  pointsBalance: number;
}
interface TxnView {
  id: number;
  type: string;
  points: number;
  currencyValue: number | null;
  sourceRef: string | null;
  at: string;
}
interface PartyRow {
  id: number;
  title: string;
}
interface Page<T> {
  content: T[];
}
interface Liability {
  outstandingPoints: number;
  estimatedLiabilityTry: number;
  activeCards: number;
}

const tl = (n: number) => new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);

export function LoyaltyPage() {
  const { has } = useAuth();
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const [resolveKey, setResolveKey] = useState("");
  const [resolved, setResolved] = useState<CardView | null>(null);
  const [resolveErr, setResolveErr] = useState<string | null>(null);

  const cards = useQuery({ queryKey: ["loyalty-cards"], queryFn: () => api<CardView[]>("/api/v1/loyalty/cards") });
  const liability = useQuery({
    queryKey: ["loyalty-liability"],
    queryFn: () => api<Liability>("/api/v1/loyalty/reports/liability"),
  });

  const doResolve = async (e: FormEvent) => {
    e.preventDefault();
    setResolveErr(null);
    setResolved(null);
    try {
      setResolved(await api<CardView>(`/api/v1/loyalty/resolve/${encodeURIComponent(resolveKey)}`));
    } catch (err) {
      setResolveErr(err instanceof ApiError ? err.message : t.common.error);
    }
  };

  return (
    <div>
      <PageHeader
        title={t.nav.loyalty}
        actions={
          has("LOYALTY_ADD") && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              Kart Çıkar
            </button>
          )
        }
      />

      {liability.data && (
        <div className="mb-4 grid gap-4 sm:grid-cols-3">
          <div className="card">
            <div className="text-sm text-slate-500">Dolaşımdaki puan</div>
            <div className="mt-1 text-2xl font-semibold">{liability.data.outstandingPoints}</div>
          </div>
          <div className="card">
            <div className="text-sm text-slate-500">Tahmini yükümlülük</div>
            <div className="mt-1 text-2xl font-semibold">{tl(liability.data.estimatedLiabilityTry)}</div>
          </div>
          <div className="card">
            <div className="text-sm text-slate-500">Aktif kart</div>
            <div className="mt-1 text-2xl font-semibold">{liability.data.activeCards}</div>
          </div>
        </div>
      )}

      <form onSubmit={doResolve} className="card mb-4 flex items-end gap-2">
        <div>
          <label className="label">Kart okut / no ara</label>
          <input className="input w-64" value={resolveKey} onChange={(e) => setResolveKey(e.target.value)} />
        </div>
        <button className="btn-ghost">Bul</button>
        {resolved && (
          <span className="text-sm text-green-700">
            {resolved.cardNo} · Bakiye {resolved.pointsBalance} puan
          </span>
        )}
        {resolveErr && <span className="text-sm text-red-600">{resolveErr}</span>}
      </form>

      {showForm && <IssueCardForm onClose={() => setShowForm(false)} />}

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2 font-medium">Kart No</th>
              <th className="px-4 py-2 font-medium">Müşteri</th>
              <th className="px-4 py-2 font-medium">Durum</th>
              <th className="px-4 py-2 text-right font-medium">Puan</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {(cards.data ?? []).map((c) => (
              <tr key={c.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setOpenId(openId === c.id ? null : c.id)}>
                <td className="px-4 py-2 font-mono text-xs">{c.cardNo}</td>
                <td className="px-4 py-2 text-slate-500">#{c.partyId}</td>
                <td className="px-4 py-2">{c.status}</td>
                <td className="px-4 py-2 text-right tabular-nums font-medium">{c.pointsBalance}</td>
                <td className="px-4 py-2 text-right text-xs text-brand-700">detay</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {openId && <CardDetail id={openId} onChanged={() => { qc.invalidateQueries({ queryKey: ["loyalty-cards"] }); qc.invalidateQueries({ queryKey: ["loyalty-liability"] }); }} />}
    </div>
  );
}

function CardDetail({ id, onChanged }: { id: number; onChanged: () => void }) {
  const { has } = useAuth();
  const qc = useQueryClient();
  const txns = useQuery({
    queryKey: ["loyalty-txns", id],
    queryFn: () => api<TxnView[]>(`/api/v1/loyalty/cards/${id}/transactions`),
  });
  const [points, setPoints] = useState("");

  const redeem = useMutation({
    mutationFn: () =>
      api(`/api/v1/loyalty/cards/${id}/redeem`, { method: "POST", body: { points: Number(points), sourceRef: "MANUAL-" + Date.now() } }),
    onSuccess: () => {
      setPoints("");
      qc.invalidateQueries({ queryKey: ["loyalty-txns", id] });
      onChanged();
    },
  });
  const lost = useMutation({
    mutationFn: () => api(`/api/v1/loyalty/cards/${id}/report-lost`, { method: "POST", body: {} }),
    onSuccess: onChanged,
  });

  return (
    <div className="mt-4 card">
      <div className="mb-3 flex items-center gap-2">
        {has("LOYALTY_EDIT") && (
          <>
            <input className="input w-28" placeholder="puan" value={points} onChange={(e) => setPoints(e.target.value)} />
            <button className="btn-primary" disabled={redeem.isPending || !points} onClick={() => redeem.mutate()}>
              Puanla Öde
            </button>
            <button className="btn-ghost" onClick={() => lost.mutate()}>
              Kayıp Bildir
            </button>
          </>
        )}
      </div>
      <table className="w-full text-sm">
        <thead className="text-left text-slate-500">
          <tr>
            <th className="py-1 font-medium">Tür</th>
            <th className="py-1 text-right font-medium">Puan</th>
            <th className="py-1 text-right font-medium">TL</th>
            <th className="py-1 font-medium">Kaynak</th>
            <th className="py-1 font-medium">Tarih</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {(txns.data ?? []).map((tx) => (
            <tr key={tx.id}>
              <td className="py-1.5">{tx.type}</td>
              <td className={`py-1.5 text-right tabular-nums ${tx.points < 0 ? "text-red-600" : "text-green-700"}`}>
                {tx.points > 0 ? "+" : ""}
                {tx.points}
              </td>
              <td className="py-1.5 text-right tabular-nums">{tx.currencyValue != null ? tl(tx.currencyValue) : "—"}</td>
              <td className="py-1.5 font-mono text-xs">{tx.sourceRef}</td>
              <td className="py-1.5 text-slate-500">{new Date(tx.at).toLocaleString("tr-TR")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function IssueCardForm({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const customers = useQuery({
    queryKey: ["parties", "MUSTERI", ""],
    queryFn: () => api<Page<PartyRow>>("/api/v1/parties?type=MUSTERI&size=100"),
  });
  const [partyId, setPartyId] = useState<number | "">("");
  const [magneticId, setMagneticId] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/loyalty/cards", { method: "POST", body: { partyId: Number(partyId), magneticId: magneticId || null } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["loyalty-cards"] });
      onClose();
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        setErr(null);
        create.mutate();
      }}
      className="card mb-4 grid gap-3 sm:grid-cols-3"
    >
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-3">{err}</div>}
      <div className="sm:col-span-2">
        <label className="label">Müşteri</label>
        <select className="input" value={partyId} onChange={(e) => setPartyId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Seçin</option>
          {(customers.data?.content ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Manyetik/Çip ID (opsiyonel)</label>
        <input className="input" value={magneticId} onChange={(e) => setMagneticId(e.target.value)} />
      </div>
      <div className="flex items-end gap-2 sm:col-span-3">
        <button className="btn-primary" disabled={create.isPending || !partyId}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

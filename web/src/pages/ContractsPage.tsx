import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { AttachmentPanel } from "../components/AttachmentPanel";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface ContractView {
  id: number;
  docNo: string;
  partyId: number;
  contractDate: string;
  totalAmount: number;
  downPayment: number;
  installmentCount: number;
  status: "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";
  currency: string;
}
interface InstallmentView {
  id: number;
  seq: number;
  dueDate: string;
  amount: number;
  paidAmount: number;
  status: "BEKLIYOR" | "ODENDI" | "GECIKMIS" | "IPTAL";
}
interface ContractDetail {
  contract: ContractView;
  lines: { id: number; description: string; lineTotal: number }[];
  installments: InstallmentView[];
}
interface PartyRow {
  id: number;
  code: string;
  title: string;
}
interface Page<T> {
  content: T[];
}

const tl = (n: number) =>
  new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);

const statusChip: Record<string, string> = {
  ACTIVE: "bg-sky-100 text-sky-700",
  COMPLETED: "bg-green-100 text-green-700",
  CANCELLED: "bg-slate-200 text-slate-600",
  DRAFT: "bg-amber-100 text-amber-700",
  BEKLIYOR: "bg-slate-100 text-slate-600",
  ODENDI: "bg-green-100 text-green-700",
  GECIKMIS: "bg-red-100 text-red-700",
  IPTAL: "bg-slate-200 text-slate-500 line-through",
};

export function ContractsPage() {
  const { has } = useAuth();
  const [showForm, setShowForm] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);

  const list = useQuery({
    queryKey: ["contracts"],
    queryFn: () => api<Page<ContractView>>("/api/v1/contracts?size=100"),
  });

  return (
    <div>
      <PageHeader
        title={t.nav.contracts}
        actions={
          has("CONTRACT_ADD") && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              {t.common.add}
            </button>
          )
        }
      />

      {showForm && <NewContractForm onClose={() => setShowForm(false)} onCreated={(id) => { setShowForm(false); setOpenId(id); }} />}

      {list.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {list.data && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">No</th>
                <th className="px-4 py-2 font-medium">Tarih</th>
                <th className="px-4 py-2 text-right font-medium">Tutar</th>
                <th className="px-4 py-2 text-right font-medium">Peşinat</th>
                <th className="px-4 py-2 text-center font-medium">Taksit</th>
                <th className="px-4 py-2 font-medium">Durum</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.data.content.map((c) => (
                <tr
                  key={c.id}
                  className="cursor-pointer hover:bg-slate-50"
                  onClick={() => setOpenId(openId === c.id ? null : c.id)}
                >
                  <td className="px-4 py-2 font-mono text-xs">{c.docNo}</td>
                  <td className="px-4 py-2">{new Date(c.contractDate).toLocaleDateString("tr-TR")}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{tl(c.totalAmount)}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{tl(c.downPayment)}</td>
                  <td className="px-4 py-2 text-center">{c.installmentCount}</td>
                  <td className="px-4 py-2">
                    <span className={`rounded px-1.5 py-0.5 text-xs ${statusChip[c.status]}`}>
                      {c.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {openId && <ContractDetailPanel id={openId} onClose={() => setOpenId(null)} />}
    </div>
  );
}

function ContractDetailPanel({ id, onClose }: { id: number; onClose: () => void }) {
  const { has } = useAuth();
  const qc = useQueryClient();
  const [showRestructure, setShowRestructure] = useState(false);
  const { data } = useQuery({
    queryKey: ["contract", id],
    queryFn: () => api<ContractDetail>(`/api/v1/contracts/${id}`),
  });
  const pay = useMutation({
    mutationFn: (installmentId: number) =>
      api(`/api/v1/contracts/installments/${installmentId}/pay`, { method: "POST", body: {} }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["contract", id] });
      qc.invalidateQueries({ queryKey: ["contracts"] });
      qc.invalidateQueries({ queryKey: ["fin-accounts"] });
    },
  });

  if (!data) return null;

  const hasOpenInstallments = data.installments.some((i) => i.status !== "ODENDI" && i.status !== "IPTAL");

  return (
    <div className="mt-4 card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold">{data.contract.docNo} — Taksit Planı</h2>
        <div className="flex items-center gap-3">
          {has("CONTRACT_EDIT") && data.contract.status === "ACTIVE" && hasOpenInstallments && (
            <button className="text-sm text-brand-700 hover:underline" onClick={() => setShowRestructure((v) => !v)}>
              Yeniden Yapılandır
            </button>
          )}
          <button className="text-sm text-slate-500 hover:underline" onClick={onClose}>
            Kapat
          </button>
        </div>
      </div>
      {showRestructure && (
        <RestructureForm
          contractId={id}
          onClose={() => setShowRestructure(false)}
          onDone={() => {
            setShowRestructure(false);
            qc.invalidateQueries({ queryKey: ["contract", id] });
            qc.invalidateQueries({ queryKey: ["contracts"] });
          }}
        />
      )}
      <table className="w-full text-sm">
        <thead className="text-left text-slate-500">
          <tr>
            <th className="py-1 font-medium">#</th>
            <th className="py-1 font-medium">Vade</th>
            <th className="py-1 text-right font-medium">Tutar</th>
            <th className="py-1 text-right font-medium">Ödenen</th>
            <th className="py-1 font-medium">Durum</th>
            <th className="py-1"></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {data.installments.map((i) => (
            <tr key={i.id}>
              <td className="py-1.5">{i.seq}</td>
              <td className="py-1.5">{new Date(i.dueDate).toLocaleDateString("tr-TR")}</td>
              <td className="py-1.5 text-right tabular-nums">{tl(i.amount)}</td>
              <td className="py-1.5 text-right tabular-nums">{tl(i.paidAmount)}</td>
              <td className="py-1.5">
                <span className={`rounded px-1.5 py-0.5 text-xs ${statusChip[i.status]}`}>{i.status}</span>
              </td>
              <td className="py-1.5 text-right">
                {i.status !== "ODENDI" && i.status !== "IPTAL" && (
                  <button
                    className="text-xs text-brand-700 hover:underline"
                    disabled={pay.isPending}
                    onClick={() => pay.mutate(i.id)}
                  >
                    Tahsil et
                  </button>
                )}
              </td>
            </tr>
          ))}
          {data.installments.length === 0 && (
            <tr>
              <td colSpan={6} className="py-4 text-center text-slate-400">
                Peşin sözleşme — taksit yok
              </td>
            </tr>
          )}
        </tbody>
      </table>
      <AttachmentPanel ownerType="CONTRACT" ownerId={id} editPermission="CONTRACT_EDIT" />
    </div>
  );
}

function RestructureForm({
  contractId,
  onClose,
  onDone,
}: {
  contractId: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const [newInstallmentCount, setNewInstallmentCount] = useState("3");
  const [newFirstDueDate, setNewFirstDueDate] = useState(() => {
    const d = new Date();
    d.setMonth(d.getMonth() + 1);
    return d.toISOString().slice(0, 10);
  });
  const [newPeriod, setNewPeriod] = useState<"AYLIK" | "HAFTALIK">("AYLIK");
  const [err, setErr] = useState<string | null>(null);

  const restructure = useMutation({
    mutationFn: () =>
      api(`/api/v1/contracts/${contractId}/restructure`, {
        method: "POST",
        body: {
          newInstallmentCount: Number(newInstallmentCount),
          newFirstDueDate,
          newPeriod,
        },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    restructure.mutate();
  };

  return (
    <form onSubmit={submit} className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:grid-cols-4">
      <div className="text-sm font-medium text-slate-700 sm:col-span-4">
        Yeniden yapılandırma: açık taksitlerin kalan toplamı iptal edilip aşağıdaki yeni planla değiştirilir.
        Cari bakiye değişmez.
      </div>
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      <div>
        <label className="label">Yeni Taksit Sayısı</label>
        <input className="input" value={newInstallmentCount} onChange={(e) => setNewInstallmentCount(e.target.value)} />
      </div>
      <div>
        <label className="label">Yeni İlk Vade</label>
        <input type="date" className="input" value={newFirstDueDate} onChange={(e) => setNewFirstDueDate(e.target.value)} />
      </div>
      <div>
        <label className="label">Periyot</label>
        <select className="input" value={newPeriod} onChange={(e) => setNewPeriod(e.target.value as "AYLIK" | "HAFTALIK")}>
          <option value="AYLIK">Aylık</option>
          <option value="HAFTALIK">Haftalık</option>
        </select>
      </div>
      <div className="flex items-end gap-2">
        <button className="btn-primary" disabled={restructure.isPending || !newInstallmentCount}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

function NewContractForm({ onClose, onCreated }: { onClose: () => void; onCreated: (id: number) => void }) {
  const customers = useQuery({
    queryKey: ["parties", "MUSTERI", ""],
    queryFn: () => api<Page<PartyRow>>("/api/v1/parties?type=MUSTERI&size=100"),
  });
  const [partyId, setPartyId] = useState<number | "">("");
  const [description, setDescription] = useState("10 Seans Paket");
  const [lineTotal, setLineTotal] = useState("");
  const [downPayment, setDownPayment] = useState("0");
  const [installmentCount, setInstallmentCount] = useState("6");
  const [firstDueDate, setFirstDueDate] = useState(() => {
    const d = new Date();
    d.setMonth(d.getMonth() + 1);
    return d.toISOString().slice(0, 10);
  });
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api<ContractDetail>("/api/v1/contracts", {
        method: "POST",
        body: {
          partyId: Number(partyId),
          lines: [{ description, quantity: 1, unitPrice: Number(lineTotal) }],
          downPayment: Number(downPayment),
          installmentCount: Number(installmentCount),
          firstDueDate,
          period: "AYLIK",
        },
      }),
    onSuccess: (d) => onCreated(d.contract.id),
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
      <div className="sm:col-span-3">
        <label className="label">Müşteri</label>
        <select
          className="input"
          value={partyId}
          onChange={(e) => setPartyId(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">Seçin</option>
          {(customers.data?.content ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.code} — {p.title}
            </option>
          ))}
        </select>
      </div>
      <div className="sm:col-span-2">
        <label className="label">Paket / Hizmet Açıklaması</label>
        <input className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div>
        <label className="label">Toplam Tutar</label>
        <input className="input" value={lineTotal} onChange={(e) => setLineTotal(e.target.value)} />
      </div>
      <div>
        <label className="label">Peşinat</label>
        <input className="input" value={downPayment} onChange={(e) => setDownPayment(e.target.value)} />
      </div>
      <div>
        <label className="label">Taksit Sayısı</label>
        <input
          className="input"
          value={installmentCount}
          onChange={(e) => setInstallmentCount(e.target.value)}
        />
      </div>
      <div>
        <label className="label">İlk Vade</label>
        <input
          type="date"
          className="input"
          value={firstDueDate}
          onChange={(e) => setFirstDueDate(e.target.value)}
        />
      </div>
      <div className="flex items-end gap-2 sm:col-span-3">
        <button className="btn-primary" disabled={create.isPending || !partyId || !lineTotal}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

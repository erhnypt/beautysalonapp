import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

type InvoiceType = "ALIS" | "SATIS" | "PERAKENDE" | "IADE_ALIS" | "IADE_SATIS";
interface InvoiceView {
  id: number;
  type: InvoiceType;
  docNo: string;
  date: string;
  partyId: number;
  grandTotal: number;
  status: "DRAFT" | "CONFIRMED" | "VOIDED";
}
interface Page<T> {
  content: T[];
}
interface PartyRow {
  id: number;
  title: string;
}
interface ItemRow {
  id: number;
  code: string;
  name: string;
  type: "EMTIA" | "HIZMET";
  vatRate: number;
}
interface AccountView {
  id: number;
  name: string;
  kind: string;
}

const tl = (n: number) =>
  new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);
const chip: Record<string, string> = {
  CONFIRMED: "bg-green-100 text-green-700",
  VOIDED: "bg-slate-200 text-slate-500 line-through",
  DRAFT: "bg-amber-100 text-amber-700",
};

export function InvoicesPage() {
  const { has } = useAuth();
  const [showForm, setShowForm] = useState(false);
  const list = useQuery({
    queryKey: ["invoices"],
    queryFn: () => api<Page<InvoiceView>>("/api/v1/invoices?size=100"),
  });

  return (
    <div>
      <PageHeader
        title={t.nav.invoices}
        actions={
          has("INVOICE_ADD") && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              {t.common.add}
            </button>
          )
        }
      />
      {showForm && <NewInvoiceForm onClose={() => setShowForm(false)} />}
      {list.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {list.data && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">No</th>
                <th className="px-4 py-2 font-medium">Tür</th>
                <th className="px-4 py-2 font-medium">Tarih</th>
                <th className="px-4 py-2 text-right font-medium">Tutar</th>
                <th className="px-4 py-2 font-medium">Durum</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.data.content.map((i) => (
                <tr key={i.id}>
                  <td className="px-4 py-2 font-mono text-xs">{i.docNo}</td>
                  <td className="px-4 py-2">{i.type}</td>
                  <td className="px-4 py-2">{new Date(i.date).toLocaleDateString("tr-TR")}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{tl(i.grandTotal)}</td>
                  <td className="px-4 py-2">
                    <span className={`rounded px-1.5 py-0.5 text-xs ${chip[i.status]}`}>{i.status}</span>
                  </td>
                </tr>
              ))}
              {list.data.content.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
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

function NewInvoiceForm({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const parties = useQuery({
    queryKey: ["parties", "MUSTERI", ""],
    queryFn: () => api<Page<PartyRow>>("/api/v1/parties?type=MUSTERI&size=100"),
  });
  const items = useQuery({
    queryKey: ["stock-items", ""],
    queryFn: () => api<Page<ItemRow>>("/api/v1/stock/items?size=200"),
  });
  const accounts = useQuery({
    queryKey: ["fin-accounts"],
    queryFn: () => api<AccountView[]>("/api/v1/finance/accounts"),
  });

  const [type, setType] = useState<InvoiceType>("SATIS");
  const [partyId, setPartyId] = useState<number | "">("");
  const [itemId, setItemId] = useState<number | "">("");
  const [qty, setQty] = useState("1");
  const [price, setPrice] = useState("");
  const [vat, setVat] = useState("20");
  const [payMethod, setPayMethod] = useState<"CASH" | "CREDIT" | "CARD">("CASH");
  const [payAccountId, setPayAccountId] = useState<number | "">("");
  const [err, setErr] = useState<string | null>(null);

  const selectedItem = items.data?.content.find((i) => i.id === Number(itemId));

  const create = useMutation({
    mutationFn: () => {
      const total = Number(qty) * Number(price) * (1 + Number(vat) / 100);
      return api("/api/v1/invoices", {
        method: "POST",
        body: {
          type,
          partyId: Number(partyId),
          lines: [
            {
              itemId: itemId === "" ? null : Number(itemId),
              service: selectedItem?.type === "HIZMET" || itemId === "",
              description: selectedItem?.name ?? "Kalem",
              quantity: Number(qty),
              unitPrice: Number(price),
              discountRate: 0,
              vatRate: Number(vat),
            },
          ],
          payments:
            payMethod === "CREDIT"
              ? []
              : [
                  {
                    method: payMethod,
                    amount: Number(total.toFixed(2)),
                    accountId: payAccountId === "" ? null : Number(payAccountId),
                  },
                ],
        },
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["invoices"] });
      qc.invalidateQueries({ queryKey: ["fin-accounts"] });
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
    <form onSubmit={submit} className="card mb-4 grid gap-3 sm:grid-cols-4">
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      <div>
        <label className="label">Tür</label>
        <select className="input" value={type} onChange={(e) => setType(e.target.value as InvoiceType)}>
          <option value="SATIS">Satış</option>
          <option value="ALIS">Alış</option>
          <option value="PERAKENDE">Perakende</option>
          <option value="IADE_SATIS">İade (Satış)</option>
          <option value="IADE_ALIS">İade (Alış)</option>
        </select>
      </div>
      <div className="sm:col-span-3">
        <label className="label">Cari</label>
        <select className="input" value={partyId} onChange={(e) => setPartyId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Seçin</option>
          {(parties.data?.content ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </select>
      </div>
      <div className="sm:col-span-2">
        <label className="label">Kalem</label>
        <select
          className="input"
          value={itemId}
          onChange={(e) => {
            const v = e.target.value ? Number(e.target.value) : "";
            setItemId(v);
            const it = items.data?.content.find((x) => x.id === v);
            if (it) setVat(String(it.vatRate));
          }}
        >
          <option value="">(serbest kalem / hizmet)</option>
          {(items.data?.content ?? []).map((i) => (
            <option key={i.id} value={i.id}>
              {i.code} — {i.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Miktar</label>
        <input className="input" value={qty} onChange={(e) => setQty(e.target.value)} />
      </div>
      <div>
        <label className="label">Birim Fiyat</label>
        <input className="input" value={price} onChange={(e) => setPrice(e.target.value)} />
      </div>
      <div>
        <label className="label">KDV %</label>
        <input className="input" value={vat} onChange={(e) => setVat(e.target.value)} />
      </div>
      <div>
        <label className="label">Ödeme</label>
        <select className="input" value={payMethod} onChange={(e) => setPayMethod(e.target.value as any)}>
          <option value="CASH">Nakit</option>
          <option value="CARD">Kart (POS)</option>
          <option value="CREDIT">Vadeli (cari)</option>
        </select>
      </div>
      {payMethod !== "CREDIT" && (
        <div className="sm:col-span-2">
          <label className="label">Hesap</label>
          <select
            className="input"
            value={payAccountId}
            onChange={(e) => setPayAccountId(e.target.value ? Number(e.target.value) : "")}
          >
            <option value="">(varsayılan kasa)</option>
            {(accounts.data ?? [])
              .filter((a) => (payMethod === "CARD" ? a.kind === "POS" : a.kind === "KASA" || a.kind === "BANKA"))
              .map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
          </select>
        </div>
      )}
      <div className="flex items-end gap-2 sm:col-span-4">
        <button className="btn-primary" disabled={create.isPending || !partyId || !price}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

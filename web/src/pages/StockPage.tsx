import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface ItemRow {
  id: number;
  code: string;
  name: string;
  type: "EMTIA" | "HIZMET";
  brand: string | null;
  vatRate: number;
  active: boolean;
  totalOnHand: number;
  reorderLevel: number | null;
}
interface Page<T> {
  content: T[];
}
interface UnitView {
  id: number;
  code: string;
  name: string;
}
interface LevelView {
  warehouseId: number;
  qtyBase: number;
  avgCost: number;
}
interface ItemDetail {
  item: ItemRow;
  levels: LevelView[];
}

export function StockPage() {
  const { has } = useAuth();
  const canAdd = has("STOCK_ADD");
  const [q, setQ] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);

  const items = useQuery({
    queryKey: ["stock-items", q],
    queryFn: () => api<Page<ItemRow>>(`/api/v1/stock/items?q=${encodeURIComponent(q)}&size=100`),
  });

  return (
    <div>
      <PageHeader
        title={t.nav.stock}
        actions={
          canAdd && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              {t.common.add}
            </button>
          )
        }
      />

      {showForm && <NewItemForm onClose={() => setShowForm(false)} />}

      <input
        className="input mb-3 max-w-sm"
        placeholder={t.common.search}
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />

      {items.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {items.error && <div className="text-red-600">{(items.error as ApiError).message}</div>}

      {items.data && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">Kod</th>
                <th className="px-4 py-2 font-medium">Ad</th>
                <th className="px-4 py-2 font-medium">Tür</th>
                <th className="px-4 py-2 font-medium">Marka</th>
                <th className="px-4 py-2 text-right font-medium">KDV %</th>
                <th className="px-4 py-2 text-right font-medium">Toplam Stok</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.data.content.map((i) => (
                <tr
                  key={i.id}
                  className={`cursor-pointer hover:bg-slate-50 ${i.active ? "" : "opacity-50"}`}
                  onClick={() => setOpenId(openId === i.id ? null : i.id)}
                >
                  <td className="px-4 py-2 font-mono text-xs">{i.code}</td>
                  <td className="px-4 py-2 font-medium">{i.name}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`rounded px-1.5 py-0.5 text-xs ${
                        i.type === "HIZMET"
                          ? "bg-violet-100 text-violet-700"
                          : "bg-sky-100 text-sky-700"
                      }`}
                    >
                      {i.type === "HIZMET" ? "Hizmet" : "Ürün"}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-slate-500">{i.brand ?? "—"}</td>
                  <td className="px-4 py-2 text-right">{i.vatRate}</td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {i.type === "HIZMET" ? "—" : Number(i.totalOnHand).toLocaleString("tr-TR")}
                  </td>
                </tr>
              ))}
              {items.data.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                    {t.common.noRecords}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {openId && <ItemDetailPanel id={openId} onClose={() => setOpenId(null)} />}
    </div>
  );
}

function ItemDetailPanel({ id, onClose }: { id: number; onClose: () => void }) {
  const { has } = useAuth();
  const qc = useQueryClient();
  const canEdit = has("STOCK_EDIT");
  const [reorderLevel, setReorderLevel] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const { data } = useQuery({
    queryKey: ["stock-item", id],
    queryFn: () => api<ItemDetail>(`/api/v1/stock/items/${id}`),
  });

  const save = useMutation({
    mutationFn: () =>
      api(`/api/v1/stock/items/${id}/reorder-level`, {
        method: "POST",
        body: { reorderLevel: reorderLevel ? Number(reorderLevel) : null },
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["stock-item", id] });
      qc.invalidateQueries({ queryKey: ["stock-items"] });
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  if (!data) return null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    save.mutate();
  };

  return (
    <div className="mt-4 card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold">{data.item.name}</h2>
        <button className="text-sm text-slate-500 hover:underline" onClick={onClose}>
          Kapat
        </button>
      </div>

      <table className="w-full text-sm">
        <thead className="text-left text-slate-500">
          <tr>
            <th className="py-1 font-medium">Depo</th>
            <th className="py-1 text-right font-medium">Miktar</th>
            <th className="py-1 text-right font-medium">Ort. Maliyet</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {data.levels.map((l) => (
            <tr key={l.warehouseId}>
              <td className="py-1.5">#{l.warehouseId}</td>
              <td className="py-1.5 text-right tabular-nums">{Number(l.qtyBase).toLocaleString("tr-TR")}</td>
              <td className="py-1.5 text-right tabular-nums">{Number(l.avgCost).toLocaleString("tr-TR")}</td>
            </tr>
          ))}
          {data.levels.length === 0 && (
            <tr>
              <td colSpan={3} className="py-4 text-center text-slate-400">
                Depo hareketi yok
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {canEdit && (
        <form onSubmit={submit} className="mt-4 flex items-end gap-2 border-t border-slate-100 pt-3">
          {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
          <div>
            <label className="label">Yeniden Sipariş Seviyesi</label>
            <input
              className="input w-40"
              placeholder={data.item.reorderLevel != null ? String(data.item.reorderLevel) : "—"}
              value={reorderLevel}
              onChange={(e) => setReorderLevel(e.target.value)}
            />
          </div>
          <button className="btn-primary" disabled={save.isPending}>
            {t.common.save}
          </button>
        </form>
      )}
    </div>
  );
}

function NewItemForm({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const units = useQuery({ queryKey: ["units"], queryFn: () => api<UnitView[]>("/api/v1/stock/units") });
  const [name, setName] = useState("");
  const [type, setType] = useState<"EMTIA" | "HIZMET">("EMTIA");
  const [baseUnitCode, setBaseUnitCode] = useState("ADET");
  const [vatRate, setVatRate] = useState("20");
  const [salePrice, setSalePrice] = useState("");
  const [brand, setBrand] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/stock/items", {
        method: "POST",
        body: {
          name,
          type,
          baseUnitCode,
          vatRate: Number(vatRate),
          brand: brand || null,
          salePrice: salePrice ? Number(salePrice) : null,
        },
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["stock-items"] });
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
      {err && (
        <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-3">{err}</div>
      )}
      <div className="sm:col-span-2">
        <label className="label">Ad</label>
        <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div>
        <label className="label">Tür</label>
        <select
          className="input"
          value={type}
          onChange={(e) => {
            const v = e.target.value as "EMTIA" | "HIZMET";
            setType(v);
            setBaseUnitCode(v === "HIZMET" ? "SEANS" : "ADET");
          }}
        >
          <option value="EMTIA">Ürün</option>
          <option value="HIZMET">Hizmet</option>
        </select>
      </div>
      <div>
        <label className="label">Ana Birim</label>
        <select
          className="input"
          value={baseUnitCode}
          onChange={(e) => setBaseUnitCode(e.target.value)}
        >
          {(units.data ?? []).map((u) => (
            <option key={u.id} value={u.code}>
              {u.code} — {u.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">KDV %</label>
        <input className="input" value={vatRate} onChange={(e) => setVatRate(e.target.value)} />
      </div>
      <div>
        <label className="label">Satış Fiyatı</label>
        <input className="input" value={salePrice} onChange={(e) => setSalePrice(e.target.value)} />
      </div>
      <div>
        <label className="label">Marka</label>
        <input className="input" value={brand} onChange={(e) => setBrand(e.target.value)} />
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

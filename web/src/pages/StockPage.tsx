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
  salePrice: number | null;
  purchasePrice: number | null;
  categoryId: number | null;
}
interface Page<T> {
  content: T[];
}
interface CategoryView {
  id: number;
  parentId: number | null;
  name: string;
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
interface ItemUnitView {
  id: number;
  unitId: number;
  unitCode: string;
  factor: number;
  isBase: boolean;
}
interface ItemDetail {
  item: ItemRow;
  units: ItemUnitView[];
  levels: LevelView[];
}
interface WarehouseView {
  id: number;
  code: string;
  name: string;
  type: "WAREHOUSE" | "SHOWCASE" | "CONSUMPTION";
  isDefault: boolean;
}
interface MovementRow {
  id: number;
  date: string;
  itemCode: string;
  itemName: string;
  warehouseCode: string;
  direction: "IN" | "OUT";
  baseQty: number;
  unitCost: number | null;
  docType: string;
  docRef: string | null;
  note: string | null;
}

const tl = (n: number) =>
  new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);

function profit(salePrice: number | null, purchasePrice: number | null) {
  if (salePrice == null || purchasePrice == null) return null;
  return salePrice - purchasePrice;
}

export function StockPage() {
  const { has } = useAuth();
  const canAdd = has("STOCK_ADD");
  const [tab, setTab] = useState<"urunler" | "hareket">("urunler");
  const [q, setQ] = useState("");
  const [categoryId, setCategoryId] = useState<number | "">("");
  const [showForm, setShowForm] = useState(false);
  const [showCategoryForm, setShowCategoryForm] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);

  const items = useQuery({
    queryKey: ["stock-items", q, categoryId],
    queryFn: () =>
      api<Page<ItemRow>>(
        `/api/v1/stock/items?q=${encodeURIComponent(q)}&size=100${categoryId ? `&categoryId=${categoryId}` : ""}`,
      ),
  });
  const categories = useQuery({
    queryKey: ["stock-categories"],
    queryFn: () => api<CategoryView[]>("/api/v1/stock/categories"),
  });
  const categoryName = (id: number | null) =>
    id == null ? null : categories.data?.find((c) => c.id === id)?.name ?? `#${id}`;

  return (
    <div>
      <PageHeader
        title={t.nav.stock}
        actions={
          tab === "urunler" &&
          canAdd && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              {t.common.add}
            </button>
          )
        }
      />

      <div className="mb-4 flex gap-1 border-b border-slate-200">
        {[
          { key: "urunler" as const, label: "Ürünler" },
          { key: "hareket" as const, label: "Stok Hareketi" },
        ].map((it) => (
          <button
            key={it.key}
            onClick={() => setTab(it.key)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              tab === it.key
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            {it.label}
          </button>
        ))}
      </div>

      {tab === "hareket" ? (
        <StockMovementTab />
      ) : (
        <>
      {showForm && <NewItemForm categories={categories.data ?? []} onClose={() => setShowForm(false)} />}
      {showCategoryForm && (
        <NewCategoryForm
          categories={categories.data ?? []}
          onClose={() => setShowCategoryForm(false)}
        />
      )}

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <input
          className="input max-w-sm"
          placeholder={t.common.search}
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select
          className="input w-auto"
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">Tüm kategoriler</option>
          {(categories.data ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        {canAdd && (
          <button type="button" className="btn-ghost" onClick={() => setShowCategoryForm((v) => !v)}>
            Kategori Ekle
          </button>
        )}
      </div>

      {items.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {items.error && <div className="text-red-600">{(items.error as ApiError).message}</div>}

      {items.data && (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">Kod</th>
                <th className="px-4 py-2 font-medium">Ad</th>
                <th className="px-4 py-2 font-medium">Tür</th>
                <th className="px-4 py-2 font-medium">Kategori</th>
                <th className="px-4 py-2 font-medium">Marka</th>
                <th className="px-4 py-2 text-right font-medium">KDV %</th>
                <th className="px-4 py-2 text-right font-medium">Toplam Stok</th>
                <th className="px-4 py-2 text-right font-medium">Alış Fiyatı</th>
                <th className="px-4 py-2 text-right font-medium">Satış Fiyatı</th>
                <th className="px-4 py-2 text-right font-medium">Kâr</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.data.content.map((i) => {
                const kar = profit(i.salePrice, i.purchasePrice);
                return (
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
                    <td className="px-4 py-2 text-slate-500">{categoryName(i.categoryId) ?? "—"}</td>
                    <td className="px-4 py-2 text-slate-500">{i.brand ?? "—"}</td>
                    <td className="px-4 py-2 text-right">{i.vatRate}</td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {i.type === "HIZMET" ? "—" : Number(i.totalOnHand).toLocaleString("tr-TR")}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums text-slate-500">
                      {i.purchasePrice != null ? tl(i.purchasePrice) : "—"}
                    </td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {i.salePrice != null ? tl(i.salePrice) : "—"}
                    </td>
                    <td
                      className={`px-4 py-2 text-right tabular-nums font-medium ${
                        kar == null ? "text-slate-400" : kar >= 0 ? "text-green-700" : "text-red-700"
                      }`}
                    >
                      {kar != null ? tl(kar) : "—"}
                    </td>
                  </tr>
                );
              })}
              {items.data.content.length === 0 && (
                <tr>
                  <td colSpan={10} className="px-4 py-6 text-center text-slate-400">
                    {t.common.noRecords}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {openId && <ItemDetailPanel id={openId} onClose={() => setOpenId(null)} />}
        </>
      )}
    </div>
  );
}

const directionLabel: Record<string, string> = { IN: "Giriş", OUT: "Çıkış" };

function StockMovementTab() {
  const { has } = useAuth();
  const canAdd = has("STOCK_ADD");
  const qc = useQueryClient();
  const [mode, setMode] = useState<"IN" | "OUT" | "TRANSFER">("IN");
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [itemId, setItemId] = useState<number | "">("");
  const [unitId, setUnitId] = useState<number | "">("");
  const [warehouseId, setWarehouseId] = useState<number | "">("");
  const [fromWarehouseId, setFromWarehouseId] = useState<number | "">("");
  const [toWarehouseId, setToWarehouseId] = useState<number | "">("");
  const [quantity, setQuantity] = useState("");
  const [unitCost, setUnitCost] = useState("");
  const [note, setNote] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const items = useQuery({
    queryKey: ["stock-items", "all-for-movement"],
    queryFn: () => api<Page<ItemRow>>("/api/v1/stock/items?size=200"),
  });
  const warehouses = useQuery({
    queryKey: ["warehouses"],
    queryFn: () => api<WarehouseView[]>("/api/v1/stock/warehouses"),
  });
  const itemDetail = useQuery({
    queryKey: ["stock-item", itemId],
    queryFn: () => api<ItemDetail>(`/api/v1/stock/items/${itemId}`),
    enabled: itemId !== "",
  });
  const recent = useQuery({
    queryKey: ["stock-movements-recent"],
    queryFn: () => api<MovementRow[]>("/api/v1/stock/reports/movements"),
  });

  const units = itemDetail.data?.units ?? [];

  const reset = () => {
    setQuantity("");
    setUnitCost("");
    setNote("");
  };

  const submit = useMutation({
    mutationFn: () => {
      if (mode === "TRANSFER") {
        return api("/api/v1/stock/transfers", {
          method: "POST",
          body: {
            date,
            itemId: Number(itemId),
            fromWarehouseId: Number(fromWarehouseId),
            toWarehouseId: Number(toWarehouseId),
            unitId: Number(unitId),
            quantity: Number(quantity),
            note: note || null,
          },
        });
      }
      return api("/api/v1/stock/movements", {
        method: "POST",
        body: {
          date,
          warehouseId: Number(warehouseId),
          direction: mode,
          lines: [
            {
              itemId: Number(itemId),
              unitId: Number(unitId),
              quantity: Number(quantity),
              unitCost: mode === "IN" && unitCost ? Number(unitCost) : null,
              note: note || null,
            },
          ],
        },
      });
    },
    onSuccess: () => {
      reset();
      qc.invalidateQueries({ queryKey: ["stock-items"] });
      qc.invalidateQueries({ queryKey: ["stock-item", itemId] });
      qc.invalidateQueries({ queryKey: ["stock-movements-recent"] });
      qc.invalidateQueries({ queryKey: ["report-stock-status"] });
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    submit.mutate();
  };

  const canSubmit =
    itemId !== "" &&
    unitId !== "" &&
    quantity !== "" &&
    (mode === "TRANSFER" ? fromWarehouseId !== "" && toWarehouseId !== "" : warehouseId !== "");

  return (
    <div>
      {canAdd && (
        <form onSubmit={handleSubmit} className="card mb-4 grid gap-3 sm:grid-cols-4">
          {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
          <div>
            <label className="label">Hareket Türü</label>
            <select
              className="input"
              value={mode}
              onChange={(e) => {
                setMode(e.target.value as "IN" | "OUT" | "TRANSFER");
                setUnitCost("");
              }}
            >
              <option value="IN">Giriş (Alış)</option>
              <option value="OUT">Çıkış</option>
              <option value="TRANSFER">Transfer</option>
            </select>
          </div>
          <div>
            <label className="label">Tarih</label>
            <input type="date" className="input" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="sm:col-span-2">
            <label className="label">Ürün</label>
            <select
              className="input"
              value={itemId}
              onChange={(e) => {
                setItemId(e.target.value ? Number(e.target.value) : "");
                setUnitId("");
              }}
            >
              <option value="">Seçin</option>
              {(items.data?.content ?? [])
                .filter((i) => i.type === "EMTIA")
                .map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.code} — {i.name}
                  </option>
                ))}
            </select>
          </div>
          <div>
            <label className="label">Birim</label>
            <select className="input" value={unitId} onChange={(e) => setUnitId(e.target.value ? Number(e.target.value) : "")}>
              <option value="">Seçin</option>
              {units.map((u) => (
                <option key={u.unitId} value={u.unitId}>
                  {u.unitCode}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Miktar</label>
            <input className="input" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </div>
          {mode === "TRANSFER" ? (
            <>
              <div>
                <label className="label">Kaynak Depo</label>
                <select
                  className="input"
                  value={fromWarehouseId}
                  onChange={(e) => setFromWarehouseId(e.target.value ? Number(e.target.value) : "")}
                >
                  <option value="">Seçin</option>
                  {(warehouses.data ?? []).map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label">Hedef Depo</label>
                <select
                  className="input"
                  value={toWarehouseId}
                  onChange={(e) => setToWarehouseId(e.target.value ? Number(e.target.value) : "")}
                >
                  <option value="">Seçin</option>
                  {(warehouses.data ?? []).map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </select>
              </div>
            </>
          ) : (
            <>
              <div>
                <label className="label">Depo</label>
                <select
                  className="input"
                  value={warehouseId}
                  onChange={(e) => setWarehouseId(e.target.value ? Number(e.target.value) : "")}
                >
                  <option value="">Seçin</option>
                  {(warehouses.data ?? []).map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </select>
              </div>
              {mode === "IN" && (
                <div>
                  <label className="label">Birim Maliyet</label>
                  <input className="input" value={unitCost} onChange={(e) => setUnitCost(e.target.value)} />
                </div>
              )}
            </>
          )}
          <div className="sm:col-span-2">
            <label className="label">Not</label>
            <input className="input" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <div className="flex items-end sm:col-span-4">
            <button className="btn-primary" disabled={submit.isPending || !canSubmit}>
              {t.common.save}
            </button>
          </div>
        </form>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2 font-medium">Tarih</th>
              <th className="px-4 py-2 font-medium">Ürün</th>
              <th className="px-4 py-2 font-medium">Depo</th>
              <th className="px-4 py-2 font-medium">Yön</th>
              <th className="px-4 py-2 text-right font-medium">Miktar</th>
              <th className="px-4 py-2 text-right font-medium">Birim Maliyet</th>
              <th className="px-4 py-2 font-medium">Not</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {(recent.data ?? []).slice(0, 30).map((m) => (
              <tr key={m.id}>
                <td className="px-4 py-2">{new Date(m.date).toLocaleDateString("tr-TR")}</td>
                <td className="px-4 py-2 font-medium">{m.itemName}</td>
                <td className="px-4 py-2 text-slate-500">{m.warehouseCode}</td>
                <td className="px-4 py-2">
                  <span
                    className={`rounded px-1.5 py-0.5 text-xs ${
                      m.direction === "IN" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
                    }`}
                  >
                    {directionLabel[m.direction] ?? m.direction}
                  </span>
                </td>
                <td className="px-4 py-2 text-right tabular-nums">{Number(m.baseQty).toLocaleString("tr-TR")}</td>
                <td className="px-4 py-2 text-right tabular-nums">
                  {m.unitCost != null ? tl(m.unitCost) : "—"}
                </td>
                <td className="px-4 py-2 text-slate-500">{m.note ?? "—"}</td>
              </tr>
            ))}
            {(recent.data ?? []).length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-6 text-center text-slate-400">
                  {t.common.noRecords}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ItemDetailPanel({ id, onClose }: { id: number; onClose: () => void }) {
  const { has } = useAuth();
  const qc = useQueryClient();
  const canEdit = has("STOCK_EDIT");
  const [reorderLevel, setReorderLevel] = useState("");
  const [salePrice, setSalePrice] = useState("");
  const [purchasePrice, setPurchasePrice] = useState("");
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

  const savePrices = useMutation({
    mutationFn: () =>
      api(`/api/v1/stock/items/${id}/prices`, {
        method: "POST",
        body: {
          // Boş bırakılan alan mevcut değerini korur — ikisi tek "Kaydet" ile
          // gönderildiğinden, diğerini düzenlerken birini sıfırlamamak için.
          salePrice: salePrice !== "" ? Number(salePrice) : (data?.item.salePrice ?? null),
          purchasePrice: purchasePrice !== "" ? Number(purchasePrice) : (data?.item.purchasePrice ?? null),
        },
      }),
    onSuccess: () => {
      setSalePrice("");
      setPurchasePrice("");
      qc.invalidateQueries({ queryKey: ["stock-item", id] });
      qc.invalidateQueries({ queryKey: ["stock-items"] });
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  if (!data) return null;

  const kar = profit(data.item.salePrice, data.item.purchasePrice);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    save.mutate();
  };

  const submitPrices = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    savePrices.mutate();
  };

  return (
    <div className="mt-4 card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold">{data.item.name}</h2>
        <button className="text-sm text-slate-500 hover:underline" onClick={onClose}>
          Kapat
        </button>
      </div>

      <dl className="mb-4 grid grid-cols-3 gap-x-6 gap-y-2 text-sm">
        <div>
          <dt className="text-slate-500">Alış Fiyatı</dt>
          <dd>{data.item.purchasePrice != null ? tl(data.item.purchasePrice) : "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Satış Fiyatı</dt>
          <dd>{data.item.salePrice != null ? tl(data.item.salePrice) : "—"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Kâr</dt>
          <dd className={kar == null ? "" : kar >= 0 ? "text-green-700" : "text-red-700"}>
            {kar != null ? tl(kar) : "—"}
          </dd>
        </div>
      </dl>

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
        <>
          {err && <div className="mt-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
          <form onSubmit={submitPrices} className="mt-4 flex items-end gap-2 border-t border-slate-100 pt-3">
            <div>
              <label className="label">Alış Fiyatı</label>
              <input
                className="input w-32"
                placeholder={data.item.purchasePrice != null ? String(data.item.purchasePrice) : "—"}
                value={purchasePrice}
                onChange={(e) => setPurchasePrice(e.target.value)}
              />
            </div>
            <div>
              <label className="label">Satış Fiyatı</label>
              <input
                className="input w-32"
                placeholder={data.item.salePrice != null ? String(data.item.salePrice) : "—"}
                value={salePrice}
                onChange={(e) => setSalePrice(e.target.value)}
              />
            </div>
            <button className="btn-primary" disabled={savePrices.isPending}>
              {t.common.save}
            </button>
          </form>
          <form onSubmit={submit} className="mt-2 flex items-end gap-2">
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
        </>
      )}
    </div>
  );
}

function NewCategoryForm({ categories, onClose }: { categories: CategoryView[]; onClose: () => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [parentId, setParentId] = useState<number | "">("");
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/stock/categories", {
        method: "POST",
        body: { name, parentId: parentId ? Number(parentId) : null },
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["stock-categories"] });
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
      <div className="sm:col-span-3 text-sm font-semibold">Yeni Kategori</div>
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-3">{err}</div>}
      <div>
        <label className="label">Ad</label>
        <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div>
        <label className="label">Üst Kategori</label>
        <select
          className="input"
          value={parentId}
          onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">— (ana kategori)</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      <div className="flex items-end gap-2">
        <button className="btn-primary" disabled={create.isPending || !name}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

function NewItemForm({ categories, onClose }: { categories: CategoryView[]; onClose: () => void }) {
  const qc = useQueryClient();
  const units = useQuery({ queryKey: ["units"], queryFn: () => api<UnitView[]>("/api/v1/stock/units") });
  const [name, setName] = useState("");
  const [type, setType] = useState<"EMTIA" | "HIZMET">("EMTIA");
  const [baseUnitCode, setBaseUnitCode] = useState("ADET");
  const [vatRate, setVatRate] = useState("20");
  const [salePrice, setSalePrice] = useState("");
  const [purchasePrice, setPurchasePrice] = useState("");
  const [brand, setBrand] = useState("");
  const [categoryId, setCategoryId] = useState<number | "">("");
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
          purchasePrice: purchasePrice ? Number(purchasePrice) : null,
          categoryId: categoryId ? Number(categoryId) : null,
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
        <label className="label">Alış Fiyatı</label>
        <input className="input" value={purchasePrice} onChange={(e) => setPurchasePrice(e.target.value)} />
      </div>
      <div>
        <label className="label">Satış Fiyatı</label>
        <input className="input" value={salePrice} onChange={(e) => setSalePrice(e.target.value)} />
      </div>
      <div>
        <label className="label">Marka</label>
        <input className="input" value={brand} onChange={(e) => setBrand(e.target.value)} />
      </div>
      <div>
        <label className="label">Kategori</label>
        <select
          className="input"
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">—</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
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

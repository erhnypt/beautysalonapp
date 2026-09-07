import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { DataTable, type Column } from "../components/DataTable";
import { t } from "../lib/i18n";

const tl = (n: number | null | undefined) =>
  n == null ? "—" : new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);
const pct = (n: number | null | undefined) => (n == null ? "—" : `%${Number(n).toFixed(2)}`);
const dt = (s: string | null | undefined) => (s ? new Date(s).toLocaleDateString("tr-TR") : "—");
const todayStr = () => new Date().toISOString().slice(0, 10);
const monthAgo = () => new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
const ym = () => new Date().toISOString().slice(0, 7);

type ModuleKey = "stok" | "cari" | "gelir-gider" | "personel";

const MODULES: { key: ModuleKey; label: string }[] = [
  { key: "stok", label: "Stok" },
  { key: "cari", label: "Cari" },
  { key: "gelir-gider", label: "Gelir-Gider" },
  { key: "personel", label: "Personel" },
];

export function ReportsPage() {
  const [mod, setMod] = useState<ModuleKey>("stok");
  return (
    <div>
      <PageHeader title={t.nav.reports} />
      <div className="mb-4 flex gap-1 border-b border-slate-200">
        {MODULES.map((m) => (
          <button
            key={m.key}
            onClick={() => setMod(m.key)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              mod === m.key
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            {m.label}
          </button>
        ))}
      </div>
      {mod === "stok" && <StockReports />}
      {mod === "cari" && <PartyReports />}
      {mod === "gelir-gider" && <FinanceReports />}
      {mod === "personel" && <StaffReports />}
    </div>
  );
}

function ReportPicker({
  options,
  value,
  onChange,
}: {
  options: { key: string; label: string }[];
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <select className="input mb-3 w-auto" value={value} onChange={(e) => onChange(e.target.value)}>
      {options.map((o) => (
        <option key={o.key} value={o.key}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

// --- Stok --------------------------------------------------------------

interface StockStatusRow {
  itemId: number; itemCode: string; itemName: string;
  warehouseCode: string; warehouseType: string; qtyBase: number; avgCost: number; stockValue: number;
}
interface MovementRow {
  id: number; date: string; itemCode: string; itemName: string; warehouseCode: string;
  direction: "IN" | "OUT"; baseQty: number; unitCost: number; docType: string; docRef: string | null;
}
interface CriticalStockRow { itemId: number; itemCode: string; itemName: string; onHand: number; reorderLevel: number; }
interface TurnoverRow { itemId: number; itemCode: string; itemName: string; issuedQty: number; currentOnHand: number; turnoverRatio: number | null; }
interface DeadStockRow { itemId: number; itemCode: string; itemName: string; onHand: number; lastMovementDate: string | null; daysSinceLastMovement: number | null; }
interface InventoryValueRow { warehouseId: number; warehouseCode: string; warehouseName: string; warehouseType: string; totalQty: number; totalValue: number; }
interface WarehouseComparisonRow { warehouseType: string; warehouseCount: number; distinctItems: number; totalQty: number; totalValue: number; }

const STOCK_REPORTS = [
  { key: "durum", label: "Stok Durumu" },
  { key: "hareket", label: "Hareket Dökümü" },
  { key: "kritik", label: "Kritik Stok" },
  { key: "devir", label: "Devir Hızı" },
  { key: "olu", label: "Ölü Stok" },
  { key: "deger", label: "Envanter Değeri" },
  { key: "depo", label: "Depo/Vitrin Karşılaştırma" },
];

function StockReports() {
  const [report, setReport] = useState("durum");
  const [from, setFrom] = useState(monthAgo());
  const [to, setTo] = useState(todayStr());
  const needsDates = report === "hareket" || report === "devir";

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3">
        <ReportPicker options={STOCK_REPORTS} value={report} onChange={setReport} />
        {needsDates && (
          <>
            <input type="date" className="input w-auto" value={from} onChange={(e) => setFrom(e.target.value)} />
            <span className="text-slate-400">—</span>
            <input type="date" className="input w-auto" value={to} onChange={(e) => setTo(e.target.value)} />
          </>
        )}
      </div>
      {report === "durum" && <StockStatusReport />}
      {report === "hareket" && <MovementReport from={from} to={to} />}
      {report === "kritik" && <CriticalStockReport />}
      {report === "devir" && <TurnoverReport from={from} to={to} />}
      {report === "olu" && <DeadStockReport />}
      {report === "deger" && <InventoryValueReport />}
      {report === "depo" && <WarehouseComparisonReport />}
    </div>
  );
}

function StockStatusReport() {
  const { data, isLoading } = useQuery({
    queryKey: ["report-stock-status"],
    queryFn: () => api<StockStatusRow[]>("/api/v1/stock/reports/stock-status"),
  });
  const columns: Column<StockStatusRow>[] = [
    { key: "itemCode", label: "Kod" },
    { key: "itemName", label: "Ürün" },
    { key: "warehouseCode", label: "Depo" },
    { key: "qtyBase", label: "Miktar", align: "right" },
    { key: "avgCost", label: "Ort. Maliyet", align: "right", render: (r) => tl(r.avgCost) },
    { key: "stockValue", label: "Değer", align: "right", render: (r) => tl(r.stockValue) },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => `${r.itemId}-${r.warehouseCode}`} />;
}

function MovementReport({ from, to }: { from: string; to: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ["report-stock-movements", from, to],
    queryFn: () => api<MovementRow[]>(`/api/v1/stock/reports/movements?from=${from}&to=${to}`),
  });
  const columns: Column<MovementRow>[] = [
    { key: "date", label: "Tarih", render: (r) => dt(r.date) },
    { key: "itemName", label: "Ürün" },
    { key: "warehouseCode", label: "Depo" },
    { key: "direction", label: "Yön", render: (r) => (r.direction === "IN" ? "Giriş" : "Çıkış") },
    { key: "baseQty", label: "Miktar", align: "right" },
    { key: "docType", label: "Belge", render: (r) => `${r.docType}${r.docRef ? " #" + r.docRef : ""}` },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.id} />;
}

function CriticalStockReport() {
  const { data, isLoading } = useQuery({
    queryKey: ["report-critical-stock"],
    queryFn: () => api<CriticalStockRow[]>("/api/v1/stock/reports/critical-stock"),
  });
  const columns: Column<CriticalStockRow>[] = [
    { key: "itemCode", label: "Kod" },
    { key: "itemName", label: "Ürün" },
    { key: "onHand", label: "Mevcut", align: "right" },
    { key: "reorderLevel", label: "Kritik Seviye", align: "right" },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return (
    <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.itemId}
      emptyLabel="Kritik stok seviyesinde ürün yok (veya hiçbir üründe kritik seviye tanımlı değil)" />
  );
}

function TurnoverReport({ from, to }: { from: string; to: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ["report-turnover", from, to],
    queryFn: () => api<TurnoverRow[]>(`/api/v1/stock/reports/turnover?from=${from}&to=${to}`),
  });
  const columns: Column<TurnoverRow>[] = [
    { key: "itemCode", label: "Kod" },
    { key: "itemName", label: "Ürün" },
    { key: "issuedQty", label: "Çıkış (dönem)", align: "right" },
    { key: "currentOnHand", label: "Mevcut Stok", align: "right" },
    { key: "turnoverRatio", label: "Devir Hızı", align: "right", render: (r) => (r.turnoverRatio == null ? "—" : r.turnoverRatio.toFixed(2)) },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.itemId} />;
}

function DeadStockReport() {
  const [idleDays, setIdleDays] = useState("90");
  const { data, isLoading } = useQuery({
    queryKey: ["report-dead-stock", idleDays],
    queryFn: () => api<DeadStockRow[]>(`/api/v1/stock/reports/dead-stock?idleDays=${idleDays}`),
  });
  const columns: Column<DeadStockRow>[] = [
    { key: "itemCode", label: "Kod" },
    { key: "itemName", label: "Ürün" },
    { key: "onHand", label: "Mevcut", align: "right" },
    { key: "lastMovementDate", label: "Son Hareket", render: (r) => dt(r.lastMovementDate) },
    { key: "daysSinceLastMovement", label: "Gün", align: "right", render: (r) => r.daysSinceLastMovement ?? "hiç" },
  ];
  return (
    <div>
      <div className="mb-3 flex items-center gap-2 text-sm">
        <label className="text-slate-600">Eşik (gün):</label>
        <input className="input w-24" value={idleDays} onChange={(e) => setIdleDays(e.target.value)} />
      </div>
      {isLoading ? <div className="text-slate-500">{t.common.loading}</div> : (
        <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.itemId} />
      )}
    </div>
  );
}

function InventoryValueReport() {
  const { data, isLoading } = useQuery({
    queryKey: ["report-inventory-value"],
    queryFn: () => api<InventoryValueRow[]>("/api/v1/stock/reports/inventory-value"),
  });
  const columns: Column<InventoryValueRow>[] = [
    { key: "warehouseCode", label: "Depo" },
    { key: "warehouseName", label: "Ad" },
    { key: "warehouseType", label: "Tür" },
    { key: "totalQty", label: "Toplam Miktar", align: "right" },
    { key: "totalValue", label: "Toplam Değer", align: "right", render: (r) => tl(r.totalValue) },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.warehouseId} />;
}

function WarehouseComparisonReport() {
  const { data, isLoading } = useQuery({
    queryKey: ["report-warehouse-comparison"],
    queryFn: () => api<WarehouseComparisonRow[]>("/api/v1/stock/reports/warehouse-comparison"),
  });
  const columns: Column<WarehouseComparisonRow>[] = [
    { key: "warehouseType", label: "Depo Türü" },
    { key: "warehouseCount", label: "Depo Sayısı", align: "right" },
    { key: "distinctItems", label: "Ürün Çeşidi", align: "right" },
    { key: "totalQty", label: "Toplam Miktar", align: "right" },
    { key: "totalValue", label: "Toplam Değer", align: "right", render: (r) => tl(r.totalValue) },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.warehouseType} />;
}

// --- Cari ----------------------------------------------------------------

interface AgingRow { partyId: number; code: string; title: string; days0to30: number; days31to60: number; days61to90: number; days90plus: number; total: number; }
interface BirthdayRow { partyId: number; code: string; title: string; birthDate: string; daysUntil: number; }
interface LostCustomerRow { partyId: number; code: string; title: string; lastSeen: string | null; daysSinceLastSeen: number | null; }

const PARTY_REPORTS = [
  { key: "yaslandirma", label: "Yaşlandırma (0-30-60-90+)" },
  { key: "dogumgunu", label: "Doğum Günü" },
  { key: "kayip", label: "Kayıp Müşteri" },
];

function PartyReports() {
  const [report, setReport] = useState("yaslandirma");
  return (
    <div>
      <ReportPicker options={PARTY_REPORTS} value={report} onChange={setReport} />
      {report === "yaslandirma" && <AgingReport />}
      {report === "dogumgunu" && <BirthdayReport />}
      {report === "kayip" && <LostCustomerReport />}
    </div>
  );
}

function AgingReport() {
  const { data, isLoading } = useQuery({
    queryKey: ["report-aging"],
    queryFn: () => api<AgingRow[]>("/api/v1/parties/reports/aging"),
  });
  const columns: Column<AgingRow>[] = [
    { key: "code", label: "Kod" },
    { key: "title", label: "Ünvan" },
    { key: "days0to30", label: "0-30", align: "right", render: (r) => tl(r.days0to30) },
    { key: "days31to60", label: "31-60", align: "right", render: (r) => tl(r.days31to60) },
    { key: "days61to90", label: "61-90", align: "right", render: (r) => tl(r.days61to90) },
    { key: "days90plus", label: "90+", align: "right", render: (r) => tl(r.days90plus) },
    { key: "total", label: "Toplam", align: "right", render: (r) => <b>{tl(r.total)}</b> },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.partyId} emptyLabel="Borçlu cari yok" />;
}

function BirthdayReport() {
  const [withinDays, setWithinDays] = useState("30");
  const { data, isLoading } = useQuery({
    queryKey: ["report-birthdays", withinDays],
    queryFn: () => api<BirthdayRow[]>(`/api/v1/parties/reports/birthdays?withinDays=${withinDays}`),
  });
  const columns: Column<BirthdayRow>[] = [
    { key: "code", label: "Kod" },
    { key: "title", label: "Ünvan" },
    { key: "birthDate", label: "Doğum Tarihi", render: (r) => dt(r.birthDate) },
    { key: "daysUntil", label: "Kalan Gün", align: "right" },
  ];
  return (
    <div>
      <div className="mb-3 flex items-center gap-2 text-sm">
        <label className="text-slate-600">Kaç gün içinde:</label>
        <input className="input w-24" value={withinDays} onChange={(e) => setWithinDays(e.target.value)} />
      </div>
      {isLoading ? <div className="text-slate-500">{t.common.loading}</div> : (
        <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.partyId} />
      )}
    </div>
  );
}

function LostCustomerReport() {
  const [idleDays, setIdleDays] = useState("90");
  const { data, isLoading } = useQuery({
    queryKey: ["report-lost-customers", idleDays],
    queryFn: () => api<LostCustomerRow[]>(`/api/v1/dashboard/reports/lost-customers?idleDays=${idleDays}`),
  });
  const columns: Column<LostCustomerRow>[] = [
    { key: "code", label: "Kod" },
    { key: "title", label: "Ünvan" },
    { key: "lastSeen", label: "Son Görülme", render: (r) => dt(r.lastSeen) },
    { key: "daysSinceLastSeen", label: "Gün", align: "right", render: (r) => r.daysSinceLastSeen ?? "hiç" },
  ];
  return (
    <div>
      <div className="mb-3 flex items-center gap-2 text-sm">
        <label className="text-slate-600">Eşik (gün):</label>
        <input className="input w-24" value={idleDays} onChange={(e) => setIdleDays(e.target.value)} />
      </div>
      {isLoading ? <div className="text-slate-500">{t.common.loading}</div> : (
        <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.partyId} />
      )}
    </div>
  );
}

// --- Gelir-Gider -----------------------------------------------------------

interface IncomeExpenseRow { cardId: number; code: string; name: string; direction: "INCOME" | "EXPENSE" | null; amount: number; budget: number | null; }
interface BudgetRow { cardId: number; code: string; name: string; direction: string; budget: number; actual: number; variance: number; usagePercent: number | null; }

const FINANCE_REPORTS = [
  { key: "gelirgider", label: "Gelir-Gider Tablosu" },
  { key: "butce", label: "Bütçe/Gerçekleşen" },
];

function FinanceReports() {
  const [report, setReport] = useState("gelirgider");
  const [from, setFrom] = useState(monthAgo());
  const [to, setTo] = useState(todayStr());
  return (
    <div>
      <div className="flex flex-wrap items-center gap-3">
        <ReportPicker options={FINANCE_REPORTS} value={report} onChange={setReport} />
        <input type="date" className="input w-auto" value={from} onChange={(e) => setFrom(e.target.value)} />
        <span className="text-slate-400">—</span>
        <input type="date" className="input w-auto" value={to} onChange={(e) => setTo(e.target.value)} />
      </div>
      {report === "gelirgider" && <IncomeExpenseReport from={from} to={to} />}
      {report === "butce" && <BudgetReport from={from} to={to} />}
    </div>
  );
}

function IncomeExpenseReport({ from, to }: { from: string; to: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ["report-income-expense", from, to],
    queryFn: () => api<IncomeExpenseRow[]>(`/api/v1/finance/reports/income-expense?from=${from}&to=${to}`),
  });
  const columns: Column<IncomeExpenseRow>[] = [
    { key: "code", label: "Kart" },
    { key: "name", label: "Ad" },
    { key: "direction", label: "Yön", render: (r) => r.direction === "INCOME" ? "Gelir" : r.direction === "EXPENSE" ? "Gider" : "—" },
    { key: "amount", label: "Tutar", align: "right", render: (r) => tl(r.amount) },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.cardId} />;
}

function BudgetReport({ from, to }: { from: string; to: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ["report-budget", from, to],
    queryFn: () => api<BudgetRow[]>(`/api/v1/finance/reports/budget?from=${from}&to=${to}`),
  });
  const columns: Column<BudgetRow>[] = [
    { key: "code", label: "Kart" },
    { key: "name", label: "Ad" },
    { key: "budget", label: "Bütçe", align: "right", render: (r) => tl(r.budget) },
    { key: "actual", label: "Gerçekleşen", align: "right", render: (r) => tl(r.actual) },
    { key: "variance", label: "Fark", align: "right", render: (r) => tl(r.variance) },
    { key: "usagePercent", label: "Kullanım", align: "right", render: (r) => pct(r.usagePercent) },
  ];
  if (isLoading) return <div className="text-slate-500">{t.common.loading}</div>;
  return <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.cardId} emptyLabel="Bütçesi tanımlı kart yok" />;
}

// --- Personel --------------------------------------------------------------

interface PerfRow { staffId: number; cnt: number; base: number; commission: number; }
interface SalaryReconciliationRow {
  staffId: number; staffTitle: string; periodYm: string; grossSalary: number | null; deductions: number | null;
  netSalary: number | null; advancesInPeriod: number; commissionsAccrued: number; commissionsPaid: number;
  netPayable: number | null; periodLocked: boolean;
}

const STAFF_REPORTS = [
  { key: "performans", label: "Personel Performansı" },
  { key: "mutabakat", label: "Maaş/Avans Mutabakatı" },
];

function StaffReports() {
  const [report, setReport] = useState("performans");
  return (
    <div>
      <ReportPicker options={STAFF_REPORTS} value={report} onChange={setReport} />
      {report === "performans" && <PerformanceReport />}
      {report === "mutabakat" && <SalaryReconciliationReport />}
    </div>
  );
}

function PerformanceReport() {
  const [fromDay, setFromDay] = useState(monthAgo());
  const [toDay, setToDay] = useState(todayStr());
  const from = `${fromDay}T00:00:00Z`;
  const to = `${toDay}T23:59:59Z`;
  const { data, isLoading } = useQuery({
    queryKey: ["report-staff-performance", from, to],
    queryFn: () =>
      api<PerfRow[]>(`/api/v1/staff/reports/performance?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  });
  const columns: Column<PerfRow>[] = [
    { key: "staffId", label: "Personel #" },
    { key: "cnt", label: "İşlem Sayısı", align: "right" },
    { key: "base", label: "Ciro (baz)", align: "right", render: (r) => tl(r.base) },
    { key: "commission", label: "Prim", align: "right", render: (r) => tl(r.commission) },
  ];
  return (
    <div>
      <div className="mb-3 flex items-center gap-3">
        <input type="date" className="input w-auto" value={fromDay} onChange={(e) => setFromDay(e.target.value)} />
        <span className="text-slate-400">—</span>
        <input type="date" className="input w-auto" value={toDay} onChange={(e) => setToDay(e.target.value)} />
      </div>
      {isLoading ? <div className="text-slate-500">{t.common.loading}</div> : (
        <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.staffId} />
      )}
    </div>
  );
}

function SalaryReconciliationReport() {
  const [period, setPeriod] = useState(ym());
  const { data, isLoading } = useQuery({
    queryKey: ["report-salary-reconciliation", period],
    queryFn: () => api<SalaryReconciliationRow[]>(`/api/v1/staff/reports/salary-reconciliation?period=${period}`),
  });
  const columns: Column<SalaryReconciliationRow>[] = [
    { key: "staffTitle", label: "Personel" },
    { key: "grossSalary", label: "Brüt", align: "right", render: (r) => tl(r.grossSalary) },
    { key: "netSalary", label: "Net", align: "right", render: (r) => tl(r.netSalary) },
    { key: "advancesInPeriod", label: "Avans", align: "right", render: (r) => tl(r.advancesInPeriod) },
    { key: "commissionsAccrued", label: "Prim (Tahakkuk)", align: "right", render: (r) => tl(r.commissionsAccrued) },
    { key: "commissionsPaid", label: "Prim (Ödenen)", align: "right", render: (r) => tl(r.commissionsPaid) },
    { key: "netPayable", label: "Net Ödenecek", align: "right", render: (r) => tl(r.netPayable) },
    { key: "periodLocked", label: "Dönem", render: (r) => (r.periodLocked ? "Kapalı" : "Açık") },
  ];
  return (
    <div>
      <div className="mb-3 flex items-center gap-2 text-sm">
        <label className="text-slate-600">Dönem (YYYY-MM):</label>
        <input className="input w-32" value={period} onChange={(e) => setPeriod(e.target.value)} />
      </div>
      {isLoading ? <div className="text-slate-500">{t.common.loading}</div> : (
        <DataTable columns={columns} rows={data ?? []} keyFn={(r) => r.staffId}
          emptyLabel="Bu dönemde avans/prim hareketi veya kapatılmış dönem yok" />
      )}
    </div>
  );
}

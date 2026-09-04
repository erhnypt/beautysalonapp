import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface BankAccount {
  id: number;
  code: string;
  name: string;
  currency: string;
}
interface StatementView {
  id: number;
  finAccountId: number;
  sourceFormat: "MT940" | "CSV";
  statementRef: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  openingBalance: number | null;
  closingBalance: number | null;
  lineCount: number;
  matchedCount: number;
  originalFilename: string | null;
  importedAt: string;
  status: "IMPORTED" | "RECONCILED";
}
interface MatchCandidate {
  txnId: number;
  score: number;
  reason: string;
}
interface LineView {
  id: number;
  lineNo: number;
  valueDate: string;
  amount: number;
  currency: string;
  description: string | null;
  counterparty: string | null;
  bankRef: string | null;
  matchStatus: "UNMATCHED" | "MATCHED" | "IGNORED" | "CREATED";
  matchedTxnId: number | null;
  matchScore: number | null;
  note: string | null;
  suggestions: MatchCandidate[];
}
interface CardView {
  id: number;
  code: string;
  name: string;
  direction: "INCOME" | "EXPENSE";
  postable: boolean;
}

const tl = (n: number | null) =>
  n == null ? "—" : new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);

const statusChip: Record<string, string> = {
  IMPORTED: "bg-amber-100 text-amber-700",
  RECONCILED: "bg-green-100 text-green-700",
};
const lineChip: Record<string, string> = {
  UNMATCHED: "bg-slate-100 text-slate-600",
  MATCHED: "bg-sky-100 text-sky-700",
  CREATED: "bg-violet-100 text-violet-700",
  IGNORED: "bg-slate-200 text-slate-400",
};

export function BankReconciliationPage() {
  const { has } = useAuth();
  const qc = useQueryClient();
  const canEdit = has("FINANCE_EDIT");

  const [accountId, setAccountId] = useState<number | "">("");
  const [format, setFormat] = useState<"CSV" | "MT940">("CSV");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const accounts = useQuery({
    queryKey: ["br-accounts"],
    queryFn: () => api<BankAccount[]>("/api/v1/bank-reconciliation/accounts"),
  });
  const statements = useQuery({
    queryKey: ["br-statements"],
    queryFn: () => api<StatementView[]>("/api/v1/bank-reconciliation"),
  });
  const detail = useQuery({
    queryKey: ["br-detail", selectedId],
    queryFn: () => api<{ statement: StatementView; lines: LineView[] }>(`/api/v1/bank-reconciliation/${selectedId}`),
    enabled: selectedId != null,
  });
  const cards = useQuery({
    queryKey: ["fin-cards"],
    queryFn: () => api<CardView[]>("/api/v1/finance/cards"),
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["br-statements"] });
    qc.invalidateQueries({ queryKey: ["br-detail", selectedId] });
  };

  const importMut = useMutation({
    mutationFn: async () => {
      const f = fileRef.current?.files?.[0];
      if (!f) throw new Error("Dosya seçin");
      if (!accountId) throw new Error("Hesap seçin");
      const fd = new FormData();
      fd.append("file", f);
      fd.append("finAccountId", String(accountId));
      fd.append("format", format);
      return api<StatementView>("/api/v1/bank-reconciliation/import", { method: "POST", body: fd });
    },
    onSuccess: (s) => {
      setErr(null);
      if (fileRef.current) fileRef.current.value = "";
      qc.invalidateQueries({ queryKey: ["br-statements"] });
      setSelectedId(s.id);
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : (e as Error).message),
  });

  const matchMut = useMutation({
    mutationFn: (v: { lineId: number; txnId: number }) =>
      api(`/api/v1/bank-reconciliation/lines/${v.lineId}/match`, { method: "POST", body: { txnId: v.txnId } }),
    onSuccess: invalidate,
  });
  const unmatchMut = useMutation({
    mutationFn: (lineId: number) => api(`/api/v1/bank-reconciliation/lines/${lineId}/unmatch`, { method: "POST" }),
    onSuccess: invalidate,
  });
  const ignoreMut = useMutation({
    mutationFn: (v: { lineId: number; note: string }) =>
      api(`/api/v1/bank-reconciliation/lines/${v.lineId}/ignore`, { method: "POST", body: { note: v.note } }),
    onSuccess: invalidate,
  });
  const createTxnMut = useMutation({
    mutationFn: (v: { lineId: number; incomeExpenseCardId: number | null; description: string }) =>
      api(`/api/v1/bank-reconciliation/lines/${v.lineId}/create-transaction`, {
        method: "POST",
        body: { incomeExpenseCardId: v.incomeExpenseCardId, description: v.description || null },
      }),
    onSuccess: invalidate,
  });
  const autoMatchMut = useMutation({
    mutationFn: (statementId: number) =>
      api<{ matched: number }>(`/api/v1/bank-reconciliation/${statementId}/auto-match`, { method: "POST" }),
    onSuccess: invalidate,
  });
  const deleteMut = useMutation({
    mutationFn: (statementId: number) => api(`/api/v1/bank-reconciliation/${statementId}`, { method: "DELETE" }),
    onSuccess: () => {
      setSelectedId(null);
      qc.invalidateQueries({ queryKey: ["br-statements"] });
    },
  });

  return (
    <div>
      <PageHeader title={t.nav.bankReconciliation} />

      {canEdit && (
        <div className="card mb-4 grid gap-3 sm:grid-cols-5">
          {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-5">{err}</div>}
          <div>
            <label className="label">Banka Hesabı</label>
            <select className="input" value={accountId} onChange={(e) => setAccountId(e.target.value ? Number(e.target.value) : "")}>
              <option value="">Seçin</option>
              {(accounts.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>{a.code} — {a.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Biçim</label>
            <select className="input" value={format} onChange={(e) => setFormat(e.target.value as "CSV" | "MT940")}>
              <option value="CSV">CSV</option>
              <option value="MT940">MT940</option>
            </select>
          </div>
          <div className="sm:col-span-2">
            <label className="label">Ekstre Dosyası</label>
            <input ref={fileRef} type="file" accept={format === "CSV" ? ".csv,.txt" : ".txt,.sta,.940"} className="block text-sm" />
          </div>
          <div className="flex items-end">
            <button
              className="btn-primary w-full"
              disabled={importMut.isPending}
              onClick={() => importMut.mutate()}
            >
              {importMut.isPending ? t.common.loading : "İçe Aktar"}
            </button>
          </div>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="card lg:col-span-1">
          <h2 className="mb-2 text-sm font-semibold">Ekstreler</h2>
          <div className="space-y-1">
            {(statements.data ?? []).map((s) => (
              <button
                key={s.id}
                onClick={() => setSelectedId(s.id)}
                className={`block w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-50 ${
                  selectedId === s.id ? "bg-brand-50 ring-1 ring-brand-200" : ""
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-medium">{s.originalFilename ?? `Ekstre #${s.id}`}</span>
                  <span className={`rounded px-1.5 py-0.5 text-xs ${statusChip[s.status]}`}>{s.status}</span>
                </div>
                <div className="text-xs text-slate-500">
                  {s.lineCount} satır · {s.matchedCount}/{s.lineCount} çözüldü
                </div>
              </button>
            ))}
            {statements.data?.length === 0 && <div className="text-sm text-slate-400">{t.common.noRecords}</div>}
          </div>
        </div>

        <div className="lg:col-span-2">
          {!selectedId && <div className="card text-sm text-slate-500">Bir ekstre seçin veya yeni bir tane içe aktarın.</div>}
          {detail.data && (
            <StatementDetail
              statement={detail.data.statement}
              lines={detail.data.lines}
              cards={cards.data ?? []}
              canEdit={canEdit}
              onMatch={(lineId, txnId) => matchMut.mutate({ lineId, txnId })}
              onUnmatch={(lineId) => unmatchMut.mutate(lineId)}
              onIgnore={(lineId, note) => ignoreMut.mutate({ lineId, note })}
              onCreateTxn={(lineId, cardId, desc) => createTxnMut.mutate({ lineId, incomeExpenseCardId: cardId, description: desc })}
              onAutoMatch={() => autoMatchMut.mutate(detail.data.statement.id)}
              onDelete={() => deleteMut.mutate(detail.data.statement.id)}
            />
          )}
        </div>
      </div>
    </div>
  );
}

function StatementDetail({
  statement,
  lines,
  cards,
  canEdit,
  onMatch,
  onUnmatch,
  onIgnore,
  onCreateTxn,
  onAutoMatch,
  onDelete,
}: {
  statement: StatementView;
  lines: LineView[];
  cards: CardView[];
  canEdit: boolean;
  onMatch: (lineId: number, txnId: number) => void;
  onUnmatch: (lineId: number) => void;
  onIgnore: (lineId: number, note: string) => void;
  onCreateTxn: (lineId: number, cardId: number | null, desc: string) => void;
  onAutoMatch: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="space-y-3">
      <div className="card flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm text-slate-500">
          {statement.periodStart ?? "—"} → {statement.periodEnd ?? "—"} · Açılış {tl(statement.openingBalance)} ·
          Kapanış {tl(statement.closingBalance)}
        </div>
        {canEdit && (
          <div className="flex gap-2">
            <button className="btn-ghost text-sm" onClick={onAutoMatch}>Otomatik Eşleştir</button>
            <button className="rounded border border-red-300 px-2 py-1 text-sm text-red-600 hover:bg-red-50" onClick={onDelete}>
              Ekstreyi Sil
            </button>
          </div>
        )}
      </div>

      <div className="space-y-2">
        {lines.map((l) => (
          <LineRow key={l.id} line={l} cards={cards} canEdit={canEdit}
            onMatch={onMatch} onUnmatch={onUnmatch} onIgnore={onIgnore} onCreateTxn={onCreateTxn} />
        ))}
      </div>
    </div>
  );
}

function LineRow({
  line, cards, canEdit, onMatch, onUnmatch, onIgnore, onCreateTxn,
}: {
  line: LineView;
  cards: CardView[];
  canEdit: boolean;
  onMatch: (lineId: number, txnId: number) => void;
  onUnmatch: (lineId: number) => void;
  onIgnore: (lineId: number, note: string) => void;
  onCreateTxn: (lineId: number, cardId: number | null, desc: string) => void;
}) {
  const [mode, setMode] = useState<"none" | "ignore" | "create">("none");
  const [note, setNote] = useState("");
  const [cardId, setCardId] = useState<number | "">("");
  const [desc, setDesc] = useState(line.description ?? "");
  const best = line.suggestions[0];

  return (
    <div className="card py-3">
      <div className="flex items-center gap-4">
        <div className="w-24 shrink-0 text-sm tabular-nums text-slate-500">{line.valueDate}</div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">{line.description ?? line.counterparty ?? "—"}</div>
          {line.bankRef && <div className="text-xs text-slate-400">Ref: {line.bankRef}</div>}
        </div>
        <div className={`w-28 shrink-0 text-right text-sm font-semibold tabular-nums ${line.amount >= 0 ? "text-green-700" : "text-red-700"}`}>
          {tl(line.amount)}
        </div>
        <span className={`shrink-0 rounded px-1.5 py-0.5 text-xs ${lineChip[line.matchStatus]}`}>{line.matchStatus}</span>
      </div>

      {line.matchStatus === "IGNORED" && line.note && (
        <div className="mt-1 pl-28 text-xs text-slate-400">Not: {line.note}</div>
      )}

      {canEdit && line.matchStatus === "UNMATCHED" && (
        <div className="mt-2 pl-28">
          {best && (
            <div className="mb-2 flex items-center gap-2 text-xs text-slate-600">
              <span>Öneri: hareket #{best.txnId} (skor {best.score})</span>
              <button className="rounded bg-sky-600 px-2 py-0.5 text-white hover:bg-sky-700"
                      onClick={() => onMatch(line.id, best.txnId)}>
                Eşleştir
              </button>
            </div>
          )}
          {mode === "none" && (
            <div className="flex gap-2 text-xs">
              <button className="rounded border border-slate-300 px-2 py-1 hover:bg-slate-100" onClick={() => setMode("create")}>
                Yeni Hareket
              </button>
              <button className="rounded border border-slate-300 px-2 py-1 hover:bg-slate-100" onClick={() => setMode("ignore")}>
                Yok Say
              </button>
            </div>
          )}
          {mode === "create" && (
            <div className="flex flex-wrap items-end gap-2 rounded-lg bg-slate-50 p-2">
              <div>
                <label className="label">Gelir/Gider Kartı</label>
                <select className="input" value={cardId} onChange={(e) => setCardId(e.target.value ? Number(e.target.value) : "")}>
                  <option value="">(seçilmedi)</option>
                  {cards.filter((c) => c.postable).map((c) => (
                    <option key={c.id} value={c.id}>{c.code} — {c.name}</option>
                  ))}
                </select>
              </div>
              <div className="flex-1">
                <label className="label">Açıklama</label>
                <input className="input" value={desc} onChange={(e) => setDesc(e.target.value)} />
              </div>
              <button className="btn-primary text-sm" onClick={() => { onCreateTxn(line.id, cardId || null, desc); setMode("none"); }}>
                Oluştur
              </button>
              <button className="btn-ghost text-sm" onClick={() => setMode("none")}>{t.common.cancel}</button>
            </div>
          )}
          {mode === "ignore" && (
            <div className="flex flex-wrap items-end gap-2 rounded-lg bg-slate-50 p-2">
              <div className="flex-1">
                <label className="label">Gerekçe</label>
                <input className="input" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Açılış kaydı, mükerrer vb." />
              </div>
              <button
                className="btn-primary text-sm"
                disabled={!note.trim()}
                onClick={() => { onIgnore(line.id, note); setMode("none"); setNote(""); }}
              >
                Yok Say
              </button>
              <button className="btn-ghost text-sm" onClick={() => setMode("none")}>{t.common.cancel}</button>
            </div>
          )}
        </div>
      )}

      {canEdit && (line.matchStatus === "MATCHED" || line.matchStatus === "CREATED" || line.matchStatus === "IGNORED") && (
        <div className="mt-2 pl-28">
          <button className="rounded border border-slate-300 px-2 py-1 text-xs hover:bg-slate-100" onClick={() => onUnmatch(line.id)}>
            Geri Al
          </button>
        </div>
      )}
    </div>
  );
}

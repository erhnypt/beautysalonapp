import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface AccountView {
  id: number;
  code: string;
  name: string;
  kind: "KASA" | "BANKA" | "POS" | "CEK";
  currency: string;
  balance: number;
  isDefault: boolean;
  avgRate: number | null;
}
interface CardView {
  id: number;
  code: string;
  name: string;
  direction: "INCOME" | "EXPENSE";
  postable: boolean;
}

const tl = (n: number) =>
  new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);

export function FinancePage() {
  const { has } = useAuth();
  const canAdd = has("FINANCE_ADD");
  const canEdit = has("FINANCE_EDIT");
  const qc = useQueryClient();
  const accounts = useQuery({
    queryKey: ["fin-accounts"],
    queryFn: () => api<AccountView[]>("/api/v1/finance/accounts"),
  });
  const cards = useQuery({
    queryKey: ["fin-cards"],
    queryFn: () => api<CardView[]>("/api/v1/finance/cards"),
  });
  const [mode, setMode] = useState<"collect" | "pay" | "fx" | "count" | "account" | "card" | null>(null);
  const [lastVoucherTxnId, setLastVoucherTxnId] = useState<number | null>(null);

  return (
    <div>
      <PageHeader
        title={t.nav.finance}
        actions={
          <div className="flex gap-2">
            {canAdd && (
              <>
                <button className="btn-primary" onClick={() => setMode("collect")}>
                  Tahsilat
                </button>
                <button className="btn-ghost" onClick={() => setMode("pay")}>
                  Tediye
                </button>
                <button className="btn-ghost" onClick={() => setMode("fx")}>
                  Döviz
                </button>
                <button className="btn-ghost" onClick={() => setMode("count")}>
                  Kasa Sayımı
                </button>
              </>
            )}
            {canEdit && (
              <>
                <button className="btn-ghost" onClick={() => setMode("account")}>
                  Hesap Ekle
                </button>
                <button className="btn-ghost" onClick={() => setMode("card")}>
                  Gelir/Gider Kartı Ekle
                </button>
              </>
            )}
          </div>
        }
      />

      {lastVoucherTxnId && (
        <div className="mb-4 flex items-center justify-between rounded-lg bg-green-50 px-4 py-2 text-sm text-green-800">
          <span>İşlem kaydedildi.</span>
          <div className="flex items-center gap-3">
            <a
              className="font-medium hover:underline"
              href={`/api/v1/finance/transactions/${lastVoucherTxnId}/voucher`}
              target="_blank"
              rel="noreferrer"
            >
              Dekont (PDF)
            </a>
            <button className="text-green-700 hover:underline" onClick={() => setLastVoucherTxnId(null)}>
              Kapat
            </button>
          </div>
        </div>
      )}

      {(mode === "collect" || mode === "pay") && (
        <CashForm
          mode={mode}
          accounts={accounts.data ?? []}
          cards={(cards.data ?? []).filter((c) => c.postable)}
          onClose={() => setMode(null)}
          onDone={(txnId) => {
            qc.invalidateQueries({ queryKey: ["fin-accounts"] });
            setMode(null);
            setLastVoucherTxnId(txnId ?? null);
          }}
        />
      )}
      {mode === "fx" && (
        <FxForm
          accounts={accounts.data ?? []}
          onClose={() => setMode(null)}
          onDone={() => {
            qc.invalidateQueries({ queryKey: ["fin-accounts"] });
            setMode(null);
          }}
        />
      )}
      {mode === "count" && (
        <CashCountForm
          accounts={accounts.data ?? []}
          onClose={() => setMode(null)}
          onDone={() => {
            qc.invalidateQueries({ queryKey: ["fin-accounts"] });
            setMode(null);
          }}
        />
      )}
      {mode === "account" && (
        <NewAccountForm
          onClose={() => setMode(null)}
          onDone={() => {
            qc.invalidateQueries({ queryKey: ["fin-accounts"] });
            setMode(null);
          }}
        />
      )}
      {mode === "card" && (
        <NewCardForm
          onClose={() => setMode(null)}
          onDone={() => {
            qc.invalidateQueries({ queryKey: ["fin-cards"] });
            setMode(null);
          }}
        />
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {(accounts.data ?? []).map((a) => (
          <div key={a.id} className="card">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium uppercase text-slate-400">{a.kind}</span>
              {a.isDefault && (
                <span className="rounded bg-brand-50 px-1.5 py-0.5 text-[10px] text-brand-700">
                  varsayılan
                </span>
              )}
            </div>
            <div className="mt-1 font-medium">{a.name}</div>
            <div className="mt-2 text-2xl font-semibold tabular-nums">
              {a.currency === "TRY" ? tl(a.balance) : `${a.balance.toLocaleString("tr-TR")} ${a.currency}`}
            </div>
            {a.avgRate != null && (
              <div className="mt-1 text-xs text-slate-400">Ort. kur: {a.avgRate.toFixed(4)}</div>
            )}
          </div>
        ))}
        {accounts.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      </div>

      <h2 className="mb-2 mt-6 text-sm font-semibold text-slate-700">Gelir/Gider Kartları</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <div className="mb-1 text-xs font-medium uppercase text-slate-400">Gelir</div>
          <ul className="overflow-hidden rounded-xl border border-slate-200 bg-white text-sm">
            {(cards.data ?? [])
              .filter((c) => c.direction === "INCOME" && c.postable)
              .map((c) => (
                <li key={c.id} className="flex justify-between border-b border-slate-100 px-3 py-1.5 last:border-b-0">
                  <span className="font-mono text-xs text-slate-400">{c.code}</span>
                  <span>{c.name}</span>
                </li>
              ))}
          </ul>
        </div>
        <div>
          <div className="mb-1 text-xs font-medium uppercase text-slate-400">Gider</div>
          <ul className="overflow-hidden rounded-xl border border-slate-200 bg-white text-sm">
            {(cards.data ?? [])
              .filter((c) => c.direction === "EXPENSE" && c.postable)
              .map((c) => (
                <li key={c.id} className="flex justify-between border-b border-slate-100 px-3 py-1.5 last:border-b-0">
                  <span className="font-mono text-xs text-slate-400">{c.code}</span>
                  <span>{c.name}</span>
                </li>
              ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

function CashForm({
  mode,
  accounts,
  cards,
  onClose,
  onDone,
}: {
  mode: "collect" | "pay";
  accounts: AccountView[];
  cards: CardView[];
  onClose: () => void;
  onDone: (txnId?: number) => void;
}) {
  const [accountId, setAccountId] = useState<number | "">(accounts.find((a) => a.isDefault)?.id ?? "");
  const [amount, setAmount] = useState("");
  const [cardId, setCardId] = useState<number | "">("");
  const [description, setDescription] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: () =>
      api<{ id: number }>(`/api/v1/finance/${mode === "collect" ? "collect" : "pay"}`, {
        method: "POST",
        body: {
          accountId: Number(accountId),
          amount: Number(amount),
          cardId: cardId === "" ? null : Number(cardId),
          description,
        },
      }),
    onSuccess: (txn) => onDone(txn.id),
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    submit.mutate();
  };

  return (
    <form onSubmit={onSubmit} className="card mb-5 grid gap-3 sm:grid-cols-4">
      <div className="sm:col-span-4 text-sm font-semibold">
        {mode === "collect" ? "Tahsilat (kasaya giriş)" : "Tediye (kasadan çıkış)"}
      </div>
      {err && (
        <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>
      )}
      <div>
        <label className="label">Hesap</label>
        <select
          className="input"
          value={accountId}
          onChange={(e) => setAccountId(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">Seçin</option>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Tutar</label>
        <input className="input" value={amount} onChange={(e) => setAmount(e.target.value)} />
      </div>
      <div>
        <label className="label">Gelir/Gider Kartı</label>
        <select
          className="input"
          value={cardId}
          onChange={(e) => setCardId(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">—</option>
          {cards
            .filter((c) => (mode === "collect" ? c.direction === "INCOME" : c.direction === "EXPENSE"))
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} — {c.name}
              </option>
            ))}
        </select>
      </div>
      <div>
        <label className="label">Açıklama</label>
        <input
          className="input"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </div>
      <div className="flex items-end gap-2 sm:col-span-4">
        <button className="btn-primary" disabled={submit.isPending || !accountId || !amount}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

function FxForm({
  accounts,
  onClose,
  onDone,
}: {
  accounts: AccountView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const fxAccounts = accounts.filter((a) => a.currency !== "TRY");
  const tryAccounts = accounts.filter((a) => a.currency === "TRY");
  const [buy, setBuy] = useState(true);
  const [fxAccountId, setFxAccountId] = useState<number | "">(fxAccounts[0]?.id ?? "");
  const [tryAccountId, setTryAccountId] = useState<number | "">(tryAccounts.find((a) => a.isDefault)?.id ?? tryAccounts[0]?.id ?? "");
  const [fxAmount, setFxAmount] = useState("");
  const [rate, setRate] = useState("");
  const [description, setDescription] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const fxCurrency = fxAccounts.find((a) => a.id === fxAccountId)?.currency ?? "";
  const [rateNote, setRateNote] = useState<string | null>(null);
  const liveRate = useMutation({
    mutationFn: () =>
      api<{ symbol: string; rate: number; asOf: string; source: string }>(
        `/api/v1/finance/fx/rate?base=${encodeURIComponent(fxCurrency)}`,
      ),
    onSuccess: (r) => {
      setRate(String(r.rate));
      setRateNote(`${r.source} · ${new Date(r.asOf).toLocaleString("tr-TR")}`);
    },
    onError: (e) => setRateNote(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = useMutation({
    mutationFn: () =>
      api("/api/v1/finance/fx", {
        method: "POST",
        body: {
          fxAccountId: Number(fxAccountId),
          tryAccountId: Number(tryAccountId),
          buy,
          fxAmount: Number(fxAmount),
          rate: Number(rate),
          description,
        },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    submit.mutate();
  };

  if (fxAccounts.length === 0) {
    return (
      <div className="card mb-5 text-sm text-slate-600">
        Döviz işlemi için önce bir döviz hesabı (TRY dışı para birimli KASA/BANKA) oluşturun.
        <button type="button" className="btn-ghost ml-3" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="card mb-5 grid gap-3 sm:grid-cols-4">
      <div className="sm:col-span-4 flex items-center gap-4 text-sm font-semibold">
        <span>Döviz İşlemi</span>
        <label className="flex items-center gap-1 text-xs font-normal">
          <input type="radio" checked={buy} onChange={() => setBuy(true)} /> Alım
        </label>
        <label className="flex items-center gap-1 text-xs font-normal">
          <input type="radio" checked={!buy} onChange={() => setBuy(false)} /> Satım
        </label>
      </div>
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      <div>
        <label className="label">Döviz Hesabı</label>
        <select className="input" value={fxAccountId} onChange={(e) => setFxAccountId(e.target.value ? Number(e.target.value) : "")}>
          {fxAccounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name} ({a.currency})
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">TL Hesabı</label>
        <select className="input" value={tryAccountId} onChange={(e) => setTryAccountId(e.target.value ? Number(e.target.value) : "")}>
          {tryAccounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Döviz Miktarı</label>
        <input className="input" value={fxAmount} onChange={(e) => setFxAmount(e.target.value)} />
      </div>
      <div>
        <div className="flex items-center justify-between">
          <label className="label">Kur</label>
          <button
            type="button"
            className="btn-ghost px-2 py-0.5 text-xs"
            disabled={!fxCurrency || liveRate.isPending}
            onClick={() => {
              setRateNote(null);
              liveRate.mutate();
            }}
          >
            {liveRate.isPending ? t.finance.fetchingLiveRate : t.finance.fetchLiveRate}
          </button>
        </div>
        <input className="input" value={rate} onChange={(e) => setRate(e.target.value)} />
        {rateNote && <div className="mt-1 text-xs text-slate-400">{rateNote}</div>}
      </div>
      <div className="sm:col-span-2">
        <label className="label">Açıklama</label>
        <input className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="flex items-end gap-2 sm:col-span-2">
        <button className="btn-primary" disabled={submit.isPending || !fxAccountId || !tryAccountId || !fxAmount || !rate}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

function CashCountForm({
  accounts,
  onClose,
  onDone,
}: {
  accounts: AccountView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const kasaAccounts = accounts.filter((a) => a.kind === "KASA");
  const [accountId, setAccountId] = useState<number | "">(kasaAccounts.find((a) => a.isDefault)?.id ?? kasaAccounts[0]?.id ?? "");
  const [countedAmount, setCountedAmount] = useState("");
  const [reason, setReason] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [result, setResult] = useState<{ difference: number } | null>(null);

  const account = accounts.find((a) => a.id === accountId);
  const expected = account?.balance ?? null;

  const submit = useMutation({
    mutationFn: () =>
      api<{ difference: number }>(`/api/v1/finance/accounts/${accountId}/cash-counts`, {
        method: "POST",
        body: { countedAmount: Number(countedAmount), reason: reason || null },
      }),
    onSuccess: (r) => {
      setResult(r);
      onDone();
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    submit.mutate();
  };

  const difference = expected != null && countedAmount ? Number(countedAmount) - expected : 0;

  return (
    <form onSubmit={onSubmit} className="card mb-5 grid gap-3 sm:grid-cols-4">
      <div className="sm:col-span-4 text-sm font-semibold">Kasa Sayımı</div>
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      {result && (
        <div className="rounded bg-green-50 px-3 py-2 text-sm text-green-800 sm:col-span-4">
          Sayım kaydedildi. Fark: {tl(result.difference)}
        </div>
      )}
      <div>
        <label className="label">Kasa</label>
        <select className="input" value={accountId} onChange={(e) => setAccountId(e.target.value ? Number(e.target.value) : "")}>
          {kasaAccounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Sistem Bakiyesi</label>
        <input className="input bg-slate-50" value={expected != null ? tl(expected) : "—"} disabled />
      </div>
      <div>
        <label className="label">Sayılan Tutar</label>
        <input className="input" value={countedAmount} onChange={(e) => setCountedAmount(e.target.value)} />
      </div>
      <div>
        <label className="label">Gerekçe {difference !== 0 && <span className="text-red-500">*</span>}</label>
        <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>
      {countedAmount && (
        <div className={`sm:col-span-4 text-sm ${difference === 0 ? "text-slate-500" : difference > 0 ? "text-green-700" : "text-red-700"}`}>
          Fark: {tl(difference)} {difference !== 0 && "— gerekçe girilmeden kaydedilemez"}
        </div>
      )}
      <div className="flex items-end gap-2 sm:col-span-4">
        <button
          className="btn-primary"
          disabled={submit.isPending || !accountId || !countedAmount || (difference !== 0 && !reason)}
        >
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

function NewCardForm({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [direction, setDirection] = useState<"INCOME" | "EXPENSE">("EXPENSE");
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/finance/cards", {
        method: "POST",
        body: { code, name, direction, serviceCard: false },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    create.mutate();
  };

  return (
    <form onSubmit={submit} className="card mb-5 grid gap-3 sm:grid-cols-4">
      <div className="sm:col-span-4 text-sm font-semibold">Yeni Gelir/Gider Kartı</div>
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      <div>
        <label className="label">Kod</label>
        <input className="input" placeholder="700.10" value={code} onChange={(e) => setCode(e.target.value)} />
      </div>
      <div className="sm:col-span-2">
        <label className="label">Ad</label>
        <input className="input" placeholder="Reklam / Kırtasiye / ..." value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div>
        <label className="label">Yön</label>
        <select className="input" value={direction} onChange={(e) => setDirection(e.target.value as "INCOME" | "EXPENSE")}>
          <option value="EXPENSE">Gider</option>
          <option value="INCOME">Gelir</option>
        </select>
      </div>
      <div className="flex items-end gap-2 sm:col-span-4">
        <button className="btn-primary" disabled={create.isPending || !code || !name}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

function NewAccountForm({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [kind, setKind] = useState<"KASA" | "BANKA" | "POS" | "CEK">("KASA");
  const [currency, setCurrency] = useState("TRY");
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/finance/accounts", {
        method: "POST",
        body: { code, name, kind, currency, makeDefault: false },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    create.mutate();
  };

  return (
    <form onSubmit={submit} className="card mb-5 grid gap-3 sm:grid-cols-4">
      <div className="sm:col-span-4 text-sm font-semibold">Yeni Hesap</div>
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      <div>
        <label className="label">Kod</label>
        <input className="input" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} />
      </div>
      <div>
        <label className="label">Ad</label>
        <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div>
        <label className="label">Tür</label>
        <select className="input" value={kind} onChange={(e) => setKind(e.target.value as typeof kind)}>
          <option value="KASA">Kasa</option>
          <option value="BANKA">Banka</option>
          <option value="POS">POS</option>
          <option value="CEK">Çek</option>
        </select>
      </div>
      <div>
        <label className="label">Para Birimi</label>
        <input className="input" value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} maxLength={3} />
      </div>
      <div className="flex items-end gap-2 sm:col-span-4">
        <button className="btn-primary" disabled={create.isPending || !code || !name}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

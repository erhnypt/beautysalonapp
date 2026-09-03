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
  const qc = useQueryClient();
  const accounts = useQuery({
    queryKey: ["fin-accounts"],
    queryFn: () => api<AccountView[]>("/api/v1/finance/accounts"),
  });
  const cards = useQuery({
    queryKey: ["fin-cards"],
    queryFn: () => api<CardView[]>("/api/v1/finance/cards"),
  });
  const [mode, setMode] = useState<"collect" | "pay" | null>(null);

  return (
    <div>
      <PageHeader
        title={t.nav.finance}
        actions={
          canAdd && (
            <div className="flex gap-2">
              <button className="btn-primary" onClick={() => setMode("collect")}>
                Tahsilat
              </button>
              <button className="btn-ghost" onClick={() => setMode("pay")}>
                Tediye
              </button>
            </div>
          )
        }
      />

      {mode && (
        <CashForm
          mode={mode}
          accounts={accounts.data ?? []}
          cards={(cards.data ?? []).filter((c) => c.postable)}
          onClose={() => setMode(null)}
          onDone={() => {
            qc.invalidateQueries({ queryKey: ["fin-accounts"] });
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
            <div className="mt-2 text-2xl font-semibold tabular-nums">{tl(a.balance)}</div>
          </div>
        ))}
        {accounts.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
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
  onDone: () => void;
}) {
  const [accountId, setAccountId] = useState<number | "">(accounts.find((a) => a.isDefault)?.id ?? "");
  const [amount, setAmount] = useState("");
  const [cardId, setCardId] = useState<number | "">("");
  const [description, setDescription] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: () =>
      api(`/api/v1/finance/${mode === "collect" ? "collect" : "pay"}`, {
        method: "POST",
        body: {
          accountId: Number(accountId),
          amount: Number(amount),
          cardId: cardId === "" ? null : Number(cardId),
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

import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

type PartyType = "MUSTERI" | "SATICI" | "PERAKENDE";

interface PartyRow {
  id: number;
  type: PartyType;
  code: string;
  title: string;
  phoneMasked: string | null;
  anonymized: boolean;
}

interface Page<T> {
  content: T[];
  totalElements: number;
}

const TABS: { key: PartyType; label: string }[] = [
  { key: "MUSTERI", label: "Müşteri" },
  { key: "SATICI", label: "Satıcı" },
  { key: "PERAKENDE", label: "Perakende" },
];

export function PartiesPage() {
  const { has } = useAuth();
  const [tab, setTab] = useState<PartyType>("MUSTERI");
  const [q, setQ] = useState("");
  const [showForm, setShowForm] = useState(false);
  const canAdd = has("PARTY_ADD");

  const { data, isLoading, error } = useQuery({
    queryKey: ["parties", tab, q],
    queryFn: () =>
      api<Page<PartyRow>>(`/api/v1/parties?type=${tab}&q=${encodeURIComponent(q)}&size=100`),
  });

  return (
    <div>
      <PageHeader
        title={t.nav.parties}
        actions={
          canAdd && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              {t.common.add}
            </button>
          )
        }
      />

      <div className="mb-4 flex gap-1 border-b border-slate-200">
        {TABS.map((it) => (
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

      {showForm && <NewPartyForm type={tab} onClose={() => setShowForm(false)} />}

      <input
        className="input mb-3 max-w-sm"
        placeholder={t.common.search}
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />

      {isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {error && <div className="text-red-600">{(error as ApiError).message}</div>}

      {data && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">Kod</th>
                <th className="px-4 py-2 font-medium">Ünvan</th>
                <th className="px-4 py-2 font-medium">Telefon</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((p) => (
                <tr key={p.id} className={p.anonymized ? "italic opacity-60" : ""}>
                  <td className="px-4 py-2 font-mono text-xs">{p.code}</td>
                  <td className="px-4 py-2 font-medium">{p.title}</td>
                  <td className="px-4 py-2 text-slate-500">{p.phoneMasked ?? "—"}</td>
                </tr>
              ))}
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-slate-400">
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

function NewPartyForm({ type, onClose }: { type: PartyType; onClose: () => void }) {
  const qc = useQueryClient();
  const [title, setTitle] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/parties", { method: "POST", body: { type, title, phone, email } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["parties"] });
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
      <div className="sm:col-span-3">
        <label className="label">Ünvan / Ad Soyad</label>
        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <div>
        <label className="label">Telefon</label>
        <input className="input" value={phone} onChange={(e) => setPhone(e.target.value)} />
      </div>
      <div>
        <label className="label">E-posta</label>
        <input className="input" value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="flex items-end gap-2">
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

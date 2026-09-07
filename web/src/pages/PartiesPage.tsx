import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { AttachmentPanel } from "../components/AttachmentPanel";
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

interface PartyDetail {
  id: number;
  type: PartyType;
  code: string;
  title: string;
  phone: string | null;
  email: string | null;
  riskLimit: number | null;
  birthDate: string | null;
  weddingAnniversary: string | null;
  anonymized: boolean;
}

interface Page<T> {
  content: T[];
  totalElements: number;
}

const tl = (n: number) =>
  new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(n);

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
  const [openId, setOpenId] = useState<number | null>(null);
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
                <tr
                  key={p.id}
                  className={`cursor-pointer hover:bg-slate-50 ${p.anonymized ? "italic opacity-60" : ""}`}
                  onClick={() => setOpenId(openId === p.id ? null : p.id)}
                >
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

      {openId && <PartyDetailPanel id={openId} onClose={() => setOpenId(null)} />}
    </div>
  );
}

function PartyDetailPanel({ id, onClose }: { id: number; onClose: () => void }) {
  const { has } = useAuth();
  const qc = useQueryClient();
  const canEdit = has("PARTY_EDIT");
  const [editing, setEditing] = useState(false);
  const { data } = useQuery({
    queryKey: ["party", id],
    queryFn: () => api<PartyDetail>(`/api/v1/parties/${id}`),
  });

  if (!data) return null;

  return (
    <div className="mt-4 card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold">{data.title}</h2>
        <div className="flex items-center gap-3">
          {canEdit && !data.anonymized && (
            <button className="text-sm text-brand-700 hover:underline" onClick={() => setEditing((v) => !v)}>
              Düzenle
            </button>
          )}
          <button className="text-sm text-slate-500 hover:underline" onClick={onClose}>
            Kapat
          </button>
        </div>
      </div>

      {editing ? (
        <PartyEditForm
          data={data}
          onClose={() => setEditing(false)}
          onDone={() => {
            setEditing(false);
            qc.invalidateQueries({ queryKey: ["party", id] });
            qc.invalidateQueries({ queryKey: ["parties"] });
          }}
        />
      ) : (
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
          <div>
            <dt className="text-slate-500">Telefon</dt>
            <dd>{data.phone ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">E-posta</dt>
            <dd>{data.email ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Risk Limiti</dt>
            <dd>{data.riskLimit != null ? tl(data.riskLimit) : "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Doğum Tarihi</dt>
            <dd>{data.birthDate ? new Date(data.birthDate).toLocaleDateString("tr-TR") : "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Evlilik Yıldönümü</dt>
            <dd>{data.weddingAnniversary ? new Date(data.weddingAnniversary).toLocaleDateString("tr-TR") : "—"}</dd>
          </div>
        </dl>
      )}

      <AttachmentPanel ownerType="PARTY" ownerId={id} editPermission="PARTY_EDIT" />
    </div>
  );
}

function PartyEditForm({
  data,
  onClose,
  onDone,
}: {
  data: PartyDetail;
  onClose: () => void;
  onDone: () => void;
}) {
  const [riskLimit, setRiskLimit] = useState(data.riskLimit != null ? String(data.riskLimit) : "");
  const [birthDate, setBirthDate] = useState(data.birthDate ?? "");
  const [weddingAnniversary, setWeddingAnniversary] = useState(data.weddingAnniversary ?? "");
  const [err, setErr] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      api(`/api/v1/parties/${data.id}`, {
        method: "PUT",
        body: {
          riskLimit: riskLimit ? Number(riskLimit) : null,
          birthDate: birthDate || null,
          weddingAnniversary: weddingAnniversary || null,
        },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    save.mutate();
  };

  return (
    <form onSubmit={submit} className="grid gap-3 border-b border-slate-100 pb-4 sm:grid-cols-3">
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-3">{err}</div>}
      <div>
        <label className="label">Risk Limiti</label>
        <input className="input" value={riskLimit} onChange={(e) => setRiskLimit(e.target.value)} />
      </div>
      <div>
        <label className="label">Doğum Tarihi</label>
        <input
          type="date"
          className="input"
          value={birthDate ? birthDate.slice(0, 10) : ""}
          onChange={(e) => setBirthDate(e.target.value)}
        />
      </div>
      <div>
        <label className="label">Evlilik Yıldönümü</label>
        <input
          type="date"
          className="input"
          value={weddingAnniversary ? weddingAnniversary.slice(0, 10) : ""}
          onChange={(e) => setWeddingAnniversary(e.target.value)}
        />
      </div>
      <div className="flex items-end gap-2 sm:col-span-3">
        <button className="btn-primary" disabled={save.isPending}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
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

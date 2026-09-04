import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface BranchView {
  id: number;
  code: string;
  title: string;
  taxId: string | null;
  address: string | null;
  phone: string | null;
  headquarters: boolean;
}

const empty = { code: "", title: "", taxId: "", address: "", phone: "" };

export function BranchesPage() {
  const { has } = useAuth();
  const qc = useQueryClient();
  const canEdit = has("SETTINGS_EDIT");
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<BranchView | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const branches = useQuery({
    queryKey: ["branches"],
    queryFn: () => api<BranchView[]>("/api/v1/branches"),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["branches"] });

  const setHq = useMutation({
    mutationFn: (id: number) => api(`/api/v1/branches/${id}/headquarters`, { method: "POST" }),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: (id: number) => api(`/api/v1/branches/${id}`, { method: "DELETE" }),
    onSuccess: invalidate,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  return (
    <div className="max-w-3xl">
      <PageHeader
        title={t.nav.branches}
        actions={
          canEdit && (
            <button className="btn-primary" onClick={() => { setEditing(null); setShowForm((v) => !v); }}>
              {t.common.add}
            </button>
          )
        }
      />
      <p className="mb-4 text-sm text-slate-500">
        Şube tanımları ve merkez şube seçimi burada yapılır. Cari/stok/fatura gibi işlem
        ekranlarının şube bazlı ayrıştırılması sonraki bir adımdır — bkz. Faz 8 notları.
      </p>

      {err && <div className="mb-3 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}

      {(showForm || editing) && (
        <BranchForm
          initial={editing}
          onDone={() => { setShowForm(false); setEditing(null); invalidate(); }}
          onCancel={() => { setShowForm(false); setEditing(null); }}
        />
      )}

      {branches.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      <div className="space-y-2">
        {(branches.data ?? []).map((b) => (
          <div key={b.id} className="card flex items-center justify-between gap-4 py-3">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs text-slate-500">{b.code}</span>
                <span className="font-medium">{b.title}</span>
                {b.headquarters && (
                  <span className="rounded bg-brand-100 px-1.5 py-0.5 text-xs text-brand-700">Merkez</span>
                )}
              </div>
              <div className="text-xs text-slate-500">
                {[b.address, b.phone, b.taxId].filter(Boolean).join(" · ") || "—"}
              </div>
            </div>
            {canEdit && (
              <div className="flex shrink-0 gap-2 text-xs">
                <button className="rounded border border-slate-300 px-2 py-1 hover:bg-slate-100" onClick={() => setEditing(b)}>
                  Düzenle
                </button>
                {!b.headquarters && (
                  <button className="rounded border border-slate-300 px-2 py-1 hover:bg-slate-100" onClick={() => setHq.mutate(b.id)}>
                    Merkez Yap
                  </button>
                )}
                {!b.headquarters && (
                  <button className="rounded border border-red-300 px-2 py-1 text-red-600 hover:bg-red-50" onClick={() => remove.mutate(b.id)}>
                    Sil
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function BranchForm({
  initial, onDone, onCancel,
}: {
  initial: BranchView | null;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [form, setForm] = useState(
    initial
      ? { code: initial.code, title: initial.title, taxId: initial.taxId ?? "", address: initial.address ?? "", phone: initial.phone ?? "" }
      : empty
  );
  const [err, setErr] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      initial
        ? api(`/api/v1/branches/${initial.id}`, { method: "PUT", body: { title: form.title, taxId: form.taxId || null, address: form.address || null, phone: form.phone || null } })
        : api("/api/v1/branches", { method: "POST", body: { code: form.code, title: form.title, taxId: form.taxId || null, address: form.address || null, phone: form.phone || null } }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    save.mutate();
  };

  return (
    <form onSubmit={submit} className="card mb-4 grid gap-3 sm:grid-cols-3">
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-3">{err}</div>}
      {!initial && (
        <div>
          <label className="label">Kod</label>
          <input className="input" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
        </div>
      )}
      <div className={initial ? "sm:col-span-2" : ""}>
        <label className="label">Ünvan</label>
        <input className="input" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
      </div>
      <div>
        <label className="label">Vergi No</label>
        <input className="input" value={form.taxId} onChange={(e) => setForm({ ...form, taxId: e.target.value })} />
      </div>
      <div className="sm:col-span-2">
        <label className="label">Adres</label>
        <input className="input" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
      </div>
      <div>
        <label className="label">Telefon</label>
        <input className="input" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
      </div>
      <div className="flex items-end gap-2 sm:col-span-3">
        <button className="btn-primary" disabled={save.isPending}>{t.common.save}</button>
        <button type="button" className="btn-ghost" onClick={onCancel}>{t.common.cancel}</button>
      </div>
    </form>
  );
}

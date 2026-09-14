import { Fragment, FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import type { RoleName, UserView } from "../lib/types";
import { PageHeader } from "../components/PageHeader";
import { t, roleLabels, permissionGroups } from "../lib/i18n";
import { useAuth } from "../lib/auth";

const ALL_ROLES: RoleName[] = ["ADMIN", "MUDUR", "KASIYER", "PERSONEL", "RAPOR_OKUYUCU"];

interface RoleView {
  id: number;
  name: RoleName;
  description: string | null;
  systemRole: boolean;
  permissions: string[];
}

export function UsersPage() {
  const [tab, setTab] = useState<"kullanicilar" | "roller">("kullanicilar");

  return (
    <div>
      <PageHeader title={t.users.title} />

      <div className="mb-4 flex gap-1 border-b border-slate-200">
        {[
          { key: "kullanicilar" as const, label: "Kullanıcılar" },
          { key: "roller" as const, label: "Roller ve Yetkiler" },
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

      {tab === "roller" ? <RolesTab /> : <UsersTab />}
    </div>
  );
}

function UsersTab() {
  const qc = useQueryClient();
  const { has } = useAuth();
  const canEdit = has("USER_EDIT");
  const { data, isLoading, error } = useQuery({
    queryKey: ["users"],
    queryFn: () => api<UserView[]>("/api/v1/users"),
  });
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const toggleEnabled = useMutation({
    mutationFn: (u: UserView) =>
      api(`/api/v1/users/${u.id}/enabled`, { method: "PUT", body: { enabled: !u.enabled } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["users"] }),
  });

  // Yalnızca ADMIN/MUDUR PIN kullanır (§A7); "PIN'imi unuttum" kurtarma yolu.
  const resetPin = useMutation({
    mutationFn: (u: UserView) => api(`/api/v1/users/${u.id}/pin`, { method: "PUT" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["users"] }),
  });

  return (
    <div>
      {canEdit && (
        <div className="mb-3">
          <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
            {t.users.newUser}
          </button>
        </div>
      )}

      {showForm && <NewUserForm onClose={() => setShowForm(false)} />}

      {isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {error && <div className="text-red-600">{(error as ApiError).message}</div>}

      {data && (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">{t.auth.username}</th>
                <th className="px-4 py-2 font-medium">{t.users.fullName}</th>
                <th className="px-4 py-2 font-medium">{t.users.roles}</th>
                <th className="px-4 py-2 font-medium">{t.users.lastLogin}</th>
                <th className="px-4 py-2 font-medium">{t.common.actions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((u) => (
                <Fragment key={u.id}>
                  <tr className={u.enabled ? "" : "opacity-50"}>
                    <td className="px-4 py-2 font-medium">{u.username}</td>
                    <td className="px-4 py-2">{u.fullName}</td>
                    <td className="px-4 py-2">{u.roles.map((r) => roleLabels[r] ?? r).join(", ")}</td>
                    <td className="px-4 py-2 text-slate-500">
                      {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString("tr-TR") : "—"}
                    </td>
                    <td className="px-4 py-2">
                      {canEdit && (
                        <div className="flex gap-3">
                          <button
                            className="text-brand-700 hover:underline"
                            onClick={() => setEditingId(editingId === u.id ? null : u.id)}
                          >
                            {editingId === u.id ? t.common.cancel : "Rolleri Düzenle"}
                          </button>
                          <button
                            className="text-brand-700 hover:underline"
                            onClick={() => toggleEnabled.mutate(u)}
                          >
                            {u.enabled ? t.common.disabled : t.common.enabled}
                          </button>
                          {(u.roles.includes("ADMIN") || u.roles.includes("MUDUR")) && (
                            <button
                              className="text-brand-700 hover:underline"
                              onClick={() => {
                                if (window.confirm(t.users.confirmPinReset)) resetPin.mutate(u);
                              }}
                            >
                              {t.users.resetPin}
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                  {editingId === u.id && (
                    <tr>
                      <td colSpan={5} className="bg-slate-50 px-4 py-3">
                        <EditUserRolesForm user={u} onClose={() => setEditingId(null)} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function EditUserRolesForm({ user, onClose }: { user: UserView; onClose: () => void }) {
  const qc = useQueryClient();
  const [roles, setRoles] = useState<RoleName[]>(user.roles);
  const [err, setErr] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => api(`/api/v1/users/${user.id}/roles`, { method: "PUT", body: { roles } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] });
      onClose();
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const toggle = (r: RoleName) => {
    setRoles((prev) => (prev.includes(r) ? prev.filter((x) => x !== r) : [...prev, r]));
  };

  return (
    <div className="flex flex-wrap items-end gap-4">
      {err && <div className="w-full rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
      <div className="flex flex-wrap gap-3">
        {ALL_ROLES.map((r) => (
          <label key={r} className="flex items-center gap-1.5 text-sm">
            <input type="checkbox" checked={roles.includes(r)} onChange={() => toggle(r)} />
            {roleLabels[r]}
          </label>
        ))}
      </div>
      <div className="flex gap-2">
        <button
          className="btn-primary"
          disabled={save.isPending || roles.length === 0}
          onClick={() => {
            setErr(null);
            save.mutate();
          }}
        >
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </div>
  );
}

function RolesTab() {
  const { has } = useAuth();
  const canEdit = has("USER_EDIT");
  const [expanded, setExpanded] = useState<number | null>(null);
  const roles = useQuery({
    queryKey: ["roles"],
    queryFn: () => api<RoleView[]>("/api/v1/roles"),
  });

  return (
    <div>
      <p className="mb-3 text-sm text-slate-500">
        Her rolün hangi ekran ve işlemlere erişebileceğini burada belirleyin. Değişiklikler,
        o role sahip kullanıcılar bir sonraki girişte etkilenir. Yönetici (ADMIN) rolü daima
        tüm yetkilere sahiptir ve değiştirilemez.
      </p>
      {roles.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      <div className="space-y-2">
        {(roles.data ?? []).map((r) => (
          <div key={r.id} className="card">
            <div className="flex items-center justify-between">
              <button
                className="flex items-center gap-2 text-left font-medium"
                onClick={() => setExpanded(expanded === r.id ? null : r.id)}
              >
                <span>{roleLabels[r.name] ?? r.name}</span>
                {r.name === "ADMIN" && (
                  <span className="rounded bg-brand-50 px-1.5 py-0.5 text-xs text-brand-700">
                    tüm yetkiler (sabit)
                  </span>
                )}
                {r.name !== "ADMIN" && (
                  <span className="text-xs text-slate-400">{r.permissions.length} yetki</span>
                )}
              </button>
              <button
                className="text-sm text-slate-500 hover:underline"
                onClick={() => setExpanded(expanded === r.id ? null : r.id)}
              >
                {expanded === r.id ? "Kapat" : "Detay"}
              </button>
            </div>
            {expanded === r.id && r.name !== "ADMIN" && (
              <RolePermissionEditor role={r} canEdit={canEdit} />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function RolePermissionEditor({ role, canEdit }: { role: RoleView; canEdit: boolean }) {
  const qc = useQueryClient();
  const [selected, setSelected] = useState<Set<string>>(new Set(role.permissions));
  const [err, setErr] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      api(`/api/v1/roles/${role.id}/permissions`, {
        method: "PUT",
        body: { permissions: Array.from(selected) },
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["roles"] }),
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const reset = useMutation({
    mutationFn: () => api(`/api/v1/roles/${role.id}/permissions/reset`, { method: "POST" }),
    onSuccess: (r: unknown) => {
      const rv = r as RoleView;
      setSelected(new Set(rv.permissions));
      qc.invalidateQueries({ queryKey: ["roles"] });
    },
  });

  const toggle = (code: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  return (
    <div className="mt-3 border-t border-slate-100 pt-3">
      {err && <div className="mb-2 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {permissionGroups.map((g) => (
          <div key={g.module}>
            <div className="mb-1 text-xs font-semibold uppercase text-slate-400">{g.module}</div>
            <div className="space-y-1">
              {g.permissions.map((p) => (
                <label key={p.code} className="flex items-center gap-1.5 text-sm">
                  <input
                    type="checkbox"
                    disabled={!canEdit}
                    checked={selected.has(p.code)}
                    onChange={() => toggle(p.code)}
                  />
                  {p.label}
                </label>
              ))}
            </div>
          </div>
        ))}
      </div>
      {canEdit && (
        <div className="mt-4 flex gap-2 border-t border-slate-100 pt-3">
          <button className="btn-primary" disabled={save.isPending} onClick={() => { setErr(null); save.mutate(); }}>
            {t.common.save}
          </button>
          <button className="btn-ghost" disabled={reset.isPending} onClick={() => reset.mutate()}>
            Fabrika Ayarına Döndür
          </button>
        </div>
      )}
    </div>
  );
}

function NewUserForm({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const [username, setUsername] = useState("");
  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");
  const [roles, setRoles] = useState<RoleName[]>(["KASIYER"]);
  const [err, setErr] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api("/api/v1/users", { method: "POST", body: { username, fullName, password, roles } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] });
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
    <form onSubmit={submit} className="card mb-4 grid gap-3 sm:grid-cols-2">
      {err && <div className="sm:col-span-2 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
      <div>
        <label className="label">{t.auth.username}</label>
        <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} />
      </div>
      <div>
        <label className="label">{t.users.fullName}</label>
        <input className="input" value={fullName} onChange={(e) => setFullName(e.target.value)} />
      </div>
      <div>
        <label className="label">{t.auth.password}</label>
        <input
          type="password"
          className="input"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>
      <div>
        <label className="label">{t.users.roles}</label>
        <select
          multiple
          className="input h-24"
          value={roles}
          onChange={(e) =>
            setRoles(Array.from(e.target.selectedOptions).map((o) => o.value as RoleName))
          }
        >
          {ALL_ROLES.map((r) => (
            <option key={r} value={r}>
              {roleLabels[r]}
            </option>
          ))}
        </select>
      </div>
      <div className="sm:col-span-2 flex gap-2">
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

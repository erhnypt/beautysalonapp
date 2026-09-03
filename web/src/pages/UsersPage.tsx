import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import type { RoleName, UserView } from "../lib/types";
import { PageHeader } from "../components/PageHeader";
import { t, roleLabels } from "../lib/i18n";
import { useAuth } from "../lib/auth";

const ALL_ROLES: RoleName[] = ["ADMIN", "MUDUR", "KASIYER", "PERSONEL", "RAPOR_OKUYUCU"];

export function UsersPage() {
  const qc = useQueryClient();
  const { has } = useAuth();
  const canEdit = has("USER_EDIT");
  const { data, isLoading, error } = useQuery({
    queryKey: ["users"],
    queryFn: () => api<UserView[]>("/api/v1/users"),
  });
  const [showForm, setShowForm] = useState(false);

  const toggleEnabled = useMutation({
    mutationFn: (u: UserView) =>
      api(`/api/v1/users/${u.id}/enabled`, { method: "PUT", body: { enabled: !u.enabled } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["users"] }),
  });

  return (
    <div>
      <PageHeader
        title={t.users.title}
        actions={
          canEdit && (
            <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
              {t.users.newUser}
            </button>
          )
        }
      />

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
                <tr key={u.id} className={u.enabled ? "" : "opacity-50"}>
                  <td className="px-4 py-2 font-medium">{u.username}</td>
                  <td className="px-4 py-2">{u.fullName}</td>
                  <td className="px-4 py-2">{u.roles.map((r) => roleLabels[r] ?? r).join(", ")}</td>
                  <td className="px-4 py-2 text-slate-500">
                    {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString("tr-TR") : "—"}
                  </td>
                  <td className="px-4 py-2">
                    {canEdit && (
                      <button
                        className="text-brand-700 hover:underline"
                        onClick={() => toggleEnabled.mutate(u)}
                      >
                        {u.enabled ? t.common.disabled : t.common.enabled}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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

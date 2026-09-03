import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../lib/api";
import { useAuth } from "../lib/auth";
import { t } from "../lib/i18n";

export function ChangePasswordPage({ forced = false }: { forced?: boolean }) {
  const { refresh, logout } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    setMsg(null);
    setBusy(true);
    try {
      await api("/api/v1/auth/change-password", {
        method: "POST",
        body: { currentPassword: current, newPassword: next },
      });
      setMsg(t.auth.passwordChanged);
      await refresh();
      if (forced) navigate("/");
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : t.common.error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={forced ? "flex h-full items-center justify-center bg-slate-100 p-4" : ""}>
      <form onSubmit={submit} className="card w-full max-w-sm space-y-4">
        <h2 className="text-base font-semibold">{t.auth.changePassword}</h2>
        {forced && <p className="text-sm text-amber-700">{t.auth.mustChangePassword}</p>}
        {msg && <div className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700">{msg}</div>}
        {err && <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
        <div>
          <label className="label">{t.auth.currentPassword}</label>
          <input
            type="password"
            className="input"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            autoComplete="current-password"
          />
        </div>
        <div>
          <label className="label">{t.auth.newPassword}</label>
          <input
            type="password"
            className="input"
            value={next}
            onChange={(e) => setNext(e.target.value)}
            autoComplete="new-password"
          />
        </div>
        <div className="flex gap-2">
          <button className="btn-primary flex-1" disabled={busy}>
            {t.auth.changePassword}
          </button>
          {forced && (
            <button
              type="button"
              className="btn-ghost"
              onClick={async () => {
                await logout();
                navigate("/login");
              }}
            >
              {t.auth.signOut}
            </button>
          )}
        </div>
      </form>
    </div>
  );
}

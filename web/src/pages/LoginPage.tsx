import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { ApiError } from "../lib/api";
import { useLicense } from "../lib/license";
import { t } from "../lib/i18n";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const { data: license } = useLicense();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(username, password);
      navigate("/");
    } catch (err) {
      if (err instanceof ApiError && err.status === 423) setError(t.auth.accountLocked);
      else setError(t.auth.badCredentials);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex h-full items-center justify-center bg-slate-100 p-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-xl bg-brand-700 text-lg font-bold text-white">
            B
          </div>
          <h1 className="text-lg font-semibold">{t.appName}</h1>
          <p className="text-sm text-slate-500">{t.tagline}</p>
        </div>

        <form onSubmit={submit} className="card space-y-4">
          <h2 className="text-base font-semibold">{t.auth.loginTitle}</h2>
          {error && (
            <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
          )}
          <div>
            <label className="label" htmlFor="u">
              {t.auth.username}
            </label>
            <input
              id="u"
              className="input"
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
            />
          </div>
          <div>
            <label className="label" htmlFor="p">
              {t.auth.password}
            </label>
            <input
              id="p"
              type="password"
              className="input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>
          <button className="btn-primary w-full" disabled={busy}>
            {busy ? t.common.loading : t.auth.signIn}
          </button>
        </form>

        {license && !license.devMode && license.status !== "ACTIVE" && (
          <p className="mt-3 text-center text-xs text-slate-500">
            {t.license[license.status] ?? license.status}
          </p>
        )}
      </div>
    </div>
  );
}

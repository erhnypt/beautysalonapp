import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../lib/api";
import { useAuth } from "../lib/auth";
import { t } from "../lib/i18n";

/**
 * Kritik roller (ADMIN/MUDUR) için parola sonrası 2. faktör adımı (§A7). App.tsx bu
 * bileşeni {@code user.pinSetupRequired}/{@code pinVerificationRequired} true olduğunda,
 * normal rotaların YERİNE gösterir — tıpkı zorunlu parola değiştirme ekranı gibi.
 */
export function PinGatePage() {
  const { user } = useAuth();
  return user?.pinSetupRequired ? <PinSetupForm /> : <PinVerifyForm />;
}

function PinSetupForm() {
  const { refresh, logout } = useAuth();
  const navigate = useNavigate();
  const [pin, setPin] = useState("");
  const [confirm, setConfirm] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    if (!/^[0-9]{4,6}$/.test(pin)) {
      setErr(t.auth.pinInvalidFormat);
      return;
    }
    if (pin !== confirm) {
      setErr(t.auth.pinMismatch);
      return;
    }
    setBusy(true);
    try {
      await api("/api/v1/auth/pin/setup", { method: "POST", body: { pin } });
      await refresh();
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : t.common.error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex h-full items-center justify-center bg-slate-100 p-4">
      <form onSubmit={submit} className="card w-full max-w-sm space-y-4">
        <h2 className="text-base font-semibold">{t.auth.pinSetupTitle}</h2>
        <p className="text-sm text-slate-600">{t.auth.pinSetupInfo}</p>
        {err && <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
        <div>
          <label className="label">{t.auth.pin}</label>
          <input
            type="password"
            inputMode="numeric"
            autoFocus
            className="input"
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
          />
        </div>
        <div>
          <label className="label">{t.auth.pinConfirm}</label>
          <input
            type="password"
            inputMode="numeric"
            className="input"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value.replace(/\D/g, "").slice(0, 6))}
          />
        </div>
        <div className="flex gap-2">
          <button className="btn-primary flex-1" disabled={busy}>
            {t.auth.pinSetupSubmit}
          </button>
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
        </div>
      </form>
    </div>
  );
}

function PinVerifyForm() {
  const { user, refresh, logout } = useAuth();
  const navigate = useNavigate();
  const [pin, setPin] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const signOut = async () => {
    await logout();
    navigate("/login");
  };

  // Sunucu bu oturumu zaten PIN_LOCKED yetkisiyle kurduysa (kilitliyken yeniden giriş
  // yapıldığında) kullanıcıyı boş yere bir PIN girip 423 almaya zorlamadan doğrudan
  // kilit mesajını gösteriyoruz — çıkış dışında yapılacak bir şey yok.
  if (user?.pinLockedUntil) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-100 p-4">
        <div className="card w-full max-w-sm space-y-4">
          <h2 className="text-base font-semibold">{t.auth.pinLockedTitle}</h2>
          <p className="text-sm text-amber-700">
            {t.auth.pinLockedUntil(new Date(user.pinLockedUntil).toLocaleString("tr-TR"))}
          </p>
          <button className="btn-primary w-full" onClick={signOut}>
            {t.auth.signOut}
          </button>
        </div>
      </div>
    );
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    setBusy(true);
    try {
      await api("/api/v1/auth/pin/verify", { method: "POST", body: { pin } });
      await refresh();
    } catch (e2) {
      if (e2 instanceof ApiError && e2.status === 423) {
        // Çok sayıda hatalı deneme: sunucu oturumu zaten sonlandırdı.
        await logout();
        navigate("/login");
        return;
      }
      setErr(e2 instanceof ApiError && e2.status === 401 ? t.auth.pinWrong
        : e2 instanceof ApiError ? e2.message : t.common.error);
      setPin("");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex h-full items-center justify-center bg-slate-100 p-4">
      <form onSubmit={submit} className="card w-full max-w-sm space-y-4">
        <h2 className="text-base font-semibold">{t.auth.pinVerifyTitle}</h2>
        <p className="text-sm text-slate-600">{t.auth.pinVerifyInfo}</p>
        {err && <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
        <div>
          <label className="label">{t.auth.pin}</label>
          <input
            type="password"
            inputMode="numeric"
            autoFocus
            className="input"
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
          />
        </div>
        <div className="flex gap-2">
          <button className="btn-primary flex-1" disabled={busy || pin.length < 4}>
            {t.auth.pinVerifySubmit}
          </button>
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
        </div>
      </form>
    </div>
  );
}

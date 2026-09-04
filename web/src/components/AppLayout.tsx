import { NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  CalendarDays,
  Users2,
  Boxes,
  Wallet,
  FileText,
  FileSignature,
  UserCog,
  Gift,
  BarChart3,
  Bell,
  Settings,
  ShieldCheck,
  ScrollText,
  KeyRound,
  DatabaseBackup,
  LogOut,
  LayoutDashboard,
  Landmark,
  Building2,
  Menu,
  X,
} from "lucide-react";
import { useAuth } from "../lib/auth";
import { t } from "../lib/i18n";
import { LicenseBanner } from "./LicenseBanner";
import { useState, type ReactNode } from "react";

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  perm?: string;
}

const items: NavItem[] = [
  { to: "/", label: t.nav.dashboard, icon: <LayoutDashboard size={18} /> },
  { to: "/randevular", label: t.nav.appointments, icon: <CalendarDays size={18} />, perm: "APPOINTMENT_VIEW" },
  { to: "/cari", label: t.nav.parties, icon: <Users2 size={18} />, perm: "PARTY_VIEW" },
  { to: "/stok", label: t.nav.stock, icon: <Boxes size={18} />, perm: "STOCK_VIEW" },
  { to: "/finans", label: t.nav.finance, icon: <Wallet size={18} />, perm: "FINANCE_VIEW" },
  { to: "/banka-mutabakat", label: t.nav.bankReconciliation, icon: <Landmark size={18} />, perm: "FINANCE_VIEW" },
  { to: "/faturalar", label: t.nav.invoices, icon: <FileText size={18} />, perm: "INVOICE_VIEW" },
  { to: "/sozlesmeler", label: t.nav.contracts, icon: <FileSignature size={18} />, perm: "CONTRACT_VIEW" },
  { to: "/personel", label: t.nav.staff, icon: <UserCog size={18} />, perm: "STAFF_VIEW" },
  { to: "/sadakat", label: t.nav.loyalty, icon: <Gift size={18} />, perm: "LOYALTY_VIEW" },
  { to: "/bildirimler", label: t.nav.notifications, icon: <Bell size={18} />, perm: "NOTIFICATION_VIEW" },
  { to: "/raporlar", label: t.nav.reports, icon: <BarChart3 size={18} />, perm: "REPORTING_VIEW" },
  { to: "/kullanicilar", label: t.nav.users, icon: <ShieldCheck size={18} />, perm: "USER_VIEW" },
  { to: "/islem-kayitlari", label: t.nav.audit, icon: <ScrollText size={18} />, perm: "AUDIT_VIEW" },
  { to: "/lisans", label: t.nav.license, icon: <KeyRound size={18} /> },
  { to: "/yedekleme", label: t.nav.backup, icon: <DatabaseBackup size={18} />, perm: "BACKUP_RUN" },
  { to: "/subeler", label: t.nav.branches, icon: <Building2 size={18} />, perm: "SETTINGS_VIEW" },
  { to: "/ayarlar", label: t.nav.settings, icon: <Settings size={18} />, perm: "SETTINGS_VIEW" },
];

export function AppLayout() {
  const { user, logout, has } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const visible = items.filter((it) => !it.perm || has(it.perm));

  return (
    <div className="flex h-full">
      {/* Mobil: kenar çubuğu açıkken arkaya karartma */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/30 sm:hidden"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-slate-200 bg-white
          transition-transform duration-200 sm:static sm:z-auto sm:w-60 sm:translate-x-0
          ${mobileOpen ? "translate-x-0" : "-translate-x-full"}`}
      >
        <div className="flex items-center justify-between gap-2 px-5 py-4">
          <div className="flex items-center gap-2">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-700 font-bold text-white">
              B
            </span>
            <div>
              <div className="text-sm font-semibold leading-tight">{t.appName}</div>
              <div className="text-[11px] text-slate-500">{t.tagline}</div>
            </div>
          </div>
          <button
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 sm:hidden"
            onClick={() => setMobileOpen(false)}
            aria-label="Menüyü kapat"
          >
            <X size={18} />
          </button>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-2 py-2">
          {visible.map((it) => (
            <NavLink
              key={it.to}
              to={it.to}
              end={it.to === "/"}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-brand-50 text-brand-700"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }`
              }
            >
              {it.icon}
              {it.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-slate-200 p-3">
          <div className="mb-2 px-2 text-xs text-slate-500">
            {user?.fullName} · {user?.roles.map((r) => r).join(", ")}
          </div>
          <button
            className="btn-ghost w-full"
            onClick={async () => {
              await logout();
              navigate("/login");
            }}
          >
            <LogOut size={16} />
            {t.auth.signOut}
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Mobil üst çubuk: hamburger */}
        <div className="flex items-center gap-2 border-b border-slate-200 bg-white px-4 py-2 sm:hidden">
          <button
            className="rounded-lg p-1.5 text-slate-600 hover:bg-slate-100"
            onClick={() => setMobileOpen(true)}
            aria-label="Menüyü aç"
          >
            <Menu size={20} />
          </button>
          <span className="text-sm font-semibold">{t.appName}</span>
        </div>
        <LicenseBanner />
        <main className="flex-1 overflow-y-auto p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

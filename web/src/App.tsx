import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./lib/auth";
import { AppLayout } from "./components/AppLayout";
import { LoginPage } from "./pages/LoginPage";
import { ChangePasswordPage } from "./pages/ChangePasswordPage";
import { DashboardPage } from "./pages/DashboardPage";
import { UsersPage } from "./pages/UsersPage";
import { SettingsPage } from "./pages/SettingsPage";
import { AuditPage } from "./pages/AuditPage";
import { LicensePage } from "./pages/LicensePage";
import { PlaceholderPage } from "./pages/PlaceholderPage";
import { PartiesPage } from "./pages/PartiesPage";
import { StockPage } from "./pages/StockPage";
import { FinancePage } from "./pages/FinancePage";
import { t } from "./lib/i18n";

export default function App() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center text-slate-500">{t.common.loading}</div>
    );
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  if (user.mustChangePassword) {
    return (
      <Routes>
        <Route path="/parola-degistir" element={<ChangePasswordPage forced />} />
        <Route path="*" element={<Navigate to="/parola-degistir" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/login" element={<Navigate to="/" replace />} />
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="parola-degistir" element={<ChangePasswordPage />} />
        <Route path="kullanicilar" element={<UsersPage />} />
        <Route path="ayarlar" element={<SettingsPage />} />
        <Route path="islem-kayitlari" element={<AuditPage />} />
        <Route path="lisans" element={<LicensePage />} />
        <Route path="randevular" element={<PlaceholderPage titleKey="appointments" />} />
        <Route path="cari" element={<PartiesPage />} />
        <Route path="stok" element={<StockPage />} />
        <Route path="finans" element={<FinancePage />} />
        <Route path="faturalar" element={<PlaceholderPage titleKey="invoices" />} />
        <Route path="sozlesmeler" element={<PlaceholderPage titleKey="contracts" />} />
        <Route path="personel" element={<PlaceholderPage titleKey="staff" />} />
        <Route path="sadakat" element={<PlaceholderPage titleKey="loyalty" />} />
        <Route path="raporlar" element={<PlaceholderPage titleKey="reports" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

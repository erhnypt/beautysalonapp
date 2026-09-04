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
import { ContractsPage } from "./pages/ContractsPage";
import { AppointmentsPage } from "./pages/AppointmentsPage";
import { InvoicesPage } from "./pages/InvoicesPage";
import { StaffPage } from "./pages/StaffPage";
import { BackupPage } from "./pages/BackupPage";
import { LoyaltyPage } from "./pages/LoyaltyPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { BankReconciliationPage } from "./pages/BankReconciliationPage";
import { BranchesPage } from "./pages/BranchesPage";
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
        <Route path="subeler" element={<BranchesPage />} />
        <Route path="islem-kayitlari" element={<AuditPage />} />
        <Route path="lisans" element={<LicensePage />} />
        <Route path="yedekleme" element={<BackupPage />} />
        <Route path="randevular" element={<AppointmentsPage />} />
        <Route path="cari" element={<PartiesPage />} />
        <Route path="stok" element={<StockPage />} />
        <Route path="finans" element={<FinancePage />} />
        <Route path="banka-mutabakat" element={<BankReconciliationPage />} />
        <Route path="faturalar" element={<InvoicesPage />} />
        <Route path="sozlesmeler" element={<ContractsPage />} />
        <Route path="personel" element={<StaffPage />} />
        <Route path="sadakat" element={<LoyaltyPage />} />
        <Route path="bildirimler" element={<NotificationsPage />} />
        <Route path="raporlar" element={<PlaceholderPage titleKey="reports" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

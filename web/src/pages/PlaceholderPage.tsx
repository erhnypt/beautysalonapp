import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";

export function PlaceholderPage({ titleKey }: { titleKey: keyof typeof t.nav }) {
  return (
    <div>
      <PageHeader title={t.nav[titleKey]} />
      <div className="card text-sm text-slate-600">{t.dashboard.comingSoon}</div>
    </div>
  );
}

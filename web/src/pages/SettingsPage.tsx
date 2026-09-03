import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../lib/api";
import type { SettingView } from "../lib/types";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

export function SettingsPage() {
  const qc = useQueryClient();
  const { has } = useAuth();
  const canEdit = has("SETTINGS_EDIT");
  const { data, isLoading } = useQuery({
    queryKey: ["settings"],
    queryFn: () => api<SettingView[]>("/api/v1/settings"),
  });

  return (
    <div>
      <PageHeader title={t.settings.title} />
      {isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {data && data.length === 0 && (
        <div className="card text-sm text-slate-600">
          Henüz ayar kaydı yok. Kurulum sihirbazı ve modüller ilk ayarları oluşturacak.
        </div>
      )}
      {data && data.length > 0 && (
        <div className="space-y-2">
          {data.map((s) => (
            <Row key={s.key} setting={s} canEdit={canEdit} onSaved={() => qc.invalidateQueries({ queryKey: ["settings"] })} />
          ))}
        </div>
      )}
    </div>
  );
}

function Row({
  setting,
  canEdit,
  onSaved,
}: {
  setting: SettingView;
  canEdit: boolean;
  onSaved: () => void;
}) {
  const [value, setValue] = useState(setting.value ?? "");
  const save = useMutation({
    mutationFn: () =>
      api(`/api/v1/settings/${encodeURIComponent(setting.key)}`, {
        method: "PUT",
        body: { value, description: setting.description, secret: setting.secret },
      }),
    onSuccess: onSaved,
  });

  return (
    <div className="card flex items-center gap-4">
      <div className="w-64 shrink-0">
        <div className="font-mono text-sm">{setting.key}</div>
        {setting.description && <div className="text-xs text-slate-500">{setting.description}</div>}
      </div>
      <input
        className="input flex-1"
        value={setting.secret ? "" : value}
        placeholder={setting.secret ? t.settings.secretHidden : ""}
        disabled={!canEdit}
        onChange={(e) => setValue(e.target.value)}
      />
      {canEdit && (
        <button className="btn-primary" disabled={save.isPending} onClick={() => save.mutate()}>
          {t.common.save}
        </button>
      )}
    </div>
  );
}

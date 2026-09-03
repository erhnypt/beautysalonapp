import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { AuditView, Page } from "../lib/types";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useState } from "react";

export function AuditPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useQuery({
    queryKey: ["audit", page],
    queryFn: () => api<Page<AuditView>>(`/api/v1/audit?page=${page}&size=50`),
  });

  return (
    <div>
      <PageHeader title={t.audit.title} />
      {isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {data && (
        <>
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-4 py-2 font-medium">{t.audit.time}</th>
                  <th className="px-4 py-2 font-medium">{t.audit.actor}</th>
                  <th className="px-4 py-2 font-medium">{t.audit.action}</th>
                  <th className="px-4 py-2 font-medium">{t.audit.entity}</th>
                  <th className="px-4 py-2 font-medium">{t.audit.summary}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((a) => (
                  <tr key={a.id}>
                    <td className="whitespace-nowrap px-4 py-2 text-slate-500">
                      {new Date(a.at).toLocaleString("tr-TR")}
                    </td>
                    <td className="px-4 py-2">{a.actor}</td>
                    <td className="px-4 py-2 font-mono text-xs">{a.action}</td>
                    <td className="px-4 py-2 text-slate-500">
                      {a.entityType}
                      {a.entityId ? ` #${a.entityId}` : ""}
                    </td>
                    <td className="px-4 py-2">{a.summary}</td>
                  </tr>
                ))}
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
                      {t.common.noRecords}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="mt-3 flex items-center gap-2 text-sm">
            <button
              className="btn-ghost"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ‹
            </button>
            <span className="text-slate-500">
              {page + 1} / {Math.max(1, data.totalPages)}
            </span>
            <button
              className="btn-ghost"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              ›
            </button>
          </div>
        </>
      )}
    </div>
  );
}

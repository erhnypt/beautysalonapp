import type { ReactNode } from "react";
import { t } from "../lib/i18n";

export interface Column<T> {
  key: string;
  label: string;
  align?: "left" | "right" | "center";
  render?: (row: T) => ReactNode;
}

/** Genel amaçlı, sütun tanımından üretilen tablo — raporlar arası kod tekrarını azaltır. */
export function DataTable<T>({
  columns,
  rows,
  keyFn,
  emptyLabel,
}: {
  columns: Column<T>[];
  rows: T[];
  keyFn: (row: T, index: number) => string | number;
  emptyLabel?: string;
}) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-left text-slate-500">
          <tr>
            {columns.map((c) => (
              <th
                key={c.key}
                className={`whitespace-nowrap px-4 py-2 font-medium ${
                  c.align === "right" ? "text-right" : c.align === "center" ? "text-center" : ""
                }`}
              >
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row, i) => (
            <tr key={keyFn(row, i)}>
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={`whitespace-nowrap px-4 py-2 ${
                    c.align === "right" ? "text-right tabular-nums" : c.align === "center" ? "text-center" : ""
                  }`}
                >
                  {c.render ? c.render(row) : String((row as Record<string, unknown>)[c.key] ?? "—")}
                </td>
              ))}
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="px-4 py-6 text-center text-slate-400">
                {emptyLabel ?? t.common.noRecords}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { useAuth } from "../lib/auth";

type OwnerType = "PARTY" | "CONTRACT";

interface AttachmentView {
  id: number;
  ownerType: OwnerType;
  ownerId: number;
  fileName: string;
  contentType: string | null;
  sizeBytes: number | null;
  sha256: string;
}

const kb = (n: number | null) => (n == null ? "—" : `${(n / 1024).toFixed(0)} KB`);

export function AttachmentPanel({
  ownerType,
  ownerId,
  editPermission,
}: {
  ownerType: OwnerType;
  ownerId: number;
  editPermission: string;
}) {
  const { has } = useAuth();
  const qc = useQueryClient();
  const canEdit = has(editPermission);
  const fileRef = useRef<HTMLInputElement>(null);
  const [err, setErr] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ["attachments", ownerType, ownerId],
    queryFn: () =>
      api<AttachmentView[]>(`/api/v1/attachments?ownerType=${ownerType}&ownerId=${ownerId}`),
  });

  const upload = useMutation({
    mutationFn: async () => {
      const f = fileRef.current?.files?.[0];
      if (!f) throw new Error("Dosya seçin");
      const fd = new FormData();
      fd.append("file", f);
      return api(`/api/v1/attachments?ownerType=${ownerType}&ownerId=${ownerId}`, {
        method: "POST",
        body: fd,
      });
    },
    onSuccess: () => {
      if (fileRef.current) fileRef.current.value = "";
      qc.invalidateQueries({ queryKey: ["attachments", ownerType, ownerId] });
    },
    onError: (e) => setErr(e instanceof ApiError ? e.message : "İşlem başarısız"),
  });

  const remove = useMutation({
    mutationFn: (id: number) => api(`/api/v1/attachments/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["attachments", ownerType, ownerId] }),
  });

  const download = async (a: AttachmentView) => {
    const res = await fetch(`/api/v1/attachments/${a.id}/download`, { credentials: "include" });
    if (!res.ok) {
      setErr("İndirme başarısız");
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = a.fileName;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="mt-4 border-t border-slate-100 pt-3">
      <h3 className="mb-2 text-sm font-semibold text-slate-700">Ek Dosyalar</h3>
      {err && <div className="mb-2 rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
      <ul className="divide-y divide-slate-100">
        {(list.data ?? []).map((a) => (
          <li key={a.id} className="flex items-center justify-between py-1.5 text-sm">
            <button className="truncate text-left text-brand-700 hover:underline" onClick={() => download(a)}>
              {a.fileName}
            </button>
            <div className="flex items-center gap-3 text-xs text-slate-400">
              <span>{kb(a.sizeBytes)}</span>
              {canEdit && (
                <button
                  className="text-red-600 hover:underline"
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(a.id)}
                >
                  Sil
                </button>
              )}
            </div>
          </li>
        ))}
        {(list.data ?? []).length === 0 && (
          <li className="py-2 text-sm text-slate-400">Ek dosya yok</li>
        )}
      </ul>
      {canEdit && (
        <div className="mt-2 flex items-center gap-2">
          <input ref={fileRef} type="file" className="text-sm" />
          <button className="btn-ghost" disabled={upload.isPending} onClick={() => upload.mutate()}>
            Yükle
          </button>
        </div>
      )}
    </div>
  );
}

import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { t } from "../lib/i18n";
import { useAuth } from "../lib/auth";

interface ApptView {
  id: number;
  partyId: number;
  staffPartyId: number;
  serviceId: number;
  startAt: string;
  endAt: string;
  status: "PLANLANDI" | "ONAYLANDI" | "GELDI" | "GELMEDI" | "IPTAL";
  notes: string | null;
  priceSnapshot: number;
}
interface PartyRow {
  id: number;
  code: string;
  title: string;
}
interface Page<T> {
  content: T[];
}
interface ServiceView {
  id: number;
  code: string;
  name: string;
  durationMin: number;
  price: number;
}

const statusChip: Record<string, string> = {
  PLANLANDI: "bg-slate-100 text-slate-600",
  ONAYLANDI: "bg-sky-100 text-sky-700",
  GELDI: "bg-green-100 text-green-700",
  GELMEDI: "bg-red-100 text-red-700",
  IPTAL: "bg-slate-200 text-slate-400 line-through",
};

const fmtTime = (iso: string) =>
  new Date(iso).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });

export function AppointmentsPage() {
  const { has } = useAuth();
  const qc = useQueryClient();
  const [day, setDay] = useState(() => new Date().toISOString().slice(0, 10));
  const [showForm, setShowForm] = useState(false);

  const range = useMemo(() => {
    const from = new Date(`${day}T00:00:00`);
    const to = new Date(`${day}T23:59:59`);
    return { from: from.toISOString(), to: to.toISOString() };
  }, [day]);

  const list = useQuery({
    queryKey: ["appointments", day],
    queryFn: () =>
      api<ApptView[]>(
        `/api/v1/appointments?from=${encodeURIComponent(range.from)}&to=${encodeURIComponent(range.to)}`,
      ),
  });
  const staff = useQuery({
    queryKey: ["parties", "PERSONEL", ""],
    queryFn: () => api<Page<PartyRow>>("/api/v1/parties?type=PERSONEL&size=100"),
  });
  const services = useQuery({
    queryKey: ["services"],
    queryFn: () => api<ServiceView[]>("/api/v1/appointments/services"),
  });

  const staffName = (id: number) => staff.data?.content.find((s) => s.id === id)?.title ?? `#${id}`;
  const svcName = (id: number) => services.data?.find((s) => s.id === id)?.name ?? `#${id}`;

  const setStatus = useMutation({
    mutationFn: ({ id, status, collectCash }: { id: number; status: string; collectCash?: boolean }) =>
      api(`/api/v1/appointments/${id}/status`, {
        method: "POST",
        body: { status, collectCash: !!collectCash },
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      qc.invalidateQueries({ queryKey: ["fin-accounts"] });
    },
  });

  return (
    <div>
      <PageHeader
        title={t.nav.appointments}
        actions={
          <div className="flex items-center gap-2">
            <input
              type="date"
              className="input w-auto"
              value={day}
              onChange={(e) => setDay(e.target.value)}
            />
            {has("APPOINTMENT_ADD") && (
              <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
                {t.common.add}
              </button>
            )}
          </div>
        }
      />

      {showForm && (
        <NewAppointmentForm
          day={day}
          staff={staff.data?.content ?? []}
          services={services.data ?? []}
          onClose={() => setShowForm(false)}
          onDone={() => {
            qc.invalidateQueries({ queryKey: ["appointments"] });
            setShowForm(false);
          }}
        />
      )}

      {list.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      {list.data && list.data.length === 0 && (
        <div className="card text-sm text-slate-500">Bu gün için randevu yok.</div>
      )}
      <div className="space-y-2">
        {(list.data ?? [])
          .slice()
          .sort((a, b) => a.startAt.localeCompare(b.startAt))
          .map((a) => (
            <div key={a.id} className="card flex items-center gap-4 py-3">
              <div className="w-24 shrink-0 text-sm font-medium tabular-nums">
                {fmtTime(a.startAt)}–{fmtTime(a.endAt)}
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium">{svcName(a.serviceId)}</div>
                <div className="text-xs text-slate-500">
                  {staffName(a.staffPartyId)} · Müşteri #{a.partyId}
                  {a.notes ? ` · ${a.notes}` : ""}
                </div>
              </div>
              <span className={`rounded px-1.5 py-0.5 text-xs ${statusChip[a.status]}`}>{a.status}</span>
              {a.status !== "GELDI" && a.status !== "GELMEDI" && a.status !== "IPTAL" && (
                <div className="flex gap-1 text-xs">
                  <button
                    className="rounded bg-green-600 px-2 py-1 text-white hover:bg-green-700"
                    onClick={() => setStatus.mutate({ id: a.id, status: "GELDI", collectCash: true })}
                  >
                    Geldi + Tahsil
                  </button>
                  <button
                    className="rounded border border-slate-300 px-2 py-1 hover:bg-slate-100"
                    onClick={() => setStatus.mutate({ id: a.id, status: "GELMEDI" })}
                  >
                    Gelmedi
                  </button>
                  <button
                    className="rounded border border-slate-300 px-2 py-1 hover:bg-slate-100"
                    onClick={() => setStatus.mutate({ id: a.id, status: "IPTAL" })}
                  >
                    İptal
                  </button>
                </div>
              )}
            </div>
          ))}
      </div>
    </div>
  );
}

function NewAppointmentForm({
  day,
  staff,
  services,
  onClose,
  onDone,
}: {
  day: string;
  staff: PartyRow[];
  services: ServiceView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const customers = useQuery({
    queryKey: ["parties", "MUSTERI", ""],
    queryFn: () => api<Page<PartyRow>>("/api/v1/parties?type=MUSTERI&size=100"),
  });
  const [partyId, setPartyId] = useState<number | "">("");
  const [staffPartyId, setStaffPartyId] = useState<number | "">("");
  const [serviceId, setServiceId] = useState<number | "">("");
  const [time, setTime] = useState("10:00");
  const [notes, setNotes] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const book = useMutation({
    mutationFn: () =>
      api("/api/v1/appointments", {
        method: "POST",
        body: {
          partyId: Number(partyId),
          staffPartyId: Number(staffPartyId),
          serviceId: Number(serviceId),
          startAt: new Date(`${day}T${time}:00`).toISOString(),
          source: "YERINDE",
          notes: notes || null,
        },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    book.mutate();
  };

  return (
    <form onSubmit={submit} className="card mb-4 grid gap-3 sm:grid-cols-4">
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      <div className="sm:col-span-2">
        <label className="label">Müşteri</label>
        <select className="input" value={partyId} onChange={(e) => setPartyId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Seçin</option>
          {(customers.data?.content ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Personel</label>
        <select className="input" value={staffPartyId} onChange={(e) => setStaffPartyId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Seçin</option>
          {staff.map((s) => (
            <option key={s.id} value={s.id}>
              {s.title}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Saat</label>
        <input type="time" className="input" value={time} onChange={(e) => setTime(e.target.value)} />
      </div>
      <div className="sm:col-span-2">
        <label className="label">Hizmet</label>
        <select className="input" value={serviceId} onChange={(e) => setServiceId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Seçin</option>
          {services.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name} ({s.durationMin} dk)
            </option>
          ))}
        </select>
      </div>
      <div className="sm:col-span-2">
        <label className="label">Not</label>
        <input className="input" value={notes} onChange={(e) => setNotes(e.target.value)} />
      </div>
      <div className="flex items-end gap-2 sm:col-span-4">
        <button className="btn-primary" disabled={book.isPending || !partyId || !staffPartyId || !serviceId}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

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
interface WaitlistView {
  id: number;
  partyId: number;
  serviceId: number;
  staffPartyId: number | null;
  preferredFrom: string | null;
  preferredTo: string | null;
  notes: string | null;
  status: "BEKLEMEDE" | "RANDEVUYA_DONUSTU" | "IPTAL";
  appointmentId: number | null;
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
  const [tab, setTab] = useState<"takvim" | "bekleme">("takvim");
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
          tab === "takvim" && (
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
          )
        }
      />

      <div className="mb-4 flex gap-1 border-b border-slate-200">
        {[
          { key: "takvim" as const, label: "Takvim" },
          { key: "bekleme" as const, label: "Bekleme Listesi" },
        ].map((it) => (
          <button
            key={it.key}
            onClick={() => setTab(it.key)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
              tab === it.key
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            {it.label}
          </button>
        ))}
      </div>

      {tab === "bekleme" ? (
        <WaitlistTab staff={staff.data?.content ?? []} services={services.data ?? []} />
      ) : (
        <>
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
        </>
      )}
    </div>
  );
}

function WaitlistTab({ staff, services }: { staff: PartyRow[]; services: ServiceView[] }) {
  const { has } = useAuth();
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [convertingId, setConvertingId] = useState<number | null>(null);

  const list = useQuery({
    queryKey: ["waitlist"],
    queryFn: () => api<WaitlistView[]>("/api/v1/appointments/waitlist?status=BEKLEMEDE"),
  });
  const customers = useQuery({
    queryKey: ["parties", "MUSTERI", ""],
    queryFn: () => api<Page<PartyRow>>("/api/v1/parties?type=MUSTERI&size=100"),
  });

  const cancel = useMutation({
    mutationFn: (id: number) => api(`/api/v1/appointments/waitlist/${id}/cancel`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["waitlist"] }),
  });

  const customerName = (id: number) => customers.data?.content.find((p) => p.id === id)?.title ?? `#${id}`;
  const staffName = (id: number | null) => (id == null ? "Fark etmez" : staff.find((s) => s.id === id)?.title ?? `#${id}`);
  const svcName = (id: number) => services.find((s) => s.id === id)?.name ?? `#${id}`;

  return (
    <div>
      <div className="mb-3 flex justify-end">
        {has("APPOINTMENT_ADD") && (
          <button className="btn-primary" onClick={() => setShowForm((v) => !v)}>
            {t.common.add}
          </button>
        )}
      </div>
      {showForm && (
        <NewWaitlistForm
          customers={customers.data?.content ?? []}
          staff={staff}
          services={services}
          onClose={() => setShowForm(false)}
          onDone={() => {
            qc.invalidateQueries({ queryKey: ["waitlist"] });
            setShowForm(false);
          }}
        />
      )}
      {list.isLoading && <div className="text-slate-500">{t.common.loading}</div>}
      <div className="space-y-2">
        {(list.data ?? []).map((w) => (
          <div key={w.id} className="card flex items-center gap-4 py-3">
            <div className="min-w-0 flex-1">
              <div className="truncate font-medium">
                {customerName(w.partyId)} · {svcName(w.serviceId)}
              </div>
              <div className="text-xs text-slate-500">
                Personel: {staffName(w.staffPartyId)}
                {w.notes ? ` · ${w.notes}` : ""}
              </div>
            </div>
            <div className="flex gap-1 text-xs">
              <button
                className="rounded bg-brand-600 px-2 py-1 text-white hover:bg-brand-700"
                onClick={() => setConvertingId(w.id)}
              >
                Randevuya Dönüştür
              </button>
              <button
                className="rounded border border-slate-300 px-2 py-1 hover:bg-slate-100"
                disabled={cancel.isPending}
                onClick={() => cancel.mutate(w.id)}
              >
                İptal
              </button>
            </div>
          </div>
        ))}
        {list.data && list.data.length === 0 && (
          <div className="card text-sm text-slate-500">Bekleme listesi boş.</div>
        )}
      </div>
      {convertingId && (
        <ConvertWaitlistForm
          waitlistId={convertingId}
          staff={staff}
          onClose={() => setConvertingId(null)}
          onDone={() => {
            setConvertingId(null);
            qc.invalidateQueries({ queryKey: ["waitlist"] });
            qc.invalidateQueries({ queryKey: ["appointments"] });
          }}
        />
      )}
    </div>
  );
}

function NewWaitlistForm({
  customers,
  staff,
  services,
  onClose,
  onDone,
}: {
  customers: PartyRow[];
  staff: PartyRow[];
  services: ServiceView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [partyId, setPartyId] = useState<number | "">("");
  const [serviceId, setServiceId] = useState<number | "">("");
  const [staffPartyId, setStaffPartyId] = useState<number | "">("");
  const [notes, setNotes] = useState("");
  const [err, setErr] = useState<string | null>(null);

  const add = useMutation({
    mutationFn: () =>
      api("/api/v1/appointments/waitlist", {
        method: "POST",
        body: {
          partyId: Number(partyId),
          serviceId: Number(serviceId),
          staffPartyId: staffPartyId === "" ? null : Number(staffPartyId),
          notes: notes || null,
        },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    add.mutate();
  };

  return (
    <form onSubmit={submit} className="card mb-4 grid gap-3 sm:grid-cols-4">
      {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 sm:col-span-4">{err}</div>}
      <div>
        <label className="label">Müşteri</label>
        <select className="input" value={partyId} onChange={(e) => setPartyId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Seçin</option>
          {customers.map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Hizmet</label>
        <select className="input" value={serviceId} onChange={(e) => setServiceId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Seçin</option>
          {services.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Tercih Edilen Personel</label>
        <select className="input" value={staffPartyId} onChange={(e) => setStaffPartyId(e.target.value ? Number(e.target.value) : "")}>
          <option value="">Fark etmez</option>
          {staff.map((s) => (
            <option key={s.id} value={s.id}>
              {s.title}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label">Not</label>
        <input className="input" value={notes} onChange={(e) => setNotes(e.target.value)} />
      </div>
      <div className="flex items-end gap-2 sm:col-span-4">
        <button className="btn-primary" disabled={add.isPending || !partyId || !serviceId}>
          {t.common.save}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>
          {t.common.cancel}
        </button>
      </div>
    </form>
  );
}

function ConvertWaitlistForm({
  waitlistId,
  staff,
  onClose,
  onDone,
}: {
  waitlistId: number;
  staff: PartyRow[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [staffPartyId, setStaffPartyId] = useState<number | "">("");
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [time, setTime] = useState("10:00");
  const [err, setErr] = useState<string | null>(null);

  const convert = useMutation({
    mutationFn: () =>
      api(`/api/v1/appointments/waitlist/${waitlistId}/convert`, {
        method: "POST",
        body: {
          staffPartyId: Number(staffPartyId),
          startAt: new Date(`${date}T${time}:00`).toISOString(),
        },
      }),
    onSuccess: onDone,
    onError: (e) => setErr(e instanceof ApiError ? e.message : t.common.error),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setErr(null);
    convert.mutate();
  };

  return (
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/30 p-4">
      <form onSubmit={submit} className="card w-full max-w-md grid gap-3">
        <h2 className="font-semibold">Randevuya Dönüştür</h2>
        {err && <div className="rounded bg-red-50 px-3 py-2 text-sm text-red-700">{err}</div>}
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
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label">Tarih</label>
            <input type="date" className="input" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div>
            <label className="label">Saat</label>
            <input type="time" className="input" value={time} onChange={(e) => setTime(e.target.value)} />
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button className="btn-primary" disabled={convert.isPending || !staffPartyId}>
            {t.common.save}
          </button>
          <button type="button" className="btn-ghost" onClick={onClose}>
            {t.common.cancel}
          </button>
        </div>
      </form>
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

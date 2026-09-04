// Tek merkezî API istemcisi. Aynı origin (üretim) veya Vite proxy (geliştirme).
// Oturum çerezi + CSRF token (XSRF-TOKEN çerezi → X-XSRF-TOKEN başlığı).
// Faz 8 tam şube izolasyonu (ADR 0006): aktif şube seçiliyse X-Branch-Id eklenir.

import { getActiveBranchId } from "./branch";

export class ApiError extends Error {
  status: number;
  code: string;
  violations: { field: string; message: string }[];
  constructor(status: number, code: string, message: string, violations: ApiError["violations"] = []) {
    super(message);
    this.status = status;
    this.code = code;
    this.violations = violations;
  }
  get isAuth() {
    return this.status === 401;
  }
  get isLicenseBlocked() {
    return this.status === 423 || this.code === "license_restricted";
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp("(^|;\\s*)" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[2]) : null;
}

let csrfPrimed = false;
async function ensureCsrf(): Promise<void> {
  if (csrfPrimed || readCookie("XSRF-TOKEN")) {
    csrfPrimed = true;
    return;
  }
  await fetch("/api/v1/auth/csrf", { credentials: "include" });
  csrfPrimed = true;
}

type Options = Omit<RequestInit, "body"> & { body?: unknown };

export async function api<T = unknown>(path: string, opts: Options = {}): Promise<T> {
  const method = (opts.method ?? "GET").toUpperCase();
  const headers = new Headers(opts.headers);
  const mutating = method !== "GET" && method !== "HEAD";

  const activeBranch = getActiveBranchId();
  if (activeBranch != null) headers.set("X-Branch-Id", String(activeBranch));

  if (mutating) {
    await ensureCsrf();
    const token = readCookie("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  let body: BodyInit | undefined;
  if (opts.body instanceof FormData) {
    body = opts.body;
  } else if (opts.body !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(opts.body);
  }

  const res = await fetch(path.startsWith("/") ? path : `/api/v1/${path}`, {
    ...opts,
    method,
    headers,
    body,
    credentials: "include",
  });

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const data = text ? safeJson(text) : null;

  if (!res.ok) {
    const code = (data && data.code) || `http_${res.status}`;
    const message = (data && data.message) || `İstek başarısız (${res.status})`;
    throw new ApiError(res.status, code, message, (data && data.violations) || []);
  }
  return data as T;
}

function safeJson(text: string): any {
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

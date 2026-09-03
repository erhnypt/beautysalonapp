export type RoleName = "ADMIN" | "MUDUR" | "KASIYER" | "PERSONEL" | "RAPOR_OKUYUCU";

export interface CurrentUser {
  username: string;
  fullName: string;
  mustChangePassword: boolean;
  roles: RoleName[];
  permissions: string[];
}

export type LicenseStatusCode =
  | "ACTIVE"
  | "EXPIRING"
  | "GRACE"
  | "READ_ONLY"
  | "LOCKED"
  | "TAMPERED";

export interface LicenseSnapshot {
  status: LicenseStatusCode;
  devMode: boolean;
  enabledModules: string[];
  notAfter: string | null;
  daysRemaining: number | null;
  lastSuccessfulHeartbeatAt: string | null;
  consecutiveHeartbeatFailures: number;
  customerName: string | null;
  plan: string | null;
  message: string | null;
}

export interface UserView {
  id: number;
  username: string;
  fullName: string;
  enabled: boolean;
  mustChangePassword: boolean;
  roles: RoleName[];
  lastLoginAt: string | null;
}

export interface SettingView {
  key: string;
  value: string | null;
  description: string | null;
  secret: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AuditView {
  id: number;
  at: string;
  actor: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  summary: string | null;
}

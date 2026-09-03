import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { LicenseSnapshot } from "./types";

export function useLicense() {
  return useQuery({
    queryKey: ["license", "status"],
    queryFn: () => api<LicenseSnapshot>("/api/v1/license/status"),
    refetchInterval: 5 * 60 * 1000,
    staleTime: 60 * 1000,
  });
}

export function licenseTone(status: LicenseSnapshot["status"]): "ok" | "warn" | "danger" {
  switch (status) {
    case "ACTIVE":
      return "ok";
    case "EXPIRING":
    case "GRACE":
      return "warn";
    default:
      return "danger";
  }
}

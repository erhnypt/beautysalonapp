import { defineConfig, devices } from "@playwright/test";

/**
 * E2E (plan §18): ana akış — giriş → (zorunlu) parola değişimi → Günlük Analiz →
 * ana modül ekranlarının yüklenmesi. Chromium.
 *
 * İki sunucu ayağa kaldırılır:
 *   1. Spring Boot backend (:8734) — izole `server/target/e2e-data` veri diziniyle,
 *      her koşuda temiz (bootstrap admin: admin/admin123).
 *   2. Vite dev sunucusu (:5173) — /api çağrılarını backend'e proxy'ler.
 *
 * Yerelde: `npm run e2e`  ·  CI: `.github/workflows/ci.yml` → `e2e` job.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",

  use: {
    baseURL: "http://127.0.0.1:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },

  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],

  webServer: [
    {
      command:
        "cd ../server && rm -rf ./target/e2e-data && " +
        "JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17} ./mvnw -q spring-boot:run " +
        '-Dspring-boot.run.arguments="--beautysalonapp.data-dir=./target/e2e-data --server.address=127.0.0.1"',
      url: "http://127.0.0.1:8734/actuator/health",
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
      stdout: "pipe",
      stderr: "pipe",
    },
    {
      command: "npm run dev -- --host 127.0.0.1",
      url: "http://127.0.0.1:5173",
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});

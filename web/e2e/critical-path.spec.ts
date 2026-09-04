import { test, expect, type Page, type ConsoleMessage } from "@playwright/test";

/**
 * Ana akış E2E (plan §18: "randevu → satış → tahsilat → rapor").
 *
 *  1. Giriş + zorunlu parola değişimi
 *  2. Panel + 6 ana modül ekranı yüklenir + cari (müşteri) oluşturma (form → API → liste)
 *  3. Derin akış: hizmet + personel (API) → randevu (UI) → "Geldi + Tahsil" (UI) →
 *     Günlük Analiz'e yansıma (dashboard API + UI)
 *
 * Backend'i playwright.config.ts izole `server/target/e2e-data` diziniyle ayağa kaldırır
 * (her koşuda temiz; bootstrap admin admin/admin123).
 */

const BOOTSTRAP_USER = "admin";
const BOOTSTRAP_PASS = "admin123";
const NEW_PASS = "E2eParola1";

function isExpectedNoise(text: string): boolean {
  return /Failed to load resource.*\b(401|403)\b/.test(text) || /\/api\/v1\/auth\/(me|csrf)\b/.test(text);
}

/** Tarayıcı bağlamının oturum çerezi + CSRF token'ı ile kimlikli POST. */
async function apiPost(page: Page, path: string, body: unknown): Promise<any> {
  await page.request.get("/api/v1/auth/csrf");
  const xsrf = (await page.context().cookies()).find((c) => c.name === "XSRF-TOKEN")?.value ?? "";
  const res = await page.request.post(path, {
    headers: { "X-XSRF-TOKEN": xsrf, "Content-Type": "application/json" },
    data: body as any,
  });
  expect(res.ok(), `POST ${path} → ${res.status()} ${await res.text()}`).toBeTruthy();
  return res.status() === 204 ? null : res.json();
}

test.describe.serial("kritik akış", () => {
  let page: Page;
  const errors: string[] = [];

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    page.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`));
    page.on("console", (m: ConsoleMessage) => {
      if (m.type() === "error" && !isExpectedNoise(m.text())) errors.push(`console: ${m.text()}`);
    });

    // giriş — bootstrap tohumlaması ApplicationRunner'da olduğundan retry'lı
    await expect(async () => {
      await page.goto("/login");
      await page.locator("#u").fill(BOOTSTRAP_USER);
      await page.locator("#p").fill(BOOTSTRAP_PASS);
      await page.getByRole("button", { name: "Giriş" }).click();
      await expect(
        page.getByText("Devam etmek için parolanızı değiştirmelisiniz.")
      ).toBeVisible({ timeout: 3_000 });
    }).toPass({ timeout: 60_000, intervals: [1_000, 2_000, 3_000, 5_000] });

    // zorunlu parola değişimi
    const pw = page.locator('input[type="password"]');
    await pw.nth(0).fill(BOOTSTRAP_PASS);
    await pw.nth(1).fill(NEW_PASS);
    await page.getByRole("button", { name: "Parolayı Değiştir" }).click();
    await expect(page.getByRole("heading", { name: /^Bugün/ })).toBeVisible();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test("panel + ana modül ekranları + cari oluşturma", async () => {
    const modules: Array<{ link: string; url: RegExp; heading: string }> = [
      { link: "Randevular", url: /\/randevular$/, heading: "Randevular" },
      { link: "Cari Hesaplar", url: /\/cari$/, heading: "Cari Hesaplar" },
      { link: "Stok", url: /\/stok$/, heading: "Stok" },
      { link: "Faturalar", url: /\/faturalar$/, heading: "Faturalar" },
      { link: "Personel", url: /\/personel$/, heading: "Personel" },
      { link: "Raporlar", url: /\/raporlar$/, heading: "Raporlar" },
    ];
    for (const m of modules) {
      await page.getByRole("link", { name: m.link, exact: true }).click();
      await expect(page).toHaveURL(m.url);
      await expect(page.getByRole("heading", { name: m.heading }).first()).toBeVisible();
    }

    // cari (müşteri) oluşturma: form → POST /api/v1/parties → liste yenilenir
    await page.getByRole("link", { name: "Cari Hesaplar", exact: true }).click();
    const musteriAdi = `E2E Müşteri ${Date.now()}`;
    await page.getByRole("button", { name: "Ekle" }).click();
    const form = page.locator('form:has-text("Ünvan / Ad Soyad")');
    await form.locator("input.input").nth(0).fill(musteriAdi);
    await form.locator("input.input").nth(1).fill("5551234567");
    await form.getByRole("button", { name: "Kaydet" }).click();
    await expect(page.getByRole("cell", { name: musteriAdi })).toBeVisible();
  });

  test("randevu → GELDI + Tahsil → Günlük Analiz'e yansır", async () => {
    const stamp = Date.now();
    const SERVICE_PRICE = 750;

    // --- ön koşullar (API): hizmet + personel + müşteri ---
    const svc = await apiPost(page, "/api/v1/appointments/services", {
      code: `E2E-HZM-${stamp}`,
      name: `E2E Hizmet ${stamp}`,
      durationMin: 30,
      price: SERVICE_PRICE,
      bufferBeforeMin: 0,
      bufferAfterMin: 0,
      resourceRequired: false,
    });
    const staff = await apiPost(page, "/api/v1/staff", {
      partyId: null,
      title: `E2E Uzman ${stamp}`,
      defaultServiceRate: 10,
    });
    const musteri = await apiPost(page, "/api/v1/parties", {
      type: "MUSTERI",
      title: `E2E Randevu Müşteri ${stamp}`,
      phone: "5559876543",
      email: "",
    });
    const staffTitle = `E2E Uzman ${stamp}`;
    const musteriTitle = `E2E Randevu Müşteri ${stamp}`;
    expect(svc.name).toBe(`E2E Hizmet ${stamp}`);
    expect(staff.partyId).toBeGreaterThan(0);

    // --- randevu oluştur (UI) ---
    await page.getByRole("link", { name: "Randevular", exact: true }).click();
    await expect(page).toHaveURL(/\/randevular$/);
    await page.getByRole("button", { name: "Ekle" }).click();
    const f = page.locator("form.card");
    await f.locator("select").nth(0).selectOption({ label: musteriTitle }); // Müşteri
    await f.locator("select").nth(1).selectOption({ label: staffTitle }); // Personel
    await f.locator("select").nth(2).selectOption({ label: `${svc.name} (30 dk)` }); // Hizmet
    await f.locator('input[type="time"]').fill("11:30");
    await f.getByRole("button", { name: "Kaydet" }).click();

    // randevu satırı listede
    const row = page.locator(".card", { hasText: svc.name });
    await expect(row).toBeVisible();
    await expect(row).toContainText("PLANLANDI");

    // --- Geldi + Tahsil (UI) ---
    await row.getByRole("button", { name: "Geldi + Tahsil" }).click();
    await expect(page.locator(".card", { hasText: svc.name })).toContainText("GELDI");

    // --- dashboard API: cross-modül yansıma ---
    const dash = await (await page.request.get("/api/v1/dashboard/today")).json();
    expect(dash.appointmentsByStatus?.GELDI ?? 0).toBeGreaterThanOrEqual(1);
    expect(Number(dash.appointmentRevenue)).toBeGreaterThanOrEqual(SERVICE_PRICE);
    expect(Number(dash.collections)).toBeGreaterThanOrEqual(1);

    // --- dashboard UI: "1 geldi" ---
    await page.getByRole("link", { name: "Günlük Analiz", exact: true }).click();
    await expect(page.getByText(/\bgeldi\b/).first()).toBeVisible();
    await expect(page.getByText("Randevu", { exact: true })).toBeVisible();

    // --- çıkış + istemci hatası olmamalı ---
    await page.getByRole("button", { name: "Çıkış" }).click();
    await expect(page).toHaveURL(/\/login$/);
    expect(errors, `İstemci hataları:\n${errors.join("\n")}`).toEqual([]);
  });
});

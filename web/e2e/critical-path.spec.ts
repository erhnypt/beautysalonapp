import { test, expect, type ConsoleMessage } from "@playwright/test";

/**
 * Ana akış dumanı (plan §18: "randevu → satış → tahsilat → rapor" akışının iskeleti).
 * Bu ilk E2E kimlik doğrulama + tüm ana modül ekranlarının hatasız yüklenmesini kapsar.
 * Derin veri-giriş akışları (randevu oluştur → GELDI → tahsilat) sonraki adımda eklenecek.
 */

const BOOTSTRAP_USER = "admin";
const BOOTSTRAP_PASS = "admin123";
const NEW_PASS = "E2eParola1";

/** Beklenen ağ gürültüsü (giriş öncesi 401/403) test hatası sayılmaz. */
function isExpectedNoise(text: string): boolean {
  return /Failed to load resource.*\b(401|403)\b/.test(text) || /\/api\/v1\/auth\/(me|csrf)\b/.test(text);
}

test("giriş → zorunlu parola değişimi → panel → ana modüller yüklenir", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`));
  page.on("console", (m: ConsoleMessage) => {
    if (m.type() === "error" && !isExpectedNoise(m.text())) errors.push(`console: ${m.text()}`);
  });

  // --- giriş (açılışta bootstrap tohumlaması ApplicationRunner'da; kısa yarış için retry) ---
  await expect(async () => {
    await page.goto("/login");
    await page.locator("#u").fill(BOOTSTRAP_USER);
    await page.locator("#p").fill(BOOTSTRAP_PASS);
    await page.getByRole("button", { name: "Giriş" }).click();
    await expect(
      page.getByText("Devam etmek için parolanızı değiştirmelisiniz.")
    ).toBeVisible({ timeout: 3_000 });
  }).toPass({ timeout: 60_000, intervals: [1_000, 2_000, 3_000, 5_000] });

  // --- bootstrap admin: parola değişimi zorunlu ---
  const pw = page.locator('input[type="password"]');
  await pw.nth(0).fill(BOOTSTRAP_PASS);
  await pw.nth(1).fill(NEW_PASS);
  await page.getByRole("button", { name: "Parolayı Değiştir" }).click();

  // --- Günlük Analiz paneli ---
  await expect(page).toHaveURL("http://127.0.0.1:5173/");
  await expect(page.getByRole("heading", { name: /^Bugün/ })).toBeVisible();

  // --- ana modül ekranları ---
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

  // --- cari (müşteri) oluşturma: form → POST /api/v1/parties → liste yenilenir ---
  await page.getByRole("link", { name: "Cari Hesaplar", exact: true }).click();
  const musteriAdi = `E2E Müşteri ${Date.now()}`;
  await page.getByRole("button", { name: "Ekle" }).click();
  const form = page.locator('form:has-text("Ünvan / Ad Soyad")');
  await form.locator("input.input").nth(0).fill(musteriAdi);
  await form.locator("input.input").nth(1).fill("5551234567");
  await form.getByRole("button", { name: "Kaydet" }).click();
  await expect(page.getByRole("cell", { name: musteriAdi })).toBeVisible();

  // --- çıkış ---
  await page.getByRole("button", { name: "Çıkış" }).click();
  await expect(page).toHaveURL(/\/login$/);

  expect(errors, `İstemci hataları:\n${errors.join("\n")}`).toEqual([]);
});

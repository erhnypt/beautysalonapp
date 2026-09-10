import { test, expect, type Page, type ConsoleMessage } from "@playwright/test";

/**
 * Satış → tahsilat → rapor E2E (plan §18: "randevu → satış → tahsilat → rapor").
 *
 * `critical-path.spec.ts` zincirin "randevu → tahsilat → panel" ayağını kapsıyor.
 * Bu dosya eksik kalan "satış (fatura) → kasa tahsilatı → Raporlar ekranı" ayağını
 * uçtan uca kapsar:
 *
 *  1. SATIS faturası (API): hizmet satırı + kısmi nakit ödeme → CONFIRMED, ters
 *     kayıtsız gerçek tahsilat kasaya işlenir.
 *  2. Faturalar ekranı (UI): fatura numarası + CONFIRMED rozeti listede görünür.
 *  3. Kasa & Finans "Tahsilat" formu (UI): kalan bakiye bir gelir kartına tahsil
 *     edilir, dekont üretilir.
 *  4. Raporlar ekranı (UI + API): Cari → Yaşlandırma kalan borcu, Gelir-Gider
 *     tablosu tahsilatı yansıtır.
 *
 * Backend'i playwright.config.ts izole `server/target/e2e-data` diziniyle ayağa
 * kaldırır (bootstrap admin admin/admin123). `critical-path.spec.ts` bu koşuda
 * daha önce çalışıp parolayı değiştirmiş olabileceğinden giriş yardımcısı her iki
 * durumu da karşılar.
 */

const BOOTSTRAP_USER = "admin";
const BOOTSTRAP_PASS = "admin123";
const NEW_PASS = "E2eParola1";

const SERVICE_PRICE = 1500;
const CASH_ON_INVOICE = 500;
const REMAINING = SERVICE_PRICE - CASH_ON_INVOICE; // 1000 — kalan cari borç

function isExpectedNoise(text: string): boolean {
  return /Failed to load resource.*\b(401|403)\b/.test(text) || /\/api\/v1\/auth\/(me|csrf)\b/.test(text);
}

const todayStr = () => new Date().toISOString().slice(0, 10);
const monthAgoStr = () => new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);

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

async function apiGet(page: Page, path: string): Promise<any> {
  const res = await page.request.get(path);
  expect(res.ok(), `GET ${path} → ${res.status()} ${await res.text()}`).toBeTruthy();
  return res.json();
}

/** critical-path.spec.ts parolayı değiştirmiş olabilir; hem taze hem değiştirilmiş backend'i karşıla. */
async function ensureLoggedIn(page: Page) {
  await expect(async () => {
    await page.goto("/login");
    if (await page.getByRole("heading", { name: /^Bugün/ }).isVisible().catch(() => false)) return;

    const attempt = async (pass: string) => {
      await page.goto("/login");
      await page.locator("#u").fill(BOOTSTRAP_USER);
      await page.locator("#p").fill(pass);
      await page.getByRole("button", { name: "Giriş" }).click();
      await Promise.race([
        page.getByRole("heading", { name: /^Bugün/ }).waitFor({ state: "visible", timeout: 4_000 }),
        page
          .getByText("Devam etmek için parolanızı değiştirmelisiniz.")
          .waitFor({ state: "visible", timeout: 4_000 }),
      ]).catch(() => {});
    };

    // önce değiştirilmiş parola (aynı koşuda critical-path daha önce çalıştıysa)
    await attempt(NEW_PASS);
    if (await page.getByRole("heading", { name: /^Bugün/ }).isVisible().catch(() => false)) return;

    // taze backend: bootstrap parolası hâlâ geçerli → zorunlu değişim
    await attempt(BOOTSTRAP_PASS);
    if (
      await page
        .getByText("Devam etmek için parolanızı değiştirmelisiniz.")
        .isVisible()
        .catch(() => false)
    ) {
      const pw = page.locator('input[type="password"]');
      await pw.nth(0).fill(BOOTSTRAP_PASS);
      await pw.nth(1).fill(NEW_PASS);
      await page.getByRole("button", { name: "Parolayı Değiştir" }).click();
    }
    await expect(page.getByRole("heading", { name: /^Bugün/ })).toBeVisible({ timeout: 8_000 });
  }).toPass({ timeout: 90_000, intervals: [1_000, 2_000, 3_000, 5_000] });
}

test.describe.serial("satış → tahsilat → rapor", () => {
  let page: Page;
  const errors: string[] = [];
  let docNo = "";
  let musteriTitle = "";

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    page.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`));
    page.on("console", (m: ConsoleMessage) => {
      if (m.type() === "error" && !isExpectedNoise(m.text())) errors.push(`console: ${m.text()}`);
    });
    await ensureLoggedIn(page);
  });

  test.afterAll(async () => {
    await page.close();
  });

  test("SATIS faturası oluşturulur ve Faturalar ekranında görünür", async () => {
    const stamp = Date.now();
    musteriTitle = `E2E Fatura Müşteri ${stamp}`;

    const musteri = await apiPost(page, "/api/v1/parties", {
      type: "MUSTERI",
      title: musteriTitle,
      phone: "5550001122",
      email: "",
    });
    expect(musteri.id).toBeGreaterThan(0);

    // --- satış: hizmet satırı + kısmi nakit ödeme ---
    const detail = await apiPost(page, "/api/v1/invoices", {
      type: "SATIS",
      partyId: musteri.id,
      lines: [
        {
          itemId: null,
          service: true,
          description: "E2E hizmet bedeli",
          quantity: 1,
          unitPrice: SERVICE_PRICE,
          discountRate: 0,
          vatRate: 0,
        },
      ],
      payments: [{ method: "CASH", amount: CASH_ON_INVOICE }],
    });
    docNo = detail.invoice.docNo;
    expect(detail.invoice.status).toBe("CONFIRMED");
    expect(Number(detail.invoice.grandTotal)).toBe(SERVICE_PRICE);
    expect(detail.payments).toHaveLength(1);
    expect(detail.payments[0].method).toBe("CASH");
    expect(Number(detail.payments[0].amount)).toBe(CASH_ON_INVOICE);

    // --- Faturalar ekranı (UI): fatura no + CONFIRMED rozeti ---
    await page.getByRole("link", { name: "Faturalar", exact: true }).click();
    await expect(page).toHaveURL(/\/faturalar$/);
    await expect(page.getByText(docNo).first()).toBeVisible();
    await expect(page.locator("tr", { hasText: docNo }).first()).toContainText("CONFIRMED");
  });

  test("kalan bakiye Kasa & Finans Tahsilat formundan gelir kartına tahsil edilir", async () => {
    // faturadaki nakit ödeme kasaya COLLECTION olarak işlenmiş olmalı (docNo ile bağlı)
    const accounts = await apiGet(page, "/api/v1/finance/accounts");
    const kasa =
      accounts.find((a: any) => a.isDefault && a.kind === "KASA") ??
      accounts.find((a: any) => a.kind === "KASA");
    expect(kasa, "varsayılan kasa hesabı").toBeTruthy();

    const from = monthAgoStr();
    const to = todayStr();
    const txns = await apiGet(
      page,
      `/api/v1/finance/transactions?accountId=${kasa.id}&from=${from}&to=${to}`,
    );
    // TxnView.docNo dekontun kendi numarası; faturaya bağ description'da ("Fatura tahsilatı <no>")
    expect(
      txns.some(
        (tx: any) =>
          tx.type === "COLLECTION" &&
          Number(tx.amount) === CASH_ON_INVOICE &&
          String(tx.description ?? "").includes(docNo),
      ),
      `fatura nakit tahsilatı kasada bulunamadı (docNo=${docNo})`,
    ).toBeTruthy();

    // --- kalan tutar "Tahsilat" formundan (UI) Hizmet Geliri kartına ---
    await page.getByRole("link", { name: "Kasa & Finans", exact: true }).click();
    await expect(page).toHaveURL(/\/finans$/);
    await page.getByRole("button", { name: "Tahsilat" }).click();

    const f = page.locator("form.card");
    await expect(f).toContainText("Tahsilat (kasaya giriş)");
    await f.locator("select").nth(0).selectOption({ label: "Ana Kasa (TL)" }); // Hesap
    await f.locator("input.input").first().fill(String(REMAINING)); // Tutar
    await f.locator("select").nth(1).selectOption({ label: "600.01 — Hizmet Geliri" }); // Gelir kartı
    await f.locator("input.input").last().fill(`E2E kalan tahsilat ${docNo}`); // Açıklama
    await f.getByRole("button", { name: "Kaydet" }).click();

    await expect(page.getByText("İşlem kaydedildi.")).toBeVisible();
    await expect(page.getByRole("link", { name: "Dekont (PDF)" })).toBeVisible();
  });

  test("Raporlar ekranı satışı yansıtır (yaşlandırma + gelir-gider)", async () => {
    // API: yaşlandırma — kalan cari borç 0-30 gün kovasında
    const aging = await apiGet(page, "/api/v1/parties/reports/aging");
    const arow = aging.find((r: any) => r.title === musteriTitle);
    expect(arow, "yaşlandırma satırı bulunamadı").toBeTruthy();
    expect(Number(arow.total)).toBeCloseTo(REMAINING, 2);
    expect(Number(arow.days0to30)).toBeCloseTo(REMAINING, 2);

    // API: gelir-gider — Hizmet Geliri kartında en az kalan tutar kadar tahsilat
    const from = monthAgoStr();
    const to = todayStr();
    const ie = await apiGet(
      page,
      `/api/v1/finance/reports/income-expense?from=${from}&to=${to}`,
    );
    const hg = ie.find((r: any) => r.code === "600.01");
    expect(hg, "Gelir-Gider tablosunda Hizmet Geliri satırı yok").toBeTruthy();
    expect(Number(hg.amount)).toBeGreaterThanOrEqual(REMAINING);

    // UI: Raporlar → Cari → Yaşlandırma tablosunda müşteri görünür
    await page.getByRole("link", { name: "Raporlar", exact: true }).click();
    await expect(page).toHaveURL(/\/raporlar$/);
    await expect(page.getByRole("heading", { name: "Raporlar" }).first()).toBeVisible();
    await page.getByRole("button", { name: "Cari", exact: true }).click();
    await expect(page.getByRole("cell", { name: musteriTitle }).first()).toBeVisible();

    // UI: Gelir-Gider sekmesi yüklenir ve 600.01 satırını gösterir
    await page.getByRole("button", { name: "Gelir-Gider", exact: true }).click();
    await expect(page.getByText("600.01").first()).toBeVisible();

    // --- çıkış + istemci hatası olmamalı ---
    await page.getByRole("button", { name: "Çıkış" }).click();
    await expect(page).toHaveURL(/\/login$/);
    expect(errors, `İstemci hataları:\n${errors.join("\n")}`).toEqual([]);
  });
});

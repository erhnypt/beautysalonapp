package com.beautysalonapp.perf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Faz 7 — performans sertleştirmesi (plan §17, §18).
 *
 * <p>10 yıllık sentetik veri üretir: varsayılan ~500.000 stok hareketi, ~120.000 randevu,
 * ~40.000 fatura, ~100.000 kasa hareketi. Amaç: liste/rapor ekranlarının hedef
 * gecikme bütçesinde (&lt; 300 ms) kaldığını gerçek hacimle doğrulamak.
 *
 * <p><b>Yalnızca</b> {@code beautysalonapp.perf.seed=true} verildiğinde çalışır. Üretim
 * derlemesinde asla etkin değildir. İş verisi üretmez — sadece test/benchmark verisidir;
 * ayrı bir veri dizinine yönlendirilmiş kurulumda çalıştırılmalıdır.
 *
 * <pre>
 *   ./mvnw spring-boot:run \
 *     -Dspring-boot.run.arguments="--beautysalonapp.perf.seed=true --beautysalonapp.data-dir=./perf-data"
 * </pre>
 *
 * Ölçekleme: {@code --beautysalonapp.perf.movements=1000000} gibi anahtarlarla büyütülebilir.
 */
@Component
@Order(100)
@ConditionalOnProperty(prefix = "beautysalonapp.perf", name = "seed", havingValue = "true")
public class PerfDataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfDataGenerator.class);
    private static final int CHUNK = 5_000;

    private final JdbcTemplate jdbc;

    private final int years;
    private final int customers;
    private final int items;
    private final int staffCount;
    private final int services;
    private final int movements;
    private final int appointments;
    private final int invoices;
    private final int cashTxns;
    private final int partyTxns;
    private final int contracts;
    private final int cheques;

    public PerfDataGenerator(DataSource dataSource,
                             @Value("${beautysalonapp.perf.years:10}") int years,
                             @Value("${beautysalonapp.perf.customers:5000}") int customers,
                             @Value("${beautysalonapp.perf.items:250}") int items,
                             @Value("${beautysalonapp.perf.staff:15}") int staffCount,
                             @Value("${beautysalonapp.perf.services:30}") int services,
                             @Value("${beautysalonapp.perf.movements:500000}") int movements,
                             @Value("${beautysalonapp.perf.appointments:120000}") int appointments,
                             @Value("${beautysalonapp.perf.invoices:40000}") int invoices,
                             @Value("${beautysalonapp.perf.cashTxns:100000}") int cashTxns,
                             @Value("${beautysalonapp.perf.partyTxns:150000}") int partyTxns,
                             @Value("${beautysalonapp.perf.contracts:4000}") int contracts,
                             @Value("${beautysalonapp.perf.cheques:2000}") int cheques) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.years = years;
        this.customers = customers;
        this.items = items;
        this.staffCount = staffCount;
        this.services = services;
        this.movements = movements;
        this.appointments = appointments;
        this.invoices = invoices;
        this.cashTxns = cashTxns;
        this.partyTxns = partyTxns;
        this.contracts = contracts;
        this.cheques = cheques;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("select count(*) from stock_movement", Integer.class);
        if (existing != null && existing > 0) {
            log.warn("PerfDataGenerator: stock_movement zaten dolu ({} satır) — tohumlama atlandı", existing);
            return;
        }

        long t0 = System.nanoTime();
        log.warn("PerfDataGenerator BAŞLADI — {} yıl, {} müşteri, {} stok hareketi hedefleniyor",
                years, customers, movements);

        LocalDate today = LocalDate.now();
        int spanDays = Math.max(1, years * 365);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        // --- referans veriler ------------------------------------------------
        // Birim/depo/kasa/kart açılışta StockDefaults + FinanceDefaults tarafından
        // tohumlanır; burada var olanlar okunur, eksik kasalar tamamlanır.
        long adetUnit = jdbc.queryForObject("select id from unit where code = 'ADET'", Long.class);
        List<Long> whIds = jdbc.queryForList("select id from warehouse order by id", Long.class);
        long whMain = whIds.get(0);
        long[] finIds = {
                jdbc.queryForObject("select id from fin_account where code = 'KASA'", Long.class),
                ensureFinAccount("POS", "POS Hesabı", "POS", now),
                ensureFinAccount("BANKA", "Banka Hesabı", "BANKA", now)
        };
        long expenseCard = jdbc.queryForObject(
                "select id from income_expense_card where code = '700.01'", Long.class);

        insert("""
                insert into item (created_at,updated_at,code,name,item_type,vat_rate,base_unit_id,reorder_level,active)
                values (?,?,?,?,?,?,?,?,true)
                """,
                items, i -> new Object[]{now, now, "URN" + pad(i, 5), "Ürün " + i,
                        "EMTIA", new BigDecimal("20.00"), adetUnit,
                        (i % 4 == 0) ? new BigDecimal("10.000000") : null});
        long itemBase = minId("item");

        insert("""
                insert into item_unit (created_at,updated_at,item_id,unit_id,factor,is_base)
                values (?,?,?,?,1,true)
                """,
                items, i -> new Object[]{now, now, itemBase + i, adetUnit});

        insert("""
                insert into stock_level (created_at,updated_at,item_id,warehouse_id,qty_base,avg_cost)
                values (?,?,?,?,?,?)
                """,
                items, i -> new Object[]{now, now, itemBase + i, whMain,
                        new BigDecimal(rnd(0, 500) + ".000000"), money(rnd(20, 400))});

        insert("""
                insert into service_definition (created_at,updated_at,code,name,duration_min,price,vat_rate)
                values (?,?,?,?,?,?,20.00)
                """,
                services, i -> new Object[]{now, now, "HZM" + pad(i, 4), "Hizmet " + i,
                        30 + (i % 4) * 15, money(rnd(150, 2500))});
        long serviceBase = minId("service_definition");

        // --- personel (party + staff) --------------------------------------
        insert("""
                insert into party (created_at,updated_at,party_type,code,title,first_name,last_name,
                                   sms_consent,email_consent,iys_status)
                values (?,?,?,?,?,?,?,false,false,'BILINMIYOR')
                """,
                staffCount, i -> new Object[]{now, now, "PERSONEL", "PRS" + pad(i, 4),
                        "Personel " + i, "Personel", "#" + i});
        long staffPartyBase = jdbc.queryForObject(
                "select min(id) from party where party_type = 'PERSONEL'", Long.class);

        insert("""
                insert into staff (created_at,updated_at,party_id,title,hire_date,active)
                values (?,?,?,?,?,true)
                """,
                staffCount, i -> new Object[]{now, now, staffPartyBase + i, "Uzman " + i,
                        java.sql.Date.valueOf(today.minusDays(rnd(200, 3000)))});

        // --- müşteriler (party + party_account) ---------------------------
        insert("""
                insert into party (created_at,updated_at,party_type,code,title,first_name,last_name,
                                   phone_bi,gender,birth_date,sms_consent,email_consent,iys_status)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                customers, i -> {
                    LocalDateTime created = LocalDateTime.now().minusDays(rnd(0, spanDays));
                    return new Object[]{Timestamp.valueOf(created), Timestamp.valueOf(created), "MUSTERI",
                            "MUS" + pad(i, 7), "Müşteri " + i, "Ad" + i, "Soyad" + i,
                            "bi" + (1_000_000_000L + i), (i % 2 == 0) ? "KADIN" : "ERKEK",
                            java.sql.Date.valueOf(today.minusDays(rnd(6570, 25550))),
                            i % 3 != 0, i % 5 == 0, (i % 3 != 0) ? "ONAY" : "BILINMIYOR"};
                });
        long custBase = jdbc.queryForObject(
                "select min(id) from party where party_type = 'MUSTERI'", Long.class);

        insert("""
                insert into party_account (created_at,updated_at,party_id,account_kind,currency,opening_balance)
                values (?,?,?,'NORMAL','TRY',0)
                """,
                customers, i -> new Object[]{now, now, custBase + i});
        long acctBase = minId("party_account");

        // --- sözleşme + taksit ------------------------------------------------
        insert("""
                insert into sales_contract (created_at,updated_at,doc_no,party_id,party_account_id,
                                            contract_date,total_amount,installment_count,first_due_date,status)
                values (?,?,?,?,?,?,?,?,?,'ACTIVE')
                """,
                contracts, i -> {
                    int c = i % Math.max(1, customers);
                    LocalDate cd = today.minusDays(rnd(30, spanDays));
                    return new Object[]{now, now, "PERF-SZL-" + pad(i, 7), custBase + c, acctBase + c,
                            java.sql.Date.valueOf(cd), money(rnd(3000, 60000)), 12,
                            java.sql.Date.valueOf(cd.plusMonths(1))};
                });
        long contractBase = minId("sales_contract");

        int instPerContract = 12;
        insertTotal("""
                insert into installment (created_at,updated_at,contract_id,seq,due_date,amount,paid_amount,status)
                values (?,?,?,?,?,?,?,?)
                """,
                contracts * instPerContract, n -> {
                    int c = n / instPerContract;
                    int seq = n % instPerContract + 1;
                    LocalDate due = today.minusDays(rnd(0, spanDays)).plusMonths(seq);
                    boolean past = due.isBefore(today);
                    BigDecimal amount = money(rnd(300, 5000));
                    BigDecimal paid;
                    String status;
                    if (!past) {
                        paid = BigDecimal.ZERO;
                        status = "BEKLIYOR";
                    } else if (n % 7 == 0) {
                        paid = amount.movePointLeft(1); // kısmi ödeme
                        status = "GECIKMIS";
                    } else {
                        paid = amount;
                        status = "ODENDI";
                    }
                    return new Object[]{now, now, contractBase + c, seq, java.sql.Date.valueOf(due),
                            amount, paid, status};
                });

        // --- faturalar + satırlar ------------------------------------------
        insert("""
                insert into invoice (created_at,updated_at,invoice_type,doc_no,invoice_date,party_id,
                                     party_account_id,currency,subtotal,discount_total,vat_total,grand_total,status)
                values (?,?,?,?,?,?,?,'TRY',?,0,?,?,'CONFIRMED')
                """,
                invoices, i -> {
                    int c = i % Math.max(1, customers);
                    LocalDate d = today.minusDays(rnd(0, spanDays));
                    long net = rnd(200, 8000);
                    BigDecimal netB = money(net);
                    BigDecimal vat = money(Math.round(net * 0.20));
                    return new Object[]{now, now, (i % 4 == 0) ? "PERAKENDE" : "SATIS",
                            "PERF-" + pad(i, 8), java.sql.Date.valueOf(d), custBase + c, acctBase + c,
                            netB, vat, netB.add(vat)};
                });
        long invoiceBase = minId("invoice");

        int linesPer = 2;
        insertTotal("""
                insert into invoice_line (created_at,updated_at,invoice_id,line_no,is_service,description,
                                          quantity,unit_price,discount_rate,vat_rate,line_net,line_vat,line_total)
                values (?,?,?,?,?,?,?,?,0,20.00,?,?,?)
                """,
                invoices * linesPer, n -> {
                    int inv = n / linesPer;
                    int lineNo = n % linesPer + 1;
                    long price = rnd(100, 4000);
                    BigDecimal net = money(price);
                    BigDecimal vat = money(Math.round(price * 0.20));
                    return new Object[]{now, now, invoiceBase + inv, lineNo, lineNo == 1,
                            "Kalem " + lineNo, new BigDecimal("1.000000"), money(price),
                            net, vat, net.add(vat)};
                });

        // --- randevular -----------------------------------------------------
        String[] apptStatus = {"GELDI", "GELDI", "GELDI", "GELDI", "PLANLANDI", "IPTAL", "GELMEDI"};
        insertTotal("""
                insert into appointment (created_at,updated_at,party_id,staff_party_id,service_id,start_at,
                                         end_at,status,source,price_snapshot,arrived_at,no_show)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                appointments, n -> {
                    int c = n % Math.max(1, customers);
                    int s = n % Math.max(1, staffCount);
                    int svc = n % Math.max(1, services);
                    LocalDateTime start = LocalDateTime.now().minusDays(rnd(0, spanDays))
                            .withHour(9 + rnd(0, 9)).withMinute((rnd(0, 4)) * 15).withSecond(0).withNano(0);
                    String st = apptStatus[n % apptStatus.length];
                    boolean came = st.equals("GELDI");
                    return new Object[]{now, now, custBase + c, staffPartyBase + s, serviceBase + svc,
                            Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)), st, "YERINDE",
                            money(rnd(150, 2500)), came ? Timestamp.valueOf(start.plusMinutes(2)) : null,
                            st.equals("GELMEDI")};
                });

        // --- kasa hareketleri ---------------------------------------------
        insertTotal("""
                insert into cash_transaction (created_at,updated_at,txn_type,txn_date,account_id,amount,
                                              currency,voided,income_expense_card_id)
                values (?,?,?,?,?,?,'TRY',false,?)
                """,
                cashTxns, n -> {
                    boolean collection = n % 3 != 0;
                    LocalDate d = today.minusDays(rnd(0, spanDays));
                    return new Object[]{now, now, collection ? "COLLECTION" : "PAYMENT",
                            java.sql.Date.valueOf(d), finIds[n % 3], money(rnd(50, 6000)),
                            collection ? null : expenseCard};
                });

        // --- cari hareketleri -------------------------------------------------
        insertTotal("""
                insert into party_transaction (created_at,updated_at,account_id,txn_date,doc_type,doc_ref,
                                               line_key,description,debit,credit,currency)
                values (?,?,?,?,?,?,?,?,?,?,'TRY')
                """,
                partyTxns, n -> {
                    int c = n % Math.max(1, customers);
                    boolean debit = n % 2 == 0;
                    BigDecimal amt = money(rnd(100, 5000));
                    LocalDate d = today.minusDays(rnd(0, spanDays));
                    return new Object[]{now, now, acctBase + c, java.sql.Date.valueOf(d),
                            debit ? "FATURA" : "TAHSILAT", "PERF", "PT" + n, "Hareket " + n,
                            debit ? amt : BigDecimal.ZERO, debit ? BigDecimal.ZERO : amt};
                });

        // --- çekler --------------------------------------------------------
        insert("""
                insert into cheque (created_at,updated_at,cheque_no,cheque_type,due_date,amount,status,bank_name)
                values (?,?,?,?,?,?,?,?)
                """,
                cheques, i -> {
                    LocalDate due = today.plusDays(rnd(-1500, 400));
                    return new Object[]{now, now, "PERF-CK-" + pad(i, 7), (i % 2 == 0) ? "ALINAN" : "VERILEN",
                            java.sql.Date.valueOf(due), money(rnd(1000, 40000)),
                            (i % 3 == 0) ? "TAHSIL_EDILDI" : "PORTFOYDE", "Banka " + (i % 10)};
                });

        // --- stok hareketleri (başlık: ~500.000) ------------------------
        String[] mvDoc = {"ALIS", "SATIS", "SAYIM", "SARF", "TRANSFER"};
        insertTotal("""
                insert into stock_movement (created_at,updated_at,mv_date,item_id,warehouse_id,direction,
                                            base_qty,entered_unit_id,entered_qty,unit_cost,doc_type,doc_ref,line_key)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                movements, n -> {
                    int it = n % Math.max(1, items);
                    LocalDate d = today.minusDays(rnd(0, spanDays));
                    boolean in = n % 2 == 0;
                    BigDecimal qty = new BigDecimal(rnd(1, 40) + ".000000");
                    return new Object[]{now, now, java.sql.Date.valueOf(d), itemBase + it,
                            whIds.get(n % whIds.size()), in ? "IN" : "OUT", qty, adetUnit, qty,
                            money(rnd(15, 350)), mvDoc[n % mvDoc.length], "PERF", "SM" + n};
                });

        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.warn("PerfDataGenerator BİTTİ — {} sn. Satır sayıları: {}", ms / 1000.0, rowCounts());
    }

    // ---------------------------------------------------------------------
    // yardımcılar
    // ---------------------------------------------------------------------

    @FunctionalInterface
    private interface Row {
        Object[] build(int index);
    }

    /** {@code count} satırı {@code CHUNK} boyutlu partiler halinde ekler. */
    private void insert(String sql, int count, Row row) {
        insertTotal(sql, count, row);
    }

    private void insertTotal(String sql, int count, Row row) {
        List<Object[]> buf = new ArrayList<>(Math.min(count, CHUNK));
        for (int i = 0; i < count; i++) {
            buf.add(row.build(i));
            if (buf.size() == CHUNK) {
                jdbc.batchUpdate(sql, buf);
                buf.clear();
            }
        }
        if (!buf.isEmpty()) {
            jdbc.batchUpdate(sql, buf);
        }
    }

    private long minId(String table) {
        Long v = jdbc.queryForObject("select min(id) from " + table, Long.class);
        return v == null ? 1L : v;
    }

    /** Kasa kartı varsa id'sini döndürür, yoksa oluşturur (POS/BANKA açılışta yok). */
    private long ensureFinAccount(String code, String name, String kind, Timestamp now) {
        List<Long> found = jdbc.queryForList(
                "select id from fin_account where branch_id = 1 and code = ?", Long.class, code);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        jdbc.update("insert into fin_account (created_at,updated_at,code,name,kind) values (?,?,?,?,?)",
                now, now, code, name, kind);
        return jdbc.queryForObject(
                "select id from fin_account where branch_id = 1 and code = ?", Long.class, code);
    }

    private static int rnd(int minInclusive, int maxExclusive) {
        if (maxExclusive <= minInclusive) {
            return minInclusive;
        }
        return ThreadLocalRandom.current().nextInt(minInclusive, maxExclusive);
    }

    /** Kuruş tabanlı tam sayıdan {@code NUMERIC(19,4)} uyumlu BigDecimal. */
    private static BigDecimal money(long wholeUnits) {
        return new BigDecimal(wholeUnits).setScale(4);
    }

    private static String pad(int v, int width) {
        return String.format("%0" + width + "d", v);
    }

    private String rowCounts() {
        StringBuilder sb = new StringBuilder();
        for (String t : new String[]{"party", "stock_movement", "appointment", "invoice", "invoice_line",
                "cash_transaction", "party_transaction", "installment", "cheque"}) {
            sb.append(t).append('=').append(jdbc.queryForObject("select count(*) from " + t, Long.class)).append(' ');
        }
        return sb.toString();
    }
}

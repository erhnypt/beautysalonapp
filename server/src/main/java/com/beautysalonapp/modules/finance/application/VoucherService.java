package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.finance.domain.CashTransaction;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import com.beautysalonapp.modules.finance.domain.FinAccount;
import com.beautysalonapp.modules.finance.infrastructure.FinAccountRepository;
import com.beautysalonapp.settings.application.SettingService;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Dekont PDF çıktısı (plan §10.6: "Dekont tasarımcısı: firmaya özel şablon (logo,
 * alanlar, dipnot), işlem sonu otomatik üretim"). Şablon {@code <dataDir>/config/reports}
 * altına kopyalanır ve orada düzenlenebilir — uygulama güncellemesi bu kopyanın üzerine
 * yazmaz; JAR içindeki sürüm yalnızca ilk kurulumda tohumlamak için kullanılır.
 */
@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);
    private static final String TEMPLATE_RESOURCE = "reports/dekont.jrxml";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AppProperties props;
    private final SettingService settings;
    private final FinAccountRepository accounts;

    public VoucherService(AppProperties props, SettingService settings, FinAccountRepository accounts) {
        this.props = props;
        this.settings = settings;
        this.accounts = accounts;
    }

    public byte[] renderVoucher(CashTransaction t) {
        FinAccount account = accounts.findById(t.getAccountId())
                .orElseThrow(() -> new NotFoundException("Hesap", t.getAccountId()));
        Map<String, Object> params = new HashMap<>();
        params.put("isletmeAdi", settings.getOrDefault("finance.voucherCompanyName", "İşletme"));
        params.put("dekontNo", t.getDocNo() == null ? "-" : t.getDocNo());
        params.put("tarih", t.getDate().format(DATE_FMT));
        params.put("tur", turkishLabel(t.getType()));
        params.put("hesap", account.getName());
        params.put("karsiTaraf", "");
        params.put("aciklama", t.getDescription() == null ? "" : t.getDescription());
        params.put("tutar", t.getAmount().setScale(2, RoundingMode.HALF_UP) + " " + t.getCurrency());
        params.put("dipnot", settings.getOrDefault("finance.voucherFootnote",
                "Bu belge bilgilendirme amaçlıdır."));

        try {
            JasperReport report = JasperCompileManager.compileReport(templateInputStream());
            JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new IllegalStateException("Dekont PDF üretilemedi", e);
        }
    }

    /**
     * Şablonu dış (düzenlenebilir) dizinden okur; ilk çalıştırmada JAR içindeki
     * varsayılanı oraya kopyalar ki güncellemede ezilmesin.
     */
    private InputStream templateInputStream() {
        try {
            Path external = Path.of(props.getDataDir(), "config", "reports", "dekont.jrxml");
            if (!Files.exists(external)) {
                Files.createDirectories(external.getParent());
                try (InputStream defaultTemplate = getClass().getClassLoader()
                        .getResourceAsStream(TEMPLATE_RESOURCE)) {
                    if (defaultTemplate == null) {
                        throw new IllegalStateException("Varsayılan dekont şablonu classpath'te yok: "
                                + TEMPLATE_RESOURCE);
                    }
                    Files.copy(defaultTemplate, external);
                    log.info("Dekont şablonu tohumlandı: {}", external);
                }
            }
            return Files.newInputStream(external);
        } catch (IOException e) {
            throw new UncheckedIOException("Dekont şablonu okunamadı", e);
        }
    }

    private static String turkishLabel(CashTxnType type) {
        return switch (type) {
            case COLLECTION -> "Tahsilat";
            case PAYMENT -> "Tediye";
            case TRANSFER -> "Virman";
            case FX_BUY -> "Döviz Alım";
            case FX_SELL -> "Döviz Satım";
        };
    }
}

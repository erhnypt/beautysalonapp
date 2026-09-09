package com.beautysalonapp.modules.contract.application;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.modules.contract.domain.ContractLine;
import com.beautysalonapp.modules.contract.domain.Installment;
import com.beautysalonapp.modules.contract.domain.SalesContract;
import com.beautysalonapp.modules.party.application.PartyDirectory;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sözleşme PDF çıktısı — imza alanlı (plan §10.12: "Sözleşme PDF çıktısı (imza alanlı),
 * taranmış imzalı nüsha yükleme"). İkinci kısım {@code attachment} modülüyle karşılanıyor;
 * bu servis birincisini üretir. Şablon {@link VoucherService} ile aynı desen: dış
 * (düzenlenebilir) dizine tohumlanır, uygulama güncellemesi üzerine yazmaz.
 */
@Service
public class ContractPdfService {

    private static final Logger log = LoggerFactory.getLogger(ContractPdfService.class);
    private static final String TEMPLATE_RESOURCE = "reports/sozlesme.jrxml";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AppProperties props;
    private final SettingService settings;
    private final ContractService contracts;
    private final PartyDirectory partyDirectory;

    public ContractPdfService(AppProperties props, SettingService settings, ContractService contracts,
                              PartyDirectory partyDirectory) {
        this.props = props;
        this.settings = settings;
        this.contracts = contracts;
        this.partyDirectory = partyDirectory;
    }

    @Transactional(readOnly = true)
    public byte[] renderContract(long contractId) {
        SalesContract c = contracts.get(contractId);
        var party = partyDirectory.require(c.getPartyId());
        List<ContractLine> lines = contracts.lines(contractId);
        List<Installment> installments = contracts.installments(contractId);

        Map<String, Object> params = new HashMap<>();
        params.put("isletmeAdi", settings.getOrDefault("finance.voucherCompanyName", "İşletme"));
        params.put("sozlesmeNo", c.getDocNo());
        params.put("tarih", c.getContractDate().format(DATE_FMT));
        params.put("musteri", party.title() + " (" + party.code() + ")");
        params.put("kalemler", formatLines(lines, c.getCurrency()));
        params.put("toplamTutar", money(c.getTotalAmount()) + " " + c.getCurrency());
        params.put("pesinat", money(c.getDownPayment()) + " " + c.getCurrency());
        params.put("taksitSayisi", String.valueOf(c.getInstallmentCount()));
        params.put("taksitPlani", formatInstallments(installments, c.getCurrency()));
        params.put("notlar", c.getNotes() == null ? "" : c.getNotes());
        params.put("dipnot", settings.getOrDefault("finance.voucherFootnote",
                "Bu belge bilgilendirme amaçlıdır."));

        try {
            JasperReport report = JasperCompileManager.compileReport(templateInputStream());
            JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new IllegalStateException("Sözleşme PDF üretilemedi", e);
        }
    }

    private static String formatLines(List<ContractLine> lines, String currency) {
        if (lines.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (ContractLine l : lines) {
            sb.append(l.getDescription() == null ? "-" : l.getDescription())
                    .append("  —  ").append(money(l.getLineTotal())).append(" ").append(currency)
                    .append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String formatInstallments(List<Installment> installments, String currency) {
        if (installments.isEmpty()) {
            return "Peşin ödeme — taksit yok";
        }
        StringBuilder sb = new StringBuilder();
        for (Installment i : installments) {
            sb.append(String.format("%2d. ", i.getSeq()))
                    .append("Vade: ").append(i.getDueDate().format(DATE_FMT))
                    .append("   Tutar: ").append(money(i.getAmount())).append(" ").append(currency)
                    .append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String money(java.math.BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toString();
    }

    /**
     * Şablonu dış (düzenlenebilir) dizinden okur; ilk çalıştırmada JAR içindeki
     * varsayılanı oraya kopyalar ki güncellemede ezilmesin (bkz. VoucherService).
     */
    private InputStream templateInputStream() {
        try {
            Path external = Path.of(props.getDataDir(), "config", "reports", "sozlesme.jrxml");
            if (!Files.exists(external)) {
                Files.createDirectories(external.getParent());
                try (InputStream defaultTemplate = getClass().getClassLoader()
                        .getResourceAsStream(TEMPLATE_RESOURCE)) {
                    if (defaultTemplate == null) {
                        throw new IllegalStateException("Varsayılan sözleşme şablonu classpath'te yok: "
                                + TEMPLATE_RESOURCE);
                    }
                    Files.copy(defaultTemplate, external);
                    log.info("Sözleşme şablonu tohumlandı: {}", external);
                }
            }
            return Files.newInputStream(external);
        } catch (IOException e) {
            throw new UncheckedIOException("Sözleşme şablonu okunamadı", e);
        }
    }
}

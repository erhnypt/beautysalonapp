package com.beautysalonapp.modules.finance;

import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.application.VoucherService;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VoucherServiceTest {

    @Autowired FinanceService finance;
    @Autowired VoucherService vouchers;

    @Test
    void dekont_gecerli_bir_pdf_uretir() {
        long kasa = finance.defaultCashAccountId();
        var txn = finance.createManual(CashTxnType.COLLECTION, LocalDate.now(), kasa, null, null, null,
                new BigDecimal("250.50"), "Test tahsilatı");

        byte[] pdf = vouchers.renderVoucher(txn);

        assertThat(pdf).isNotEmpty();
        // PDF dosya imzası: "%PDF-"
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}

package com.beautysalonapp.modules.contract;

import com.beautysalonapp.modules.contract.application.ContractPdfService;
import com.beautysalonapp.modules.contract.application.ContractService;
import com.beautysalonapp.modules.contract.application.ContractService.CreateContractCommand;
import com.beautysalonapp.modules.contract.application.ContractService.NewLine;
import com.beautysalonapp.modules.contract.domain.InstallmentPeriod;
import com.beautysalonapp.modules.contract.domain.SalesContract;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContractPdfServiceTest {

    @Autowired ContractService contracts;
    @Autowired ContractPdfService pdfService;
    @Autowired PartyService partyService;

    @Test
    void sozlesme_gecerli_bir_pdf_uretir() {
        long partyId = partyService.create(PartyType.MUSTERI, null,
                "Sözleşme PDF Test " + System.nanoTime(), null, null, null, null, null, null).getId();

        var cmd = new CreateContractCommand(partyId, LocalDate.now(),
                List.of(new NewLine(null, "10 Seans Cilt Bakımı", BigDecimal.ONE, 10, new BigDecimal("5000"))),
                new BigDecimal("1000"), 4, LocalDate.now().plusMonths(1), InstallmentPeriod.AYLIK,
                BigDecimal.ZERO, null, "Test notu");
        SalesContract contract = contracts.create(cmd);

        byte[] pdf = pdfService.renderContract(contract.getId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void taksitsiz_pesin_sozlesme_de_pdf_uretir() {
        long partyId = partyService.create(PartyType.MUSTERI, null,
                "Peşin Sözleşme PDF Test " + System.nanoTime(), null, null, null, null, null, null).getId();

        var cmd = new CreateContractCommand(partyId, LocalDate.now(),
                List.of(new NewLine(null, "Tek Seans", BigDecimal.ONE, 1, new BigDecimal("500"))),
                new BigDecimal("500"), 1, LocalDate.now().plusMonths(1), InstallmentPeriod.AYLIK,
                BigDecimal.ZERO, null, null);
        SalesContract contract = contracts.create(cmd);

        byte[] pdf = pdfService.renderContract(contract.getId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}

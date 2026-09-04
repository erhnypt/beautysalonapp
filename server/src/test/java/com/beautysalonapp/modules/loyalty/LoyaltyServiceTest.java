package com.beautysalonapp.modules.loyalty;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.loyalty.application.LoyaltyPort;
import com.beautysalonapp.modules.loyalty.application.LoyaltyService;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyCard;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.CardStatus;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LoyaltyServiceTest {

    @Autowired LoyaltyService loyalty;
    @Autowired LoyaltyPort port;
    @Autowired PartyService partyService;

    private long customer() {
        return partyService.create(PartyType.MUSTERI, null, "Sadakat " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    @Test
    void satistan_puan_kazanimi_ve_bakiye() {
        long cust = customer();
        loyalty.issueCard(cust, null, "MAG-" + System.nanoTime());
        int earned = port.accrueFromSale(cust, new BigDecimal("250"), "INV-A");
        assertThat(earned).isEqualTo(25); // 250 * 0.1
        assertThat(port.cardForParty(cust)).get().extracting(LoyaltyPort.CardInfo::pointsBalance).isEqualTo(25);
    }

    @Test
    void ayni_kaynak_iki_kez_puan_vermez() {
        long cust = customer();
        loyalty.issueCard(cust, null, null);
        port.accrueFromSale(cust, new BigDecimal("100"), "INV-DUP");
        int second = port.accrueFromSale(cust, new BigDecimal("100"), "INV-DUP");
        assertThat(second).isZero();
        assertThat(port.cardForParty(cust)).get().extracting(LoyaltyPort.CardInfo::pointsBalance).isEqualTo(10);
    }

    @Test
    void karti_olmayan_musteri_no_op() {
        assertThat(port.accrueFromSale(customer(), new BigDecimal("500"), "INV-X")).isZero();
    }

    @Test
    void puanla_odeme_bakiyeyi_duser_tl_dondurur() {
        long cust = customer();
        LoyaltyCard card = loyalty.issueCard(cust, null, null);
        port.accrueFromSale(cust, new BigDecimal("1000"), "INV-B"); // 100 puan
        var value = port.redeem(card.getId(), 40, "INV-B-REDEEM"); // 40 * 0.05 = 2.00
        assertThat(value.getAmount()).isEqualByComparingTo("2.00");
        assertThat(loyalty.getCard(card.getId()).getPointsBalance()).isEqualTo(60);
    }

    @Test
    void yetersiz_puan_reddedilir() {
        long cust = customer();
        LoyaltyCard card = loyalty.issueCard(cust, null, null);
        assertThatThrownBy(() -> port.redeem(card.getId(), 10, "X"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void kayip_bildir_bakiyeyi_yeni_karta_tasir() {
        long cust = customer();
        LoyaltyCard card = loyalty.issueCard(cust, null, null);
        port.accrueFromSale(cust, new BigDecimal("300"), "INV-C"); // 30 puan

        LoyaltyCard neu = loyalty.reportLost(card.getId(), null);
        assertThat(loyalty.getCard(card.getId()).getStatus()).isEqualTo(CardStatus.MERGED);
        assertThat(neu.getPointsBalance()).isEqualTo(30);
        assertThat(neu.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void yukumluluk_raporu() {
        long cust = customer();
        LoyaltyCard card = loyalty.issueCard(cust, null, null);
        port.accrueFromSale(cust, new BigDecimal("2000"), "INV-D"); // 200 puan
        var r = loyalty.liabilityReport();
        assertThat(((Number) r.get("outstandingPoints")).longValue()).isGreaterThanOrEqualTo(200);
        assertThat(new BigDecimal(r.get("estimatedLiabilityTry").toString())).isGreaterThanOrEqualTo(new BigDecimal("10.00"));
    }
}

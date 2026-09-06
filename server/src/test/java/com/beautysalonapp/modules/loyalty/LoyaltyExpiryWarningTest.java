package com.beautysalonapp.modules.loyalty;

import com.beautysalonapp.modules.loyalty.application.LoyaltyService;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyCard;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.LoyaltyTxnType;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyTransaction;
import com.beautysalonapp.modules.loyalty.infrastructure.LoyaltyTransactionRepository;
import com.beautysalonapp.modules.notification.application.NotificationService;
import com.beautysalonapp.modules.notification.domain.NotificationStatus;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.IysStatus;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoyaltyExpiryWarningTest {

    @Autowired LoyaltyService loyalty;
    @Autowired LoyaltyTransactionRepository txns;
    @Autowired PartyService partyService;
    @Autowired NotificationService notifications;

    private long queued() {
        return notifications.listQueue(NotificationStatus.PENDING, PageRequest.of(0, 500)).getTotalElements()
                + notifications.listQueue(NotificationStatus.SENT, PageRequest.of(0, 500)).getTotalElements();
    }

    @Test
    void tam_30_gun_sonra_dolacak_puan_icin_uyari_gonderilir() {
        long partyId = partyService.create(PartyType.MUSTERI, null, "Puan Uyarı " + System.nanoTime(),
                null, null, "05551112233", null, null, null).getId();
        partyService.update(partyId, null, null, null, "05551112233", null, null, null,
                true, false, IysStatus.IZINLI, null, null, null);
        LoyaltyCard card = loyalty.issueCard(partyId, null, null);

        LoyaltyTransaction earn = new LoyaltyTransaction(card.getId(), LoyaltyTxnType.EARN, 50, "TEST-WARN-1");
        earn.setExpiresAt(LocalDate.now().plusDays(30)); // varsayılan uyarı eşiği
        txns.save(earn);

        long before = queued();
        loyalty.warnExpiringPoints();

        assertThat(queued()).isGreaterThan(before);
    }

    @Test
    void henuz_uyari_esigine_girmemis_puan_icin_bildirim_yok() {
        long partyId = partyService.create(PartyType.MUSTERI, null, "Puan Erken " + System.nanoTime(),
                null, null, "05559998877", null, null, null).getId();
        partyService.update(partyId, null, null, null, "05559998877", null, null, null,
                true, false, IysStatus.IZINLI, null, null, null);
        LoyaltyCard card = loyalty.issueCard(partyId, null, null);

        LoyaltyTransaction earn = new LoyaltyTransaction(card.getId(), LoyaltyTxnType.EARN, 50, "TEST-WARN-2");
        earn.setExpiresAt(LocalDate.now().plusDays(90)); // eşiğin çok ötesinde
        txns.save(earn);

        long before = queued();
        loyalty.warnExpiringPoints();

        assertThat(queued()).isEqualTo(before);
    }
}

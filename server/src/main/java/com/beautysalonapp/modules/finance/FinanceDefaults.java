package com.beautysalonapp.modules.finance;

import com.beautysalonapp.modules.finance.domain.CardDirection;
import com.beautysalonapp.modules.finance.domain.FinAccount;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import com.beautysalonapp.modules.finance.domain.IncomeExpenseCard;
import com.beautysalonapp.modules.finance.infrastructure.FinAccountRepository;
import com.beautysalonapp.modules.finance.infrastructure.IncomeExpenseCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** İlk açılış: varsayılan TL kasa + temel gelir/gider kart planı. */
@Component
@Order(30)
public class FinanceDefaults implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FinanceDefaults.class);
    private static final Long BRANCH = 1L;

    private final FinAccountRepository accounts;
    private final IncomeExpenseCardRepository cards;

    public FinanceDefaults(FinAccountRepository accounts, IncomeExpenseCardRepository cards) {
        this.accounts = accounts;
        this.cards = cards;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.findByBranchIdAndCode(BRANCH, "KASA").isEmpty()) {
            FinAccount kasa = new FinAccount("KASA", "Ana Kasa (TL)", FinAccountKind.KASA, "TRY");
            kasa.setDefault(true);
            accounts.save(kasa);
            log.info("Varsayılan kasa oluşturuldu: KASA");
        }

        seedCard(null, "600", "Gelirler", CardDirection.INCOME, false, false);
        seedCard(null, "600.01", "Hizmet Geliri", CardDirection.INCOME, true, true);
        seedCard(null, "600.02", "Ürün Satış Geliri", CardDirection.INCOME, false, true);
        seedCard(null, "600.09", "Diğer Gelirler", CardDirection.INCOME, false, true);
        seedCard(null, "700", "Giderler", CardDirection.EXPENSE, false, false);
        seedCard(null, "700.01", "Kira", CardDirection.EXPENSE, false, true);
        seedCard(null, "700.02", "Personel Maaş/Prim", CardDirection.EXPENSE, false, true);
        seedCard(null, "700.03", "Malzeme/Sarf Alımı", CardDirection.EXPENSE, false, true);
        seedCard(null, "700.09", "Genel Giderler", CardDirection.EXPENSE, false, true);
    }

    private void seedCard(Long parentId, String code, String name, CardDirection dir,
                          boolean serviceCard, boolean postable) {
        if (cards.findByBranchIdAndCode(BRANCH, code).isPresent()) {
            return;
        }
        IncomeExpenseCard c = new IncomeExpenseCard(parentId, code, name, dir);
        c.setServiceCard(serviceCard);
        c.setPostable(postable);
        cards.save(c);
    }
}

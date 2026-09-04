package com.beautysalonapp.modules.loyalty.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.domain.Money;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.core.sequence.SequenceService;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyCard;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.CardStatus;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.LoyaltyTxnType;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyProgram;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyTransaction;
import com.beautysalonapp.modules.loyalty.domain.PointsCalc;
import com.beautysalonapp.modules.loyalty.infrastructure.LoyaltyCardRepository;
import com.beautysalonapp.modules.loyalty.infrastructure.LoyaltyProgramRepository;
import com.beautysalonapp.modules.loyalty.infrastructure.LoyaltyTransactionRepository;
import com.beautysalonapp.modules.notification.application.NotificationService;
import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import com.beautysalonapp.modules.party.application.PartyDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class LoyaltyService implements LoyaltyPort {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);
    private static final Long BRANCH = 1L;

    private final LoyaltyProgramRepository programs;
    private final LoyaltyCardRepository cards;
    private final LoyaltyTransactionRepository txns;
    private final PartyDirectory partyDirectory;
    private final NotificationService notifications;
    private final SequenceService sequences;
    private final AuditService audit;

    public LoyaltyService(LoyaltyProgramRepository programs, LoyaltyCardRepository cards,
                          LoyaltyTransactionRepository txns, PartyDirectory partyDirectory,
                          NotificationService notifications, SequenceService sequences, AuditService audit) {
        this.programs = programs;
        this.cards = cards;
        this.txns = txns;
        this.partyDirectory = partyDirectory;
        this.notifications = notifications;
        this.sequences = sequences;
        this.audit = audit;
    }

    // --- program ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<LoyaltyProgram> listPrograms() {
        return programs.findAllByDeletedFalseOrderByName();
    }

    public LoyaltyProgram upsertProgram(Long id, String name, BigDecimal earnRate,
                                        BigDecimal pointToCurrency, int expiryMonths, boolean active) {
        LoyaltyProgram p = id != null ? programs.findById(id)
                .orElseThrow(() -> new NotFoundException("Program", id)) : new LoyaltyProgram(name);
        p.setName(name);
        if (earnRate != null) p.setEarnRate(earnRate);
        if (pointToCurrency != null) p.setPointToCurrency(pointToCurrency);
        if (expiryMonths > 0) p.setExpiryMonths(expiryMonths);
        p.setActive(active);
        return programs.save(p);
    }

    private LoyaltyProgram activeProgram() {
        return programs.findFirstByActiveTrueAndDeletedFalseOrderByIdAsc()
                .orElseThrow(() -> new BusinessRuleException("no_program", "Aktif sadakat programı yok"));
    }

    // --- kartlar --------------------------------------------------

    @Transactional(readOnly = true)
    public List<LoyaltyCard> listCards(String q) {
        return cards.search((q == null || q.isBlank()) ? null : q.trim());
    }

    @Transactional(readOnly = true)
    public LoyaltyCard getCard(long id) {
        return cards.findById(id).orElseThrow(() -> new NotFoundException("Kart", id));
    }

    public LoyaltyCard issueCard(long partyId, String cardNo, String magneticId) {
        partyDirectory.require(partyId);
        if (cards.findByPartyIdAndStatus(partyId, CardStatus.ACTIVE).isPresent()) {
            throw new BusinessRuleException("card_exists", "Bu müşterinin zaten aktif bir kartı var");
        }
        String no = (cardNo == null || cardNo.isBlank())
                ? sequences.next(BRANCH, "LOYALTY_CARD", "K") : cardNo.trim();
        if (cards.findByBranchIdAndCardNo(BRANCH, no).isPresent()) {
            throw new BusinessRuleException("card_no_taken", "Bu kart no zaten kayıtlı");
        }
        LoyaltyCard card = new LoyaltyCard(no, partyId, activeProgram().getId());
        card.setMagneticId(magneticId);
        cards.save(card);
        audit.record("LOYALTY_CARD_ISSUE", "LoyaltyCard", card.getId(), "Sadakat kartı: " + no);
        return card;
    }

    /** Kayıp bildir: eski kart MERGED, yeni kart oluştur, bakiye taşı. */
    public LoyaltyCard reportLost(long cardId, String newCardNo) {
        LoyaltyCard old = getCard(cardId);
        if (old.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessRuleException("not_active", "Yalnızca aktif kart kayıp bildirilebilir");
        }
        int balance = old.getPointsBalance();
        LoyaltyCard neu = issueCardInternal(old.getPartyId(), newCardNo);
        if (balance > 0) {
            post(old.getId(), LoyaltyTxnType.TRANSFER_OUT, -balance, "TRANSFER:" + neu.getId(), null, null, null);
            old.addPoints(-balance);
            post(neu.getId(), LoyaltyTxnType.TRANSFER_IN, balance, "TRANSFER:" + old.getId(), null, null, null);
            neu.addPoints(balance);
        }
        old.setStatus(CardStatus.MERGED);
        cards.save(old);
        audit.record("LOYALTY_CARD_LOST", "LoyaltyCard", cardId,
                "Kayıp bildirimi → yeni kart " + neu.getCardNo() + " (bakiye " + balance + ")");
        return neu;
    }

    private LoyaltyCard issueCardInternal(long partyId, String cardNo) {
        String no = (cardNo == null || cardNo.isBlank())
                ? sequences.next(BRANCH, "LOYALTY_CARD", "K") : cardNo.trim();
        LoyaltyCard c = new LoyaltyCard(no, partyId, activeProgram().getId());
        return cards.save(c);
    }

    public void setStatus(long cardId, CardStatus status) {
        LoyaltyCard c = getCard(cardId);
        c.setStatus(status);
    }

    @Transactional(readOnly = true)
    public Optional<LoyaltyCard> resolve(String key) {
        return cards.findByBranchIdAndCardNo(BRANCH, key.trim())
                .or(() -> cards.findByMagneticId(key.trim()));
    }

    @Transactional(readOnly = true)
    public List<LoyaltyTransaction> transactions(long cardId) {
        return txns.findAllByCardIdOrderByAtDesc(cardId);
    }

    // --- puan işlemleri (port) -------------------------------

    @Override
    public int accrueFromSale(long partyId, BigDecimal spendAmount, String sourceRef) {
        Optional<LoyaltyCard> maybe = cards.findByPartyIdAndStatus(partyId, CardStatus.ACTIVE);
        if (maybe.isEmpty()) {
            return 0;
        }
        LoyaltyCard card = maybe.get();
        if (txns.existsByCardIdAndTypeAndSourceRef(card.getId(), LoyaltyTxnType.EARN, sourceRef)) {
            return 0; // idempotent
        }
        LoyaltyProgram program = programs.findById(card.getProgramId()).orElseGet(this::activeProgram);
        int earned = PointsCalc.earn(spendAmount, program.getEarnRate());
        if (earned <= 0) {
            return 0;
        }
        LocalDate expires = LocalDate.now().plusMonths(program.getExpiryMonths());
        post(card.getId(), LoyaltyTxnType.EARN, earned, sourceRef, spendAmount, null, expires);
        card.addPoints(earned);
        return earned;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CardInfo> cardForParty(long partyId) {
        return cards.findByPartyIdAndStatus(partyId, CardStatus.ACTIVE).map(this::toInfo);
    }

    @Override
    public Money redeem(long cardId, int points, String sourceRef) {
        LoyaltyCard card = getCard(cardId);
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessRuleException("card_not_active", "Kart aktif değil");
        }
        if (points <= 0) {
            throw new BusinessRuleException("bad_points", "Kullanılacak puan pozitif olmalı");
        }
        if (points > card.getPointsBalance()) {
            throw new BusinessRuleException("insufficient_points",
                    "Yetersiz puan (bakiye " + card.getPointsBalance() + ")");
        }
        LoyaltyProgram program = programs.findById(card.getProgramId()).orElseGet(this::activeProgram);
        BigDecimal value = PointsCalc.redemptionValue(points, program.getPointToCurrency());
        post(card.getId(), LoyaltyTxnType.REDEEM, -points, sourceRef, null, value, null);
        card.addPoints(-points);
        audit.record("LOYALTY_REDEEM", "LoyaltyCard", cardId,
                points + " puan kullanıldı = " + value + " TL (kaynak " + sourceRef + ")");
        return Money.of(value, "TRY");
    }

    public LoyaltyTransaction adjust(long cardId, int points, String reason) {
        LoyaltyCard card = getCard(cardId);
        var t = post(card.getId(), LoyaltyTxnType.ADJUST, points, "ADJUST", null, null, null);
        card.addPoints(points);
        audit.record("LOYALTY_ADJUST", "LoyaltyCard", cardId, points + " puan düzeltme: " + reason);
        return t;
    }

    // --- zaman aşımı ------------------------------------------

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 45 3 * * *")
    public void expirePoints() {
        LocalDate today = LocalDate.now();
        List<LoyaltyTransaction> expired = txns.expiredEarnings(LoyaltyTxnType.EARN, today);
        Map<Long, Integer> perCard = new java.util.HashMap<>();
        for (LoyaltyTransaction earn : expired) {
            earn.setExpired(true);
            perCard.merge(earn.getCardId(), earn.getPoints(), Integer::sum);
        }
        for (var e : perCard.entrySet()) {
            LoyaltyCard card = cards.findById(e.getKey()).orElse(null);
            if (card == null) {
                continue;
            }
            int toExpire = Math.min(e.getValue(), card.getPointsBalance());
            if (toExpire <= 0) {
                continue;
            }
            post(card.getId(), LoyaltyTxnType.EXPIRE, -toExpire, "EXPIRE:" + today, null, null, null);
            card.addPoints(-toExpire);
            notifications.enqueue(NotificationType.KAMPANYA, NotificationChannel.SMS, card.getPartyId(),
                    null, Map.of("kampanya", "puan zaman aşımı", "tarih", today.toString()), null);
        }
        if (!perCard.isEmpty()) {
            log.info("Puan zaman aşımı: {} kartta işlem yapıldı", perCard.size());
        }
    }

    // --- rapor ---------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> liabilityReport() {
        long totalPoints = cards.totalOutstandingPoints();
        BigDecimal rate = programs.findFirstByActiveTrueAndDeletedFalseOrderByIdAsc()
                .map(LoyaltyProgram::getPointToCurrency).orElse(BigDecimal.ZERO);
        BigDecimal liability = BigDecimal.valueOf(totalPoints).multiply(rate)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        long activeCards = cards.findAllByStatus(CardStatus.ACTIVE).size();
        return Map.of("outstandingPoints", totalPoints, "estimatedLiabilityTry", liability,
                "activeCards", activeCards);
    }

    // --- iç yardımcı --------------------------------------

    private LoyaltyTransaction post(Long cardId, LoyaltyTxnType type, int points, String sourceRef,
                                    BigDecimal spend, BigDecimal value, LocalDate expiresAt) {
        LoyaltyTransaction t = new LoyaltyTransaction(cardId, type, points, sourceRef);
        t.setSpendAmount(spend);
        t.setCurrencyValue(value);
        t.setExpiresAt(expiresAt);
        return txns.save(t);
    }

    private CardInfo toInfo(LoyaltyCard c) {
        BigDecimal rate = programs.findById(c.getProgramId())
                .map(LoyaltyProgram::getPointToCurrency).orElse(BigDecimal.ZERO);
        return new CardInfo(c.getId(), c.getCardNo(), c.getPartyId(), c.getPointsBalance(),
                PointsCalc.redemptionValue(c.getPointsBalance(), rate));
    }
}

package com.beautysalonapp.core.sequence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Belge numarası dağıtımı. Format: {@code <prefix><yıl>-<6 hane sıfır dolgulu>},
 * ör. {@code A2026-000123}.
 */
@Service
public class SequenceService {

    private static final Long DEFAULT_BRANCH = 1L;

    private final SequenceCounterRepository repository;

    public SequenceService(SequenceCounterRepository repository) {
        this.repository = repository;
    }

    /**
     * Sıradaki numarayı üretir. Numara üretimi belge kaydıyla aynı transaction'da
     * çağrılmalıdır; iş geri alınırsa numara da geri alınır (numara "yakılmaz").
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String type) {
        return next(DEFAULT_BRANCH, type, "");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long branchId, String type, String defaultPrefix) {
        int year = LocalDate.now().getYear();
        SequenceCounter counter = repository.lockByKey(branchId, type, year)
                .orElseGet(() -> repository.save(new SequenceCounter(branchId, type, year, defaultPrefix)));
        long value = counter.next();
        String prefix = counter.getPrefix() == null ? "" : counter.getPrefix();
        return "%s%d-%06d".formatted(prefix, year, value);
    }
}

package com.beautysalonapp.modules.contract.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Otomatik taksitlendirme algoritması (plan §9.8). <b>Saf domain — çerçevesiz, %100 test.</b>
 *
 * <pre>
 * kalan        = toplam - peşinat
 * taksitTutarı = ROUND(kalan / adet, 2)            (kuruş)
 * son taksit   = kalan - taksitTutarı * (adet-1)   (kuruş farkı son taksite)
 * vadeler      = ilkVade, ilkVade + 1 dönem, …     (ay sonu düzeltmesi LocalDate ile)
 * </pre>
 *
 * <p>v1: {@code interestRate} saklanır ama tutara uygulanmaz (vade farkı Faz 4).
 */
public final class InstallmentPlan {

    private InstallmentPlan() {
    }

    public record PlannedInstallment(int seq, LocalDate dueDate, BigDecimal amount) {}

    public static List<PlannedInstallment> generate(BigDecimal total, BigDecimal downPayment,
                                                    int count, LocalDate firstDueDate,
                                                    InstallmentPeriod period) {
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("Sözleşme tutarı pozitif olmalı");
        }
        BigDecimal down = downPayment == null ? BigDecimal.ZERO : downPayment;
        if (down.signum() < 0) {
            throw new IllegalArgumentException("Peşinat negatif olamaz");
        }
        if (down.compareTo(total) > 0) {
            throw new IllegalArgumentException("Peşinat toplam tutardan büyük olamaz");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Taksit adedi en az 1 olmalı");
        }
        if (firstDueDate == null) {
            throw new IllegalArgumentException("İlk vade tarihi zorunlu");
        }

        BigDecimal remaining = total.subtract(down).setScale(2, RoundingMode.HALF_UP);
        List<PlannedInstallment> result = new ArrayList<>(count);

        if (remaining.signum() == 0) {
            // Tamamı peşin: tek 0 TL taksit üretmek yerine boş plan döndürmek daha temiz
            return List.of();
        }

        BigDecimal per = remaining.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        BigDecimal accumulated = BigDecimal.ZERO;

        for (int i = 0; i < count; i++) {
            BigDecimal amount;
            if (i < count - 1) {
                amount = per;
                accumulated = accumulated.add(per);
            } else {
                amount = remaining.subtract(accumulated); // kuruş farkı son taksite
            }
            result.add(new PlannedInstallment(i + 1, period.advance(firstDueDate, i), amount));
        }
        return result;
    }

    /** Plan tutarları toplamı = kalan (doğrulama yardımcı). */
    public static BigDecimal sum(List<PlannedInstallment> plan) {
        return plan.stream().map(PlannedInstallment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

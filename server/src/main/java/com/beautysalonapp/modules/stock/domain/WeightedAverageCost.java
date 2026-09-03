package com.beautysalonapp.modules.stock.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ağırlıklı ortalama maliyet (plan §10.1). Saf, çerçevesiz, %100 test kapsamı hedefli.
 *
 * <p>Tüm miktarlar <b>base unit</b> cinsindendir. Maliyet, base birim başına tutardır.
 */
public record WeightedAverageCost(BigDecimal quantityBase, BigDecimal avgUnitCost) {

    private static final int QTY_SCALE = 6;
    private static final int COST_SCALE = 4;

    public static WeightedAverageCost empty() {
        return new WeightedAverageCost(BigDecimal.ZERO.setScale(QTY_SCALE), BigDecimal.ZERO.setScale(COST_SCALE));
    }

    public WeightedAverageCost {
        quantityBase = quantityBase.setScale(QTY_SCALE, RoundingMode.HALF_UP);
        avgUnitCost = avgUnitCost.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Stok girişi. Yeni ortalama = (mevcutMiktar·mevcutOrt + girenMiktar·girenMaliyet) /
     * (mevcutMiktar + girenMiktar). Toplam miktar ≤ 0 olursa ortalama korunur.
     */
    public WeightedAverageCost receive(BigDecimal incomingBaseQty, BigDecimal incomingUnitCost) {
        if (incomingBaseQty.signum() <= 0) {
            return this;
        }
        BigDecimal newQty = quantityBase.add(incomingBaseQty);
        if (newQty.signum() <= 0) {
            return new WeightedAverageCost(newQty, avgUnitCost);
        }
        BigDecimal currentValue = quantityBase.multiply(avgUnitCost);
        BigDecimal incomingValue = incomingBaseQty.multiply(incomingUnitCost);
        BigDecimal newAvg = currentValue.add(incomingValue)
                .divide(newQty, COST_SCALE, RoundingMode.HALF_UP);
        return new WeightedAverageCost(newQty, newAvg);
    }

    /** Stok çıkışı. Ortalama maliyet değişmez; yalnızca miktar düşer. */
    public WeightedAverageCost issue(BigDecimal outgoingBaseQty) {
        if (outgoingBaseQty.signum() <= 0) {
            return this;
        }
        return new WeightedAverageCost(quantityBase.subtract(outgoingBaseQty), avgUnitCost);
    }

    /** Elde kalan stok değeri (miktar · ortalama). */
    public BigDecimal value() {
        return quantityBase.multiply(avgUnitCost).setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    public boolean isNegative() {
        return quantityBase.signum() < 0;
    }
}

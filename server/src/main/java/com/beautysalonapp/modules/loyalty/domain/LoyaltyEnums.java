package com.beautysalonapp.modules.loyalty.domain;

public final class LoyaltyEnums {
    private LoyaltyEnums() {
    }

    public enum CardStatus {
        ACTIVE, LOST, BLOCKED, MERGED
    }

    public enum LoyaltyTxnType {
        EARN, REDEEM, EXPIRE, TRANSFER_IN, TRANSFER_OUT, ADJUST
    }

    public enum RewardType {
        POINT_BONUS, DISCOUNT_RATE, GIFT
    }
}

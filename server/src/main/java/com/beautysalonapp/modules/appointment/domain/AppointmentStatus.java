package com.beautysalonapp.modules.appointment.domain;

/** Randevu durum akışı (§9.7). */
public enum AppointmentStatus {
    PLANLANDI,
    ONAYLANDI,
    GELDI,
    GELMEDI,
    IPTAL;

    public boolean canTransitionTo(AppointmentStatus next) {
        return switch (this) {
            case PLANLANDI -> next == ONAYLANDI || next == GELDI || next == GELMEDI || next == IPTAL;
            case ONAYLANDI -> next == GELDI || next == GELMEDI || next == IPTAL || next == PLANLANDI;
            case GELDI, GELMEDI, IPTAL -> false; // uç durumlar
        };
    }

    public boolean isTerminal() {
        return this == GELDI || this == GELMEDI || this == IPTAL;
    }
}

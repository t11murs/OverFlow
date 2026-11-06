package com.orderflow.inventory.adapter.inbound;

import com.orderflow.inventory.application.inventory.ReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryJob {
    private final ReservationService reservationService;

    public ReservationExpiryJob(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${inventory.expiry-scan-delay:30s}")
    public void expireReservations() {
        reservationService.expireBatch();
    }
}


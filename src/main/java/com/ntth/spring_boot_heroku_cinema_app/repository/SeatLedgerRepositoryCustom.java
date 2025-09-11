package com.ntth.spring_boot_heroku_cinema_app.repository;

import java.time.Instant;
import java.util.List;

public interface SeatLedgerRepositoryCustom {
    // FREE -> HOLD (lock)
    long holdSeats(String showtimeId, List<String> seats, String holdId, Instant expiresAt);

    // HOLD -> CONFIRMED (by hold)
    long confirmSeatsByHold(String showtimeId, List<String> seats, String holdId, String bookingId);

    // HOLD -> FREE (release by hold)
    long releaseSeatsByHold(String showtimeId, List<String> seats, String holdId);

    // HOLD (expired) -> FREE (background cleanup)
    long releaseExpiredLocks(Instant now);

    // CONFIRMED -> FREE (cancel by booking)
    long releaseSeatsByBookingId(String bookingId);
    /**
     * CONFIRM nhiều ghế atomically; trả về số ghế được cập nhật.
     */
    long confirmMany(String showtimeId, List<String> seats, String bookingId, String holdId);
    /** Trả ghế về FREE cho 1 booking (idempotent-safe) */
    long freeMany(String showtimeId, List<String> seats, String bookingId);
}

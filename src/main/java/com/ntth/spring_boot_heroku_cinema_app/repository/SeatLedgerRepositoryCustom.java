package com.ntth.spring_boot_heroku_cinema_app.repository;

import java.util.List;

public interface SeatLedgerRepositoryCustom {
    /**
     * CONFIRM 1 ghế: chỉ thành công khi hiện tại đang HOLD bởi holdId tương ứng.
     * Trả về true nếu cập nhật thành công (1 doc).
     */
    boolean confirm(String showtimeId, String seat, String bookingId, String holdId);
    /**
     * CONFIRM nhiều ghế atomically; trả về số ghế được cập nhật.
     */
    long confirmMany(String showtimeId, List<String> seats, String bookingId, String holdId);
    /** Trả ghế về FREE cho 1 booking (idempotent-safe) */
    long freeMany(String showtimeId, List<String> seats, String bookingId);
}

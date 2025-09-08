//package com.ntth.spring_boot_heroku_cinema_app.service;
//
//import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLock;
//import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepository;
//import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLockRepository;
//import org.springframework.http.HttpStatus;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.server.ResponseStatusException;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//@Service
//public class HoldService {
//
//    private static final Duration HOLD_TTL = Duration.ofMinutes(5);
//
//    private final SeatLockRepository lockRepo;
//    private final SeatLedgerRepository ledgerRepo;
//    private final PricingService pricing;
//
//    public HoldService(SeatLockRepository lockRepo,
//                       SeatLedgerRepository ledgerRepo,
//                       PricingService pricing) {
//        this.lockRepo = lockRepo; this.ledgerRepo = ledgerRepo; this.pricing = pricing;
//    }
//
//    @Transactional
//    public Map<String,Object> createHold(String userId, String showtimeId, List<String> seats) {
//        if (seats == null || seats.isEmpty()) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SEATS_REQUIRED");
//        }
//
//        // 1) Tính tiền + hạn giữ
//        long amount = pricing.calcAmount(showtimeId, seats);
//        Instant expiresAt = Instant.now().plus(HOLD_TTL);
//        String holdId = "hold_" + UUID.randomUUID();
//
//        // 2) Ghi ledger: FREE -> HOLD
//        long updated = ledgerRepo.holdMany(showtimeId, seats, holdId, expiresAt);
//        if (updated != seats.size()) {
//            // rollback các ghế đã chuyển sang HOLD bởi holdId (nếu có)
//            ledgerRepo.rollbackHoldByRef(showtimeId, holdId);
//            throw new ResponseStatusException(HttpStatus.CONFLICT, "SOME_SEATS_ALREADY_TAKEN");
//        }
//
//        // 3) Tạo seat_locks (TTL)
//        SeatLock lock = new SeatLock();
//        lock.setId(holdId);
//        lock.setUserId(userId);
//        lock.setShowtimeId(showtimeId);
//        lock.setSeats(new ArrayList<>(seats));
//        lock.setAmount(amount);
//        lock.setExpiresAt(expiresAt);
//        lockRepo.save(lock);
//
//        // 4) Trả response
//        return Map.of(
//                "holdId", holdId,
//                "amount", amount,
//                "expiresAt", expiresAt.toString()
//        );
//    }
//}
//

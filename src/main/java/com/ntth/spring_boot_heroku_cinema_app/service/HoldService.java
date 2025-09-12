package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLock;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class HoldService {

    private final SeatLedgerRepository ledgerRepo;
    private final SeatLockRepository lockRepo;

    public HoldService(SeatLedgerRepository ledgerRepo, SeatLockRepository lockRepo) {
        this.ledgerRepo = ledgerRepo;
        this.lockRepo = lockRepo;
    }

    @Transactional
    public Map<String,Object> createHold(String userId, String showtimeId, List<String> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_SEATS");
        }
        seats = seats.stream().filter(Objects::nonNull).map(s -> s.trim().toUpperCase()).toList();

        // TTL mặc định 20 phút
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(20));

        // 1) Tính tiền (tuỳ bạn lấy từ showtime/room)
        long amount = computeAmount(showtimeId, seats);

        // 2) FREE -> HOLD (đổi API theo repo mới)
        String holdId = UUID.randomUUID().toString();
        long updated = ledgerRepo.holdSeats(showtimeId, seats, holdId, expiresAt);
        if (updated != seats.size()) {
            ledgerRepo.releaseSeatsByHold(showtimeId, seats, holdId); // rollback
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SOME_SEATS_ALREADY_TAKEN");
        }

        // 3) Tạo seat_locks (TTL)
        SeatLock lock = new SeatLock();
        lock.setId(holdId);
        lock.setUserId(userId);
        lock.setShowtimeId(showtimeId);
        lock.setSeats(new ArrayList<>(seats));
        try { lock.setAmount(amount); } catch (Throwable ignore) {} // field Amount/amount
        lock.setExpiresAt(expiresAt);
        lockRepo.save(lock);

        // 4) Trả response
        return Map.of(
                "holdId", holdId,
                "amount", amount,
                "expiresAt", expiresAt.toString()
        );
    }

    private long computeAmount(String showtimeId, List<String> seats) {
        // TODO: load giá từ showtime. Tạm thời: 60k/ghế
        return seats.size() * 60000L;
    }
}


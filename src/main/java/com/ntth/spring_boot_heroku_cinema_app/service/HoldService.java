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
    // TTL giữ ghế (DEV có thể để 20 phút). Điều chỉnh theo nhu cầu.
    private static final long HOLD_TTL_MINUTES = 20L;

    /**
     * Giữ ghế:
     *  - Tạo SeatLock (userId, showtimeId, seats, amount, expiresAt)
     *  - Upsert vào seat_ledger: FREE hoặc HOLD (hết hạn) -> HOLD (refType=LOCK, refId=holdId)
     *  - Nếu giữ không đủ ghế yêu cầu -> rollback SeatLock và trả 409
     */
    @Transactional
    public Map<String, Object> createHold(String userId, String showtimeId, List<String> seats) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        if (showtimeId == null || showtimeId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_SHOWTIME");
        }
        if (seats == null || seats.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_SEATS");
        }

        // chuẩn hoá & loại trùng
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String s : seats) {
            if (s != null && !s.isBlank()) unique.add(s.trim().toUpperCase(Locale.ROOT));
        }
        if (unique.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_SEATS");
        List<String> seatList = new ArrayList<>(unique);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(HOLD_TTL_MINUTES));

        long amount = computeAmount(showtimeId, seatList);

        // 1) Lưu SeatLock (CHỈ các field có thật trong SeatLock của bạn)
        SeatLock lock = new SeatLock();
        lock.setUserId(userId);
        lock.setShowtimeId(showtimeId);
        lock.setSeats(seatList);
        lock.setAmount(amount);       // SeatLock của bạn có setAmount(long)
        lock.setExpiresAt(expiresAt); // SeatLock của bạn có setExpiresAt(Instant)
        lockRepo.save(lock);
        String holdId = lock.getId();

        // 2) Upsert vào seat_ledger: FREE | (HOLD đã hết hạn) -> HOLD
        //    (yêu cầu SeatLedgerRepository đã có method custom lockFromFree)
        long locked = ledgerRepo.lockFromFree(showtimeId, seatList, holdId, expiresAt);
        if (locked != seatList.size()) {
            // rollback SeatLock nếu không giữ đủ
            try { lockRepo.deleteById(holdId); } catch (Throwable ignore) {}
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_SOLD");
        }

        // 3) Trả response
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("holdId", holdId);
        res.put("amount", amount);
        res.put("expiresAt", expiresAt.toString());
        return res;
    }

    private long computeAmount(String showtimeId, List<String> seats) {
        // TODO: nếu bạn có bảng giá theo showtime, lấy ở đây. Tạm tính 60k/ghế.
        return seats.size() * 60000L;
    }
}


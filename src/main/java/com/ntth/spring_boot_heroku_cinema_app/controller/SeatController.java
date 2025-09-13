package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLedger;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatState;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/showtimes")
public class SeatController {
    @Autowired
    private SeatLedgerRepository ledgerRepo;

    /**
     * Trả về danh sách trạng thái ghế có trong ledger cho 1 suất chiếu.
     * Ghế không có trong danh sách được hiểu là FREE ở phía client.
     * Mỗi phần tử: { "seat": "B1", "state": "FREE|HOLD|CONFIRMED" }
     */
    @GetMapping("/{showtimeId}/seats")
    public List<Map<String, String>> getSeatLedger(@PathVariable String showtimeId) {
        List<SeatLedger> rows = ledgerRepo.findByShowtimeId(showtimeId);
        Instant now = Instant.now();

        List<Map<String, String>> out = new ArrayList<>(rows.size());
        for (SeatLedger r : rows) {
            SeatState state = r.getState();
            // Nếu HOLD đã hết hạn thì coi như FREE để UI không thấy HOLD "treo"
            if (state == SeatState.HOLD) {
                Instant exp = r.getExpiresAt();
                if (exp != null && exp.isBefore(now)) {
                    state = SeatState.FREE;
                }
            }

            Map<String, String> o = new LinkedHashMap<>();
            o.put("seat", r.getSeat());
            o.put("state", state.name()); // trả string "FREE"/"HOLD"/"CONFIRMED"
            out.add(o);
        }
        return out;
    }
}

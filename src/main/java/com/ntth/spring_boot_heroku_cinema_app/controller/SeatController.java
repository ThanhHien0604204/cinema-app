package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLedger;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatState;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepository;
import com.ntth.spring_boot_heroku_cinema_app.service.ShowtimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/showtimes")
public class SeatController {
    @Autowired
    private SeatLedgerRepository ledgerRepo;

    @Autowired
    private ShowtimeService showtimeService;

    /**
     * Trả về danh sách trạng thái ghế có trong ledger cho 1 suất chiếu.
     * Ghế không có trong danh sách được hiểu là FREE ở phía client.
     * Mỗi phần tử: { "seat": "B1", "state": "FREE|HOLD|CONFIRMED" }
     */
    @GetMapping("/{showtimeId}/seats")
    public List<Map<String, String>> getSeatLedger(@PathVariable String showtimeId) {

        // Lấy showtime để biết totalSeats (adjust theo model Showtime của bạn)
        // Giả sử Showtime có method getTotalSeats(), hoặc lấy từ room liên kết
        int totalSeats = showtimeService.getById(showtimeId).getTotalSeats();

        // Generate tất cả seat codes
        List<String> allSeatCodes = generateAllSeatCodes(totalSeats);

        List<SeatLedger> rows = ledgerRepo.findByShowtimeId(showtimeId);
        Instant now = Instant.now();

        // Map existing states (với check hết hạn HOLD)
        Map<String, SeatState> stateMap = new HashMap<>();
        for (SeatLedger r : rows) {
            SeatState state = r.getState();
            if (state == SeatState.HOLD) {
                Instant exp = r.getExpiresAt();
                if (exp != null && exp.isBefore(now)) {
                    state = SeatState.FREE;
                }
            }
            stateMap.put(r.getSeat(), state);
        }
        // Build full response: FREE nếu không có trong map
        List<Map<String, String>> out = new ArrayList<>(totalSeats);
        for (String seatCode : allSeatCodes) {
            SeatState state = stateMap.getOrDefault(seatCode, SeatState.FREE);
            Map<String, String> o = new LinkedHashMap<>();
            o.put("seat", seatCode);
            o.put("state", state.name());  // "FREE"/"HOLD"/"CONFIRMED"
            out.add(o);
        }
        return out;
    }
    // Helper: Generate seat codes (A1-A10, B1-B10, ..., đến totalSeats)
    private List<String> generateAllSeatCodes(int totalSeats) {
        List<String> seats = new ArrayList<>();
        char row = 'A';
        int col = 1;
        int seatsPerRow = 10;  // Adjust theo layout phòng (e.g., từ Room model)

        while (seats.size() < totalSeats) {
            String seatCode = String.valueOf(row) + col;
            seats.add(seatCode);
            col++;
            if (col > seatsPerRow) {
                col = 1;
                row++;
                if (row > 'Z') break;  // Prevent overflow, though unlikely
            }
        }
        // Trim nếu vượt (rare case)
        return seats.subList(0, Math.min(seats.size(), totalSeats));
    }
}

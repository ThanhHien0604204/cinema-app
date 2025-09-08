package com.ntth.spring_boot_heroku_cinema_app.dto;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Component
public class ShowtimeMapper {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // Dùng khi CHƯA có takenSeats (giữ tương thích cũ)
    public ShowtimeResponse toResponse(Showtime s) {
        return toResponse(s, Collections.emptyList());
    }

    // Dùng khi Service đã tính sẵn takenSeats
    public ShowtimeResponse toResponse(Showtime s, List<String> takenSeats) {
        String start = s.getStartAt() == null ? null
                : s.getStartAt().atZone(VN).toLocalTime().format(HHMM);
        String end = s.getEndAt() == null ? null
                : s.getEndAt().atZone(VN).toLocalTime().format(HHMM);

        Integer available = s.getAvailableSeats();
        if (available == null && s.getTotalSeats() != null && takenSeats != null) {
            available = s.getTotalSeats() - takenSeats.size();
        }

        return new ShowtimeResponse(
                s.getId(), s.getMovieId(), s.getRoomId(), s.getSessionName(),
                start, end, s.getPrice(), s.getTotalSeats(), available,
                s.getDate(), takenSeats
        );
    }
}

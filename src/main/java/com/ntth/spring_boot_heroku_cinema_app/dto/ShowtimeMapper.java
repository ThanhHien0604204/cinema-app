package com.ntth.spring_boot_heroku_cinema_app.dto;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ShowtimeMapper {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public ShowtimeResponse toResponse(Showtime s) {
        String start = s.getStartAt() == null ? null
                : s.getStartAt().atZone(VN).toLocalTime().format(HHMM);
        String end = s.getEndAt() == null ? null
                : s.getEndAt().atZone(VN).toLocalTime().format(HHMM);
        return new ShowtimeResponse(
                s.getId(), s.getMovieId(), s.getRoomId(), s.getSessionName(),
                start, end, s.getPrice(), s.getTotalSeats(), s.getAvailableSeats(),
                s.getDate()
        );
    }
}

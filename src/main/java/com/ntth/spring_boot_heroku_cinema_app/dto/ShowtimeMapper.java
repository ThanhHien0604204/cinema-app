package com.ntth.spring_boot_heroku_cinema_app.dto;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import org.springframework.stereotype.Component;

@Component
public class ShowtimeMapper {
    public ShowtimeResponse toResponse(Showtime s) {
        return new ShowtimeResponse(
                s.getId(), s.getMovieId(), s.getRoomId(), s.getSessionName(),
                s.getStartAt(), s.getEndAt(),
                s.getPrice(), s.getTotalSeats(), s.getAvailableSeats()
        );
    }
}
package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ShowtimeRepository extends MongoRepository<Showtime, String> {
    //tìm theo ID phim và bắt đầu ở giữa
    Page<Showtime> findByMovieIdInAndStartAtBetween(
            Collection<String> movieIds, Instant from, Instant to, Pageable p);
    //tìm theo ID phòng và bắt đầu ở giữa
    Page<Showtime> findByRoomIdAndStartAtBetween(String roomId, Instant from, Instant to, Pageable p);

    //tìm Theo Bắt đầu Tại Giữa
    Page<Showtime> findByStartAtBetween(Instant from, Instant to, Pageable p);

    //Tìm Top10 Theo ID Phim Và Bắt Đầu Tại Sau Thứ Tự Theo Bắt Đầu Tại Tăng Dần
    List<Showtime> findTop10ByMovieIdAndStartAtAfterOrderByStartAtAsc(String movieId, Instant now);
}

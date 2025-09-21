package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ShowtimeRepository extends MongoRepository<Showtime, String> {
    List<Showtime> findByMovieId(String movieId);
    //tìm theo ID phim và bắt đầu ở giữa
    Page<Showtime> findByMovieIdInAndStartAtBetween(
            Collection<String> movieIds, Instant from, Instant to, Pageable p);
    //tìm theo ID phòng và bắt đầu ở giữa
    Page<Showtime> findByRoomIdAndStartAtBetween(String roomId, Instant from, Instant to, Pageable p);

    //tìm Theo Bắt đầu Tại Giữa
    Page<Showtime> findByStartAtBetween(Instant from, Instant to, Pageable p);

    //Tìm Top10 Theo ID Phim Và Bắt Đầu Tại Sau Thứ Tự Theo Bắt Đầu Tại Tăng Dần
    List<Showtime> findTop10ByMovieIdAndStartAtAfterOrderByStartAtAsc(String movieId, Instant now);

    // Lấy theo nhiều roomId
    List<Showtime> findByRoomIdIn(List<String> roomIds);

    // Thường ta cần lọc theo ngày: [date 00:00, date+1 00:00)
    @Query("{ 'roomId': { $in: ?0 }, 'startAt': { $gte: ?1, $lt: ?2 } }")
    List<Showtime> findByRoomIdInAndStartAtBetween(List<String> roomIds, Instant start, Instant end);

    // Trả toàn bộ showtime theo danh sách room + movie, sort theo startAt tăng dần
    List<Showtime> findByRoomIdInAndMovieIdOrderByStartAtAsc(Collection<String> roomIds, String movieId);

    // Lọc theo khoảng thời gian startAt (ví dụ theo 1 ngày)
    @Query("{ 'roomId': { $in: ?0 }, 'movieId': ?1, 'startAt': { $gte: ?2, $lt: ?3 } }")
    List<Showtime> findByRoomIdInAndMovieIdAndStartAtBetweenOrderByStartAtAsc(
            Collection<String> roomIds, String movieId, Instant start, Instant end
    );
}

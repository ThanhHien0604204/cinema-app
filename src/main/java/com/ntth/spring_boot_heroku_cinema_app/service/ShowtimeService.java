package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.dto.CreateShowtimeRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ShowtimeMapper;
import com.ntth.spring_boot_heroku_cinema_app.dto.ShowtimeResponse;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.ShowtimeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ShowtimeService {
    @Autowired
    private ShowtimeRepository showtimeRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private ShowtimeMapper mapper;
    @Autowired
    private MongoTemplate mongo;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");  //múi giờ VN

//    public boolean reserveSeats(String showtimeId, int qty) {
//        Query q = new Query(Criteria.where("_id").is(showtimeId)
//                .and("availableSeats").gte(qty));
//        Update u = new Update().inc("availableSeats", -qty);
//        UpdateResult r = mongoTemplate.updateFirst(q, u, Showtime.class);
//        return r.getModifiedCount() == 1;
//    }

    public List<ShowtimeResponse> getAllShowtimes() {                     // sắp xếp theo thời gian
        return showtimeRepository.findAll(Sort.by("startAt").ascending())
                .stream()
                .map(mapper::toResponse)
                .toList();
    }


    Instant toInstant(LocalDate day, String hhmm, ZoneId zone) {
        LocalTime t = LocalTime.parse(hhmm);         // "19:39"
        return ZonedDateTime.of(day, t, zone).toInstant();
    }

    Instant buildStartAt(OffsetDateTime startDayIso, String startTime) {
        LocalDate dayVN = startDayIso.atZoneSameInstant(VN).toLocalDate();
        return toInstant(dayVN, startTime, VN);
    }

    Instant buildEndAt(OffsetDateTime startDayIso, String endTime) {
        LocalDate dayVN = startDayIso.atZoneSameInstant(VN).toLocalDate();
        return toInstant(dayVN, endTime, VN);
    }

    public Showtime createShowtime(@Valid CreateShowtimeRequest r) {
        // 1. Lấy phim từ DB
        Movie movie = movieRepository.findById(r.movieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
        // 2. Tạo thời gian bắt đầu và kết thúc
        LocalTime t = LocalTime.parse(r.startTime());         // "18:00"
        Instant startAt = ZonedDateTime.of(r.date(), t, VN).toInstant();
        Instant endAt = startAt.plus(movie.getDurationMinutes(), ChronoUnit.MINUTES);
        // 3. Tạo và gán giá trị cho Showtime
        Showtime s = new Showtime();
        s.setMovieId(r.movieId());
        s.setRoomId(r.roomId());
        s.setSessionName(r.sessionName());
        s.setStartAt(startAt);
        s.setEndAt(endAt);
        s.setPrice(r.price());
        s.setTotalSeats(r.totalSeats());
        s.setAvailableSeats(r.availableSeats());

        // 4 RÀNG BUỘC: không cho trùng khung giờ cùng roomId
        // overlap khi (startAt < newEndAt) && (endAt > newStartAt)
        boolean overlap = mongo.exists(
                new Query(new Criteria().andOperator(
                        Criteria.where("roomId").is(s.getRoomId()),
                        Criteria.where("startAt").lt(s.getEndAt()),
                        Criteria.where("endAt").gt(s.getStartAt())
                )),
                Showtime.class
        );
        if (overlap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room time overlapped");
        }
        // 5. Lưu vào DB
        return showtimeRepository.save(s);
    }

    public Showtime updateShowtime(String id, @Valid Showtime s) {
        var current = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));

        // cập nhật field
        current.setMovieId(s.getMovieId());
        current.setRoomId(s.getRoomId());
        current.setSessionName(s.getSessionName());
        current.setStartAt(s.getStartAt());
        current.setEndAt(s.getEndAt());
        current.setPrice(s.getPrice());
        current.setTotalSeats(s.getTotalSeats());
        current.setAvailableSeats(s.getAvailableSeats());

        // tính endAt nếu cần
        if (current.getEndAt() == null) {
            var movie = movieRepository.findById(current.getMovieId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
            current.setEndAt(current.getStartAt().plus(movie.getDurationMinutes(), java.time.temporal.ChronoUnit.MINUTES));
        }
        if (!current.getEndAt().isAfter(current.getStartAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt");
        }

        // kiểm tra overlap, NHƯNG loại trừ chính showtime đang update
        boolean overlap = mongo.exists(
                new Query(new Criteria().andOperator(
                        Criteria.where("roomId").is(current.getRoomId()),
                        Criteria.where("startAt").lt(current.getEndAt()),
                        Criteria.where("endAt").gt(current.getStartAt()),
                        Criteria.where("_id").ne(current.getId())
                )),
                Showtime.class
        );
        if (overlap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room time overlapped");
        }

        return showtimeRepository.save(current);
    }

}

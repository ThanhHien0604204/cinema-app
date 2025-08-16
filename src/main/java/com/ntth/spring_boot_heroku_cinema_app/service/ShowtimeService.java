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

    public List<ShowtimeResponse> getAllShowtimes() {                     // sắp xếp theo thời gian
        return showtimeRepository.findAll(Sort.by("startAt").ascending())
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

//    Instant toInstant(LocalDate day, String hhmm, ZoneId zone) {
//        LocalTime t = LocalTime.parse(hhmm);         // "19:39"
//        return ZonedDateTime.of(day, t, zone).toInstant();
//    }
//
//    Instant buildStartAt(OffsetDateTime startDayIso, String startTime) {
//        LocalDate dayVN = startDayIso.atZoneSameInstant(VN).toLocalDate();
//        return toInstant(dayVN, startTime, VN);
//    }
//
//    Instant buildEndAt(OffsetDateTime startDayIso, String endTime) {
//        LocalDate dayVN = startDayIso.atZoneSameInstant(VN).toLocalDate();
//        return toInstant(dayVN, endTime, VN);
//    }

    public Showtime createShowtime(@Valid CreateShowtimeRequest r) {
        System.out.println("Đã nhận được yêu cầu: " + r);
        // 1. Lấy phim từ DB
        Movie movie = movieRepository.findById(r.movieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim"));
        System.out.println("Found movie: " + movie);
        // 2. Parse giờ bắt đầu và kết thúc từ request
        LocalTime startTime = parseTime(r.startTime()); // "14:00"
        LocalTime endTime = parseTime(r.endTime());     // "17:45"

        // 3. Tạo Instant từ date + giờ, múi giờ VN
        Instant startAt = ZonedDateTime.of(r.date(), startTime, VN).toInstant();
        Instant endAt = ZonedDateTime.of(r.date(), endTime, VN).toInstant();
        // 4. Kiểm tra endAt phải lớn hơn startAt
        if (!endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime phải sau startTime");
        }
        // 5. Tạo và gán giá trị cho Showtime
        Showtime s = new Showtime();
        s.setMovieId(r.movieId());
        s.setRoomId(r.roomId());
        s.setSessionName(r.sessionName());
        s.setStartAt(startAt);
        s.setEndAt(endAt);
        s.setPrice(r.price());
        s.setTotalSeats(r.totalSeats());
        s.setAvailableSeats(r.availableSeats());
        s.setDate(r.date());

        // 6 RÀNG BUỘC: không cho trùng khung giờ cùng roomId
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thời gian phòng chồng chéo");
        }
        // 5. Lưu vào DB
        return showtimeRepository.save(s);
    }
    // Phương thức parseTime để xử lý "HH:mm"
    private LocalTime parseTime(String timeStr) {
        try {
            return LocalTime.parse(timeStr); // Định dạng kỳ vọng: "HH:mm"
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time format. Use HH:mm (e.g., 14:00)");
        }
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
        current.setDate(s.getDate());

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

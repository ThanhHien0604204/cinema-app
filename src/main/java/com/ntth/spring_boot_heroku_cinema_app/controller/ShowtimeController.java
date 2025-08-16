package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.dto.CreateShowtimeRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ShowtimeResponse;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.ShowtimeRepository;
import com.ntth.spring_boot_heroku_cinema_app.service.ShowtimeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {
    @Autowired
    private ShowtimeRepository showtimeRepository;
    @Autowired
    private ShowtimeService showtimeService;
    @Autowired
    private MovieRepository movieRepository;

    // Danh sách lịch chiếu theo khoảng ngày (UTC)
//    @GetMapping
//    public Page<Showtime> list(
//            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
//            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
//            @RequestParam(defaultValue="0") int page,
//            @RequestParam(defaultValue="10") int size) {
//        return showtimeRepository.findByStartAtBetween(from, to, PageRequest.of(page, size, Sort.by("startAt").ascending()));
//    }
    @GetMapping
    public List<ShowtimeResponse> getAllShowtimes() {
        return showtimeService.getAllShowtimes();
    }
    // Lịch chiếu theo phim trong ngày (dùng ngày địa phương)
    @GetMapping("/by-movie/{movieTitle}")
    public Page<Showtime> byMovieTitleAndDate(
            @PathVariable String movieTitle,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "Asia/Ho_Chi_Minh") String zone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ZoneId z = ZoneId.of(zone);
        Instant from = date.atStartOfDay(z).toInstant();
        Instant to   = date.plusDays(1).atStartOfDay(z).toInstant();

        // 1) Tìm movie theo tiêu đề (cho phép chứa từ khóa, không phân biệt hoa/thường)
        String safeRegex = ".*" + java.util.regex.Pattern.quote(movieTitle) + ".*";
        List<Movie> movies = movieRepository.findByTitleRegexIgnoreCase(safeRegex);

        if (movies.isEmpty()) {
            // Không có phim khớp tên -> trả trang rỗng
            return Page.empty(PageRequest.of(page, size));
        }

        // 2) Lấy danh sách id phim
        List<String> movieIds = movies.stream().map(Movie::getId).toList();

        // 3) Truy vấn showtime theo nhiều movieId trong khoảng ngày
        return showtimeRepository.findByMovieIdInAndStartAtBetween(
                movieIds, from, to, PageRequest.of(page, size, Sort.by("startAt").ascending()));
    }

    @GetMapping("/{id}")
    public Showtime get(@PathVariable String id) {
        return showtimeRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public Showtime create(@Valid @RequestBody CreateShowtimeRequest dto) {
        return showtimeService.createShowtime(dto);
    }

    @PutMapping("/{id}")
    public Showtime update(@PathVariable String id, @RequestBody @Valid Showtime s) {
        return showtimeService.updateShowtime(id, s);
    }
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        showtimeRepository.deleteById(id); }
}


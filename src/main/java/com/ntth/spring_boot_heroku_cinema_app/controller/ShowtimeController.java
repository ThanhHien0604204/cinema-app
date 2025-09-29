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
import java.time.format.DateTimeParseException;
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

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

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
    /**
     * GET /api/showtimes/{cinemaId}/showtimes?date=2025-08-25
     * date: (optional) lọc trong ngày; nếu bỏ qua sẽ trả tất cả
     */
    @GetMapping("/{cinemaId}/showtimes")
    public List<ShowtimeResponse> getByCinema(
            @PathVariable String cinemaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        System.out.println("CinemaId: " + cinemaId + ", Date: " + date);
        return showtimeService.getShowtimesByCinema(cinemaId, date);
    }
    /**
     * GET /api/showtimes/{cinemaId}/movies/{movieId}/showtimes?date=2025-08-25
     * - Lấy showtime theo rạp và theo phim
     * - Sort theo startAt (tăng dần)
     * - Nếu truyền ?date=YYYY-MM-DD → lọc trong ngày đó (theo Asia/Ho_Chi_Minh)
     */
    @GetMapping("/cinemas/{cinemaId}/movies/{movieId}/showtimes")
    public List<ShowtimeResponse> getByCinemaAndMovie(
            @PathVariable String cinemaId,
            @PathVariable String movieId,
            @RequestParam(name = "date", required = false) String dateStr
    ) {
        try {
            LocalDate date = null;
            if (dateStr != null && !dateStr.isBlank()) {
                String cleanedDateStr = dateStr.replaceAll("[\\n\\r\\t]", "").trim();
                date = LocalDate.parse(cleanedDateStr);
            }
            System.out.println("Calling service with cinemaId: " + cinemaId + ", movieId: " + movieId + ", date: " + date);
            return showtimeService.getByCinemaAndMovie(cinemaId, movieId, date);
        } catch (Exception e) {
            e.printStackTrace(); // Log để debug
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi server: " + e.getMessage());
        }
    }
    //showtime details
    @GetMapping("/{id}")
    public Showtime get(@PathVariable String id) {
        return showtimeRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // ADMIN
    @PostMapping
    public ShowtimeResponse create(@Valid @RequestBody CreateShowtimeRequest dto) {
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


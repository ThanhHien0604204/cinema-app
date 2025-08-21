package com.ntth.spring_boot_heroku_cinema_app.controller;

import java.time.LocalDate;
import java.util.List;

import com.ntth.spring_boot_heroku_cinema_app.dto.MovieRequest;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieRepository;
import com.ntth.spring_boot_heroku_cinema_app.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    @Autowired
    private MovieService movieService;
    @Autowired // Tiêm repository để truy vấn dữ liệu từ cơ sở dữ liệu
    private final MovieRepository repo;

    public MovieController(MovieService service, MovieRepository repo) {
        this.movieService = service;
        this.repo = repo;
    }
    // GET /api/movies
    @GetMapping
    public List<Movie> getAllMovies() {
        return movieService.getAllMovies();
    }
    @GetMapping("/hot") ///lấy danh sách các phim "hot" (dựa trên số lượt xem - views)
    // Sử dụng PageRequest để giới hạn số lượng bản ghi trả về,limit Số lượng phim tối đa trả về, mặc định là 10
    public List<Movie> hot(@RequestParam(defaultValue = "10") int limit) {
        return repo.findTopByOrderByViewsDesc(PageRequest.of(0, limit));//Trang đầu tiên (0) với số lượng bản ghi là limit
    }

    // tăng số lượt xem (views) khi người dùng xem chi tiết
    @PostMapping("/{id}/view")
    public void incView(@PathVariable String id) {
        // Tìm phim theo ID, nếu tồn tại thì thực hiện tăng lượt xem
        repo.findById(id).ifPresent(m -> {
            m.setViews((m.getViews()==null?0//gán giá trị mặc định là 0
                    :m.getViews()) + 1);
            repo.save(m);
        });
    }

    // GET /api/movies/{id}
    @GetMapping("/{id}")
    public Movie get(@PathVariable String id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
    }

    // GET /api/movies/upcoming?from=2025-08-01&to=2025-09-30
    @GetMapping("/upcoming")
    public List<Movie> upcoming(@RequestParam LocalDate from,
                                @RequestParam LocalDate to) {
        return repo.findByMovieDateStartBetween(from, to);
    }

    // POST /api/movies
    @PostMapping
    public ResponseEntity<Movie> create(@Valid @RequestBody MovieRequest req) {
        return ResponseEntity.ok(movieService.create(req));
    }

    // PUT /api/movies/{id}
    @PutMapping("/{id}")
    public Movie update(@PathVariable String id, @Valid @RequestBody MovieRequest req) {
        return movieService.update(id, req);
    }

    // DELETE /api/movies/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Search nhẹ nhàng theo tham số tuỳ chọn
    @GetMapping("/search")
    public Page<Movie> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("movieDateStart").descending());

        if (q != null && !q.isBlank()) {
            return repo.findByTitleRegexIgnoreCase(q, pageable);
        }
        if (genre != null && !genre.isBlank()) {
            return movieService.list(null, genre, page, size); // Sử dụng service logic
        }
        if (minRating != null) {
            return repo.findByRatingGreaterThanEqual(minRating, pageable);
        }
        if (from != null || to != null) {
            LocalDate start = (from != null) ? from : LocalDate.MIN;
            LocalDate end   = (to != null) ? to : LocalDate.MAX;
            return repo.findByMovieDateStartBetween(start, end, pageable);
        }
        return repo.findAll(pageable);
    }
}
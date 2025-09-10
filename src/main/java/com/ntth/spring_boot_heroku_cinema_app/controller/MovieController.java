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
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional
    @PostMapping("/{id}/view")
    public ResponseEntity<String> incView(@PathVariable String id) {
        return repo.findById(id)
                .map(movie -> {
                    Long currentViews = movie.getViews() != null ? movie.getViews() : 0L;
                    movie.setViews(currentViews + 1);
                    repo.save(movie);
                    return ResponseEntity.ok("Số lượt xem đã tăng thành công");
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET /api/movies/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Movie> get(@PathVariable String id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // GET /api/movies/upcoming?from=2025-08-01&to=2025-09-30
    @GetMapping("/upcoming")
    public List<Movie> upcoming(@RequestParam LocalDate from,
                                @RequestParam LocalDate to) {
        return repo.findByMovieDateStartBetween(from, to);
    }

    //ADMIN
    // POST /api/movies
    @PostMapping
    public ResponseEntity<Movie> create(@Valid @RequestBody MovieRequest req) {
        return ResponseEntity.ok(movieService.create(req));
    }

    // PUT /api/movies/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Movie> update(@PathVariable String id, @Valid @RequestBody MovieRequest req) {
        try {
            Movie updatedMovie = movieService.update(id, req);
            return ResponseEntity.ok(updatedMovie);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // DELETE /api/movies/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (repo.existsById(id)) {
            repo.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Search nhẹ nhàng theo tham số tuỳ chọn
    @GetMapping("/searchs")
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
    // ===== (A) API: Tìm theo thể loại =====
    // GET /api/movies/search-by-genre?genreId=...&page=0&size=12
    @GetMapping("/search-by-genre")
    @ResponseStatus(HttpStatus.OK)
    public Page<Movie> searchByGenre(
            @RequestParam String genreId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return movieService.searchByGenreId(genreId, page, size);
    }

    // GET /api/movies/search?q=...&page=0&size=12
    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    public Page<Movie> search(
            @RequestParam(name = "q") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return movieService.smartSearch(keyword, page, size);
    }
}
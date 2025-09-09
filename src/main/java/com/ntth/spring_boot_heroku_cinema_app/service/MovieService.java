package com.ntth.spring_boot_heroku_cinema_app.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ntth.spring_boot_heroku_cinema_app.dto.MovieRequest;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Genre;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
import com.ntth.spring_boot_heroku_cinema_app.pojo.MovieGenre;
import com.ntth.spring_boot_heroku_cinema_app.repository.GenreRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieGenreRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class MovieService {
    @Autowired
    private MovieRepository repo;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private MovieGenreRepository movieGenreRepository;
    public MovieService(MovieRepository repo) { this.repo = repo; }

    public Page<Movie> list(String q, String genre, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("movieDateStart").descending());
        if (q != null && !q.isBlank()) {
            return repo.findByTitleRegexIgnoreCase(".*" + q + ".*", pageable);
        }
        if (genre != null && !genre.isBlank()) {
            // Tìm ID của thể loại dựa trên tên
            Genre foundGenre = genreRepository.findByNameIgnoreCase(genre);
            if (foundGenre != null) {
                return repo.findByGenreIdsContaining(foundGenre.getId(), pageable);
            } else {
                return Page.empty(pageable); // Trả về trang trống nếu không tìm thấy thể loại
            }
        }
        return repo.findAll(pageable);
    }
    // ===== (A) Lọc theo thể loại =====
    public Page<Movie> searchByGenreId(String genreId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "movieDateStart"));
        return repo.findByGenreIdsContains(genreId, pageable);
    }

    // ===== B) Smart search: title trước, fallback author/actors =====
    public Page<Movie> smartSearch(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "movieDateStart"));
        String safe = Pattern.quote(keyword);

        // 1) by title
        Query q1 = new Query(Criteria.where("title").regex(safe, "i")).with(pageable);
        List<Movie> first = mongo.find(q1, Movie.class);
        long c1 = mongo.count(Query.of(q1).limit(-1).skip(-1), Movie.class);
        if (c1 > 0) return new PageImpl<>(first, pageable, c1);

        // 2) fallback by people: author OR actors (List<String>)
        Criteria byAuthor = Criteria.where("author").regex(safe, "i");
        Criteria byActors = Criteria.where("actors").elemMatch(Criteria.where("$regex").is(safe).and("$options").is("i"));
        Query q2 = new Query(new Criteria().orOperator(byAuthor, byActors)).with(pageable);
        List<Movie> data = mongo.find(q2, Movie.class);
        long total = mongo.count(Query.of(q2).limit(-1).skip(-1), Movie.class);
        return new PageImpl<>(data, pageable, total);
    }

    public List<Movie> getAllMovies() {
        return repo.findAll();
    }
    public Movie create(MovieRequest r) {
        Movie m = new Movie();
        m.setTitle(r.title());
        m.setImageUrl(r.imageUrl());
        m.setDurationMinutes(r.durationMinutes());
        m.setMovieDateStart(r.movieDateStart());
        m.setRating(r.rating());
        m.setSummary(r.summary());
        m.setTrailerUrl(r.trailerUrl());
        m.setAuthor(r.author());
        m.setActors(r.actors());
        m.setViews(r.views() != null ? r.views() : 0L); // Đảm bảo views không null

        // Lưu Movie trước để lấy ID
        Movie savedMovie = repo.save(m);

        // Tra cứu và gán genreIds từ danh sách tên thể loại
        List<String> genreIds = r.genre().stream()
                .map(genreName -> {
                    // Tìm hoặc tạo Genre nếu chưa tồn tại
                    Genre genre = genreRepository.findByNameIgnoreCase(genreName);
                    if (genre == null) {
                        Genre newGenre = new Genre();
                        newGenre.setName(genreName);
                        genre = genreRepository.save(newGenre);
                    }
                    return genre.getId();
                })
                .collect(Collectors.toList());

        m.setGenreIds(genreIds);

        // Lưu lại Movie với genreIds
        Movie updatedMovie = repo.save(m);

        // Lưu mối quan hệ vào movie_genres
        List<MovieGenre> movieGenres = genreIds.stream().map(genreId -> {
            MovieGenre mg = new MovieGenre();
            mg.setMovieId(updatedMovie.getId());
            mg.setGenreId(genreId);
            return mg;
        }).collect(Collectors.toList());

        movieGenreRepository.saveAll(movieGenres);

        return updatedMovie;
    }

    public Movie update(String id, MovieRequest r) {
        Movie m = repo.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy phim"));
        m.setTitle(r.title());
        m.setImageUrl(r.imageUrl());
        m.setDurationMinutes(r.durationMinutes());
        m.setMovieDateStart(r.movieDateStart());
        m.setRating(r.rating());
        m.setSummary(r.summary());
        m.setTrailerUrl(r.trailerUrl());
        m.setAuthor(r.author());
        m.setActors(r.actors());
        m.setViews(r.views() != null ? r.views() : m.getViews()); // Giữ giá trị cũ nếu views null

        // Cập nhật genreIds
        List<String> genreIds = r.genre().stream()
                .map(genreName -> {
                    Genre genre = genreRepository.findByNameIgnoreCase(genreName);
                    if (genre == null) {
                        Genre newGenre = new Genre();
                        newGenre.setName(genreName);
                        genre = genreRepository.save(newGenre);
                    }
                    return genre.getId();
                })
                .collect(Collectors.toList());

        m.setGenreIds(genreIds);

        // Lưu lại Movie
        Movie updatedMovie = repo.save(m);

        // Cập nhật mối quan hệ trong movie_genres
        movieGenreRepository.deleteByMovieId(id); // Xóa các mối quan hệ cũ
        List<MovieGenre> movieGenres = genreIds.stream().map(genreId -> {
            MovieGenre mg = new MovieGenre();
            mg.setMovieId(updatedMovie.getId());
            mg.setGenreId(genreId);
            return mg;
        }).collect(Collectors.toList());

        movieGenreRepository.saveAll(movieGenres);

        return updatedMovie;
    }
    public void delete(String id) { repo.deleteById(id); }
}

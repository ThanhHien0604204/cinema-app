package com.ntth.spring_boot_heroku_cinema_app.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ntth.spring_boot_heroku_cinema_app.dto.MovieRequest;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Genre;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
import com.ntth.spring_boot_heroku_cinema_app.pojo.MovieGenre;
import com.ntth.spring_boot_heroku_cinema_app.repository.GenreRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieGenreRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
@RequiredArgsConstructor
public class MovieService {
    @Autowired
    private MovieRepository repo;
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

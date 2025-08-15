package com.ntth.spring_boot_heroku_cinema_app.service;

import java.time.LocalDate;
import java.util.List;

import com.ntth.spring_boot_heroku_cinema_app.dto.MovieRequest;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
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

    public MovieService(MovieRepository repo) { this.repo = repo; }

    public Page<Movie> list(String q, String genre, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("movieDateStart").descending());
        if (q != null && !q.isBlank())
            return repo.findByTitleRegexIgnoreCase(".*" + q + ".*", pageable);
        if (genre != null && !genre.isBlank())
            return repo.findByGenreIgnoreCase(genre, pageable);
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
        m.setGenre(r.genre());
        m.setMovieDateStart(r.movieDateStart());
        m.setRating(r.rating());
        m.setSummary(r.summary());
        m.setTrailerUrl(r.trailerUrl());
        return repo.save(m);
    }

    public Movie update(String id, MovieRequest r) {
        Movie m = repo.findById(id).orElseThrow();
        m.setTitle(r.title());
        m.setImageUrl(r.imageUrl());
        m.setDurationMinutes(r.durationMinutes());
        m.setGenre(r.genre());
        m.setMovieDateStart(r.movieDateStart());
        m.setRating(r.rating());
        m.setSummary(r.summary());
        m.setTrailerUrl(r.trailerUrl());
        return repo.save(m);
    }
    public void delete(String id) { repo.deleteById(id); }
}

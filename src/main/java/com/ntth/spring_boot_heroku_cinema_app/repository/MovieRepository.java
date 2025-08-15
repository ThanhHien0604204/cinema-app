package com.ntth.spring_boot_heroku_cinema_app.repository;

import java.time.LocalDate;
import java.util.List;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovieRepository extends MongoRepository<Movie, String> {

    // search theo title (regex, ignore case)
    Page<Movie> findByTitleRegexIgnoreCase(String titlePattern, Pageable pageable);

    // filter theo thể loại
    Page<Movie> findByGenreIgnoreCase(String genre, Pageable pageable);

    // phim sắp chiếu trong khoảng ngày
    List<Movie> findByMovieDateStartBetween(LocalDate from, LocalDate to);
    Page<Movie> findByMovieDateStartBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<Movie> findByRatingGreaterThanEqual(Double minRating, Pageable pageable);
}

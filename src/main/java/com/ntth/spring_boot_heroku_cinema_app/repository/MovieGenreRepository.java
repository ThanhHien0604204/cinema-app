package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.MovieGenre;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovieGenreRepository extends MongoRepository<MovieGenre, String> {
    void deleteByMovieId(String movieId);
}

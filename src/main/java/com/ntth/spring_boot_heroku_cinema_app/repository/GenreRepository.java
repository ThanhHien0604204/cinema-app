package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Genre;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GenreRepository extends MongoRepository<Genre, String> {
    Genre findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Genre> findByNameRegexIgnoreCase(String regex); // search đơn giản
}
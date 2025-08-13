package com.ntth.spring_boot_heroku_cinema_app.pojo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "movie")
public class Movie {
    @Id
    private String id;

    private String title;
    private String summary;
    private String duration;
    private String genre;
    private String imageUrl;
    private String rating;
    private String trailerUrl;
//    private List<Showtime> showtimes;
}
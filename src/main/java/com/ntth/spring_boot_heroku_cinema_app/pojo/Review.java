package com.ntth.spring_boot_heroku_cinema_app.pojo;

import jakarta.validation.constraints.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("review")
public class Review {
    @Id
    private String id;

    @Indexed
    private String movieId;             // query theo movie

    @Indexed private String userId;     // query theo user

    @DecimalMin("1.0") @DecimalMax("5.0")
    private double rating;

    @Size(max = 2000)
    private String content;

    private Instant reviewTime;

    public Review() {
    }

    public Review(String movieId, String userId, double rating, String content) {
        this.movieId = movieId;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.reviewTime = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = movieId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(Instant reviewTime) {
        this.reviewTime = reviewTime;
    }
}


package com.ntth.spring_boot_heroku_cinema_app.pojo;


import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.*;

@Document("movie")
public class Movie {
    @Id
    private String id;              // map từ ObjectId → String tự động

    @NotBlank
    @Indexed // tạo index cho tìm kiếm theo title
    private String title;

    @NotBlank
    private String imageUrl;

    @Positive
    private Integer durationMinutes; // chuẩn hoá 99 thay vì "99 phút"

    @NotBlank
    @Indexed                          // lọc theo thể loại nhanh
    private String genre;

    @NotNull
    private LocalDate movieDateStart;

    @DecimalMin("0.0") @DecimalMax("10.0")
    private Double rating;

    @NotBlank
    private String summary;

    private String trailerUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public LocalDate getMovieDateStart() {
        return movieDateStart;
    }

    public void setMovieDateStart(LocalDate movieDateStart) {
        this.movieDateStart = movieDateStart;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl;
    }
}

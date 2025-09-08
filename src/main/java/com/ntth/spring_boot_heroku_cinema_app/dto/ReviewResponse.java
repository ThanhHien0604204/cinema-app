package com.ntth.spring_boot_heroku_cinema_app.dto;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Review;

import java.time.Instant;

public record ReviewResponse(
        String id, String movieId, String userId,
        int rating, String content, Instant reviewTime
) {
    public static ReviewResponse of(Review r) {
        return new ReviewResponse(r.getId(), r.getMovieId(), r.getUserId(),
                r.getRating(), r.getContent(), r.getReviewTime());
    }
}

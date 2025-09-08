package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.dto.ReviewResponse;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Review;
import com.ntth.spring_boot_heroku_cinema_app.repository.MovieRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class ReviewService {
    @Autowired
    private ReviewRepository repo;

    // Tạo mới hoặc cập nhật review của chính user cho movie
    public ReviewResponse upsert(String movieId, String userId, int rating, String content) {
        Review r = repo.findByMovieIdAndUserId(movieId, userId).orElse(null);
        if (r == null) {
            r = new Review(movieId, userId, rating, content);
        } else {
            r.setRating(rating);
            r.setContent(content);
            r.setReviewTime(Instant.now());
        }
        Review saved = repo.save(r);
        return ReviewResponse.of(saved);
    }

    public void delete(String id, String userId) {
        Review r = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!r.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        repo.delete(r);
        // Không cache -> không cần update gì thêm
    }
}

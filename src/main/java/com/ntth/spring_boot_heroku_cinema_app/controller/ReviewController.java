package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.dto.MovieRatingSummary;
import com.ntth.spring_boot_heroku_cinema_app.dto.ReviewRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ReviewResponse;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Review;
import com.ntth.spring_boot_heroku_cinema_app.repository.ReviewRepository;
import com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails;
import com.ntth.spring_boot_heroku_cinema_app.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired private ReviewService reviewService;
    @Autowired private ReviewRepository reviewRepo;

    // Upsert review của CHÍNH TÔI cho 1 movie
    @PostMapping
    public ReviewResponse upsert(@Valid @RequestBody ReviewRequest req,
                                 @AuthenticationPrincipal CustomUserDetails me) {
        String userId = me.getUser().getId();      // <--- lấy từ JWT
        return reviewService.upsert(
                req.movieId(),                     // <-- record accessor
                userId,
                req.rating(),                      // <-- record accessor
                req.content()                      // <-- record accessor
        );
    }
    // 2) Lấy review của CHÍNH TÔI - PHÂN TRANG
    @GetMapping("/me")
    public Page<ReviewResponse> myReviews(@AuthenticationPrincipal CustomUserDetails me,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        String userId = me.getUser().getId(); // lấy từ JWT
        Page<Review> p = reviewRepo.findByUserIdOrderByReviewTimeDesc(userId, PageRequest.of(page, size));
        return p.map(ReviewResponse::of);
    }

    // Lấy review của CHÍNH TÔI cho 1 movie
    @GetMapping("/movie/{movieId}/me")
    public ResponseEntity<ReviewResponse> myReview(@PathVariable String movieId,
                                                   @AuthenticationPrincipal CustomUserDetails me) {
        String userId = me.getUser().getId();                 // <--- lấy từ JWT
        return reviewRepo.findByMovieIdAndUserId(movieId, userId)
                .map(ReviewResponse::of).map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // Xoá review của CHÍNH TÔI
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @AuthenticationPrincipal CustomUserDetails me) {
        String userId = me.getUser().getId();                 // <--- lấy từ JWT
        reviewService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    // Danh sách review theo movie (public)
    @GetMapping("/movie/{movieId}")
    public Page<ReviewResponse> listByMovie(@PathVariable String movieId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return reviewRepo.findByMovieIdOrderByReviewTimeDesc(movieId, PageRequest.of(page, size))
                .map(ReviewResponse::of);
    }

    // Tóm tắt rating (Trung bình đánh giá(avg) + Số lượng đánh giá (count))) theo movie (public)
    @GetMapping("/movie/{movieId}/summary")
//    @PreAuthorize("hasRole('ADMIN')")
    public MovieRatingSummary summary(@PathVariable String movieId) {
        var st = reviewRepo.aggregateStatsByMovie(movieId);
        double avg = st.map(s -> s.getAvgRating() == null ? 0d : s.getAvgRating()).orElse(0d);
        int cnt    = st.map(s -> s.getReviewCount() == null ? 0  : s.getReviewCount()).orElse(0);
        return new MovieRatingSummary(movieId, avg, cnt);
    }

}
package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.dto.MovieRatingSummary;
import com.ntth.spring_boot_heroku_cinema_app.dto.ReviewRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ReviewResponse;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Review;
import com.ntth.spring_boot_heroku_cinema_app.repository.ReviewRepository;
import com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails;
import com.ntth.spring_boot_heroku_cinema_app.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ReviewRepository reviewRepo;

    // Lấy review của CHÍNH TÔI - PHÂN TRANG
    @GetMapping("/me")
    public ResponseEntity<Page<ReviewResponse>> myReviews(
            @AuthenticationPrincipal JwtUser me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = me.getUserId(); // Lấy userId từ JwtUser
        Page<Review> p = reviewRepo.findByUserIdOrderByReviewTimeDesc(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reviewTime")));

        Page<ReviewResponse> responsePage = p.map(ReviewResponse::of);
        return ResponseEntity.ok(responsePage);
    }

    // Upsert review của CHÍNH TÔI cho 1 movie
    @PostMapping
    public ReviewResponse upsert(@Valid @RequestBody ReviewRequest req,
                                 @AuthenticationPrincipal JwtUser me) {
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        String userId = me.getUserId(); // lấy từ JwtUser
        return reviewService.upsert(
                req.movieId(),
                userId,
                req.rating(),
                req.content()
        );
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
    public MovieRatingSummary summary(@PathVariable String movieId) {
        var list = reviewRepo.findByMovieId(movieId);
        if (list.isEmpty()) return new MovieRatingSummary(movieId, 0d, 0);
        double avg = list.stream().mapToDouble(Review::getRating).average().orElse(0d);
        return new MovieRatingSummary(movieId, avg, list.size());
    }


}
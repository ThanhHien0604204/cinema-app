package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {
    Page<Review> findByMovieIdOrderByReviewTimeDesc(String movieId, Pageable pageable);

    Optional<Review> findByMovieIdAndUserId(String movieId, String userId);

    // Tính trung bình + đếm theo movieId (movieId đang là String trong collection "review")
    @Aggregation(pipeline = {
            "{ $match: { movieId: ?0 } }",
            "{ $group: { _id: '$movieId', avgRating: { $avg: '$rating' }, reviewCount: { $sum: 1 } } }"
    })
    Optional<RatingStats> aggregateStatsByMovie(String movieId);

    interface RatingStats {
        Double getAvgRating();
        Integer getReviewCount();
    }

    Page<Review> findByUserIdOrderByReviewTimeDesc(String userId, Pageable pageable);

    List<Review> findByMovieId(String movieId);
}


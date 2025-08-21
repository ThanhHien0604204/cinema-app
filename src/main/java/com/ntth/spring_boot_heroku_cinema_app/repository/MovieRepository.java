package com.ntth.spring_boot_heroku_cinema_app.repository;

import java.time.LocalDate;
import java.util.List;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovieRepository extends MongoRepository<Movie, String> {

    // search theo title (regex, ignore case)
    Page<Movie> findByTitleRegexIgnoreCase(String titlePattern, Pageable pageable);

    // filter theo thể loại
    //Page<Movie> findByGenreIgnoreCase(String genre, Pageable pageable);
    Page<Movie> findByGenreIdsContaining(String genreId, Pageable pageable);

    // phim sắp chiếu trong khoảng ngày
    List<Movie> findByMovieDateStartBetween(LocalDate from, LocalDate to);
    Page<Movie> findByMovieDateStartBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<Movie> findByRatingGreaterThanEqual(Double minRating, Pageable pageable);
    //tìm chứa từ khóa (khuyến nghị)
    List<Movie> findByTitleRegexIgnoreCase(String regex);

    List<Movie> findTop10ByOrderByViewsDesc();
    //lấy top phim dựa trên cột views, sắp xếp giảm dần
    List<Movie> findTopByOrderByViewsDesc(Pageable pageable);
    // Tùy chọn: Tìm phim theo danh sách genreIds
    Page<Movie> findByGenreIdsIn(List<String> genreIds, Pageable pageable);
}

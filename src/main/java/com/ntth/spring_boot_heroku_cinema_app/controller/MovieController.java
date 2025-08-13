//package com.ntth.spring_boot_heroku_cinema_app.controller;
//
//import com.ntth.spring_boot_heroku_cinema_app.pojo.Movie;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api")
//public class MovieController {
//    @GetMapping("/movies")
//    public List<Movie> getAllMovies() {
//        return movieRepository.findAll();
//    }
//
//    @GetMapping("/movies/{id}")
//    public ResponseEntity<Movie> getMovie(@PathVariable String id) {
//        return ResponseEntity.of(movieRepository.findById(id));
//    }
//}

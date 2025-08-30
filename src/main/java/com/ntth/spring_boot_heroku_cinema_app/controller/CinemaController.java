package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Cinema;
import com.ntth.spring_boot_heroku_cinema_app.repository.CinemaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CinemaController {

    @Autowired
    private CinemaRepository cinemaRepository;

    @GetMapping("/cinemas")
    public ResponseEntity<List<Cinema>> getAllCinemas() {
        List<Cinema> cinemas = cinemaRepository.findAll();
        return ResponseEntity.ok(cinemas);
    }
    @PostMapping("/cinemas")
    public ResponseEntity<Cinema> addCinema(@RequestBody Cinema cinema) {
        Cinema savedCinema = cinemaRepository.save(cinema);
        return ResponseEntity.ok(savedCinema);
    }
}

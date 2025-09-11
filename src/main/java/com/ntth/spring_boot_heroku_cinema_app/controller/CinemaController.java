package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Cinema;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Room;
import com.ntth.spring_boot_heroku_cinema_app.repository.CinemaRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/cinemas")
public class CinemaController {

    @Autowired
    private CinemaRepository cinemaRepository;
    @Autowired
    private RoomRepository roomRepository;

    public CinemaController(CinemaRepository cinemaRepository, RoomRepository roomRepository) {
        this.cinemaRepository = cinemaRepository;
        this.roomRepository = roomRepository;
    }
    @GetMapping
    public ResponseEntity<List<Cinema>> getAllCinemas() {
        List<Cinema> cinemas = cinemaRepository.findAll();
        return ResponseEntity.ok(cinemas);
    }
    // GET /api/cinemas/{id}
    @GetMapping("/{id}")
    public Cinema getCinemaId(@PathVariable String id) {
        return cinemaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cinema not found"));
    }

    // GET /api/cinemas/{cinemaId}/rooms
    @GetMapping("/{cinemaId}/rooms")
    public java.util.List<Room> getRoomsByCinema(@PathVariable String cinemaId) {
        java.util.List<Room> rooms = roomRepository.findByCinemaId(cinemaId);
        if (rooms == null || rooms.isEmpty()) {
            // có thể trả [] thay vì 404 tùy bạn; ở đây trả [] cho dễ dùng phía client
            return java.util.List.of();
        }
        return rooms;
    }
    @PostMapping("/cinemas")
    public ResponseEntity<Cinema> addCinema(@RequestBody Cinema cinema) {
        Cinema savedCinema = cinemaRepository.save(cinema);
        return ResponseEntity.ok(savedCinema);
    }
}

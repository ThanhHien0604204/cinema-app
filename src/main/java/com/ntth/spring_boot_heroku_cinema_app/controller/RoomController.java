package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Room;
import com.ntth.spring_boot_heroku_cinema_app.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    // GET /api/rooms
    @GetMapping
    public java.util.List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    // GET /api/rooms/{id}
    @GetMapping("/{id}")
    public Room getRoom(@PathVariable String id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }
}

package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Room;
import com.ntth.spring_boot_heroku_cinema_app.repository.RoomRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
@Validated
public class RoomController {

    private final RoomRepository roomRepository;

    @Autowired
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
    // ✅ POST /api/rooms
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room addRoom(@Valid @RequestBody Room room) {
        // Validate cinemaId exists (optional)
        if (room.getCinemaId() != null && !room.getCinemaId().isEmpty() &&
                !roomRepository.existsByCinemaId(room.getCinemaId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cinema with ID " + room.getCinemaId() + " does not exist");
        }

        // Validate totalSeats = rows * columns (optional)
        if (room.getTotalSeats() != room.getRows() * room.getColumns()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total seats must equal rows * columns");
        }

        return roomRepository.save(room);
    }

    // PUT /api/rooms/{id}
    @PutMapping("/{id}")
    public Room updateRoom(@PathVariable String id, @Valid @RequestBody Room room) {
        Room existing = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        // Cập nhật fields từ request
        existing.setRoomName(room.getRoomName());
        existing.setCinemaId(room.getCinemaId());
        existing.setTotalSeats(room.getTotalSeats());
        existing.setRows(room.getRows());
        existing.setColumns(room.getColumns());

        // Validate sau khi update
        if (existing.getTotalSeats() != existing.getRows() * existing.getColumns()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total seats must equal rows * columns");
        }

        return roomRepository.save(existing);
    }

    // DELETE /api/rooms/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@PathVariable String id) {
        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
        roomRepository.deleteById(id);
    }
}

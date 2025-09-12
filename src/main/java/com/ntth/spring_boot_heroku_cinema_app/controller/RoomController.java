package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Room;
import com.ntth.spring_boot_heroku_cinema_app.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
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
    public Room addRoom(@RequestBody Room room) {
        return roomRepository.save(room);
    }

    // PUT /api/rooms/{id}
    @PutMapping("/{id}")
    public Room updateRoom(@PathVariable String id, @RequestBody Room room) {
        Room existing = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        // cập nhật field từ room request
        existing.setRoomName(room.getRoomName());
        existing.setCinemaId(room.getCinemaId());
        existing.setTotalSeats(room.getTotalSeats());
        existing.setRows(room.getRows());
        existing.setColumns(room.getColumns());
        return roomRepository.save(existing);
    }

    // DELETE /api/rooms/{id}
    @DeleteMapping("/{id}")
    public void deleteRoom(@PathVariable String id) {
        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
        roomRepository.deleteById(id);
    }
}

package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLedger;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatLedgerRepository
        extends MongoRepository<SeatLedger, String>, SeatLedgerRepositoryCustom {

    // Find queries
    List<SeatLedger> findByShowtimeIdAndSeatIn(String showtimeId, List<String> seats);
    List<SeatLedger> findByShowtimeId(String showtimeId);
    Optional<SeatLedger> findById(String id); // id format: showtimeId + "#" + seat

    // Find seats by status
    @Query("{showtimeId: ?0, seatNumber: {$in: ?1}, status: ?2}")
    List<SeatLedger> findByShowtimeIdAndSeatsAndStatus(String showtimeId, List<String> seats, String status);

    // Find available seats
    @Query("{showtimeId: ?0, $or: [{status: 'FREE'}, {status: 'HOLD', expiresAt: {$lt: ?1}}]}")
    List<SeatLedger> findAvailableSeats(String showtimeId, Instant now);
}
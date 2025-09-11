package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLedger;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SeatLedgerRepository
        extends MongoRepository<SeatLedger, String>, SeatLedgerRepositoryCustom {

    List<SeatLedger> findByShowtimeIdAndSeatIn(String showtimeId, List<String> seats);
    List<SeatLedger> findByShowtimeId(String showtimeId);
    Optional<SeatLedger> findById(String id); // id format: showtimeId + "#" + seat
}
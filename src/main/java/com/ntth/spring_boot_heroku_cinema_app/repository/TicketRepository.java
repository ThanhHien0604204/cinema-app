package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TicketRepository extends MongoRepository<Ticket, String> {
    Optional<Ticket> findByBookingCode(String bookingCode);
}

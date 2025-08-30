package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLock;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeatLockRepository extends MongoRepository<SeatLock, String> {}

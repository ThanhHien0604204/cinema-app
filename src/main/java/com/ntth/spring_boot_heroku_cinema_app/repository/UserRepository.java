package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    //Tìm kiếm 1 user theo email
    Optional<User> findByEmail(String email);
    //Kiểm tra email đã tồn tại hay chưa
    boolean existsByEmail(String email);
}

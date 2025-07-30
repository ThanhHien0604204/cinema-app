package com.ntth.spring_boot_heroku_cinema_app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    @GetMapping("api/test")
    public ResponseEntity<?> testApi() {
        return ResponseEntity.ok("Kết nối Thành công!");
    }
}

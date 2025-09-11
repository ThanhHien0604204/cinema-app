package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.service.HoldService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/showtimes")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) { this.holdService = holdService; }

    @PostMapping("/{showtimeId}/hold")
    public ResponseEntity<Map<String,Object>> hold(@PathVariable String showtimeId,
                                                   @RequestBody Map<String, List<String>> body,
                                                   @AuthenticationPrincipal JwtUser user) {
        List<String> seats = body.get("seats");
        Map<String,Object> res = holdService.createHold(user.getUserId(), showtimeId, seats);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

}


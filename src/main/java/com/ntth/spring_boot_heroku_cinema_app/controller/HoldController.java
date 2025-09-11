package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.service.HoldService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/showtimes")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) { this.holdService = holdService; }

    @PostMapping("/{showtimeId}/hold")
    public ResponseEntity<Map<String,Object>> hold(
            @PathVariable String showtimeId,
            @RequestBody Map<String, List<String>> body,
            @AuthenticationPrincipal JwtUser user,   // có thể null
            Authentication authentication            // fallback
    ) {
        String userId = null;

        if (user != null) {
            userId = user.getUserId();
        } else if (authentication != null && authentication.isAuthenticated()) {
            // đa số trường hợp getName() là email
            String name = authentication.getName();
            // nếu Service của bạn cần userId, có 2 lựa chọn:
            // (A) nếu name đã là userId -> dùng luôn
            // (B) nếu name là email -> tra ra userId (giả sử có userRepo)
            // userId = userRepo.findByEmail(name).map(User::getId).orElseThrow(...);
            userId = name; // tạm thời, nếu service chấp nhận email như "userId"
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        List<String> seats = body.get("seats");
        Map<String,Object> res = holdService.createHold(userId, showtimeId, seats);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(res);
    }
    @GetMapping("/api/test_users/me")
    public Map<String,Object> me(@AuthenticationPrincipal JwtUser user,
                                 org.springframework.security.core.Authentication auth) {
        return Map.of(
                "principalType", auth == null ? null : auth.getPrincipal().getClass().getName(),
                "name", auth == null ? null : auth.getName(),
                "jwtUser", user
        );
    }

}


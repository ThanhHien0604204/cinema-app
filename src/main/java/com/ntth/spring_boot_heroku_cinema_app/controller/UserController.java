package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtProvider;
import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import com.ntth.spring_boot_heroku_cinema_app.dto.AuthRequest;
import com.ntth.spring_boot_heroku_cinema_app.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER");
        userRepo.save(user);
        return ResponseEntity.ok("Đăng ký thành công!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tồn tại email"));

        if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            String token = jwtProvider.generateToken(user.getEmail());
            return ResponseEntity.ok(Map.of("token", token));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sai mật khẩu");
    }
//    @PostMapping("/login")
//    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> loginRequest) {
//        // System.out.println("[DEBUG] Login request received: " + loginRequest);
//        String username = loginRequest.get("username");
//        String password = loginRequest.get("password");
//
//        // Kiểm tra đầu vào
//        Map<String, Object> response = new HashMap<>();
//        if (username == null || username.trim().isEmpty()) {
//            response.put("success", false);
//            response.put("message", "Tên đăng nhập là bắt buộc.");
//            System.out.println("[DEBUG] Missing or empty username");
//            return ResponseEntity.badRequest().body(response);
//        }
//        if (password == null || password.trim().isEmpty()) {
//            response.put("success", false);
//            response.put("message", "Mật khẩu là bắt buộc.");
//            System.out.println("[DEBUG] Missing or empty password");
//            return ResponseEntity.badRequest().body(response);
//        }
//
//        response = userService.authenticateUser(username, password);
//        if ((boolean) response.get("success")) {
//            return ResponseEntity.ok(response);
//        } else {
//            return ResponseEntity.badRequest().body(response);
//        }
//    }
}

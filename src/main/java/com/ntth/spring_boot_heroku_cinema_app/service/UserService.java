package com.ntth.spring_boot_heroku_cinema_app.service;

import com.mongodb.MongoException;
import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    @Autowired//Gọi và sử dụng ở bất kỳ class nào được Spring quản lý
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    public String register(User user) {
        // Kiểm tra email tồn tại
        if (userRepository.existsByEmail(user.getEmail())) {
            return "Email đã tồn tại!";
        }
        // Lưu user mới
        user.setPassword(passwordEncoder.encode(user.getPassword()));//Mã hóa mật khẩu trước khi lưu
        userRepository.save(user);
        return "Đăng ký thành công";
    }
    @Retryable(value = {MongoException.class}, maxAttempts = 3, backoff = @Backoff(delay = 5000))
    public Optional<User> findByEmailWithRetry(String email) {
        return userRepository.findByEmail(email);
    }
    public User login(String email, String password) {
        // Tìm user theo email
        return userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))//So sánh mật khẩu người dùng nhập với bản đã mã hóa trong DB
                .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu sai"));
    }

//    //xác thực người dùng dựa trên username và password
//    public Map<String, Object> authenticateUser(String username, String password) {
//        Map<String, Object> response = new HashMap<>();
//        // Tìm user theo username
//        User user = userRepository.findByEmail(username);
//        if (user == null) {
//            response.put("success", false);
//            response.put("message", "Tên đăng nhập không tồn tại.");
//            return response;
//        }
//        // Kiểm tra mật khẩu
//        if (!passwordEncoder.matches(password, user.getPassword())) {
//            response.put("success", false);
//            response.put("message", "Mật khẩu không đúng.");
//            return response;
//        }
//        // Tạo JWT token
//        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().toString());
//
//        response.put("success", true);
//        response.put("message", "Đăng nhập thành công!");
//        response.put("token", token);
//        response.put("user", user);
//        return response;
//    }
}

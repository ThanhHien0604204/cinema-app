package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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

    public User login(String email, String password) {
        // Tìm user theo email
        return userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))//So sánh mật khẩu người dùng nhập với bản đã mã hóa trong DB
                .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu sai"));
    }
}

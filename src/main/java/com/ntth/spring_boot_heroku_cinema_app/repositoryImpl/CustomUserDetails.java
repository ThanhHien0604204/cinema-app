package com.ntth.spring_boot_heroku_cinema_app.repositoryImpl;

import com.ntth.spring_boot_heroku_cinema_app.pojo.User;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Lớp này "bọc" entity User thành UserDetails để Spring Security hiểu.
// Spring Security làm việc với UserDetails (chứa username, password, roles, ...).
public class CustomUserDetails implements UserDetails {

    private final User user; // giữ reference tới User thực trong DB

    public CustomUserDetails(User user) {
        this.user = user;
    }

    // Trả về danh sách quyền (roles) của user.
    // Lưu ý: Spring Security chuẩn dùng tiền tố "ROLE_".
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Ví dụ DB lưu "USER" hoặc "ADMIN" → ta thêm tiền tố ROLE_ khi cấp quyền.
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    // Password đã được mã hoá (BCrypt) trong DB.
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    // Username dùng để đăng nhập (ở đây chọn email làm username).
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    // Các flag trạng thái tài khoản. Nếu không dùng logic khoá/hết hạn → cứ true.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // Giữ lại User gốc nếu cần truy cập thêm thông tin ở controller.
    public User getUser() {
        return user;
    }
}


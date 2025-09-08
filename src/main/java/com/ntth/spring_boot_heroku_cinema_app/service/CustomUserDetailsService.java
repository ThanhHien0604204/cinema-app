package com.ntth.spring_boot_heroku_cinema_app.service;


import com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Service chuẩn để Spring gọi "loadUserByUsername" khi cần lấy UserDetails.
// Dùng email làm "username".
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;

    public CustomUserDetailsService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // Spring sẽ gọi method này với "email" rút ra từ JWT.
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user với email: " + email));
        // Bọc User → CustomUserDetails để Security hiểu.
        return new CustomUserDetails(user);
    }
}


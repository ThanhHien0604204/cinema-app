package com.ntth.spring_boot_heroku_cinema_app.filter;

import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtProvider jwtProvider;
    // Optional: dùng để lấy userId & role từ DB (đúng với log của bạn: findByEmail)
    private final UserRepository userRepository;  // ← Bỏ @Nullable

    public JwtFilter(JwtProvider jwtProvider, UserRepository userRepository) {  // ← Bỏ @Nullable
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String bearer = request.getHeader("Authorization");
            if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
                String token = bearer.substring(7);

                // 1) Kiểm tra token hợp lệ
                if (jwtProvider.validateToken(token)) {

                    // 2) Lấy email từ token
                    String email = jwtProvider.getEmailFromToken(token);

                    // 3) Nếu chưa có Authentication thì thiết lập
                    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
                    if (existing == null) {

                        // 3a) Tìm user trong database
                        Optional<User> optionalUser = userRepository.findByEmail(email);
                        if (optionalUser.isPresent()) {
                            User user = optionalUser.get();

                            // 3b) Tạo JwtUser thay vì CustomUserDetails
                            JwtUser principal = new JwtUser(
                                    user.getId(),
                                    user.getUserName(), // hoặc tên field tương ứng trong User entity
                                    user.getEmail(),
                                    user.getRole(),
                                    user.getPassword() // có thể để null nếu không cần password
                            );

                            // 3c) Tạo authorities từ role
                            List<SimpleGrantedAuthority> authorities =
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));

                            // 3d) Thiết lập Authentication
                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);

                            if (log.isDebugEnabled()) {
                                log.debug("✅ Xác thực thành công: email={}, userId={}, role={}",
                                        email, user.getId(), user.getRole());
                            }
                        } else {
                            log.warn("❌ Không tìm thấy user với email: {}", email);
                        }
                    }
                } else {
                    log.debug("JWT không hợp lệ hoặc đã hết hạn");
                }
            }
        } catch (Exception e) {
            log.error("Lỗi bộ lọc JWT: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}

package com.ntth.spring_boot_heroku_cinema_app.filter;

import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
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
    private final @Nullable UserRepository userRepository;

    public JwtFilter(JwtProvider jwtProvider,
                     @Nullable UserRepository userRepository) {
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

                // 1) validate token
                if (jwtProvider.validateToken(token)) {

                    // 2) lấy email (subject)
                    String email = jwtProvider.getEmailFromToken(token);

                    // 3) nếu chưa có Authentication thì set
                    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
                    if (existing == null) {

                        // 3a) Lấy user từ DB (để có _id và role)
                        String userId = email; // fallback
                        String role = "USER";  // fallback

                        if (userRepository != null) {
                            Optional<User> u = userRepository.findByEmail(email);
                            if (u.isPresent()) {
                                userId = String.valueOf(u.get().getId());
                                // field của bạn là "role" (String) -> ví dụ "USER"
                                if (u.get().getRole() != null) {
                                    role = u.get().getRole().toUpperCase(Locale.ROOT);
                                }
                            } else {
                                log.debug("User not found by email={}, continue with email as userId", email);
                            }
                        }

                        // 3b) principal kiểu JwtUser để @AuthenticationPrincipal dùng được
                        JwtUser principal = new JwtUser(userId, /* username: */ email, email, role);

                        // 3c) authorities từ role
                        List<SimpleGrantedAuthority> authorities =
                                List.of(new SimpleGrantedAuthority("ROLE_" + role));

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        if (log.isDebugEnabled()) {
                            log.debug("Authenticated request: email={}, userId={}, role={}", email, userId, role);
                        }
                    }
                } else {
                    log.debug("JWT invalid or expired");
                }
            }
        } catch (Exception e) {
            // không chặn request, chỉ log cho dễ debug
            log.error("JWT filter error: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}

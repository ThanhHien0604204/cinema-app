
package com.ntth.spring_boot_heroku_cinema_app.filter;

import com.ntth.spring_boot_heroku_cinema_app.service.CustomUserDetailsService;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

// Filter này chạy MỖI REQUEST (một lần) để:
// 1) Đọc header Authorization
// 2) Tách JWT
// 3) Validate JWT
// 4) Lấy email từ JWT → load UserDetails
// 5) Gắn Authentication vào SecurityContext để downstream (controller) biết đã đăng nhập.
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    // Dùng constructor injection cho rõ dependencies (khuyến nghị hiện nay).
    public JwtFilter(JwtProvider jwtProvider, CustomUserDetailsService userDetailsService) {
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1) Lấy header Authorization
        String authHeader = request.getHeader("Authorization");

        // 2) Kiểm tra header có dạng "Bearer <token>" không
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            // Cắt bỏ "Bearer " để lấy ra phần token thực sự
            String token = authHeader.substring(7);

            // 3) Validate chữ ký + hạn token
            if (jwtProvider.validateToken(token)) {

                // 4) Trích xuất email (subject) từ token
                String email = jwtProvider.extractEmail(token);

                // Tránh set lại Authentication nếu context đã có (ví dụ filter chạy lần nữa)
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // 5) Load UserDetails (lấy roles, password, username) từ DB theo email
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // 6) Tạo Authentication chứa principal (userDetails) + authorities (ROLE_USER/ADMIN)
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities() // rất quan trọng để @PreAuthorize / hasRole hoạt động
                            );

                    // Gắn thêm thông tin chi tiết từ request (IP, sessionId...) nếu cần
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 7) Đặt Authentication vào SecurityContext → coi như "đã đăng nhập"
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            // (Tuỳ chọn) else: token hết hạn/sai signature → có thể trả 401 ở đây nếu muốn chặn sớm.
        }

        // 8) Tiếp tục đẩy request xuống các filter/controller phía sau
        filterChain.doFilter(request, response);
    }
}

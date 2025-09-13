package com.ntth.spring_boot_heroku_cinema_app.Config;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtFilter;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Review;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
@EnableRetry
@EnableMethodSecurity(prePostEnabled = true)
@EnableTransactionManagement
@ComponentScan(basePackages = {
        "com.ntth.spring_boot_heroku_cinema_app.controller",
        "com.ntth.spring_boot_heroku_cinema_app.repositorie",
        "com.ntth.spring_boot_heroku_cinema_app.repositoryImpl",
        "com.ntth.spring_boot_heroku_cinema_app.service",
        "com.ntth.spring_boot_heroku_cinema_app.filter"
})
public class SpringSecurityConfigs {

    @Autowired
    private JwtFilter jwtFilter;

    public SpringSecurityConfigs(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private Environment environment;


    //Tạo bean để Spring quản lý
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

//    @Bean
//    public Cloudinary cloudinary() {
//        Cloudinary cloudinary
//                = new Cloudinary(ObjectUtils.asMap(
//                "cloud_name", "dn0q3mhbs",
//                "api_key", "111668583654845",
//                "api_secret", "tPx6pczCFOrKqMnK22LYrhtq2MA",
//                "secure", true));
//        return cloudinary;
//    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws
            Exception {
        http// Dùng JWT (stateless) → tắt CSRF
                .csrf(csrf -> csrf.disable())//Tắt tính năng CSRF
                // đang xây dựng hệ thống dùng JWT, không dùng session
                //Bắt đầu cấu hình quyền truy cập cho từng endpoint
                .authorizeHttpRequests(requests
                        -> requests
                        //toàn bộ các request đến /api/** được truy cập công khai (không cần đăng nhập)
//                        .requestMatchers("/api/**").permitAll()
                                .requestMatchers("/error").permitAll()
                                .requestMatchers("/api/payments/zalopay/ipn").permitAll()
                        // Public: login
                        .requestMatchers(HttpMethod.POST, "/api/login","/api/register").permitAll()

                        // Public: đọc dữ liệu chung
                        .requestMatchers(HttpMethod.GET,
                                "/api/movies/**",
                                "/api/genres/**",
                                "/api/users/**"             // GET /api/users/{id} và GET /api/users?ids=...
                        ).permitAll()

                        // Public: list & summary review theo movie
                        .requestMatchers(HttpMethod.GET,
                                "/api/reviews/movie/*",
                                "/api/reviews/movie/*/summary"
                        ).permitAll()

                        // Cần đăng nhập: review của tôi / upsert / delete
                        .requestMatchers(HttpMethod.GET,  "/api/reviews/movie/*/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reviews").authenticated()
                        .requestMatchers(HttpMethod.DELETE,"/api/reviews/**").authenticated()

                        // Cần đăng nhập: chỉnh sửa hồ sơ & đổi mật khẩu
                        .requestMatchers(HttpMethod.PUT,   "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/users/me/password").authenticated()
                                .requestMatchers("/api/showtimes/**",
                                        "/api/bookings/**",
                                        "/api/payments/zalopay/create",
                                        "/api/bookings/zalopay").authenticated()

                        // ADMIN: Quản lý phim, thể loại, người dùng
//                        .requestMatchers(HttpMethod.GET, "/products").hasRole("ADMIN")
//                        .requestMatchers(HttpMethod.GET,
//                                "/products/**").hasAnyRole("USER", "ADMIN")
//                        .anyRequest().authenticated())//Tất cả request khác ngoài /api/** bắt buộc phải đăng nhập
                        // Các request khác: cho qua
                        .anyRequest().permitAll()
                )
                // Xử lý 401/403 gọn gàng
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) // 401 khi chưa đăng nhập
                )
                // Chèn JwtFilter "trước" filter mặc định UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout.logoutSuccessUrl("/login").permitAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*")); // Cho phép tất cả nguồn gốc khi test
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    // Optional: dùng để @Autowired AuthenticationManager nếu cần
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    @Bean
    ApplicationRunner initIndexes(MongoTemplate mongo) {
        return args -> {
            IndexOperations ops = mongo.indexOps(Review.class);
            ops.ensureIndex(new Index().on("movieId", Sort.Direction.ASC)
                    .on("userId", Sort.Direction.ASC)
                    .unique());
            ops.ensureIndex(new Index().on("movieId", Sort.Direction.ASC));
        };
    }
    //tạo index cho movieId để aggregate nhanh
    @Bean ApplicationRunner initIdx(MongoTemplate mongo) {
        return args -> mongo.indexOps(Review.class).ensureIndex(new Index().on("movieId", Sort.Direction.ASC));
    }

}

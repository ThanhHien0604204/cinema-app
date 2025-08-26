/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ntth.spring_boot_heroku_cinema_app.Config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
//import com.ntth.filter.JwtFilter;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.List;


@Configuration
@EnableWebSecurity
@EnableTransactionManagement
@ComponentScan(basePackages = {
        "com.ntth.spring_boot_heroku_cinema_app.controller",
        "com.ntth.spring_boot_heroku_cinema_app.repositorie",
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
                        //.requestMatchers("/api/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/register", "/api/login").permitAll()
//                        .requestMatchers(HttpMethod.GET, "/products").hasRole("ADMIN")
//                        .requestMatchers(HttpMethod.GET,
//                                "/products/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())//Tất cả request khác ngoài /api/** bắt buộc phải đăng nhập
//                .formLogin(form -> form.loginPage("/login")
//                        .loginProcessingUrl("/login")
//                        .defaultSuccessUrl("/", true)
//                        .failureUrl("/login?error=true").permitAll())
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
}

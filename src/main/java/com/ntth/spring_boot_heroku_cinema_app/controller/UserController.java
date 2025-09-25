package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.mongodb.MongoException;
import com.ntth.spring_boot_heroku_cinema_app.dto.*;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtProvider;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails;
import com.ntth.spring_boot_heroku_cinema_app.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

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

    public UserController(UserRepository userRepo, BCryptPasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        try {
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                return ResponseEntity.badRequest().body("Email không được để trống");
            }
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body("Mật khẩu không được để trống");
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setRole("USER");
            userRepo.save(user);
            return ResponseEntity.ok("Đăng ký thành công!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Lỗi đăng ký: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            System.out.println("Starting login process for email: " + request.getEmail());
            if (request == null || request.getEmail() == null || request.getEmail().isEmpty()) {
                return ResponseEntity.badRequest().body("Email không được để trống");
            }
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body("Mật khẩu không được để trống");
            }
            System.out.println("Querying MongoDB for user with email: " + request.getEmail());
//            User user = userRepo.findByEmail(request.getEmail())
            User user = userService.findByEmailWithRetry(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Không tồn tại email"));
            System.out.println("User found in MongoDB: " + user.getEmail());

            if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                String token = jwtProvider.generateToken(user.getEmail());
                System.out.println("Token generated: " + token);
                return ResponseEntity.ok(Map.of("token", token));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sai mật khẩu");
//        } catch (HttpMessageNotReadableException e) {
//            return ResponseEntity.badRequest().body("Request body không hợp lệ hoặc bị thiếu: " + e.getMessage());
//        }
        } catch (com.mongodb.MongoTimeoutException e) {
            System.out.println("MongoDB timeout: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Lỗi kết nối MongoDB: Timeout");
        } catch (com.mongodb.MongoException e) {
            System.out.println("MongoDB error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Lỗi kết nối MongoDB: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Runtime error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tồn tại email");
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi server: " + e.getMessage());
        }
    }

    @GetMapping("/user/me")
    public ResponseEntity<User> getCurrentUser(@RequestHeader("Authorization") String token) {
        // Xác thực token
        String email = jwtProvider.getEmailFromToken(token.replace("Bearer ", ""));
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        return ResponseEntity.ok(user);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<PublicUserResponse> getUserById(@PathVariable String userId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(PublicUserResponse.of(user));
    }
    // Batch: /api/users?ids=1,2,3 hoặc /api/users?ids=1&ids=2
    @GetMapping("/users")
    public ResponseEntity<List<PublicUserResponse>> getUsersByIds(
            @RequestParam(value = "ids", required = false) List<String> idsParam,  // hỗ trợ ids lặp lại
            @RequestParam(value = "ids", required = false) String idsCsv           // hỗ trợ ids=1,2,3
    ) {
        // Gom tất cả về 1 list
        Set<String> ids = new LinkedHashSet<>();
        if (idsParam != null) {
            for (String v : idsParam) {
                if (v != null && !v.isBlank()) {
                    // v có thể là "1,2,3"
                    for (String s : v.split(",")) {
                        s = s.trim();
                        if (!s.isEmpty()) ids.add(s);
                    }
                }
            }
        }
        if (idsCsv != null && !idsCsv.isBlank()) {
            for (String s : idsCsv.split(",")) {
                s = s.trim();
                if (!s.isEmpty()) ids.add(s);
            }
        }

        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'ids' is required");
        }

        var users = userRepo.findAllById(ids);
        var out = users.stream().map(PublicUserResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    // 1) Cập nhật thông tin người dùng hiện tại (userName, email)
    @PutMapping("/users/me")
    public PublicUserResponse updateMe(@Valid @RequestBody UpdateUserRequest req,
                                       @AuthenticationPrincipal JwtUser me) {
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        String userId = me.getUserId();

        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (req.userName != null) {
            String name = req.userName.trim();
            if (!name.isEmpty()) u.setUserName(name);
        }
        if (req.email != null) {
            String emailNew = req.email.trim().toLowerCase();
            if (!emailNew.equalsIgnoreCase(u.getEmail())) {
                if (userRepo.existsByEmail(emailNew))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
                u.setEmail(emailNew);
                // Nếu JWT dùng sub=email: app nên logout hoặc nhận token mới
            }
        }
        userRepo.save(u);
        return PublicUserResponse.of(u);
    }
    // 2) Đổi mật khẩu người dùng hiện tại
    @PatchMapping(value = "/users/me/password", consumes = "application/json")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                               @AuthenticationPrincipal JwtUser me) {
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");

        String userId = me.getUserId();
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!req.newPassword.equals(req.confirmNewPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xác nhận mật khẩu không khớp");
        }
        String encoded = u.getPassword();
        if (encoded == null || encoded.isBlank() || !passwordEncoder.matches(req.currentPassword, encoded)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(req.newPassword, encoded)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được trùng mật khẩu cũ");
        }

        u.setPassword(passwordEncoder.encode(req.newPassword));
        userRepo.save(u);
        return ResponseEntity.noContent().build();
    }

    private String resolveUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null ||
                "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Object principal = auth.getPrincipal();

        // Thêm hỗ trợ cho JwtUser
        if (principal instanceof JwtUser jwtUser) {
            return jwtUser.getUserId();
        }

        // Trường hợp bạn dùng CustomUserDetails (nếu vẫn giữ)
        if (principal instanceof com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails cud) {
            return cud.getUser().getId();
        }
        // Trường hợp là UserDetails chuẩn (username = email)
        if (principal instanceof UserDetails ud) {
            String email = ud.getUsername();
            return userRepo.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                    .getId();
        }
        // Trường hợp là String (đa phần là email)
        if (principal instanceof String email) {
            return userRepo.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                    .getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported principal");
    }

    //ADMIN

    // GET /api (with optional search)
    @GetMapping("/user/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PublicUserResponse> getAllUsers(@RequestParam(required = false) String search) {
        List<User> users;
        if (search != null && !search.isEmpty()) {
            users = userRepo.findByUserNameContainingIgnoreCase(search);
        } else {
            users = userRepo.findAll();
        }
        // Return DTO to avoid exposing password
        return users.stream().map(PublicUserResponse::of).collect(Collectors.toList());
    }

    // GET /api/{id}
    @GetMapping("/user/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PublicUserResponse getUserByIdADMIN(@PathVariable String id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return PublicUserResponse.of(user);
    }

    // POST /api
    @PostMapping("/user/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public PublicUserResponse createUser(@RequestBody User user) {
        if (userRepo.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        // Hash password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepo.save(user);
        return PublicUserResponse.of(savedUser);
    }

    // PUT /api/{id}
    @PutMapping("/user/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PublicUserResponse updateUser(@PathVariable String id, @RequestBody User updatedUser) {
        User existingUser = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Update fields
        if (updatedUser.getUserName() != null) {
            existingUser.setUserName(updatedUser.getUserName());
        }
        if (updatedUser.getEmail() != null) {
            if (!updatedUser.getEmail().equals(existingUser.getEmail()) && userRepo.existsByEmail(updatedUser.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
            }
            existingUser.setEmail(updatedUser.getEmail());
        }
        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }
        if (updatedUser.getRole() != null) {
            existingUser.setRole(updatedUser.getRole());
        }

        User savedUser = userRepo.save(existingUser);
        return PublicUserResponse.of(savedUser);
    }

    // DELETE /api/users/{id}
    @DeleteMapping("/user/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        if (!userRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepo.deleteById(id);
    }
}

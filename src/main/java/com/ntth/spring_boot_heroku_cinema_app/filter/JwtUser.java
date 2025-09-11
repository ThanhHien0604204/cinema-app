package com.ntth.spring_boot_heroku_cinema_app.filter;

public class JwtUser {

    private String userId;
    private String username;
    private String email;
    private String role;

    public JwtUser(String userId, String username, String email,String role) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
    }
    public JwtUser() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}

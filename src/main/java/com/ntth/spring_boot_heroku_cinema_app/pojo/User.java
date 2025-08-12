package com.ntth.spring_boot_heroku_cinema_app.pojo;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user")  // tên collection trong MongoDB
public class User {
    @Id
    private String id; // Spring sẽ tự convert ObjectId thành String

    private String email;
    private String userName;
    private String password;  // mã hóa bằng BCrypt
    private String role;        //  USER / ADMIN

    public User(String id, String email, String userName, String password, String role) {
        this.id = id;
        this.userName = userName;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

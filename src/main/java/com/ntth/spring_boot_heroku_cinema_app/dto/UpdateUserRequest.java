package com.ntth.spring_boot_heroku_cinema_app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class UpdateUserRequest {
    // Cho phép null -> không đổi; nếu có giá trị thì validate
    @Size(min = 2, max = 50, message = "userName phải 2-50 ký tự")
    public String userName;

    @Email(message = "Email không hợp lệ")
    public String email;
}
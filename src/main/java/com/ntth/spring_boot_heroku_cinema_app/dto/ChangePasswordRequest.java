package com.ntth.spring_boot_heroku_cinema_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {
    @NotBlank
    public String currentPassword;

    @NotBlank @Size(min = 6, max = 100, message = "Mật khẩu mới phải >= 6 ký tự")
    public String newPassword;

    @NotBlank public String confirmNewPassword;
}

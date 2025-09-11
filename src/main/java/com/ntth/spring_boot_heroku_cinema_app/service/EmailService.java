package com.ntth.spring_boot_heroku_cinema_app.service;

//dùng để quên mật khẩu
public interface EmailService {
    void send(String to, String subject, String text);
}

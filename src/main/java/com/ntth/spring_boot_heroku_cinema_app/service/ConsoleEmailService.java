package com.ntth.spring_boot_heroku_cinema_app.service;

import org.springframework.stereotype.Service;

@Service
public class ConsoleEmailService implements EmailService {
    @Override
    public void send(String to, String subject, String text) {
        System.out.println("[EMAIL] to=" + to + " | sub=" + subject + " | body=" + text);
    }
}

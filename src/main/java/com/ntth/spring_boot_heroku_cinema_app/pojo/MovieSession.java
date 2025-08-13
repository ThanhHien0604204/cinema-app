package com.ntth.spring_boot_heroku_cinema_app.pojo;

import java.util.List;

public class MovieSession {
    private String date;
    private String time;
    private String room;
    private List<String> availableSeats;  // ["A1", "A2", "B1",...]
}

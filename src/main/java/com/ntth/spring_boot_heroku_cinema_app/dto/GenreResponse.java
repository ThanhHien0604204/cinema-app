package com.ntth.spring_boot_heroku_cinema_app.dto;

public class GenreResponse {
    private String id;
    private String name;

    // builder-like ctor
    public GenreResponse(String id, String name) {
        this.id = id; this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }

}

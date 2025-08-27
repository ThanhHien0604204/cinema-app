package com.ntth.spring_boot_heroku_cinema_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GenreRequest {
    @NotBlank
    @Size(min = 2, max = 50)
    private String name; // Ví dụ: "Hành động"

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

}

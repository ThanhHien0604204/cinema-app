package com.ntth.spring_boot_heroku_cinema_app.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rooms")
public class Room {
    @Id
    private String id;

    @NotBlank(message = "Room name cannot be blank")
    private String roomName;

    @NotBlank(message = "Cinema ID cannot be blank")
    private String cinemaId; // ID của rạp mà phòng chiếu thuộc về

    @NotNull(message = "Total seats cannot be null")
    @Positive(message = "Total seats must be positive")
    private Integer totalSeats;    // Sức chứa của phòng chiếu

    @NotNull(message = "Rows cannot be null")
    @PositiveOrZero(message = "Rows must be non-negative")
    private Integer rows;

    @NotNull(message = "Columns cannot be null")
    @PositiveOrZero(message = "Columns must be non-negative")
    private Integer columns;

    public Room() {}

    public Room(String id, String name, String cinemaId, int capacity, int row, int col) {
        this.id = id;
        this.roomName = name;
        this.cinemaId = cinemaId;
        this.totalSeats = capacity;
        this.rows = row;
        this.columns = col;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getCinemaId() { return cinemaId; }
    public void setCinemaId(String cinemaId) { this.cinemaId = cinemaId; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    public Integer getRows() { return rows; }
    public void setRows(Integer rows) { this.rows = rows; }

    public Integer getColumns() { return columns; }
    public void setColumns(Integer columns) { this.columns = columns; }
}
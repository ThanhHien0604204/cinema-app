package com.ntth.spring_boot_heroku_cinema_app.pojo;

public class Room {
    private String id;
    private String roomName;
    private String cinemaId; // ID của rạp mà phòng chiếu thuộc về
    private int totalSeats;    // Sức chứa của phòng chiếu
    private int rows;
    private int columns;


    public Room() {}

    public Room(String id, String name, String cinemaId, int capacity,int row, int col) {
        this.id = id;
        this.roomName = name;
        this.cinemaId = cinemaId;
        this.totalSeats = capacity;
        this.rows=row;
        this.columns=col;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getCinemaId() {
        return cinemaId;
    }

    public void setCinemaId(String cinemaId) {
        this.cinemaId = cinemaId;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }
}

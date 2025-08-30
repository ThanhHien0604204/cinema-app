package com.ntth.spring_boot_heroku_cinema_app.pojo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cinema")
public class Cinema {

    @Id
    private String id;  // ObjectId sẽ tự convert sang String

    private String name;
    private String address;
    private int numberOfRooms;  // Giả sử là int, dù trong dữ liệu có thể là String

    // Constructors
    public Cinema() {}

    public Cinema(String id, String name, String address, int numberOfRooms) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.numberOfRooms = numberOfRooms;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getNumberOfRooms() {
        return numberOfRooms;
    }

    public void setNumberOfRooms(int numberOfRooms) {
        this.numberOfRooms = numberOfRooms;
    }
}

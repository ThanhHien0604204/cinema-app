package com.ntth.spring_boot_heroku_cinema_app.pojo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document("seat_ledger")
public class SeatLedger {
    @Id
    private String id;             // format: showtimeId + "#" + seat
    private String showtimeId;
    private String seat;

    @Field("status")
    private SeatState status;       // FREE/HOLD/CONFIRMED

    private String refType;        // LOCK|BOOKING
    private String refId;          // holdId|bookingId
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;     // chỉ dùng cho HOLD

    public String getShowtimeId() {
        return showtimeId;
    }

    public void setShowtimeId(String showtimeId) {
        this.showtimeId = showtimeId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
    }

    public SeatState getStatus() {
        return status;
    }

    public void setStatus(SeatState status) {
        this.status = status;
    }
    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
